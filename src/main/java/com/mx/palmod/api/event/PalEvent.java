package com.mx.palmod.api.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.Event;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Base class for Palmod's addon-facing lifecycle events, fired on the Forge event
 * bus ({@code MinecraftForge.EVENT_BUS}). Subscribe with a normal {@code @SubscribeEvent}
 * method — no compile dependency on Palmod internals beyond this package.
 */
public abstract class PalEvent extends Event {

    private final LivingEntity pal;
    private final UUID ownerUUID;

    protected PalEvent(LivingEntity pal, UUID ownerUUID) {
        this.pal = pal;
        this.ownerUUID = ownerUUID;
    }

    /** The pal entity this event concerns. */
    public LivingEntity getPal() {
        return pal;
    }

    /** The owning player's UUID — resolve with {@link #getOwner()} if they need to be online. */
    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    /** The owning player, or null if they're not currently online on this server. */
    @Nullable
    public Player getOwner() {
        if (pal.level() instanceof ServerLevel serverLevel) {
            return serverLevel.getServer().getPlayerList().getPlayer(ownerUUID);
        }
        return null;
    }
}
