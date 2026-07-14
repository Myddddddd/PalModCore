package com.mx.palmod.api.event;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.Cancelable;

import java.util.UUID;

/**
 * Fired the instant a wild-catch roll succeeds, before the pal entity is removed
 * and the filled sphere is granted. Cancel to veto the catch outright — it's
 * treated exactly like a failed catch roll (sphere lost, unless Infinite returns it).
 */
@Cancelable
public class PalCaughtEvent extends PalEvent {

    private final ItemStack sphereStack;
    private final int effectiveLevel;

    public PalCaughtEvent(LivingEntity pal, UUID ownerUUID, ItemStack sphereStack, int effectiveLevel) {
        super(pal, ownerUUID);
        this.sphereStack = sphereStack;
        this.effectiveLevel = effectiveLevel;
    }

    /** The Pal Sphere item that made the catch (before it's converted to a filled sphere). */
    public ItemStack getSphereStack() {
        return sphereStack;
    }

    /** Sphere tier + Leveling enchant bonus combined. */
    public int getEffectiveLevel() {
        return effectiveLevel;
    }
}
