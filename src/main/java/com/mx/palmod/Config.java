package com.mx.palmod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Forge's config APIs
@Mod.EventBusSubscriber(modid = Palmod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config
{
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue LOG_DIRT_BLOCK = BUILDER
            .comment("Whether to log the dirt block on common setup")
            .define("logDirtBlock", true);

    private static final ForgeConfigSpec.IntValue MAGIC_NUMBER = BUILDER
            .comment("A magic number")
            .defineInRange("magicNumber", 42, 0, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.ConfigValue<String> MAGIC_NUMBER_INTRODUCTION = BUILDER
            .comment("What you want the introduction message to be for the magic number")
            .define("magicNumberIntroduction", "The magic number is... ");

    // a list of strings that are treated as resource locations for items
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> ITEM_STRINGS = BUILDER
            .comment("A list of items to log on common setup.")
            .defineListAllowEmpty("items", List.of("minecraft:iron_ingot"), Config::validateItemName);

    private static final ForgeConfigSpec.DoubleValue CATCHABLE_CHANCE = BUILDER
            .comment("Chance (0.0-1.0) that a naturally spawning mob rolls catchable.",
                     "Catchable mobs glow with an enchanted shimmer; spheres thrown at",
                     "anything else are simply lost.")
            .defineInRange("catchableChance", 0.65D, 0.0D, 1.0D);

    // ── Wild toughness ─────────────────────────────────────────────────────
    // Catchable wild mobs become tanky, hard-hitting, self-healing bruisers.
    // The catch *probability* deliberately ignores these buffs (it charges each
    // mob's TRUE base health) — the challenge is the FIGHT to weaken them, not
    // the catch math. catchRateMultiplier is the only lever on catch odds.

    private static final ForgeConfigSpec.DoubleValue WILD_HEALTH_MULTIPLIER = BUILDER
            .comment("Max-health multiplier applied to every catchable wild mob (stacks on",
                     "top of any per-mob brawler multiplier). Does NOT affect catch odds.")
            .defineInRange("wildHealthMultiplier", 4.5D, 1.0D, 50.0D);

    private static final ForgeConfigSpec.DoubleValue WILD_DAMAGE_MULTIPLIER = BUILDER
            .comment("Attack-damage multiplier applied to every catchable wild mob that has an",
                     "attack-damage attribute (passive animals are unaffected).")
            .defineInRange("wildDamageMultiplier", 2.0D, 1.0D, 50.0D);

    private static final ForgeConfigSpec.DoubleValue WILD_REGEN_FRACTION_PER_SECOND = BUILDER
            .comment("Fraction of max health a catchable wild mob regenerates per second while",
                     "not recently hurt. 0 disables wild regen. Forces a burst-then-catch loop.")
            .defineInRange("wildRegenFractionPerSecond", 0.015D, 0.0D, 1.0D);

    private static final ForgeConfigSpec.IntValue WILD_REGEN_SUPPRESS_TICKS = BUILDER
            .comment("Ticks after taking damage during which a catchable wild mob does NOT regen",
                     "(20 ticks = 1 second). Gives players a window to weaken then throw.")
            .defineInRange("wildRegenSuppressTicks", 60, 0, 12000);

    // ── Catch curve (fraction-based) ───────────────────────────────────────
    // catchChance = catchLowHpMaxRate × weaken^p × catchRateMultiplier
    //   weaken = clamp((1 − hpFrac) / (1 − catchFullRateHpFraction), 0, 1)
    //   p      = (1 + catchToughnessWeight·log2(baseMaxHealth)) / ballPower
    //   ballPower = 1 + catchBallPowerPerLevel·(sphereLevel + Leveling − 1)
    // Fraction-based so ANY mob is catchable once weakened (no absolute-HP wall);
    // tankier mobs get a bigger p → strictly harder at every mid-HP point, but all
    // converge to catchLowHpMaxRate at/below catchFullRateHpFraction and to ~0 at full.

    private static final ForgeConfigSpec.DoubleValue CATCH_LOW_HP_MAX_RATE = BUILDER
            .comment("Catch chance a mob reaches once its health fraction drops to/below",
                     "catchFullRateHpFraction — the same for every mob, tanky or not.")
            .defineInRange("catchLowHpMaxRate", 0.90D, 0.0D, 1.0D);

    private static final ForgeConfigSpec.DoubleValue CATCH_FULL_RATE_HP_FRACTION = BUILDER
            .comment("Health fraction (0-1) at or below which catchLowHpMaxRate is reached.",
                     "0.05 = 'once the mob is under ~5% HP, catch odds peak'.")
            .defineInRange("catchFullRateHpFraction", 0.05D, 0.0D, 0.9D);

    private static final ForgeConfigSpec.DoubleValue CATCH_TOUGHNESS_WEIGHT = BUILDER
            .comment("How much max health steepens the curve (K in 1 + K·log2(baseMaxHealth)).",
                     "Higher = tanky mobs much harder at mid HP (still peak at low HP). 0 = HP",
                     "size irrelevant, curve identical for all mobs.")
            .defineInRange("catchToughnessWeight", 0.5D, 0.0D, 10.0D);

    private static final ForgeConfigSpec.DoubleValue CATCH_BALL_POWER_PER_LEVEL = BUILDER
            .comment("How much each effective sphere level (tier + Leveling) flattens the curve",
                     "so better spheres catch at higher HP. 0 = sphere tier only affects nothing",
                     "here (low-HP peak is unchanged either way).")
            .defineInRange("catchBallPowerPerLevel", 0.15D, 0.0D, 10.0D);

    private static final ForgeConfigSpec.DoubleValue CATCH_RATE_MULTIPLIER = BUILDER
            .comment("Final global multiplier on catch chance (applied after the curve).",
                     "1.0 = leave the curve as-is; < 1.0 makes everything harder.")
            .defineInRange("catchRateMultiplier", 1.0D, 0.0D, 2.0D);

    // ── Pal self-heal (well-fed regeneration) ──────────────────────────────

    private static final ForgeConfigSpec.DoubleValue PAL_HEAL_WELL_FED_THRESHOLD = BUILDER
            .comment("Hunger (0-100) at or above which an owned Pal begins regenerating health.")
            .defineInRange("palHealWellFedThreshold", 80.0D, 0.0D, 100.0D);

    private static final ForgeConfigSpec.DoubleValue PAL_HEAL_PER_SECOND = BUILDER
            .comment("Max health per second a fully-fed, happy Pal regenerates. Scales down with",
                     "how close hunger is to the threshold and with mood.")
            .defineInRange("palHealPerSecond", 2.0D, 0.0D, 100.0D);

    private static final ForgeConfigSpec.DoubleValue PAL_HEAL_HUNGER_COST_PER_HP = BUILDER
            .comment("Hunger consumed per point of health a Pal heals (healing burns food).")
            .defineInRange("palHealHungerCostPerHp", 0.5D, 0.0D, 20.0D);

    // ── Hunting Night ──────────────────────────────────────────────────────
    // Each day rolls a chance that TONIGHT the mobs hunt: ~5 min before dusk a
    // warning is broadcast; at nightfall every mob turns aggressive (even passive
    // ones deal zombie-level damage, attackers deal double), extra mobs spawn in
    // waves around players, and at dawn the hunt ends and its spawns despawn.

    private static final ForgeConfigSpec.BooleanValue HUNT_ENABLED = BUILDER
            .comment("Master switch for the Hunting Night event.")
            .define("huntEnabled", true);

    private static final ForgeConfigSpec.DoubleValue HUNT_NIGHT_CHANCE = BUILDER
            .comment("Chance (0-1) that any given night becomes a hunting night.")
            .defineInRange("huntNightChance", 0.10D, 0.0D, 1.0D);

    private static final ForgeConfigSpec.IntValue HUNT_WARNING_LEAD_TICKS = BUILDER
            .comment("Ticks before nightfall (13000) that the warning is rolled/broadcast.",
                     "6000 ticks ≈ 5 real minutes.")
            .defineInRange("huntWarningLeadTicks", 6000, 0, 12000);

    private static final ForgeConfigSpec.DoubleValue HUNT_DAMAGE_MULTIPLIER = BUILDER
            .comment("Multiplier on a hunting mob's attack damage during the hunt.")
            .defineInRange("huntDamageMultiplier", 2.0D, 1.0D, 20.0D);

    private static final ForgeConfigSpec.DoubleValue HUNT_MIN_DAMAGE = BUILDER
            .comment("Floor damage a hunting mob deals — so normally-passive mobs (no attack",
                     "damage) still hit for at least this much (zombie ≈ 3).")
            .defineInRange("huntMinDamage", 3.0D, 0.0D, 100.0D);

    private static final ForgeConfigSpec.DoubleValue HUNT_CHASE_SPEED = BUILDER
            .comment("Movement speed multiplier a hunting mob uses to chase its prey.")
            .defineInRange("huntChaseSpeed", 1.3D, 0.1D, 5.0D);

    private static final ForgeConfigSpec.DoubleValue HUNT_SENSE_RANGE = BUILDER
            .comment("How far (blocks) a hunting mob can sense and pursue a player.")
            .defineInRange("huntSenseRange", 24.0D, 1.0D, 128.0D);

    private static final ForgeConfigSpec.IntValue HUNT_ATTACK_COOLDOWN_TICKS = BUILDER
            .comment("Ticks between a hunting mob's melee hits.")
            .defineInRange("huntAttackCooldownTicks", 20, 1, 200);

    private static final ForgeConfigSpec.IntValue HUNT_SPAWN_INTERVAL_TICKS = BUILDER
            .comment("Ticks between hunting-night spawn waves. 0 disables extra spawns.")
            .defineInRange("huntSpawnIntervalTicks", 100, 0, 12000);

    private static final ForgeConfigSpec.IntValue HUNT_SPAWN_PER_WAVE = BUILDER
            .comment("Mobs force-spawned per wave, per player.")
            .defineInRange("huntSpawnPerWave", 3, 0, 50);

    private static final ForgeConfigSpec.IntValue HUNT_SPAWN_MAX_PER_PLAYER = BUILDER
            .comment("Cap on live hunting-night spawns near one player (waves stop at the cap).")
            .defineInRange("huntSpawnMaxPerPlayer", 24, 0, 200);

    private static final ForgeConfigSpec.IntValue HUNT_SPAWN_MIN_RADIUS = BUILDER
            .comment("Minimum spawn distance (blocks) from the player.")
            .defineInRange("huntSpawnMinRadius", 16, 1, 128);

    private static final ForgeConfigSpec.IntValue HUNT_SPAWN_MAX_RADIUS = BUILDER
            .comment("Maximum spawn distance (blocks) from the player.")
            .defineInRange("huntSpawnMaxRadius", 44, 2, 128);

    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> HUNT_SPAWN_POOL = BUILDER
            .comment("Entity ids force-spawned in waves during a hunting night (grounded mobs work best).")
            .defineListAllowEmpty("huntSpawnPool", List.of(
                    "minecraft:zombie", "minecraft:skeleton", "minecraft:spider",
                    "alexsmobs:grizzly_bear", "alexsmobs:gorilla", "alexsmobs:tasmanian_devil",
                    "alexsmobs:kangaroo", "alexsmobs:raccoon"), Config::isNonEmptyString);

    private static final ForgeConfigSpec.BooleanValue ALLOW_VANILLA_CLIENTS = BUILDER
            .comment("Dedicated servers only: patch all Forge network channels so vanilla clients",
                     "(and protocol bots like mineflayer) can join even when other installed mods",
                     "would normally reject them. Modded content still works server-side; vanilla",
                     "clients simply won't see custom animations/GUIs from those mods.")
            .define("allowVanillaClients", true);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static boolean logDirtBlock;
    public static int magicNumber;
    public static String magicNumberIntroduction;
    public static Set<Item> items;
    public static boolean allowVanillaClients;
    public static double catchableChance = 0.65D;

    // Wild toughness
    public static double wildHealthMultiplier = 4.5D;
    public static double wildDamageMultiplier = 2.0D;
    public static double wildRegenFractionPerSecond = 0.015D;
    public static int    wildRegenSuppressTicks = 60;
    public static double catchLowHpMaxRate = 0.90D;
    public static double catchFullRateHpFraction = 0.05D;
    public static double catchToughnessWeight = 0.5D;
    public static double catchBallPowerPerLevel = 0.15D;
    public static double catchRateMultiplier = 1.0D;

    // Pal self-heal
    public static double palHealWellFedThreshold = 80.0D;
    public static double palHealPerSecond = 2.0D;
    public static double palHealHungerCostPerHp = 0.5D;

    // Hunting Night
    public static boolean huntEnabled = true;
    public static double  huntNightChance = 0.10D;
    public static int     huntWarningLeadTicks = 6000;
    public static double  huntDamageMultiplier = 2.0D;
    public static double  huntMinDamage = 3.0D;
    public static double  huntChaseSpeed = 1.3D;
    public static double  huntSenseRange = 24.0D;
    public static int     huntAttackCooldownTicks = 20;
    public static int     huntSpawnIntervalTicks = 100;
    public static int     huntSpawnPerWave = 3;
    public static int     huntSpawnMaxPerPlayer = 24;
    public static int     huntSpawnMinRadius = 16;
    public static int     huntSpawnMaxRadius = 44;
    public static java.util.List<String> huntSpawnPool = java.util.List.of(
            "minecraft:zombie", "minecraft:skeleton", "minecraft:spider",
            "alexsmobs:grizzly_bear", "alexsmobs:gorilla", "alexsmobs:tasmanian_devil",
            "alexsmobs:kangaroo", "alexsmobs:raccoon");

    private static boolean isNonEmptyString(final Object obj) {
        return obj instanceof final String s && !s.isEmpty();
    }

    private static boolean validateItemName(final Object obj)
    {
        return obj instanceof final String itemName && ForgeRegistries.ITEMS.containsKey(new ResourceLocation(itemName));
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event)
    {
        logDirtBlock = LOG_DIRT_BLOCK.get();
        magicNumber = MAGIC_NUMBER.get();
        magicNumberIntroduction = MAGIC_NUMBER_INTRODUCTION.get();

        // convert the list of strings into a set of items
        items = ITEM_STRINGS.get().stream()
                .map(itemName -> ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemName)))
                .collect(Collectors.toSet());

        allowVanillaClients = ALLOW_VANILLA_CLIENTS.get();
        catchableChance = CATCHABLE_CHANCE.get();

        wildHealthMultiplier = WILD_HEALTH_MULTIPLIER.get();
        wildDamageMultiplier = WILD_DAMAGE_MULTIPLIER.get();
        wildRegenFractionPerSecond = WILD_REGEN_FRACTION_PER_SECOND.get();
        wildRegenSuppressTicks = WILD_REGEN_SUPPRESS_TICKS.get();
        catchLowHpMaxRate = CATCH_LOW_HP_MAX_RATE.get();
        catchFullRateHpFraction = CATCH_FULL_RATE_HP_FRACTION.get();
        catchToughnessWeight = CATCH_TOUGHNESS_WEIGHT.get();
        catchBallPowerPerLevel = CATCH_BALL_POWER_PER_LEVEL.get();
        catchRateMultiplier = CATCH_RATE_MULTIPLIER.get();

        palHealWellFedThreshold = PAL_HEAL_WELL_FED_THRESHOLD.get();
        palHealPerSecond = PAL_HEAL_PER_SECOND.get();
        palHealHungerCostPerHp = PAL_HEAL_HUNGER_COST_PER_HP.get();

        huntEnabled = HUNT_ENABLED.get();
        huntNightChance = HUNT_NIGHT_CHANCE.get();
        huntWarningLeadTicks = HUNT_WARNING_LEAD_TICKS.get();
        huntDamageMultiplier = HUNT_DAMAGE_MULTIPLIER.get();
        huntMinDamage = HUNT_MIN_DAMAGE.get();
        huntChaseSpeed = HUNT_CHASE_SPEED.get();
        huntSenseRange = HUNT_SENSE_RANGE.get();
        huntAttackCooldownTicks = HUNT_ATTACK_COOLDOWN_TICKS.get();
        huntSpawnIntervalTicks = HUNT_SPAWN_INTERVAL_TICKS.get();
        huntSpawnPerWave = HUNT_SPAWN_PER_WAVE.get();
        huntSpawnMaxPerPlayer = HUNT_SPAWN_MAX_PER_PLAYER.get();
        huntSpawnMinRadius = HUNT_SPAWN_MIN_RADIUS.get();
        huntSpawnMaxRadius = HUNT_SPAWN_MAX_RADIUS.get();
        huntSpawnPool = HUNT_SPAWN_POOL.get().stream()
                .map(String::valueOf).collect(Collectors.toList());
    }
}
