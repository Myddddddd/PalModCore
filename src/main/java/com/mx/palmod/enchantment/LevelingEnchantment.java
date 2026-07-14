package com.mx.palmod.enchantment;

import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * Leveling (I–V): Each level adds +1 to the sphere's effective level for catch-rate calculation.
 */
public class LevelingEnchantment extends Enchantment {

    public LevelingEnchantment(Rarity rarity, EnchantmentCategory category) {
        super(rarity, category, new net.minecraft.world.entity.EquipmentSlot[]{});
    }

    @Override
    public int getMaxLevel() {
        return 5;
    }

    @Override
    public int getMinCost(int pLevel) {
        return pLevel * 8;
    }

    @Override
    public int getMaxCost(int pLevel) {
        return getMinCost(pLevel) + 20;
    }

    /** Returns the level bonus contributed by this enchantment. */
    public static int getLevelBonus(int enchantLevel) {
        return enchantLevel; // +1 per level
    }
}
