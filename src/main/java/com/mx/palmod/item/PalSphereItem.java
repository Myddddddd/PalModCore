package com.mx.palmod.item;

import com.mx.palmod.enchantment.FastBallEnchantment;
import com.mx.palmod.entity.PalSphereProjectile;
import com.mx.palmod.registry.ModRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.List;

public class PalSphereItem extends Item {

    /** Sphere tier level: 1 = Basic, 5 = Mid, 10 = Advanced */
    private final int sphereLevel;

    public PalSphereItem(Properties pProperties, int sphereLevel) {
        super(pProperties);
        this.sphereLevel = sphereLevel;
    }

    public int getSphereLevel() {
        return sphereLevel;
    }

    @Override
    public void appendHoverText(ItemStack pStack, @Nullable Level pLevel, List<Component> pTooltipComponents, TooltipFlag pIsAdvanced) {
        super.appendHoverText(pStack, pLevel, pTooltipComponents, pIsAdvanced);
        pTooltipComponents.add(Component.literal("Sphere Level: " + sphereLevel)
                .withStyle(sphereLevel >= 10 ? net.minecraft.ChatFormatting.AQUA
                         : sphereLevel >= 5  ? net.minecraft.ChatFormatting.GOLD
                         : net.minecraft.ChatFormatting.WHITE));
    }

    @Override
    public boolean isEnchantable(ItemStack pStack) {
        return true;
    }

    @Override
    public int getEnchantmentValue() {
        return 10;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level pLevel, Player pPlayer, InteractionHand pHand) {
        ItemStack itemstack = pPlayer.getItemInHand(pHand);
        throwSphere(pLevel, pPlayer, itemstack, sphereLevel);

        pPlayer.awardStat(Stats.ITEM_USED.get(this));
        if (!pPlayer.getAbilities().instabuild) {
            itemstack.shrink(1);
        }

        return InteractionResultHolder.sidedSuccess(itemstack, pLevel.isClientSide());
    }

    /**
     * Launches the catching projectile. Shared by use() and the close-range
     * path (right-clicking directly on a mob routes to EntityInteract, which
     * never reaches use() — ForgeEvents calls this instead).
     */
    public static void throwSphere(Level pLevel, Player pPlayer, ItemStack itemstack, int sphereLevel) {
        pLevel.playSound(null, pPlayer.getX(), pPlayer.getY(), pPlayer.getZ(),
                SoundEvents.SNOWBALL_THROW, SoundSource.NEUTRAL, 0.5F, 0.4F / (pLevel.getRandom().nextFloat() * 0.4F + 0.8F));

        if (!pLevel.isClientSide) {
            PalSphereProjectile projectile = new PalSphereProjectile(pLevel, pPlayer);
            projectile.setItem(itemstack);
            projectile.setSphereLevel(sphereLevel);
            projectile.setEnchantments(itemstack);

            // FastBall: extreme throw speed
            int fastBallLevel = EnchantmentHelper.getItemEnchantmentLevel(
                    ModRegistries.ENCHANT_FASTBALL.get(), itemstack);
            float speed = fastBallLevel > 0 ? FastBallEnchantment.FAST_SPEED : 1.5f;

            projectile.shootFromRotation(pPlayer, pPlayer.getXRot(), pPlayer.getYRot(), 0.0F, speed, 1.0F);
            pLevel.addFreshEntity(projectile);
        }
    }
}

