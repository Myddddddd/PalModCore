package com.mx.palmod.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import java.util.List;

/**
 * Standalone Paldex item. Right-click opens the Alex's Mobs / Citadel dictionary
 * book at Palmod's index page (a clickable table of contents → guide sections +
 * bestiary), reusing that book engine instead of any custom GUI. The open is
 * purely client-side (like AM's own dictionary item) — no networking needed.
 */
public class PaldexItem extends Item {
    public PaldexItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (level.isClientSide) {
            // PaldexClient (and the AM book classes it touches) load only on the client.
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.mx.palmod.client.PaldexClient.open(held));
        }
        return InteractionResultHolder.sidedSuccess(held, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.palmod.paldex").withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, level, tooltip, flag);
    }
}
