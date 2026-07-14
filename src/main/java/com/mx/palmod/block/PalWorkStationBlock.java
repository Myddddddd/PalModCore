package com.mx.palmod.block;

import com.mx.palmod.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
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

public class PalWorkStationBlock extends BaseEntityBlock {

    protected static final VoxelShape SHAPE = box(2.0, 0.0, 2.0, 14.0, 10.0, 14.0);

    public PalWorkStationBlock(BlockBehaviour.Properties pProperties) {
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
        return new PalWorkStationBlockEntity(pPos, pState);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level pLevel, BlockState pState, BlockEntityType<T> pBlockEntityType) {
        if (pLevel.isClientSide) return null;
        return createTickerHelper(pBlockEntityType, ModRegistries.PAL_WORK_STATION_BLOCK_ENTITY.get(),
                (level, pos, state, be) -> be.serverTick(level, pos, state));
    }

    @Override
    public InteractionResult use(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer, InteractionHand pHand, BlockHitResult pHit) {
        if (pLevel.isClientSide) return InteractionResult.SUCCESS;
        BlockEntity be = pLevel.getBlockEntity(pPos);
        if (be instanceof PalWorkStationBlockEntity station) {
            ItemStack contained = station.getStoredItem();
            if (!contained.isEmpty()) {
                // Hand the stored produce to the player — without this (and
                // without a hopper) a full station stalls its worker forever
                ItemStack taken = station.extractStored(contained.getCount());
                pPlayer.sendSystemMessage(Component.literal(
                        "Collected " + taken.getCount() + "x " + taken.getHoverName().getString()));
                if (!pPlayer.getInventory().add(taken)) {
                    pPlayer.drop(taken, false);
                }
            } else {
                pPlayer.sendSystemMessage(Component.literal("Ant station is empty."));
            }
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void playerWillDestroy(Level pLevel, BlockPos pPos, BlockState pState, Player pPlayer) {
        if (!pLevel.isClientSide && pLevel instanceof ServerLevel serverLevel) {
            BlockEntity be = pLevel.getBlockEntity(pPos);
            if (be instanceof PalWorkStationBlockEntity station) {
                station.onBreak(serverLevel, pPos, pPlayer);
            }
        }
        super.playerWillDestroy(pLevel, pPos, pState, pPlayer);
    }

    @Override
    public void setPlacedBy(Level pLevel, BlockPos pPos, BlockState pState, @Nullable LivingEntity pPlacer, ItemStack pStack) {
        // Block is placed programmatically only
    }
}
