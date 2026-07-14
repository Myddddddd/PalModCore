package com.mx.palmod.block;

import com.mx.palmod.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

public class PalFeederBlock extends BaseEntityBlock {

    // Trough shape: flat wide basin
    protected static final VoxelShape SHAPE = box(0.0, 0.0, 0.0, 16.0, 8.0, 16.0);

    public PalFeederBlock(BlockBehaviour.Properties pProperties) {
        super(pProperties);
    }

    @Override
    public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState pState) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
        return new PalFeederBlockEntity(pPos, pState);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level pLevel, BlockState pState, BlockEntityType<T> pBlockEntityType) {
        if (pLevel.isClientSide) return null;
        return createTickerHelper(pBlockEntityType, ModRegistries.PAL_FEEDER_BLOCK_ENTITY.get(),
                (level, pos, state, be) -> be.serverTick(level, pos, state));
    }

    @Override
    public InteractionResult use(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer,
                                 InteractionHand pHand, BlockHitResult pHit) {
        if (pLevel.isClientSide) return InteractionResult.SUCCESS;

        BlockEntity be = pLevel.getBlockEntity(pPos);
        if (!(be instanceof PalFeederBlockEntity feeder)) return InteractionResult.PASS;

        ItemStack held = pPlayer.getItemInHand(pHand);

        if (!held.isEmpty()) {
            // Try to place item into feeder
            ItemStack current = feeder.getFood();
            if (current.isEmpty()) {
                // Empty feeder — fill with 1 item from hand
                feeder.setFood(held.copyWithCount(1));
                if (!pPlayer.getAbilities().instabuild) held.shrink(1);
                pPlayer.sendSystemMessage(Component.literal("Placed " + feeder.getFood().getHoverName().getString() + " in the feeder."));
                return InteractionResult.CONSUME;
            } else if (ItemStack.isSameItemSameTags(current, held)) {
                // Same item — top up (max 64)
                int space = 64 - current.getCount();
                if (space > 0) {
                    int add = Math.min(held.getCount(), space);
                    current.grow(add);
                    feeder.setFood(current);
                    if (!pPlayer.getAbilities().instabuild) held.shrink(add);
                    pPlayer.sendSystemMessage(Component.literal("Feeder now has " + current.getCount() + "x " + current.getHoverName().getString()));
                    return InteractionResult.CONSUME;
                } else {
                    pPlayer.sendSystemMessage(Component.literal("Feeder is full!"));
                    return InteractionResult.CONSUME;
                }
            } else {
                pPlayer.sendSystemMessage(Component.literal("Feeder already contains " + current.getHoverName().getString() + ". Remove it first by right-clicking empty-handed."));
                return InteractionResult.CONSUME;
            }
        } else {
            // Empty hand — inspect or remove contents
            ItemStack current = feeder.getFood();
            if (current.isEmpty()) {
                pPlayer.sendSystemMessage(Component.literal("Feeder is empty."));
            } else {
                // Give back the food to player
                if (!pPlayer.getAbilities().instabuild) {
                    if (!pPlayer.getInventory().add(current.copy())) {
                        pPlayer.drop(current.copy(), false);
                    }
                }
                feeder.setFood(ItemStack.EMPTY);
                pPlayer.sendSystemMessage(Component.literal("Removed food from feeder."));
            }
            return InteractionResult.CONSUME;
        }
    }
}
