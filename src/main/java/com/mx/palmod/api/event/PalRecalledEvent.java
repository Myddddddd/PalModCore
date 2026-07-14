package com.mx.palmod.api.event;

import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;

/**
 * Fired right before a released pal is recalled into its sphere — covers both a
 * deliberate player recall and an automatic one (inventory shuffle, item toss,
 * logout).
 */
public class PalRecalledEvent extends PalEvent {
    public PalRecalledEvent(LivingEntity pal, UUID ownerUUID) {
        super(pal, ownerUUID);
    }
}
