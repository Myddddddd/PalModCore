package com.mx.palmod.ai;

import com.mx.palmod.behavior.PalBehavior;
import com.mx.palmod.pal.TempBlockReverter;
import com.mx.palmod.stats.PalStats;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Elemental combat casts: instead of biting, the pal channels an element at its
 * current target (fed by the protect_owner/attack_target goals). One goal class,
 * four cast types selected by behaviors.combat_ability.type:
 *
 *  - "lightning":   a bolt strikes the target (visual-only bolt + attributed damage)
 *  - "fireball":    small fireballs materialize from thin air around the target
 *  - "earth_spike": a dirt column erupts under the target, launching it (earth bender);
 *                   the raised earth crumbles back after a few seconds
 *  - "water_burst": a crushing water blast — damage, upward wash, slowness
 */
public class PalCastAttackGoal extends Goal {

    private final Mob mob;
    private final String castType;
    private final float damage;
    private final float range;
    private final int cooldownTicks;
    private final float hungerCost;

    private int cooldownTimer = 0;

    public PalCastAttackGoal(Mob mob, PalBehavior behavior) {
        this.mob = mob;
        this.castType = behavior.getCombatAbilityType();
        this.damage = behavior.getCombatDamage();
        this.range = behavior.getCombatRange();
        this.cooldownTicks = behavior.getCombatCooldownTicks();
        this.hungerCost = behavior.getCombatHungerCost();
        this.setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive()
                && mob.distanceToSqr(target) <= range * range
                && (isWild() || !PalStats.isInactive(mob));
    }

    /** Wild brawlers cast for free — hunger is a pal-management mechanic. */
    private boolean isWild() {
        return !mob.getPersistentData().contains("PalOwner");
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        cooldownTimer = 10; // short windup on engage
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return;

        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (--cooldownTimer > 0) return;
        if (mob.distanceToSqr(target) > range * range) return;
        cooldownTimer = cooldownTicks;

        if (!(mob.level() instanceof ServerLevel level)) return;
        switch (castType) {
            case "lightning" -> castLightning(level, target);
            case "fireball" -> castFireball(level, target);
            case "earth_spike" -> castEarthSpike(level, target);
            case "water_burst" -> castWaterBurst(level, target);
            default -> { return; }
        }
        if (!isWild()) {
            PalStats.modifyHunger(mob, -hungerCost);
        }
    }

    // ──────────────────────────────────────────────────────────────

    private void castLightning(ServerLevel level, LivingEntity target) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(target.getX(), target.getY(), target.getZ());
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
        target.hurt(level.damageSources().mobAttack(mob), damage);
    }

    private void castFireball(ServerLevel level, LivingEntity target) {
        // Fireballs materialize from the void — 3 random points in the air
        // around the target, converging on it
        for (int i = 0; i < 3; i++) {
            Vec3 spawn = target.position().add(
                    (mob.getRandom().nextDouble() - 0.5) * 8.0,
                    3.0 + mob.getRandom().nextDouble() * 3.0,
                    (mob.getRandom().nextDouble() - 0.5) * 8.0);
            Vec3 dir = target.position().add(0, target.getBbHeight() * 0.5, 0)
                    .subtract(spawn).normalize().scale(0.15);
            SmallFireball fireball = new SmallFireball(level, spawn.x, spawn.y, spawn.z, dir.x, dir.y, dir.z);
            fireball.setOwner(mob);
            level.addFreshEntity(fireball);
            level.sendParticles(ParticleTypes.FLAME, spawn.x, spawn.y, spawn.z, 8, 0.2, 0.2, 0.2, 0.02);
        }
        level.playSound(null, target.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.NEUTRAL, 1.0F, 1.0F);
    }

    private void castEarthSpike(ServerLevel level, LivingEntity target) {
        BlockPos feet = target.blockPosition();
        // Raise a dirt column under the target (temporary — crumbles after 5s)
        for (BlockPos pos : new BlockPos[]{feet, feet.above()}) {
            if (level.getBlockState(pos).canBeReplaced()) {
                TempBlockReverter.place(level, pos, Blocks.DIRT.defaultBlockState(), 100);
            }
        }
        target.setDeltaMovement(target.getDeltaMovement().x, 0.9, target.getDeltaMovement().z);
        target.hurt(level.damageSources().mobAttack(mob), damage);
        level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(
                        ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()),
                feet.getX() + 0.5, feet.getY() + 1.0, feet.getZ() + 0.5, 30, 0.4, 0.6, 0.4, 0.15);
        level.playSound(null, feet, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.NEUTRAL, 1.2F, 0.7F);
    }

    private void castWaterBurst(ServerLevel level, LivingEntity target) {
        target.hurt(level.damageSources().mobAttack(mob), damage);
        // A crushing wave: wash the target upward and away from the caster
        Vec3 away = target.position().subtract(mob.position()).normalize();
        target.setDeltaMovement(away.x * 0.6, 0.6, away.z * 0.6);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
        level.sendParticles(ParticleTypes.SPLASH,
                target.getX(), target.getY() + 0.8, target.getZ(), 60, 0.6, 0.8, 0.6, 0.2);
        level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP,
                target.getX(), target.getY() + 0.3, target.getZ(), 20, 0.3, 0.5, 0.3, 0.1);
        level.playSound(null, target.blockPosition(), SoundEvents.GENERIC_SPLASH, SoundSource.NEUTRAL, 1.2F, 0.8F);
    }
}
