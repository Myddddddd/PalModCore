package com.mx.palmod.ai;

import com.mx.palmod.behavior.PalBehavior;
import com.mx.palmod.stats.PalStats;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Elemental combat casts: instead of biting, the pal channels an element at its
 * current target (fed by the protect_owner/attack_target goals). One goal class
 * handles range/cooldown/hunger gating for every cast type; the cast itself is
 * looked up from {@link CombatAbilityRegistry} by behaviors.combat_ability.type,
 * so third-party mods can register new types without touching this class.
 */
public class PalCastAttackGoal extends Goal {

    private final Mob mob;
    private final PalBehavior behavior;
    private final String castType;
    private final float range;
    private final int cooldownTicks;
    private final float hungerCost;

    private int cooldownTimer = 0;

    public PalCastAttackGoal(Mob mob, PalBehavior behavior) {
        this.mob = mob;
        this.behavior = behavior;
        this.castType = behavior.getCombatAbilityType();
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
        CombatAbilityRegistry.Ability ability = CombatAbilityRegistry.get(castType);
        if (ability == null) return;
        ability.cast(level, mob, target, behavior);

        if (!isWild()) {
            PalStats.modifyHunger(mob, -hungerCost);
        }
    }
}
