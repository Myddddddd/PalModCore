package com.mx.palmod.aura;

import com.mx.palmod.behavior.PalBehavior;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;

/**
 * "heal": pulses Regeneration (amplifier = aura.potency) onto the owner.
 * Skips pulsing when the owner is already at full health so the Pal
 * doesn't burn hunger for nothing.
 */
public class HealAura implements PalAura {

    @Override
    public boolean apply(ServerLevel level, Mob pal, ServerPlayer owner, PalBehavior behavior) {
        if (owner.getHealth() >= owner.getMaxHealth()) return false;

        owner.addEffect(new MobEffectInstance(MobEffects.REGENERATION,
                behavior.getAuraIntervalTicks() + 40, behavior.getAuraPotency()));

        // Spore ring at the pal + a trace on the owner
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                pal.getX(), pal.getY() + 0.8, pal.getZ(), 10, 0.5, 0.4, 0.5, 0.02);
        level.sendParticles(ParticleTypes.HEART,
                owner.getX(), owner.getY() + 1.2, owner.getZ(), 2, 0.3, 0.3, 0.3, 0.02);
        level.playSound(null, pal.getX(), pal.getY(), pal.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.5F, 1.4F);
        return true;
    }
}
