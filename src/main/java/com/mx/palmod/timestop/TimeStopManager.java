package com.mx.palmod.timestop;

import com.mx.palmod.behavior.PalBehavior;
import com.mx.palmod.behavior.PalBehaviorManager;
import com.mx.palmod.stats.PalStats;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ZA WARUDO — stops time in a radius around the caster.
 *
 * There is no single "pause the world except me" switch in 1.20.1, so the
 * freeze runs on two tracks:
 *  - TRACK A (living mobs): the cancelable LivingTickEvent is canceled for
 *    every frozen mob — AI, gravity, animations, effects all halt, and they
 *    resume cleanly with their old momentum when time flows again.
 *  - TRACK B (projectiles, TNT, falling blocks, items): no cancelable tick
 *    event exists, so a ServerTick sweep re-clamps them every tick (zero
 *    delta + no gravity, TNT fuse held) and caches their original state to
 *    restore on resume — arrows hang mid-air, then keep flying.
 *
 * Damage the caster deals to frozen victims is BANKED and applied in one
 * burst when the freeze expires ("you were already dead").
 *
 * Safety: hard expiry by game time, plus resume-all on caster logout and
 * server stop — a freeze can never be left dangling in the world.
 */
public final class TimeStopManager {

    private record NonLivingState(Vec3 delta, boolean noGravity) {}

    private static final class Freeze {
        final UUID casterId;
        final ResourceKey<Level> dimension;
        final Vec3 center;
        final double radius;
        final long endGameTime;
        final Map<UUID, NonLivingState> nonLiving = new HashMap<>();
        final Map<UUID, Float> bankedDamage = new HashMap<>();

        Freeze(UUID casterId, ResourceKey<Level> dimension, Vec3 center, double radius, long endGameTime) {
            this.casterId = casterId;
            this.dimension = dimension;
            this.center = center;
            this.radius = radius;
            this.endGameTime = endGameTime;
        }
    }

    /** casterId -> active freeze (one per caster). */
    private static final Map<UUID, Freeze> ACTIVE = new ConcurrentHashMap<>();

    /** True while the resume burst applies banked damage (guards re-entrant procs). */
    private static boolean applyingBurst = false;

    // Persistent markers so frozen state survives chunk unload mid-freeze
    private static final String KEY_TS_FROZEN = "PalTSFrozen";
    private static final String KEY_TS_NOGRAV = "PalTSNoGrav";
    private static final String KEY_TS_DELTA = "PalTSDelta";

    private TimeStopManager() {}

    public static boolean isApplyingBurst() {
        return applyingBurst;
    }

    // ──────────────────────────────────────────────────────────────
    //  Activation (fires on summon — see PalSphereProjectile.handleSummoning)
    // ──────────────────────────────────────────────────────────────

    /**
     * Fires the freeze the instant the pal is summoned, centered on the PAL
     * (where it lands) instead of the caster — the player picks the moment and
     * the spot by choosing when/where to throw the sphere. Cooldown is tracked
     * on the sphere; a too-soon or too-hungry summon just spawns the pal
     * without the freeze. Never blocks the summon itself.
     */
    public static void tryActivateOnSummon(ServerPlayer caster, ServerLevel level, Mob pal,
                                           CompoundTag sphereTag, PalBehavior behavior) {
        if (behavior.getTimeStopDurationTicks() <= 0) return;
        if (ACTIVE.containsKey(caster.getUUID())) return; // one freeze per caster

        long now = level.getGameTime();
        long cooldownUntil = sphereTag.getLong("TimeStopCooldownUntil");
        if (now < cooldownUntil) {
            caster.displayClientMessage(Component.literal(
                    "⏱ Time Stop still recharging (" + ((cooldownUntil - now) / 20) + "s)"), true);
            return;
        }
        float hungerCost = behavior.getTimeStopHungerCost();
        if (PalStats.getHunger(pal) < hungerCost) {
            caster.displayClientMessage(Component.literal(
                    pal.getName().getString() + " is too hungry to stop time."), true);
            return;
        }

        PalStats.modifyHunger(pal, -hungerCost);
        sphereTag.putLong("TimeStopCooldownUntil", now + behavior.getTimeStopCooldownTicks());

        Vec3 center = pal.position();
        ACTIVE.put(caster.getUUID(), new Freeze(caster.getUUID(), level.dimension(),
                center, behavior.getTimeStopRadius(), now + behavior.getTimeStopDurationTicks()));

        level.playSound(null, pal.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 2.0F, 0.5F);
        spawnRing(level, center, behavior.getTimeStopRadius());
        caster.displayClientMessage(Component.literal("⏱ ZA WARUDO! Time has stopped."), true);
    }

    // ──────────────────────────────────────────────────────────────
    //  TRACK A — frozen living entities (LivingTickEvent canceled)
    // ──────────────────────────────────────────────────────────────

    public static boolean isFrozen(LivingEntity entity) {
        if (ACTIVE.isEmpty() || entity instanceof Player) return false;
        for (Freeze freeze : ACTIVE.values()) {
            if (isFrozenBy(freeze, entity)) return true;
        }
        return false;
    }

    private static boolean isFrozenBy(Freeze freeze, LivingEntity entity) {
        if (!entity.level().dimension().equals(freeze.dimension)) return false;
        if (entity.position().distanceToSqr(freeze.center) > freeze.radius * freeze.radius) return false;
        // The caster's own pals move freely inside their owner's stopped time
        CompoundTag data = entity.getPersistentData();
        return !(data.hasUUID("PalOwner") && data.getUUID("PalOwner").equals(freeze.casterId));
    }

    // ──────────────────────────────────────────────────────────────
    //  Damage banking ("you were already dead")
    // ──────────────────────────────────────────────────────────────

    /**
     * Banks damage the caster deals to a frozen victim. Returns true when the
     * hit was banked (caller cancels the event).
     */
    public static boolean bank(ServerPlayer caster, LivingEntity victim, float amount) {
        Freeze freeze = ACTIVE.get(caster.getUUID());
        if (freeze == null || !isFrozenBy(freeze, victim)) return false;
        freeze.bankedDamage.merge(victim.getUUID(), amount, Float::sum);
        // A frozen mob never counts its hurt-invulnerability down — reset it so
        // every follow-up hit during the stop still registers and banks.
        victim.invulnerableTime = 0;
        victim.level().playSound(null, victim.blockPosition(),
                SoundEvents.AMETHYST_CLUSTER_HIT, SoundSource.PLAYERS, 1.0F, 0.6F);
        return true;
    }

    // ──────────────────────────────────────────────────────────────
    //  TRACK B + expiry — driven from ServerTickEvent(END)
    // ──────────────────────────────────────────────────────────────

    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;
        for (Freeze freeze : ACTIVE.values()) {
            ServerLevel level = server.getLevel(freeze.dimension);
            if (level == null) {
                ACTIVE.remove(freeze.casterId);
                continue;
            }
            if (level.getGameTime() >= freeze.endGameTime) {
                resume(level, freeze);
                continue;
            }
            sweepNonLiving(level, freeze);
        }
    }

    private static void sweepNonLiving(ServerLevel level, Freeze freeze) {
        Entity caster = level.getPlayerByUUID(freeze.casterId);
        AABB box = AABB.ofSize(freeze.center, freeze.radius * 2, freeze.radius * 2, freeze.radius * 2);
        double radiusSqr = freeze.radius * freeze.radius;
        List<Entity> entities = level.getEntities((Entity) null, box, e ->
                !(e instanceof LivingEntity)
                        && e.position().distanceToSqr(freeze.center) <= radiusSqr
                        && isFreezableNonLiving(e, caster));
        for (Entity entity : entities) {
            if (!freeze.nonLiving.containsKey(entity.getUUID())) {
                freeze.nonLiving.put(entity.getUUID(),
                        new NonLivingState(entity.getDeltaMovement(), entity.isNoGravity()));
                // Mirror the original state into the entity's own NBT so a chunk
                // unload mid-freeze can't strand it floating forever — it restores
                // itself on rejoin (see restoreIfStale).
                CompoundTag data = entity.getPersistentData();
                data.putBoolean(KEY_TS_FROZEN, true);
                data.putBoolean(KEY_TS_NOGRAV, entity.isNoGravity());
                Vec3 delta = entity.getDeltaMovement();
                CompoundTag deltaTag = new CompoundTag();
                deltaTag.putDouble("x", delta.x);
                deltaTag.putDouble("y", delta.y);
                deltaTag.putDouble("z", delta.z);
                data.put(KEY_TS_DELTA, deltaTag);
            }
            entity.setDeltaMovement(Vec3.ZERO);
            entity.setNoGravity(true);
            if (entity instanceof PrimedTnt tnt) {
                tnt.setFuse(tnt.getFuse() + 1); // hold the fuse — no mid-freeze booms
            }
        }
    }

    private static boolean isFreezableNonLiving(Entity entity, Entity caster) {
        if (entity instanceof com.mx.palmod.entity.PalSphereProjectile sphere) {
            // The caster's own thrown spheres fly freely — catch mobs mid-stop!
            return caster == null || sphere.getOwner() != caster;
        }
        return entity instanceof Projectile
                || entity instanceof FallingBlockEntity
                || entity instanceof PrimedTnt
                || entity instanceof ItemEntity;
    }

    /**
     * Called when any entity joins the level: an entity still carrying frozen
     * markers with no active freeze covering it was stranded by a chunk unload —
     * restore its original motion state.
     */
    public static void restoreIfStale(Entity entity) {
        CompoundTag data = entity.getPersistentData();
        if (!data.getBoolean(KEY_TS_FROZEN)) return;
        for (Freeze freeze : ACTIVE.values()) {
            if (entity.level().dimension().equals(freeze.dimension)
                    && entity.position().distanceToSqr(freeze.center) <= freeze.radius * freeze.radius) {
                return; // still legitimately frozen
            }
        }
        restoreFromMarkers(entity);
    }

    private static void restoreFromMarkers(Entity entity) {
        CompoundTag data = entity.getPersistentData();
        if (data.contains(KEY_TS_DELTA)) {
            CompoundTag deltaTag = data.getCompound(KEY_TS_DELTA);
            entity.setDeltaMovement(new Vec3(
                    deltaTag.getDouble("x"), deltaTag.getDouble("y"), deltaTag.getDouble("z")));
        }
        entity.setNoGravity(data.getBoolean(KEY_TS_NOGRAV));
        data.remove(KEY_TS_FROZEN);
        data.remove(KEY_TS_NOGRAV);
        data.remove(KEY_TS_DELTA);
    }

    // ──────────────────────────────────────────────────────────────
    //  Resume — time flows again
    // ──────────────────────────────────────────────────────────────

    private static void resume(ServerLevel level, Freeze freeze) {
        ACTIVE.remove(freeze.casterId);

        // Restore non-living momentum: arrows resume mid-flight
        for (UUID entityId : freeze.nonLiving.keySet()) {
            Entity entity = level.getEntity(entityId);
            if (entity != null && entity.isAlive()) {
                restoreFromMarkers(entity);
            }
            // Unloaded entities restore themselves via restoreIfStale on rejoin
        }

        // The banked burst — everything the caster did lands at once.
        // applyingBurst guards re-entrant handlers (storm proc would double-bill).
        ServerPlayer caster = (ServerPlayer) level.getPlayerByUUID(freeze.casterId);
        applyingBurst = true;
        try {
            for (Map.Entry<UUID, Float> entry : freeze.bankedDamage.entrySet()) {
                if (!(level.getEntity(entry.getKey()) instanceof LivingEntity victim) || !victim.isAlive()) continue;
                victim.invulnerableTime = 0;
                victim.hurt(caster != null
                                ? level.damageSources().playerAttack(caster)
                                : level.damageSources().magic(),
                        entry.getValue());
                level.sendParticles(ParticleTypes.CRIT,
                        victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(),
                        20, 0.4, 0.4, 0.4, 0.3);
            }
        } finally {
            applyingBurst = false;
        }

        level.playSound(null, net.minecraft.core.BlockPos.containing(freeze.center),
                SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 2.0F, 1.2F);
        if (caster != null) {
            caster.displayClientMessage(Component.literal("⏱ ...and time flows again."), true);
        }
    }

    /** Caster logged out — end their freeze immediately (never leave the world frozen). */
    public static void endForCaster(UUID casterId, MinecraftServer server) {
        Freeze freeze = ACTIVE.get(casterId);
        if (freeze == null || server == null) return;
        ServerLevel level = server.getLevel(freeze.dimension);
        if (level != null) {
            resume(level, freeze);
        } else {
            ACTIVE.remove(casterId);
        }
    }

    /** Server stopping — resume everything. */
    public static void resumeAll(MinecraftServer server) {
        for (Freeze freeze : ACTIVE.values()) {
            ServerLevel level = server.getLevel(freeze.dimension);
            if (level != null) {
                resume(level, freeze);
            } else {
                ACTIVE.remove(freeze.casterId);
            }
        }
    }

    // ──────────────────────────────────────────────────────────────

    private static void spawnRing(ServerLevel level, Vec3 center, double radius) {
        for (int i = 0; i < 48; i++) {
            double angle = (Math.PI * 2 * i) / 48;
            double r = Math.min(radius, 6.0);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                    center.x + Math.cos(angle) * r, center.y + 1.0, center.z + Math.sin(angle) * r,
                    1, 0.0, 0.2, 0.0, 0.02);
        }
        level.sendParticles(ParticleTypes.END_ROD, center.x, center.y + 1.2, center.z, 30, 0.6, 0.8, 0.6, 0.05);
    }
}
