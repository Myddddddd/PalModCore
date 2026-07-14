package com.mx.palmod.ai;

import com.mx.palmod.stats.PalStats;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;

import java.util.Comparator;
import java.util.List;

/**
 * Sentry deployment: the pal roots at its AnchorPos (via {@link PalRootGoal})
 * and fights as a turret.
 *
 * This goal does NOT attack by itself — it feeds the nearest hostile into
 * setTarget() and lets the mob's own native attack goals (e.g. Alex's Mobs
 * ranged attacks, which this mod cannot reference at compile time) do the
 * actual fighting.
 */
public class PalSentryGoal extends PalRootGoal {

    private final float sentryRadius;
    private int retargetDelay = 0;

    public PalSentryGoal(Mob mob, float sentryRadius) {
        super(mob);
        this.sentryRadius = sentryRadius;
    }

    @Override
    public boolean canUse() {
        return "sentry".equals(mob.getPersistentData().getString("DeployMode")) && super.canUse();
    }

    @Override
    public void tick() {
        super.tick(); // root re-pin

        // ── Feed targets to the mob's native attack AI ──
        if (--retargetDelay > 0) return;
        retargetDelay = 20;

        if (PalStats.isInactive(mob)) {
            mob.setTarget(null);
            return;
        }

        LivingEntity target = mob.getTarget();
        boolean targetValid = target != null && target.isAlive()
                && mob.distanceToSqr(target) <= sentryRadius * sentryRadius;
        if (targetValid) return;

        List<Monster> hostiles = mob.level().getEntitiesOfClass(Monster.class,
                mob.getBoundingBox().inflate(sentryRadius),
                e -> e.isAlive() && e != mob && !e.getPersistentData().contains("PalOwner"));
        mob.setTarget(hostiles.stream()
                .min(Comparator.comparingDouble(mob::distanceToSqr))
                .orElse(null));
    }
}
