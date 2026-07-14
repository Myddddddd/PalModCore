package com.mx.palmod.enchantment;

import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * WarpTether (I): a Pal caught with this sphere links its owner to it —
 * deliberately recalling the summoned Pal teleports the owner to where
 * the Pal stood.
 */
public class WarpTetherEnchantment extends Enchantment {

    public WarpTetherEnchantment(Rarity rarity, EnchantmentCategory category) {
        super(rarity, category, new net.minecraft.world.entity.EquipmentSlot[]{});
    }

    @Override
    public int getMaxLevel() {
        return 1;
    }

    @Override
    public int getMinCost(int pLevel) {
        return 20;
    }

    @Override
    public int getMaxCost(int pLevel) {
        return 50;
    }
}
