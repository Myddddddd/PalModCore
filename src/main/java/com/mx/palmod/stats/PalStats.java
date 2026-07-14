package com.mx.palmod.stats;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Static utility class for managing Pal stat values (Hunger, Mood) stored in PersistentData NBT.
 *
 * NBT keys used:
 *   PalHunger         (float 0–100, default 100)
 *   PalMood           (float 0–100, default 80)
 *   PalHungerDecayTimer (int tick counter)
 *   PalBaseSpeed      (double, cached base move speed)
 */
public final class PalStats {

    // Threshold constants
    public static final float HUNGER_SEEK_FEEDER = 80.0f;   // Below this: seek feeder
    public static final float HUNGER_STARVING     = 30.0f;   // Below this: reduced performance
    public static final float HUNGER_INACTIVE     = 5.0f;    // Below this: stop all actions

    public static final float MAX_HUNGER = 100.0f;
    public static final float MAX_MOOD   = 100.0f;

    private static final String KEY_HUNGER       = "PalHunger";
    private static final String KEY_MOOD         = "PalMood";
    private static final String KEY_DECAY_TIMER  = "PalHungerDecayTimer";
    private static final String KEY_BASE_SPEED   = "PalBaseSpeed";
    private static final String KEY_SPEED_APPLIED = "PalSpeedApplied";
    private static final String KEY_SPEED_TIER   = "PalSpeedTier";

    private PalStats() {}

    // ──────────────────────────────────────────────────────────────
    //  Hunger
    // ──────────────────────────────────────────────────────────────

    public static float getHunger(LivingEntity entity) {
        CompoundTag data = entity.getPersistentData();
        if (!data.contains(KEY_HUNGER)) {
            data.putFloat(KEY_HUNGER, MAX_HUNGER);
        }
        return data.getFloat(KEY_HUNGER);
    }

    public static void setHunger(LivingEntity entity, float value) {
        float clamped = Math.max(0f, Math.min(MAX_HUNGER, value));
        entity.getPersistentData().putFloat(KEY_HUNGER, clamped);
    }

    /**
     * Modifies hunger by delta (negative = consume, positive = restore).
     * Returns the new hunger value.
     */
    public static float modifyHunger(LivingEntity entity, float delta) {
        float newVal = Math.max(0f, Math.min(MAX_HUNGER, getHunger(entity) + delta));
        entity.getPersistentData().putFloat(KEY_HUNGER, newVal);
        return newVal;
    }

    // ──────────────────────────────────────────────────────────────
    //  Mood
    // ──────────────────────────────────────────────────────────────

    public static float getMood(LivingEntity entity) {
        CompoundTag data = entity.getPersistentData();
        if (!data.contains(KEY_MOOD)) {
            data.putFloat(KEY_MOOD, 80.0f);
        }
        return data.getFloat(KEY_MOOD);
    }

    public static void setMood(LivingEntity entity, float value) {
        float clamped = Math.max(0f, Math.min(MAX_MOOD, value));
        entity.getPersistentData().putFloat(KEY_MOOD, clamped);
    }

    public static float modifyMood(LivingEntity entity, float delta) {
        float newVal = Math.max(0f, Math.min(MAX_MOOD, getMood(entity) + delta));
        entity.getPersistentData().putFloat(KEY_MOOD, newVal);
        return newVal;
    }

    // ──────────────────────────────────────────────────────────────
    //  State helpers
    // ──────────────────────────────────────────────────────────────

    /** Below 5% hunger — mob stops all actions. */
    public static boolean isInactive(LivingEntity entity) {
        return getHunger(entity) < HUNGER_INACTIVE;
    }

    /** Below 30% hunger — reduced performance. */
    public static boolean isStarving(LivingEntity entity) {
        return getHunger(entity) < HUNGER_STARVING;
    }

    /** Below 80% hunger — mob seeks a feeder. */
    public static boolean needsFood(LivingEntity entity) {
        return getHunger(entity) < HUNGER_SEEK_FEEDER;
    }

    // ──────────────────────────────────────────────────────────────
    //  Decay timer
    // ──────────────────────────────────────────────────────────────

    public static int getDecayTimer(LivingEntity entity) {
        return entity.getPersistentData().getInt(KEY_DECAY_TIMER);
    }

    public static void setDecayTimer(LivingEntity entity, int value) {
        entity.getPersistentData().putInt(KEY_DECAY_TIMER, value);
    }

    // ──────────────────────────────────────────────────────────────
    //  Speed management
    // ──────────────────────────────────────────────────────────────

    /**
     * Applies a movement-speed penalty when starving, or restores normal speed.
     * Must be called from server-side tick.
     */
    public static void applySpeedEffects(Mob mob) {
        var attribute = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute == null) return;

        CompoundTag data = mob.getPersistentData();
        boolean alreadyApplied = data.getBoolean(KEY_SPEED_APPLIED);

        // Inactive pals crawl (0.3x) instead of freezing solid — a starving pal
        // must still be able to reach a feeder or it can never recover.
        int tier = isInactive(mob) ? 2 : isStarving(mob) ? 1 : 0;
        int appliedTier = data.getInt(KEY_SPEED_TIER);

        if (tier > 0) {
            if (!alreadyApplied) {
                // Cache base speed
                data.putDouble(KEY_BASE_SPEED, attribute.getBaseValue());
            }
            if (!alreadyApplied || appliedTier != tier) {
                double base = data.getDouble(KEY_BASE_SPEED);
                float mult = tier == 2 ? 0.3f : 0.7f;
                attribute.setBaseValue(base * mult);
                data.putBoolean(KEY_SPEED_APPLIED, true);
                data.putInt(KEY_SPEED_TIER, tier);
            }
        } else {
            if (alreadyApplied) {
                // Restore cached speed
                if (data.contains(KEY_BASE_SPEED)) {
                    attribute.setBaseValue(data.getDouble(KEY_BASE_SPEED));
                }
                data.putBoolean(KEY_SPEED_APPLIED, false);
                data.putInt(KEY_SPEED_TIER, 0);
            }
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Convenience: percent
    // ──────────────────────────────────────────────────────────────

    public static float getHungerPercent(LivingEntity entity) {
        return getHunger(entity) / MAX_HUNGER * 100f;
    }

    public static float getMoodPercent(LivingEntity entity) {
        return getMood(entity) / MAX_MOOD * 100f;
    }
}
