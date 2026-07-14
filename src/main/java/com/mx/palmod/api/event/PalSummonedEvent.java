package com.mx.palmod.api.event;

import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;

/** Fired after a filled sphere throw finishes placing a pal into the world. */
public class PalSummonedEvent extends PalEvent {
    public PalSummonedEvent(LivingEntity pal, UUID ownerUUID) {
        super(pal, ownerUUID);
    }
}
