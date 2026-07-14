package com.mx.palmod.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Roots a deployed (anchor/sentry) pal to its AnchorPos with a per-tick re-pin.
 *
 * Goal flags only suppress other GOALS — mobs whose flight comes from their own
 * MoveControl (many Alex's Mobs flyers) ignore them, so this goal actively
 * cancels navigation and snaps the mob back when it drifts. It claims JUMP only:
 * claiming MOVE would starve the mob's own native attack goals (which sentries
 * rely on to fight), and the re-pin defeats movement anyway.
 */
public class PalRootGoal extends Goal {

    protected final Mob mob;

    public PalRootGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        return !mob.getPersistentData().getString("DeployMode").isEmpty()
                && mob.getPersistentData().contains("AnchorPos");
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        BlockPos anchor = BlockPos.of(mob.getPersistentData().getLong("AnchorPos"));
        mob.getNavigation().stop();
        double distSqr = mob.distanceToSqr(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5);
        if (distSqr > 4.0) {
            mob.setPos(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5);
            mob.setDeltaMovement(Vec3.ZERO);
        } else if (distSqr > 0.5) {
            // Gently damp horizontal drift (flyers bob in place)
            Vec3 delta = mob.getDeltaMovement();
            mob.setDeltaMovement(delta.x * 0.5, delta.y, delta.z * 0.5);
        }
    }
}
