package com.mx.palmod.block;

import com.mx.palmod.item.FilledPalSphereItem;
import com.mx.palmod.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

public class PalWorkStationBlockEntity extends BlockEntity {

    // 1 slot for harvested goods (max 64 of one item type)
    private final ItemStackHandler itemHandler = new ItemStackHandler(1) {
        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }
    };
    private final LazyOptional<IItemHandler> lazyItemHandler = LazyOptional.of(() -> itemHandler);

    // NBT data of the original FilledPalSphere
    private CompoundTag sphereNbt = new CompoundTag();
    // UUID of the worker ant currently attached
    @Nullable
    private UUID workerEntityUUID = null;
    // Grace period before declaring the worker dead: on (re)load the station's
    // chunk can tick before the worker entity is loaded, and instantly breaking
    // the station here would cascade into discarding the ant when it DOES load.
    private int workerMissingTicks = 0;
    private static final int WORKER_MISSING_GRACE_TICKS = 200;
    // Last place the worker was seen. getEntity() only finds entities in loaded
    // chunks — the station may block-tick while the worker's chunk is unloaded,
    // and that must NOT count as "vanished" (it would dupe the pal).
    @Nullable
    private BlockPos lastWorkerPos = null;

    public PalWorkStationBlockEntity(BlockPos pPos, BlockState pState) {
        super(ModRegistries.PAL_WORK_STATION_BLOCK_ENTITY.get(), pPos, pState);
    }

    public void setSphereNbt(CompoundTag nbt) {
        this.sphereNbt = nbt.copy();
        setChanged();
    }

    public void setWorkerUUID(UUID uuid) {
        this.workerEntityUUID = uuid;
        setChanged();
    }

    /** Returns the item stored by the ant (harvested goods). */
    public ItemStack getStoredItem() {
        return itemHandler.getStackInSlot(0);
    }

    /**
     * Deposit an item into the station. Returns the leftover that couldn't fit.
     */
    public ItemStack depositItem(ItemStack stack) {
        ItemStack existing = itemHandler.getStackInSlot(0);
        if (existing.isEmpty()) {
            int toStore = Math.min(stack.getCount(), 64);
            itemHandler.setStackInSlot(0, stack.copyWithCount(toStore));
            stack.shrink(toStore);
            setChanged();
        } else if (ItemStack.isSameItemSameTags(existing, stack)) {
            int space = 64 - existing.getCount();
            if (space > 0) {
                int toStore = Math.min(stack.getCount(), space);
                existing.grow(toStore);
                stack.shrink(toStore);
                setChanged();
            }
        }
        return stack;
    }

    public boolean isFull() {
        ItemStack s = itemHandler.getStackInSlot(0);
        return !s.isEmpty() && s.getCount() >= 64;
    }

    /** Take up to count items out of the station (sorter withdrawing its input). */
    public ItemStack extractStored(int count) {
        ItemStack stored = itemHandler.getStackInSlot(0);
        if (stored.isEmpty()) return ItemStack.EMPTY;
        ItemStack taken = stored.split(count);
        if (stored.isEmpty()) {
            itemHandler.setStackInSlot(0, ItemStack.EMPTY);
        }
        setChanged();
        return taken;
    }

    public void serverTick(Level level, BlockPos pos, BlockState state) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        // Check if worker is alive
        if (workerEntityUUID != null) {
            Entity worker = serverLevel.getEntity(workerEntityUUID);
            if (worker != null && worker.isAlive()) {
                lastWorkerPos = worker.blockPosition();
                workerMissingTicks = 0;
            } else if (lastWorkerPos != null && !serverLevel.isPositionEntityTicking(lastWorkerPos)) {
                // The worker's chunk isn't entity-ticking — it may simply be
                // unloaded out there. Hold the counter instead of counting down
                // to a false "vanished" verdict.
            } else if (++workerMissingTicks >= WORKER_MISSING_GRACE_TICKS) {
                // Worker VANISHED without a confirmed death (hive AI swallowed
                // it, despawn, unloaded-chunk kill...). Return the FILLED
                // sphere so the pal itself isn't lost; a confirmed death goes
                // through onWorkerDied() and drops an empty sphere instead.
                ItemStack sphere;
                if (sphereNbt.contains("CapturedEntity")) {
                    CompoundTag returned = sphereNbt.copy();
                    returned.putBoolean("IsReleased", false);
                    returned.remove("EntityUUID");
                    sphere = new ItemStack(ModRegistries.FILLED_PAL_SPHERE.get());
                    sphere.setTag(returned);
                } else {
                    sphere = new ItemStack(ModRegistries.PAL_SPHERE.get());
                }
                breakAndDrop(serverLevel, pos, sphere);
                return;
            }
        }

        // Push items to hopper below
        BlockPos below = pos.below();
        BlockEntity belowBe = serverLevel.getBlockEntity(below);
        if (belowBe != null) {
            belowBe.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).ifPresent(handler -> {
                ItemStack stored = itemHandler.getStackInSlot(0);
                if (!stored.isEmpty()) {
                    for (int i = 0; i < handler.getSlots(); i++) {
                        ItemStack remaining = handler.insertItem(i, stored.copyWithCount(1), false);
                        if (remaining.isEmpty()) {
                            stored.shrink(1);
                            if (stored.isEmpty()) {
                                itemHandler.setStackInSlot(0, ItemStack.EMPTY);
                            }
                            setChanged();
                            break;
                        }
                    }
                }
            });
        }
    }

    /** Confirmed worker death (from onLivingDeath): pal death reverts to an EMPTY sphere. */
    public void onWorkerDied(ServerLevel serverLevel, BlockPos pos) {
        breakAndDrop(serverLevel, pos, new ItemStack(ModRegistries.PAL_SPHERE.get()));
    }

    /** Drops the given sphere + any stored produce, then removes the station block. */
    private void breakAndDrop(ServerLevel serverLevel, BlockPos pos, ItemStack sphere) {
        net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(
                serverLevel, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, sphere);
        serverLevel.addFreshEntity(drop);
        ItemStack stored = itemHandler.getStackInSlot(0);
        if (!stored.isEmpty()) {
            net.minecraft.world.entity.item.ItemEntity drop2 = new net.minecraft.world.entity.item.ItemEntity(
                    serverLevel, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, stored.copy());
            serverLevel.addFreshEntity(drop2);
        }
        serverLevel.removeBlock(pos, false);
    }

    /** Called when the block is broken by a player - returns the filled sphere. */
    public void onBreak(ServerLevel serverLevel, BlockPos pos, Player player) {
        // Recall the ant
        if (workerEntityUUID != null) {
            Entity worker = serverLevel.getEntity(workerEntityUUID);
            if (worker != null && worker.isAlive()) {
                // Save ant data back into sphere NBT
                CompoundTag entityData = new CompoundTag();
                if (worker.saveAsPassenger(entityData)) {
                    sphereNbt.put("CapturedEntity", entityData);
                }
                sphereNbt.putBoolean("IsReleased", false);
                worker.discard();
            }
        }

        // Drop the sphere with ant data
        ItemStack sphere = new ItemStack(ModRegistries.FILLED_PAL_SPHERE.get());
        sphere.setTag(sphereNbt.copy());
        net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(
                serverLevel, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, sphere);
        drop.setNoPickUpDelay();
        serverLevel.addFreshEntity(drop);

        // Drop harvested goods
        ItemStack stored = itemHandler.getStackInSlot(0);
        if (!stored.isEmpty()) {
            net.minecraft.world.entity.item.ItemEntity drop2 = new net.minecraft.world.entity.item.ItemEntity(
                    serverLevel, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, stored.copy());
            serverLevel.addFreshEntity(drop2);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag pTag) {
        super.saveAdditional(pTag);
        pTag.put("Inventory", itemHandler.serializeNBT());
        pTag.put("SphereNbt", sphereNbt);
        if (workerEntityUUID != null) {
            pTag.putUUID("WorkerUUID", workerEntityUUID);
        }
        if (lastWorkerPos != null) {
            pTag.putLong("LastWorkerPos", lastWorkerPos.asLong());
        }
    }

    @Override
    public void load(CompoundTag pTag) {
        super.load(pTag);
        if (pTag.contains("Inventory")) itemHandler.deserializeNBT(pTag.getCompound("Inventory"));
        if (pTag.contains("SphereNbt")) sphereNbt = pTag.getCompound("SphereNbt");
        if (pTag.hasUUID("WorkerUUID")) workerEntityUUID = pTag.getUUID("WorkerUUID");
        if (pTag.contains("LastWorkerPos")) lastWorkerPos = BlockPos.of(pTag.getLong("LastWorkerPos"));
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return lazyItemHandler.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        lazyItemHandler.invalidate();
    }
}
