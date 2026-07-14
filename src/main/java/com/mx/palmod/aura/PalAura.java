package com.mx.palmod.aura;

import com.mx.palmod.behavior.PalBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

/**
 * An effect a summoned, fed Pal continuously grants its owner.
 * Registered in {@link PalAuraManager} under the aura.type datapack string;
 * ticked from ForgeEvents.onLivingTick on the Pal's 20-tick cadence.
 */
public interface PalAura {

    /**
     * Called once per aura interval while the Pal is alive, fed, and its owner
     * is online and within aura radius.
     *
     * @return true if the pulse did real work — only then is the Pal charged
     *         the aura hunger cost.
     */
    boolean apply(ServerLevel level, Mob pal, ServerPlayer owner, PalBehavior behavior);
}
