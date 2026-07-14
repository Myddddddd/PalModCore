package com.mx.palmod.api.event;

import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;

/** Fired when an owned pal dies, right before its sphere reverts to an empty Pal Sphere. */
public class PalDiedEvent extends PalEvent {
    public PalDiedEvent(LivingEntity pal, UUID ownerUUID) {
        super(pal, ownerUUID);
    }
}
