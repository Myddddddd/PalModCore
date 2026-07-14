package com.mx.palmod.api.event;

import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;

/** Fired when a pal roots into a deploy mode (anchor/sentry) on summon. */
public class PalDeployedEvent extends PalEvent {

    private final String deployMode;

    public PalDeployedEvent(LivingEntity pal, UUID ownerUUID, String deployMode) {
        super(pal, ownerUUID);
        this.deployMode = deployMode;
    }

    /** "anchor" or "sentry" for the built-in modes; addons may define others. */
    public String getDeployMode() {
        return deployMode;
    }
}
