package com.mx.palmod.enchantment;

import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * Following (I-II): After landing, the sphere chases the nearest LivingEntity
 * within 5 blocks (I) or 8 blocks (II).
 */
public class FollowingEnchantment extends Enchantment {

    public FollowingEnchantment(Rarity rarity, EnchantmentCategory category) {
        super(rarity, category, new net.minecraft.world.entity.EquipmentSlot[]{});
    }

    @Override
    public int getMaxLevel() {
        return 2;
    }

    @Override
    public int getMinCost(int pLevel) {
        return pLevel * 12;
    }

    @Override
    public int getMaxCost(int pLevel) {
        return getMinCost(pLevel) + 20;
    }

    /** Returns the homing radius for the given enchant level. */
    public static double getHomingRadius(int enchantLevel) {
        return enchantLevel >= 2 ? 8.0 : 5.0;
    }
}
