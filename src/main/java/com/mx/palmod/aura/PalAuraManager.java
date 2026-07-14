package com.mx.palmod.aura;

import com.mojang.logging.LogUtils;
import com.mx.palmod.behavior.PalBehavior;
import com.mx.palmod.stats.PalStats;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry + dispatcher for owner auras. A Pal with a non-empty aura.type in
 * its behavior config pulses its aura every aura.interval_ticks while summoned,
 * fed, and near its owner. Each pulse costs aura.hunger_cost.
 */
public final class PalAuraManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String KEY_TIMER = "PalAuraTimer";

    private static final Map<String, PalAura> AURAS = new HashMap<>();

    static {
        register("heal", new HealAura());
        register("storm", new StormAura());
    }

    private PalAuraManager() {}

    public static void register(String type, PalAura aura) {
        AURAS.put(type, aura);
    }

    /** Called from onLivingTick (every 20 ticks) for each owned Pal. */
    public static void tickAura(Mob mob, PalBehavior behavior) {
        if (behavior.getAuraType().isEmpty()) return;
        if (!(mob.level() instanceof ServerLevel level)) return;

        CompoundTag data = mob.getPersistentData();
        int timer = data.getInt(KEY_TIMER) + 20;
        if (timer < behavior.getAuraIntervalTicks()) {
            data.putInt(KEY_TIMER, timer);
            return;
        }
        data.putInt(KEY_TIMER, 0);

        // A starving Pal's aura goes dark
        if (PalStats.isStarving(mob)) return;

        PalAura aura = AURAS.get(behavior.getAuraType());
        if (aura == null) {
            LOGGER.warn("Unknown aura type '{}' for {}", behavior.getAuraType(), mob.getType());
            return;
        }

        if (!data.contains("PalOwner")) return;
        Player player = level.getPlayerByUUID(data.getUUID("PalOwner"));
        if (!(player instanceof ServerPlayer owner)) return;

        double radius = behavior.getAuraRadius();
        if (owner.distanceToSqr(mob) > radius * radius) return;

        if (aura.apply(level, mob, owner, behavior)) {
            PalStats.modifyHunger(mob, -behavior.getAuraHungerCost());
        }
    }
}
