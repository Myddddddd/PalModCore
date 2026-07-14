package com.mx.palmod.block;

import com.mx.palmod.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
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

/**
 * Stores a single food stack.
 * Supports Hopper insertion from above.
 * Mobs feed themselves via PalSeekFeederGoal by calling consumeFood().
 */
public class PalFeederBlockEntity extends BlockEntity {

    private final ItemStackHandler itemHandler = new ItemStackHandler(1) {
        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private final LazyOptional<IItemHandler> lazyItemHandler = LazyOptional.of(() -> itemHandler);

    public PalFeederBlockEntity(BlockPos pPos, BlockState pState) {
        super(ModRegistries.PAL_FEEDER_BLOCK_ENTITY.get(), pPos, pState);
    }

    // ─────────────────────────────────────────────────────────────────
    //  Food access
    // ─────────────────────────────────────────────────────────────────

    public ItemStack getFood() {
        return itemHandler.getStackInSlot(0);
    }

    public void setFood(ItemStack stack) {
        itemHandler.setStackInSlot(0, stack.copy());
        setChanged();
    }

    public boolean isEmpty() {
        return itemHandler.getStackInSlot(0).isEmpty();
    }

    /**
     * Removes one item from the feeder (mob eating).
     * Returns the item that was consumed, or EMPTY if nothing available.
     */
    public ItemStack consumeOne() {
        ItemStack stored = itemHandler.getStackInSlot(0);
        if (stored.isEmpty()) return ItemStack.EMPTY;
        ItemStack eaten = stored.copyWithCount(1);
        stored.shrink(1);
        if (stored.isEmpty()) {
            itemHandler.setStackInSlot(0, ItemStack.EMPTY);
        }
        setChanged();
        return eaten;
    }

    // ─────────────────────────────────────────────────────────────────
    //  Tick — passive hopper from above already handled by IItemHandler capability
    // ─────────────────────────────────────────────────────────────────

    public void serverTick(Level level, BlockPos pos, BlockState state) {
        // Hopper insertion from above is handled automatically via IItemHandler capability.
        // No active logic needed here; mobs poll feeders themselves via goal.
    }

    // ─────────────────────────────────────────────────────────────────
    //  NBT
    // ─────────────────────────────────────────────────────────────────

    @Override
    protected void saveAdditional(CompoundTag pTag) {
        super.saveAdditional(pTag);
        pTag.put("Food", itemHandler.serializeNBT());
    }

    @Override
    public void load(CompoundTag pTag) {
        super.load(pTag);
        if (pTag.contains("Food")) itemHandler.deserializeNBT(pTag.getCompound("Food"));
    }

    // ─────────────────────────────────────────────────────────────────
    //  Capabilities
    // ─────────────────────────────────────────────────────────────────

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
