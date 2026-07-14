package com.mx.palmod.ai;

import com.mx.palmod.behavior.PalBehavior;
import com.mx.palmod.pal.TempBlockReverter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
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
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Maps behaviors.combat_ability.type strings to a cast implementation.
 * Adding a new ability = one {@link #register(String, Ability)} call from any mod's
 * init (constructor or FMLCommonSetupEvent) — no Palmod source changes required.
 * Unknown types are simply skipped (mirrors WorkerGoalRegistry's graceful handling,
 * minus the fallback since there's no sensible default cast).
 */
public final class CombatAbilityRegistry {

    private static final Logger LOGGER = LogUtils.getLogger();

    @FunctionalInterface
    public interface Ability {
        /** Perform the cast. Called once per successful trigger, already range/cooldown-gated. */
        void cast(ServerLevel level, Mob mob, LivingEntity target, PalBehavior behavior);
    }

    private static final Map<String, Ability> ABILITIES = new HashMap<>();

    static {
        register("lightning", CombatAbilityRegistry::castLightning);
        register("fireball", CombatAbilityRegistry::castFireball);
        register("earth_spike", CombatAbilityRegistry::castEarthSpike);
        register("water_burst", CombatAbilityRegistry::castWaterBurst);
    }

    private CombatAbilityRegistry() {}

    public static void register(String type, Ability ability) {
        ABILITIES.put(type, ability);
    }

    @Nullable
    public static Ability get(String type) {
        Ability ability = ABILITIES.get(type);
        if (ability == null) {
            LOGGER.warn("Unknown combat_ability type '{}' — cast skipped", type);
        }
        return ability;
    }

    // ── Built-in casts ───────────────────────────────────────────────────

    private static void castLightning(ServerLevel level, Mob mob, LivingEntity target, PalBehavior behavior) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(target.getX(), target.getY(), target.getZ());
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
        target.hurt(level.damageSources().mobAttack(mob), behavior.getCombatDamage());
    }

    private static void castFireball(ServerLevel level, Mob mob, LivingEntity target, PalBehavior behavior) {
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

    private static void castEarthSpike(ServerLevel level, Mob mob, LivingEntity target, PalBehavior behavior) {
        BlockPos feet = target.blockPosition();
        // Raise a dirt column under the target (temporary — crumbles after 5s)
        for (BlockPos pos : new BlockPos[]{feet, feet.above()}) {
            if (level.getBlockState(pos).canBeReplaced()) {
                TempBlockReverter.place(level, pos, Blocks.DIRT.defaultBlockState(), 100);
            }
        }
        target.setDeltaMovement(target.getDeltaMovement().x, 0.9, target.getDeltaMovement().z);
        target.hurt(level.damageSources().mobAttack(mob), behavior.getCombatDamage());
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()),
                feet.getX() + 0.5, feet.getY() + 1.0, feet.getZ() + 0.5, 30, 0.4, 0.6, 0.4, 0.15);
        level.playSound(null, feet, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.NEUTRAL, 1.2F, 0.7F);
    }

    private static void castWaterBurst(ServerLevel level, Mob mob, LivingEntity target, PalBehavior behavior) {
        target.hurt(level.damageSources().mobAttack(mob), behavior.getCombatDamage());
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
