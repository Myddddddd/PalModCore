package com.mx.palmod.ai;

import com.mx.palmod.block.PalFeederBlockEntity;
import com.mx.palmod.block.PalWorkStationBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;

/**
 * The "sorter" station worker (built for the raccoon): the station is an INPUT
 * buffer (hopper-feed it mixed items); the raccoon grabs the stored stack and
 * carries it to a nearby container that already holds the same item — a living
 * item-sorting system. Items no container "wants" stay in the station.
 */
public class SorterGoal extends AbstractStationWorkerGoal {

    /** How many items the raccoon carries per trip. */
    private static final int CARRY_SIZE = 16;

    @Nullable
    private BlockPos destination = null;

    public SorterGoal(Mob mob, BlockPos stationPos, int harvestRadius, int wanderRadius) {
        super(mob, stationPos, harvestRadius, wanderRadius);
    }

    @Override
    protected boolean isValidTarget(ServerLevel level, BlockPos pos) {
        // The only work target is the station itself with something to sort
        return pos.equals(stationPos) && hasSortableInput(level);
    }

    @Nullable
    @Override
    protected BlockPos findTarget(ServerLevel level) {
        if (!hasSortableInput(level)) return null;
        return stationPos;
    }

    @Override
    protected void doWork(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(stationPos);
        if (!(be instanceof PalWorkStationBlockEntity station)) return;

        ItemStack stored = station.getStoredItem();
        if (stored.isEmpty()) return;

        BlockPos dest = findDestinationFor(level, stored);
        if (dest == null) return;

        ItemStack taken = station.extractStored(CARRY_SIZE);
        if (taken.isEmpty()) return;

        this.destination = dest;
        pickupItem(taken);
        chargeHunger("sort", 1.0f);
    }

    @Override
    protected boolean waitsWhenStationFull() {
        // The station is our INPUT buffer — full means there's work to do
        return false;
    }

    @Override
    protected BlockPos depositPos() {
        return destination != null ? destination : stationPos;
    }

    @Override
    protected void onDepositComplete() {
        destination = null;
    }

    // ──────────────────────────────────────────────────────────────

    private boolean hasSortableInput(ServerLevel level) {
        BlockEntity be = level.getBlockEntity(stationPos);
        if (!(be instanceof PalWorkStationBlockEntity station)) return false;
        ItemStack stored = station.getStoredItem();
        return !stored.isEmpty() && findDestinationFor(level, stored) != null;
    }

    /**
     * Finds a nearby container that already holds the same item and has room —
     * "put it where its kind already lives".
     */
    @Nullable
    private BlockPos findDestinationFor(ServerLevel level, ItemStack item) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(
                stationPos.getX() - harvestRadius, stationPos.getY() - 2, stationPos.getZ() - harvestRadius,
                stationPos.getX() + harvestRadius, stationPos.getY() + 2, stationPos.getZ() + harvestRadius)) {
            // Skip the station itself and the drain hopper directly below it —
            // the hopper always holds the same item and would swallow everything
            if (pos.equals(stationPos) || pos.equals(stationPos.below())) continue;
            BlockEntity be = level.getBlockEntity(pos);
            if (be == null || be instanceof PalWorkStationBlockEntity || be instanceof PalFeederBlockEntity) continue;
            IItemHandler handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElse(null);
            if (handler == null) continue;

            boolean containsSame = false;
            for (int i = 0; i < handler.getSlots(); i++) {
                if (ItemStack.isSameItemSameTags(handler.getStackInSlot(i), item)) {
                    containsSame = true;
                    break;
                }
            }
            if (!containsSame) continue;

            // Must actually have room for at least one item
            ItemStack leftover = ItemHandlerHelper.insertItemStacked(handler, item.copyWithCount(1), true);
            if (!leftover.isEmpty()) continue;

            double d = pos.distSqr(stationPos);
            if (d < bestDist) {
                bestDist = d;
                best = pos.immutable();
            }
        }
        return best;
    }
}
