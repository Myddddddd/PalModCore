package com.mx.palmod.enchantment;

import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * FastBall (I): Sphere is thrown at extreme velocity.
 */
public class FastBallEnchantment extends Enchantment {

    public static final float FAST_SPEED = 5.0f; // vs normal 1.5f

    public FastBallEnchantment(Rarity rarity, EnchantmentCategory category) {
        super(rarity, category, new net.minecraft.world.entity.EquipmentSlot[]{});
    }

    @Override
    public int getMaxLevel() {
        return 1;
    }

    @Override
    public int getMinCost(int pLevel) {
        return 15;
    }

    @Override
    public int getMaxCost(int pLevel) {
        return 40;
    }
}
