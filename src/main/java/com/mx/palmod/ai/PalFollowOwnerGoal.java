package com.mx.palmod.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.pathfinder.BlockPathTypes;

import java.util.EnumSet;
import java.util.UUID;

public class PalFollowOwnerGoal extends Goal {
    private final Mob mob;
    private LivingEntity owner;
    private final LevelReader level;
    private final double speedModifier;
    private final PathNavigation navigation;
    private int timeToRecalcPath;
    private final float stopDistance;
    private final float startDistance;
    // Beyond this the pal gives up pathing and teleports to the owner
    private final float teleportDistance;
    private float oldWaterCost;

    public PalFollowOwnerGoal(Mob mob, double speedModifier, float startDistance, float stopDistance) {
        this.mob = mob;
        this.level = mob.level();
        this.speedModifier = speedModifier;
        this.navigation = mob.getNavigation();
        this.startDistance = startDistance;
        this.stopDistance = stopDistance;
        this.teleportDistance = Math.max(24.0F, startDistance * 2.0F);
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    /** While the pal has a live fight inside its activity range, combat wins over following. */
    private boolean busyFighting(Player player) {
        LivingEntity target = this.mob.getTarget();
        return target != null && target.isAlive()
                && this.mob.distanceToSqr(player) < (double)(this.teleportDistance * this.teleportDistance);
    }

    @Override
    public boolean canUse() {
        if (!this.mob.getPersistentData().contains("PalOwner")) {
            return false;
        }
        if (this.mob.getPersistentData().getBoolean("PalSitting")) {
            return false;
        }
        UUID ownerUUID = this.mob.getPersistentData().getUUID("PalOwner");
        Player player = this.mob.level().getPlayerByUUID(ownerUUID);
        if (player == null) {
            return false;
        } else if (player.isSpectator()) {
            return false;
        } else if (busyFighting(player)) {
            return false;
        } else if (this.mob.distanceToSqr(player) < (double)(this.startDistance * this.startDistance)) {
            return false;
        } else {
            this.owner = player;
            return true;
        }
    }

    @Override
    public boolean canContinueToUse() {
        if (this.owner == null || this.owner.isRemoved() || this.owner.isSpectator()) {
            return false;
        }
        if (this.mob.getPersistentData().getBoolean("PalSitting")) {
            return false;
        }
        if (this.owner instanceof Player p && busyFighting(p)) {
            return false;
        }
        if (this.navigation.isDone()) {
            return false;
        } else {
            return !(this.mob.distanceToSqr(this.owner) <= (double)(this.stopDistance * this.stopDistance));
        }
    }

    @Override
    public void start() {
        this.timeToRecalcPath = 0;
        this.oldWaterCost = this.mob.getPathfindingMalus(BlockPathTypes.WATER);
        this.mob.setPathfindingMalus(BlockPathTypes.WATER, 0.0F);
    }

    @Override
    public void stop() {
        this.owner = null;
        this.navigation.stop();
        this.mob.setPathfindingMalus(BlockPathTypes.WATER, this.oldWaterCost);
    }

    @Override
    public void tick() {
        this.mob.getLookControl().setLookAt(this.owner, 10.0F, (float)this.mob.getMaxHeadXRot());
        if (--this.timeToRecalcPath <= 0) {
            this.timeToRecalcPath = this.adjustedTickDelay(10);
            if (!this.mob.isLeashed() && !this.mob.isPassenger()) {
                if (this.mob.distanceToSqr(this.owner) >= (double)(this.teleportDistance * this.teleportDistance)) {
                    this.teleportToOwner();
                } else {
                    this.navigation.moveTo(this.owner, this.speedModifier);
                }
            }
        }
    }

    private void teleportToOwner() {
        BlockPos blockpos = this.owner.blockPosition();
        for (int i = 0; i < 10; ++i) {
            int x = this.randomIntInclusive(-3, 3);
            int y = this.randomIntInclusive(-1, 1);
            int z = this.randomIntInclusive(-3, 3);
            if (this.maybeTeleportTo(blockpos.getX() + x, blockpos.getY() + y, blockpos.getZ() + z)) {
                return;
            }
        }
    }

    private boolean maybeTeleportTo(int x, int y, int z) {
        if (Math.abs((double)x - this.owner.getX()) < 2.0D && Math.abs((double)z - this.owner.getZ()) < 2.0D) {
            return false;
        } else if (!this.canTeleportTo(new BlockPos(x, y, z))) {
            return false;
        } else {
            this.mob.moveTo((double)x + 0.5D, (double)y, (double)z + 0.5D, this.mob.getYRot(), this.mob.getXRot());
            this.navigation.stop();
            return true;
        }
    }

    private boolean canTeleportTo(BlockPos pos) {
        return this.level.getBlockState(pos).isAir() && this.level.getBlockState(pos.above()).isAir() && !this.level.getBlockState(pos.below()).isAir();
    }

    private int randomIntInclusive(int min, int max) {
        return this.mob.getRandom().nextInt(max - min + 1) + min;
    }
}
