package com.mx.palmod.ai;

import com.mx.palmod.behavior.PalBehavior;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Skittish wild mobs spot players from far away (sight) and pick up on noisy
 * approaches (hearing — sprinting counts as noise, even through walls) and bolt
 * before a sphere can reach them. Sneaking cuts the detection ranges to a
 * quarter, so a careful stalk is the intended counter.
 */
public class WildFleeGoal extends Goal {

    private final PathfinderMob mob;
    private final double sightRange;
    private final double hearingRange;
    private final double speed;

    private Player threat;
    // findThreat raytraces every nearby player — throttle the scans
    private int scanCooldown;
    private int rescanCooldown;

    public WildFleeGoal(PathfinderMob mob, PalBehavior behavior) {
        this.mob = mob;
        this.sightRange = behavior.getWildSightRange();
        this.hearingRange = behavior.getWildHearingRange();
        this.speed = behavior.getWildFleeSpeed();
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (--scanCooldown > 0) return false;
        scanCooldown = 5;
        threat = findThreat();
        return threat != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (threat == null || !threat.isAlive() || threat.isSpectator() || threat.isCreative()) {
            return false;
        }
        // Keep running until well outside sight range
        return mob.distanceToSqr(threat) < sightRange * sightRange * 2.25;
    }

    @Override
    public void start() {
        flee();
    }

    @Override
    public void stop() {
        threat = null;
    }

    @Override
    public void tick() {
        if (--rescanCooldown <= 0) {
            rescanCooldown = 10;
            Player closer = findThreat();
            if (closer != null) {
                threat = closer;
            }
        }
        if (mob.getNavigation().isDone()) {
            flee();
        }
    }

    private void flee() {
        if (threat == null) return;
        Vec3 away = DefaultRandomPos.getPosAway(mob, 16, 7, threat.position());
        if (away != null) {
            mob.getNavigation().moveTo(away.x, away.y, away.z, speed);
        }
    }

    private Player findThreat() {
        Player best = null;
        double bestDistSqr = Double.MAX_VALUE;
        for (Player player : mob.level().players()) {
            if (player.isSpectator() || player.isCreative()) continue;
            double distSqr = mob.distanceToSqr(player);
            // Sneaking players are harder to spot and make no noise
            double sight = player.isCrouching() ? sightRange * 0.25 : sightRange;
            boolean seen = distSqr <= sight * sight && mob.hasLineOfSight(player);
            boolean heard = player.isSprinting() && distSqr <= hearingRange * hearingRange;
            if ((seen || heard) && distSqr < bestDistSqr) {
                bestDistSqr = distSqr;
                best = player;
            }
        }
        return best;
    }
}
