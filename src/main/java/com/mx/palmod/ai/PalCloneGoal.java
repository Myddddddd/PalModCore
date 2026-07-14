package com.mx.palmod.ai;

import com.mx.palmod.behavior.PalBehavior;
import com.mx.palmod.stats.PalStats;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

import java.util.EnumSet;
import java.util.List;

/**
 * Swarm ability for small fighters (tasmanian devil, capuchin monkey): when the
 * pal engages a target, it splits into a handful of temporary clones that pile
 * onto the same enemy, then vanish. The clones are marked {@code PalClone}, drop
 * nothing, aren't catchable, and despawn on a timer (or when their master is
 * recalled / dies — see {@link com.mx.palmod.event.ForgeEvents}).
 */
public class PalCloneGoal extends Goal {

    public static final String KEY_CLONE = "PalClone";
    public static final String KEY_MASTER = "CloneMaster";
    public static final String KEY_EXPIRE = "CloneExpireAt";
    private static final String KEY_READY = "CloneReadyAt";

    private final Mob mob;
    private final PalBehavior behavior;

    public PalCloneGoal(Mob mob, PalBehavior behavior) {
        this.mob = mob;
        this.behavior = behavior;
        setFlags(EnumSet.noneOf(Goal.Flag.class)); // spawns clones; grabs no controls
    }

    @Override
    public boolean canUse() {
        if (!(mob.level() instanceof ServerLevel)) return false;
        if (mob.getPersistentData().getBoolean(KEY_CLONE)) return false; // clones don't clone
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;
        long now = mob.level().getGameTime();
        if (now < mob.getPersistentData().getLong(KEY_READY)) return false;
        if (PalStats.getHunger(mob) < behavior.getCloneHungerCost()) return false;
        return countLiveClones() < behavior.getCloneMax();
    }

    @Override
    public boolean canContinueToUse() {
        return false; // one-shot per activation
    }

    @Override
    public void start() {
        ServerLevel level = (ServerLevel) mob.level();
        LivingEntity target = mob.getTarget();
        long now = level.getGameTime();

        int want = behavior.getCloneMax() - countLiveClones();
        long expireAt = now + behavior.getCloneDurationTicks();
        int spawned = 0;
        for (int i = 0; i < want; i++) {
            if (!(mob.getType().create(level) instanceof Mob clone)) break;
            clone.moveTo(mob.getX() + (mob.getRandom().nextDouble() - 0.5) * 2.0,
                    mob.getY(),
                    mob.getZ() + (mob.getRandom().nextDouble() - 0.5) * 2.0,
                    mob.getYRot(), 0);
            if (!level.noCollision(clone)) {
                clone.moveTo(mob.getX(), mob.getY(), mob.getZ(), mob.getYRot(), 0);
            }
            // Strip the copy's native AI (wander/panic/flee/hive) so it single-
            // mindedly pursues the master's enemy — AM mobs otherwise ignore an
            // externally-set target (see known issue #1). Only the injected
            // melee goal drives it; onLivingTick re-seeds the target.
            clone.goalSelector.removeAllGoals(g -> true);
            clone.targetSelector.removeAllGoals(g -> true);
            // Fragile echoes — half health, no loot, no name tag
            clone.setHealth(Math.max(1.0f, clone.getMaxHealth() * 0.5f));
            CompoundTag data = clone.getPersistentData();
            data.putBoolean(KEY_CLONE, true);
            data.putUUID(KEY_MASTER, mob.getUUID());
            data.putLong(KEY_EXPIRE, expireAt);
            if (mob.getPersistentData().hasUUID("PalOwner")) {
                data.putUUID("PalOwner", mob.getPersistentData().getUUID("PalOwner"));
            }
            clone.setPersistenceRequired();
            clone.setCustomName(null);
            clone.setTarget(target);
            if (clone instanceof PathfinderMob pathClone) {
                clone.goalSelector.addGoal(1, new MeleeAttackGoal(pathClone, 1.35D, true));
            }
            level.addFreshEntity(clone);
            level.sendParticles(ParticleTypes.CLOUD,
                    clone.getX(), clone.getY() + clone.getBbHeight() * 0.5, clone.getZ(),
                    8, 0.2, 0.3, 0.2, 0.02);
            spawned++;
        }

        if (spawned > 0) {
            PalStats.modifyHunger(mob, -behavior.getCloneHungerCost());
            mob.getPersistentData().putLong(KEY_READY, now + behavior.getCloneCooldownTicks());
            level.playSound(null, mob.blockPosition(),
                    SoundEvents.SLIME_SQUISH, SoundSource.NEUTRAL, 1.0F, 1.4F);
        }
    }

    private int countLiveClones() {
        if (!(mob.level() instanceof ServerLevel level)) return 0;
        List<Mob> clones = level.getEntitiesOfClass(Mob.class,
                mob.getBoundingBox().inflate(48.0),
                m -> m != mob && m.isAlive()
                        && m.getPersistentData().getBoolean(KEY_CLONE)
                        && m.getPersistentData().hasUUID(KEY_MASTER)
                        && m.getPersistentData().getUUID(KEY_MASTER).equals(mob.getUUID()));
        return clones.size();
    }
}
