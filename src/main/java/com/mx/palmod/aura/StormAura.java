package com.mx.palmod.aura;

import com.mx.palmod.behavior.PalBehavior;
import com.mx.palmod.behavior.PalBehaviorManager;
import com.mx.palmod.stats.PalStats;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "storm": while a fed storm Pal is summoned near its owner, the owner's melee
 * hits call lightning down on the victim.
 *
 * The aura pulse only refreshes a cached per-player charge (O(1) lookup on the
 * hit path — never scan entities per hit). The actual proc runs from
 * ForgeEvents' LivingHurtEvent handler via {@link #tryProc}.
 *
 * Safety: the bolt is visual-only + manual damage on the victim ONLY (no area
 * zap hitting the owner/pals, no fire, no mob conversion), and the proc damage
 * source IS lightning so the event handler's anti-loop check kills re-entry.
 */
public class StormAura implements PalAura {

    private record Charge(UUID palId, long expiryGameTime) {}

    private static final Map<UUID, Charge> CHARGES = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_PROC = new ConcurrentHashMap<>();

    @Override
    public boolean apply(ServerLevel level, Mob pal, ServerPlayer owner, PalBehavior behavior) {
        // Refresh the charge with a grace window past the next expected pulse
        CHARGES.put(owner.getUUID(),
                new Charge(pal.getUUID(), level.getGameTime() + behavior.getAuraIntervalTicks() + 40));

        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                pal.getX(), pal.getY() + 0.6, pal.getZ(), 6, 0.4, 0.3, 0.4, 0.05);
        return true;
    }

    /**
     * Called from the LivingHurtEvent handler when the owner lands a direct
     * melee hit. Returns the BONUS damage to add onto that hit (0 = no proc).
     * The bonus rides the triggering hit via event.setAmount — a nested
     * victim.hurt() here would be swallowed by the 20-tick hurt-invulnerability
     * window the melee hit just opened.
     */
    public static float tryProc(ServerPlayer player, LivingEntity victim) {
        if (!(player.level() instanceof ServerLevel level)) return 0;

        Charge charge = CHARGES.get(player.getUUID());
        long now = level.getGameTime();
        if (charge == null || now > charge.expiryGameTime()) return 0;

        // Resolve the storm pal (same dimension only) for params + hunger billing
        Entity palEntity = level.getEntity(charge.palId());
        if (!(palEntity instanceof Mob pal) || !pal.isAlive()) return 0;
        PalBehavior behavior = PalBehaviorManager.getBehavior(pal.getType());
        if (PalStats.isStarving(pal)) return 0;

        long cooldown = (long) behavior.getAuraParam("storm_cooldown_ticks", 60.0f);
        Long last = LAST_PROC.get(player.getUUID());
        if (last != null && now - last < cooldown) return 0;
        LAST_PROC.put(player.getUUID(), now);

        // Visual-only bolt for the spectacle (no fire, no area zap, no conversion)
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(victim.getX(), victim.getY(), victim.getZ());
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }

        PalStats.modifyHunger(pal, -behavior.getAuraParam("storm_hunger_cost", 2.0f));
        return behavior.getAuraParam("storm_damage", 5.0f);
    }

    /** Drop cached state for a player (called on logout). */
    public static void clear(UUID playerId) {
        CHARGES.remove(playerId);
        LAST_PROC.remove(playerId);
    }
}
