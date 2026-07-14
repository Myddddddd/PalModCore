package com.mx.palmod.enchantment;

import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * Infinite (I-II): On miss or failed catch, 80%/99% chance to return the sphere to the player.
 */
public class InfiniteEnchantment extends Enchantment {

    public InfiniteEnchantment(Rarity rarity, EnchantmentCategory category) {
        super(rarity, category, new net.minecraft.world.entity.EquipmentSlot[]{});
    }

    @Override
    public int getMaxLevel() {
        return 2;
    }

    @Override
    public int getMinCost(int pLevel) {
        return pLevel * 10;
    }

    @Override
    public int getMaxCost(int pLevel) {
        return getMinCost(pLevel) + 25;
    }

    /** Returns the return-chance (0.0–1.0) for the given enchant level. */
    public static float getReturnChance(int enchantLevel) {
        return enchantLevel >= 2 ? 0.99f : 0.80f;
    }
}
