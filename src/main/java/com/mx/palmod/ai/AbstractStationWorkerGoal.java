package com.mx.palmod.ai;

import com.mx.palmod.behavior.PalBehavior;
import com.mx.palmod.behavior.PalBehaviorManager;
import com.mx.palmod.block.PalWorkStationBlockEntity;
import com.mx.palmod.stats.PalStats;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;
import java.util.EnumSet;

/**
 * Template for all station-bound worker Pals (the leafcutter-ant pattern):
 *  1. Roam within wanderRadius of the bound work station
 *  2. Find a work target via {@link #findTarget}
 *  3. Do the job via {@link #doWork}, collecting produce with {@link #pickupItem}
 *  4. Carry produce back and deposit into the station BlockEntity
 *  5. Idle near the station when its inventory is full; stop entirely when starving
 *
 * A new job = one subclass registered in {@link WorkerGoalRegistry} under a
 * station_mode.worker_type string.
 */
public abstract class AbstractStationWorkerGoal extends Goal {

    protected final Mob mob;
    protected final BlockPos stationPos;
    protected final int harvestRadius;
    protected final int wanderRadius;

    // Internal state
    @Nullable
    private BlockPos targetPos = null;
    private boolean carrying = false;
    // Held produce (single stack, max 64)
    private ItemStack heldItem = ItemStack.EMPTY;
    private int tickDelay = 0;

    protected AbstractStationWorkerGoal(Mob mob, BlockPos stationPos, int harvestRadius, int wanderRadius) {
        this.mob = mob;
        this.stationPos = stationPos;
        this.harvestRadius = harvestRadius;
        this.wanderRadius = wanderRadius;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return mob.getPersistentData().contains("WorkStationPos");
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        // Re-engage immediately after being preempted (feeder runs etc.)
        tickDelay = 0;
    }

    @Override
    public void tick() {
        tickDelay--;
        if (tickDelay > 0) {
            // Between work pulses: AM mobs (climbing ants) switch navigators and
            // drop paths constantly — re-issue the current path cheaply so the
            // worker doesn't stand around until the next 1s pulse.
            if (targetPos != null && mob.getNavigation().isDone()) {
                moveTo(targetPos, 1.0);
            }
            return;
        }
        tickDelay = 20; // Run every second

        Level level = mob.level();
        if (!(level instanceof ServerLevel serverLevel)) return;

        // Self-feed: a hungry PRODUCER eats produce stored in its own station
        // (keeps harvesters like the leafcutter ant running without a feeder).
        // Drain-type workers (sorter) are excluded — their station slot holds
        // the player's items in transit, not their own produce.
        if (waitsWhenStationFull() && PalStats.needsFood(mob) && trySelfFeed(serverLevel)) return;

        // Stop when starving (hunger < 5%) — EXCEPT self-feeding workers (the
        // harvester), which must keep working to produce their own food. A hard
        // stop deadlocks a harvester at hunger 0: it can't harvest → can't
        // self-feed → stays at 0 forever even with ripe crops in front of it.
        // It keeps going at the 0.3x starving speed until it restocks and eats
        // back up. Other workers (lumberjack/miner/sorter) rely on a feeder and
        // are correctly halted here (PalSeekFeederGoal still runs while inactive).
        if (PalStats.isInactive(mob) && !selfFeedsFromOutput()) return;

        // Step 1: If holding items, go deposit at station
        if (!heldItem.isEmpty() || carrying) {
            depositCarried(serverLevel);
            return;
        }

        // Step 2: Make sure the worker isn't too far from its station
        double distToStation = mob.distanceToSqr(
                stationPos.getX() + 0.5, stationPos.getY(), stationPos.getZ() + 0.5);
        if (distToStation > wanderRadius * wanderRadius) {
            moveTo(stationPos, 1.0);
            return;
        }

        // Step 3: Check station inventory - if full, wait near station
        // (unless the worker DRAINS the station, like the sorter)
        if (waitsWhenStationFull()) {
            BlockEntity be = serverLevel.getBlockEntity(stationPos);
            if (be instanceof PalWorkStationBlockEntity station && station.isFull()) {
                moveTo(stationPos, 1.0);
                return;
            }
        }

        // Step 4: Find a work target
        if (targetPos != null && !isValidTarget(serverLevel, targetPos)) {
            targetPos = null;
        }
        if (targetPos == null) {
            targetPos = findTarget(serverLevel);
        }

        if (targetPos != null) {
            moveTo(targetPos, 1.0);
            // Check if close enough to work
            if (mob.distanceToSqr(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5) < workReachSqr()) {
                doWork(serverLevel, targetPos);
                targetPos = null;
            }
        } else {
            // Nothing ripe to harvest: idle CLOSE to the station. The old code
            // wandered up to ±harvestRadius (8) blocks away in a random
            // direction and routinely got stuck jittering against a wall/edge —
            // or ramming an entity that happened to stand in the roam zone. Now
            // the worker just drifts home when idle so it never strays into
            // obstacles while waiting for crops to grow.
            double homeDistSqr = mob.distanceToSqr(
                    stationPos.getX() + 0.5, stationPos.getY(), stationPos.getZ() + 0.5);
            if (homeDistSqr > 9.0) { // strayed >3 blocks — walk back to the station
                moveTo(stationPos, 1.0);
            } else if (!mob.getNavigation().isInProgress()) {
                // Gentle shuffle within ~1.5 blocks of the station
                double offsetX = (mob.getRandom().nextDouble() - 0.5) * 3.0;
                double offsetZ = (mob.getRandom().nextDouble() - 0.5) * 3.0;
                mob.getNavigation().moveTo(
                        stationPos.getX() + 0.5 + offsetX,
                        mob.getY(),
                        stationPos.getZ() + 0.5 + offsetZ, 0.6);
            }
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Job hooks
    // ──────────────────────────────────────────────────────────────

    /** Is this previously chosen position still worth working? */
    protected abstract boolean isValidTarget(ServerLevel level, BlockPos pos);

    /**
     * Scan for the next work target near the mob, constrained by harvestRadius
     * (use {@link #withinStationRange} to also respect the station's wanderRadius).
     * Return null when there is nothing to do.
     */
    @Nullable
    protected abstract BlockPos findTarget(ServerLevel level);

    /** Perform the job at the target. Collect produce with {@link #pickupItem}. */
    protected abstract void doWork(ServerLevel level, BlockPos pos);

    /** Squared distance at which the mob is close enough to work the target. */
    protected double workReachSqr() {
        return 3.0;
    }

    /**
     * Producers pause when their station output is full. Workers that DRAIN the
     * station (sorter) override to false — a full station is exactly their work.
     */
    protected boolean waitsWhenStationFull() {
        return true;
    }

    /**
     * True if this worker's station output is edible for it, so it can recover
     * from starvation by working (the harvester). Such workers keep running when
     * inactive instead of deadlocking at hunger 0. Others rely on a feeder.
     */
    protected boolean selfFeedsFromOutput() {
        return false;
    }

    /** Where carried produce gets delivered. Defaults to the work station. */
    protected BlockPos depositPos() {
        return stationPos;
    }

    /**
     * Insert the carried stack at the deposit position. Default handles the
     * work station BE and any generic IItemHandler container. Returns leftover.
     */
    protected ItemStack depositInto(ServerLevel level, BlockPos pos, ItemStack stack) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof PalWorkStationBlockEntity station) {
            return station.depositItem(stack);
        }
        if (be != null) {
            var handlerOpt = be.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER)
                    .resolve();
            if (handlerOpt.isPresent()) {
                return net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(handlerOpt.get(), stack, false);
            }
        }
        return stack;
    }

    /** Called when a deposit fully completes (carried stack emptied). */
    protected void onDepositComplete() {
    }

    // ──────────────────────────────────────────────────────────────
    //  Shared plumbing
    // ──────────────────────────────────────────────────────────────

    protected void moveTo(BlockPos pos, double speed) {
        mob.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, speed);
    }

    protected boolean withinStationRange(BlockPos pos) {
        return pos.distSqr(stationPos) <= (double) (wanderRadius * wanderRadius);
    }

    /**
     * Add produce to the carried stack (same-item stacking, cap 64).
     * Returns whatever could NOT be absorbed — callers should spill it into the
     * world (e.g. {@code Block.popResource}) instead of letting it vanish.
     */
    protected ItemStack pickupItem(ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        if (heldItem.isEmpty()) {
            int taken = Math.min(stack.getCount(), 64);
            heldItem = stack.copyWithCount(taken);
            carrying = true;
            return stack.getCount() > taken ? stack.copyWithCount(stack.getCount() - taken) : ItemStack.EMPTY;
        }
        if (ItemStack.isSameItemSameTags(heldItem, stack)) {
            int taken = Math.min(stack.getCount(), 64 - heldItem.getCount());
            heldItem.grow(taken);
            return stack.getCount() > taken ? stack.copyWithCount(stack.getCount() - taken) : ItemStack.EMPTY;
        }
        // Different item — can't carry two kinds
        return stack;
    }

    /** Deduct this action's hunger cost from the worker (key matches hunger_costs JSON). */
    protected void chargeHunger(String action, float defaultCost) {
        PalBehavior behavior = PalBehaviorManager.getBehavior(mob.getType());
        PalStats.modifyHunger(mob, -behavior.getHungerCost(action, defaultCost));
    }

    /**
     * Eats one item from the station's stored produce if it's food for this mob.
     * Returns true while the worker is walking to / eating from the station.
     */
    private boolean trySelfFeed(ServerLevel serverLevel) {
        BlockEntity be = serverLevel.getBlockEntity(stationPos);
        if (!(be instanceof PalWorkStationBlockEntity station)) return false;
        ItemStack stored = station.getStoredItem();
        if (stored.isEmpty()) return false;
        var foodTable = com.mx.palmod.stats.PalFoodManager.getTable(mob.getType());
        if (!stored.isEdible() && !foodTable.canHandFeed(stored.getItem())) return false;

        double distSqr = mob.distanceToSqr(
                stationPos.getX() + 0.5, stationPos.getY(), stationPos.getZ() + 0.5);
        if (distSqr > 4.0) {
            if (PalStats.isInactive(mob)) return false; // too weak to walk there
            moveTo(stationPos, 1.0);
            return true;
        }
        if (com.mx.palmod.stats.PalFoodManager.tryFeed(mob, stored)) {
            station.extractStored(1);
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                    mob.getX(), mob.getY() + mob.getBbHeight(), mob.getZ(), 3, 0.2, 0.2, 0.2, 0.05);
            return true;
        }
        return false;
    }

    private void depositCarried(ServerLevel serverLevel) {
        BlockPos target = depositPos();
        double distToTarget = mob.distanceToSqr(
                target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
        if (distToTarget > 4.0) {
            moveTo(target, 1.0);
            return;
        }
        // A full station just needs its hopper to drain — wait beside it
        BlockEntity targetBe = serverLevel.getBlockEntity(target);
        if (targetBe instanceof PalWorkStationBlockEntity station && station.isFull()) {
            return;
        }
        // Close enough - deposit
        int before = heldItem.getCount();
        heldItem = depositInto(serverLevel, target, heldItem);
        if (!heldItem.isEmpty() && heldItem.getCount() == before) {
            // Destination rejected the whole stack (different item / full / gone) —
            // spill beside it instead of looping "walk there, fail deposit" forever.
            net.minecraft.world.level.block.Block.popResource(
                    serverLevel, target.above(), heldItem);
            heldItem = ItemStack.EMPTY;
        }
        if (heldItem.isEmpty()) {
            carrying = false;
            onDepositComplete();
        }
    }
}
