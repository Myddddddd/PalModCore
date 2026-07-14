package com.mx.palmod.pal;

import com.mojang.logging.LogUtils;
import com.mx.palmod.behavior.PalBehavior;
import com.mx.palmod.behavior.PalBehaviorManager;
import com.mx.palmod.entity.PalSphereProjectile;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Maps behaviors.wild.type strings (the "op"/"predator_locked" signature move) to a
 * defense implementation. Adding a new wild type = one {@link #register(String, WildDefense)}
 * call from any mod's init (constructor or FMLCommonSetupEvent) — no Palmod source changes
 * required. See {@link WildDefense} for the hook shape; a defense only needs to override
 * whichever hooks it actually uses.
 */
public final class WildDefenseRegistry {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * All hooks default to a no-op / "didn't act" so a defense only implements what it
     * needs (e.g. a hide-type defense only overrides {@link #tick}, a dodge-type only
     * overrides {@link #onSphereHit}).
     */
    public interface WildDefense {
        /** Ambient ~20-tick hook — telegraphs, hide/reveal state, auras. */
        default void tick(Mob mob, PalBehavior behavior, ServerLevel level) {}

        /**
         * A thrown Pal Sphere just struck this mob directly (a catch attempt is about to
         * be rolled). Return true to consume the sphere and cancel the attempt outright —
         * the mob "defended" against this specific throw.
         */
        default boolean onSphereHit(Mob mob, PalBehavior behavior, PalSphereProjectile sphere, ServerLevel level) {
            return false;
        }

        /**
         * An in-flight sphere is within scanning range of this mob, before it has hit
         * anything. Return true to consume the sphere pre-emptively (e.g. freezing it
         * mid-air). Called every other tick against every wild mob with a registered
         * defense within ~32 blocks of the sphere, so keep this cheap.
         */
        default boolean onSphereNearby(Mob mob, PalBehavior behavior, PalSphereProjectile sphere, ServerLevel level) {
            return false;
        }

        /** A hit landed on this mob. Return true to cancel the damage entirely. */
        default boolean onDamage(Mob mob, PalBehavior behavior, LivingHurtEvent event, ServerLevel level) {
            return false;
        }
    }

    private static final Map<String, WildDefense> DEFENSES = new HashMap<>();

    static {
        register("time_stop", new TimeStopDefense());
        register("blink", new BlinkDefense());
        register("storm_dodge", new StormDodgeDefense());
        register("damage_immune", new DamageImmuneDefense());
        register("shy_hide", new HideDefense(false));
        register("ambush_hide", new HideDefense(true));
    }

    private WildDefenseRegistry() {}

    public static void register(String wildType, WildDefense defense) {
        DEFENSES.put(wildType, defense);
    }

    @Nullable
    public static WildDefense get(String wildType) {
        return DEFENSES.get(wildType);
    }

    // ── Built-in defenses ────────────────────────────────────────────────

    /** ZA WARUDO: freezes an incoming sphere in its radius, drops it as an item, blinks away. */
    private static final class TimeStopDefense implements WildDefense {
        @Override
        public void tick(Mob mob, PalBehavior behavior, ServerLevel level) {
            CompoundTag data = mob.getPersistentData();
            if (level.getGameTime() >= data.getLong(WildCatchManager.KEY_ZW_READY_AT)) {
                level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                        mob.getX(), mob.getY() + mob.getBbHeight() * 0.5, mob.getZ(), 3, 0.3, 0.3, 0.3, 0.02);
            }
        }

        @Override
        public boolean onSphereNearby(Mob mob, PalBehavior behavior, PalSphereProjectile sphere, ServerLevel level) {
            // The caller's scan box caps the effective radius at 32
            double radius = Math.min(behavior.getWildOpRadius(), 32.0);
            if (mob.distanceToSqr(sphere) > radius * radius) return false;
            CompoundTag data = mob.getPersistentData();
            if (level.getGameTime() < data.getLong(WildCatchManager.KEY_ZW_READY_AT)) return false;

            data.putLong(WildCatchManager.KEY_ZW_READY_AT, level.getGameTime() + behavior.getWildOpCooldownTicks());
            level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                    sphere.getX(), sphere.getY(), sphere.getZ(), 40, 0.6, 0.6, 0.6, 0.02);
            level.playSound(null, sphere.blockPosition(),
                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 1.2F, 0.5F);

            // The frozen sphere clatters to the ground where time stopped it
            ItemStack drop = sphere.getItem().copy();
            drop.setCount(1);
            ItemEntity itemEntity = new ItemEntity(level, sphere.getX(), sphere.getY(), sphere.getZ(), drop);
            itemEntity.setDeltaMovement(Vec3.ZERO);
            level.addFreshEntity(itemEntity);

            WildCatchManager.message(sphere.getOwner(), "ZA WARUDO! Time froze your sphere — throw again while it recharges!");
            WildCatchManager.blinkAway(mob);
            sphere.discard();
            return true;
        }
    }

    /** Enderman-style teleport away from an incoming sphere unless it's WarpTether-enchanted. */
    private static final class BlinkDefense implements WildDefense {
        @Override
        public boolean onSphereHit(Mob mob, PalBehavior behavior, PalSphereProjectile sphere, ServerLevel level) {
            if (sphere.getWarpTetherLevel() > 0) return false;
            WildCatchManager.blinkAway(mob);
            WildCatchManager.message(sphere.getOwner(), "It blinked away! A WarpTether sphere could pin it down.");
            return true;
        }
    }

    /** Mid-air sphere dodge + retaliation lightning bolt on the thrower; grounded mobs are still catchable. */
    private static final class StormDodgeDefense implements WildDefense {
        @Override
        public boolean onSphereHit(Mob mob, PalBehavior behavior, PalSphereProjectile sphere, ServerLevel level) {
            if (mob.onGround()) return false;
            WildCatchManager.stormDodge(mob, sphere.getOwner());
            WildCatchManager.message(sphere.getOwner(), "It dodged mid-air and struck back! Catch it while it's grounded.");
            return true;
        }
    }

    /** Only an owned pal whose combat_ability matches counter_ability can hurt this mob. */
    private static final class DamageImmuneDefense implements WildDefense {
        @Override
        public boolean onDamage(Mob mob, PalBehavior behavior, LivingHurtEvent event, ServerLevel level) {
            Entity source = event.getSource().getEntity();
            if (source instanceof Mob palMob && palMob.getPersistentData().contains("PalOwner")) {
                String counter = behavior.getWildCounterAbility();
                if (counter.isEmpty() || counter.equals(
                        PalBehaviorManager.getBehavior(palMob.getType()).getCombatAbilityType())) {
                    return false; // the natural predator pierces the immunity
                }
            }
            level.sendParticles(ParticleTypes.END_ROD,
                    mob.getX(), mob.getY() + mob.getBbHeight() * 0.5, mob.getZ(), 8, 0.4, 0.4, 0.4, 0.05);
            if (source instanceof ServerPlayer player) {
                WildCatchManager.message(player, "Your attacks can't hurt it — a certain pal's power might...");
            }
            return true;
        }
    }

    /** Invisible + rooted near players unless a bait pal is close enough (shy: passive only, ambush: any). */
    private static final class HideDefense implements WildDefense {
        private final boolean ambush;

        HideDefense(boolean ambush) {
            this.ambush = ambush;
        }

        @Override
        public void tick(Mob mob, PalBehavior behavior, ServerLevel level) {
            CompoundTag data = mob.getPersistentData();
            Player threat = nearestSurvivalPlayer(mob, behavior.getWildHideRange());
            // Shy mobs need a PASSIVE pal to feel safe; ambushers pounce ANY bait pal
            Mob lure = nearestOwnedPal(mob, behavior.getWildLureRange(), !ambush);

            boolean shouldHide = threat != null && lure == null;
            if (shouldHide) {
                if (!data.getBoolean(WildCatchManager.KEY_HIDDEN)) {
                    data.putBoolean(WildCatchManager.KEY_HIDDEN, true);
                    // Glowing outlines render on invisible entities — strip the
                    // leftover shimmer or the hide is telegraphed through walls
                    mob.removeEffect(net.minecraft.world.effect.MobEffects.GLOWING);
                    level.sendParticles(ParticleTypes.POOF,
                            mob.getX(), mob.getY() + 0.5, mob.getZ(), 12, 0.3, 0.3, 0.3, 0.05);
                }
                mob.setTarget(null);
            } else {
                if (data.getBoolean(WildCatchManager.KEY_HIDDEN)) {
                    data.putBoolean(WildCatchManager.KEY_HIDDEN, false);
                    mob.removeEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY);
                    level.sendParticles(ParticleTypes.CLOUD,
                            mob.getX(), mob.getY() + 0.5, mob.getZ(), 12, 0.3, 0.3, 0.3, 0.05);
                }
                if (ambush && lure != null) {
                    mob.setTarget(lure);
                }
            }
        }

        private static Player nearestSurvivalPlayer(Mob mob, double range) {
            Player best = null;
            double bestDistSqr = range * range;
            for (Player player : mob.level().players()) {
                if (player.isSpectator() || player.isCreative()) continue;
                double distSqr = mob.distanceToSqr(player);
                if (distSqr <= bestDistSqr) {
                    bestDistSqr = distSqr;
                    best = player;
                }
            }
            return best;
        }

        private static Mob nearestOwnedPal(Mob mob, double range, boolean passiveOnly) {
            java.util.List<Mob> pals = mob.level().getEntitiesOfClass(Mob.class,
                    mob.getBoundingBox().inflate(range),
                    other -> other != mob && other.getPersistentData().contains("PalOwner")
                            && (!passiveOnly
                                || !PalBehaviorManager.getBehavior(other.getType()).isAttackTarget()));
            Mob best = null;
            double bestDistSqr = Double.MAX_VALUE;
            for (Mob pal : pals) {
                double distSqr = mob.distanceToSqr(pal);
                if (distSqr < bestDistSqr) {
                    bestDistSqr = distSqr;
                    best = pal;
                }
            }
            return best;
        }
    }
}
