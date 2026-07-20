package com.mx.palmod.ai;

import com.mx.palmod.Config;
import com.mx.palmod.hunt.HuntingNightManager;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/**
 * During a {@link HuntingNightManager} hunt, EVERY mob (except owned pals) turns
 * predator: it seeks the nearest survival player, chases at {@code huntChaseSpeed},
 * and melees for {@code max(baseAttack × huntDamageMultiplier, huntMinDamage)} — so
 * normally-passive mobs (no attack damage) still hit for the zombie-level floor,
 * while real attackers hit for double.
 *
 * The goal is fully self-gating: {@link #canUse()} returns false whenever no hunt
 * is active, so it can be injected once and left dormant — when the night ends the
 * mob simply resumes its normal AI, with nothing to un-inject.
 */
public class HuntNightAttackGoal extends Goal {

    private final PathfinderMob mob;
    private Player target;
    private int scanCooldown;
    private int attackCooldown;
    private int repathCooldown;

    public HuntNightAttackGoal(PathfinderMob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!HuntingNightManager.isActive()) return false;
        // Owned pals never turn on their owner (or anyone).
        if (mob.getPersistentData().contains("PalOwner")) return false;
        if (--scanCooldown > 0) return false;
        scanCooldown = 10;
        target = findTarget();
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (!HuntingNightManager.isActive()) return false;
        if (target == null || !target.isAlive() || target.isCreative() || target.isSpectator()) {
            return false;
        }
        double range = Config.huntSenseRange * 1.5;
        return mob.distanceToSqr(target) < range * range;
    }

    @Override
    public void start() {
        attackCooldown = 0;
    }

    @Override
    public void stop() {
        target = null;
        mob.getNavigation().stop();
        mob.setAggressive(false);
    }

    @Override
    public void tick() {
        if (target == null) return;
        mob.getLookControl().setLookAt(target, 30.0f, 30.0f);
        mob.setAggressive(true);

        double reachSq = mob.getBbWidth() * 2.0f * mob.getBbWidth() * 2.0f + target.getBbWidth();
        double distSq = mob.distanceToSqr(target.getX(), target.getY(), target.getZ());

        if (distSq > reachSq) {
            // Re-path toward the player's CURRENT position periodically (moveTo is
            // one-shot), so a hunter actually runs down a moving/sprinting target.
            if (--repathCooldown <= 0 || mob.getNavigation().isDone()) {
                repathCooldown = 10;
                mob.getNavigation().moveTo(target, Config.huntChaseSpeed);
            }
        } else {
            mob.getNavigation().stop();
        }

        if (attackCooldown > 0) attackCooldown--;
        if (distSq <= reachSq && attackCooldown <= 0) {
            attackCooldown = Config.huntAttackCooldownTicks;
            mob.swing(InteractionHand.MAIN_HAND);
            target.hurt(mob.damageSources().mobAttack(mob), huntDamage());
        }
    }

    private float huntDamage() {
        AttributeInstance atk = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        float base = atk != null ? (float) atk.getValue() : 0f;
        return Math.max(base * (float) Config.huntDamageMultiplier, (float) Config.huntMinDamage);
    }

    private Player findTarget() {
        Player best = null;
        double bestSq = Config.huntSenseRange * Config.huntSenseRange;
        for (Player player : mob.level().players()) {
            if (player.isCreative() || player.isSpectator() || !player.isAlive()) continue;
            double distSq = mob.distanceToSqr(player);
            if (distSq < bestSq) {
                bestSq = distSq;
                best = player;
            }
        }
        return best;
    }
}
