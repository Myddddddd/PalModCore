package com.mx.palmod.behavior;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class PalBehaviorManager extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    
    private static final Map<EntityType<?>, PalBehavior> BEHAVIORS = new HashMap<>();
    // Shared read-only fallback — getBehavior sits on per-tick hot paths
    private static final PalBehavior DEFAULT = new PalBehavior();
    private static volatile boolean anyWildTimeStop = false;
    public static final PalBehaviorManager INSTANCE = new PalBehaviorManager();

    private PalBehaviorManager() {
        super(GSON, "pal_behaviors");
    }

    public static PalBehavior getBehavior(EntityType<?> type) {
        return BEHAVIORS.getOrDefault(type, DEFAULT);
    }

    /** True when any loaded behavior defines a wild time-stopper (op/time_stop). */
    public static boolean hasWildTimeStop() {
        return anyWildTimeStop;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> pObject, ResourceManager pResourceManager, ProfilerFiller pProfiler) {
        BEHAVIORS.clear();
        LOGGER.info("Loading Pal Behaviors from datapacks...");

        for (Map.Entry<ResourceLocation, JsonElement> entry : pObject.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            try {
                JsonObject jsonObject = GsonHelper.convertToJsonObject(entry.getValue(), "pal_behavior");
                
                String entityStr = GsonHelper.getAsString(jsonObject, "entity");
                ResourceLocation entityId = new ResourceLocation(entityStr);
                
                if (!ForgeRegistries.ENTITY_TYPES.containsKey(entityId)) {
                    LOGGER.warn("Unknown entity type '{}' in pal behavior config '{}'", entityStr, fileId);
                    continue;
                }
                
                EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(entityId);
                PalBehavior behavior = new PalBehavior();

                if (jsonObject.has("behaviors")) {
                    JsonObject behaviorsJson = GsonHelper.getAsJsonObject(jsonObject, "behaviors");
                    
                    if (behaviorsJson.has("follow_owner")) {
                        JsonElement followElement = behaviorsJson.get("follow_owner");
                        if (followElement.isJsonPrimitive() && followElement.getAsBoolean()) {
                            behavior.setFollowOwner(true);
                        } else if (followElement.isJsonObject()) {
                            JsonObject followJson = followElement.getAsJsonObject();
                            behavior.setFollowOwner(true);
                            if (followJson.has("speed")) behavior.setFollowSpeed(GsonHelper.getAsDouble(followJson, "speed"));
                            if (followJson.has("start_distance")) behavior.setStartDistance(GsonHelper.getAsFloat(followJson, "start_distance"));
                            if (followJson.has("stop_distance")) behavior.setStopDistance(GsonHelper.getAsFloat(followJson, "stop_distance"));
                        } else {
                            behavior.setFollowOwner(false);
                        }
                    } else {
                        behavior.setFollowOwner(false);
                    }
                    
                    if (behaviorsJson.has("protect_owner")) {
                        behavior.setProtectOwner(GsonHelper.getAsBoolean(behaviorsJson, "protect_owner"));
                    }
                    
                    if (behaviorsJson.has("attack_target")) {
                        behavior.setAttackTarget(GsonHelper.getAsBoolean(behaviorsJson, "attack_target"));
                    }

                    if (behaviorsJson.has("guard_only")) {
                        behavior.setGuardOnly(GsonHelper.getAsBoolean(behaviorsJson, "guard_only"));
                    }

                    if (behaviorsJson.has("guard_leash")) {
                        behavior.setGuardLeash(GsonHelper.getAsFloat(behaviorsJson, "guard_leash"));
                    }

                    if (behaviorsJson.has("deploy_mode")) {
                        behavior.setDeployMode(GsonHelper.getAsString(behaviorsJson, "deploy_mode"));
                    }

                    if (behaviorsJson.has("warp_beacon")) {
                        behavior.setWarpBeacon(GsonHelper.getAsBoolean(behaviorsJson, "warp_beacon"));
                    }

                    if (behaviorsJson.has("clone")) {
                        JsonObject cloneJson = GsonHelper.getAsJsonObject(behaviorsJson, "clone");
                        behavior.setCloneMax(GsonHelper.getAsInt(cloneJson, "max", 6));
                        if (cloneJson.has("duration_ticks")) behavior.setCloneDurationTicks(GsonHelper.getAsInt(cloneJson, "duration_ticks"));
                        if (cloneJson.has("cooldown_ticks")) behavior.setCloneCooldownTicks(GsonHelper.getAsInt(cloneJson, "cooldown_ticks"));
                        if (cloneJson.has("hunger_cost")) behavior.setCloneHungerCost(GsonHelper.getAsFloat(cloneJson, "hunger_cost"));
                    }

                    if (behaviorsJson.has("sentry_radius")) {
                        behavior.setSentryRadius(GsonHelper.getAsFloat(behaviorsJson, "sentry_radius"));
                    }

                    if (behaviorsJson.has("mobile_chest_size")) {
                        behavior.setMobileChestSize(GsonHelper.getAsInt(behaviorsJson, "mobile_chest_size"));
                    }

                    if (behaviorsJson.has("combat_ability")) {
                        JsonObject caJson = GsonHelper.getAsJsonObject(behaviorsJson, "combat_ability");
                        behavior.setCombatAbilityType(GsonHelper.getAsString(caJson, "type", ""));
                        if (caJson.has("damage")) behavior.setCombatDamage(GsonHelper.getAsFloat(caJson, "damage"));
                        if (caJson.has("range")) behavior.setCombatRange(GsonHelper.getAsFloat(caJson, "range"));
                        if (caJson.has("cooldown_ticks")) behavior.setCombatCooldownTicks(GsonHelper.getAsInt(caJson, "cooldown_ticks"));
                        if (caJson.has("hunger_cost")) behavior.setCombatHungerCost(GsonHelper.getAsFloat(caJson, "hunger_cost"));
                    }

                    if (behaviorsJson.has("rideable")) {
                        JsonElement rideEl = behaviorsJson.get("rideable");
                        if (rideEl.isJsonPrimitive() && rideEl.getAsJsonPrimitive().isBoolean()) {
                            behavior.setRideable(rideEl.getAsBoolean());
                        } else if (rideEl.isJsonObject()) {
                            behavior.setRideable(true);
                            JsonObject rideJson = rideEl.getAsJsonObject();
                            if (rideJson.has("speed")) behavior.setRideSpeed(GsonHelper.getAsDouble(rideJson, "speed"));
                        }
                    }

                    if (behaviorsJson.has("fetch")) {
                        JsonElement fetchEl = behaviorsJson.get("fetch");
                        if (fetchEl.isJsonPrimitive() && fetchEl.getAsJsonPrimitive().isBoolean()) {
                            behavior.setCanFetch(fetchEl.getAsBoolean());
                        } else if (fetchEl.isJsonObject()) {
                            behavior.setCanFetch(true);
                            JsonObject fetchJson = fetchEl.getAsJsonObject();
                            if (fetchJson.has("radius")) behavior.setFetchRadius(GsonHelper.getAsInt(fetchJson, "radius"));
                        }
                    }

                    if (behaviorsJson.has("xp_collector")) {
                        JsonObject xpJson = GsonHelper.getAsJsonObject(behaviorsJson, "xp_collector");
                        behavior.setXpCollectRadius(GsonHelper.getAsFloat(xpJson, "radius", 8.0f));
                        if (xpJson.has("max_stored")) behavior.setXpMaxStored(GsonHelper.getAsInt(xpJson, "max_stored"));
                    }

                    if (behaviorsJson.has("magnet")) {
                        JsonObject magJson = GsonHelper.getAsJsonObject(behaviorsJson, "magnet");
                        behavior.setMagnetRadius(GsonHelper.getAsFloat(magJson, "radius", 6.0f));
                        if (magJson.has("slots")) behavior.setMagnetSlots(GsonHelper.getAsInt(magJson, "slots"));
                    }

                    if (behaviorsJson.has("greedy_boom")) {
                        JsonElement boomEl = behaviorsJson.get("greedy_boom");
                        if (boomEl.isJsonPrimitive() && boomEl.getAsJsonPrimitive().isBoolean()) {
                            behavior.setGreedyBoom(boomEl.getAsBoolean());
                        } else if (boomEl.isJsonObject()) {
                            behavior.setGreedyBoom(true);
                            JsonObject boomJson = boomEl.getAsJsonObject();
                            if (boomJson.has("boom_at_hunger")) behavior.setBoomAtHunger(GsonHelper.getAsFloat(boomJson, "boom_at_hunger"));
                        }
                    }
                    
                    if (behaviorsJson.has("melee_attack")) {
                        JsonElement meleeElement = behaviorsJson.get("melee_attack");
                        if (meleeElement.isJsonPrimitive() && meleeElement.getAsBoolean()) {
                            behavior.setMeleeAttack(true);
                        } else if (meleeElement.isJsonObject()) {
                            JsonObject meleeJson = meleeElement.getAsJsonObject();
                            behavior.setMeleeAttack(true);
                            if (meleeJson.has("speed")) behavior.setMeleeAttackSpeed(GsonHelper.getAsDouble(meleeJson, "speed"));
                        } else {
                            behavior.setMeleeAttack(false);
                        }
                    }

                    if (behaviorsJson.has("station_mode")) {
                        JsonElement stationElement = behaviorsJson.get("station_mode");
                        if (stationElement.isJsonObject()) {
                            JsonObject stationJson = stationElement.getAsJsonObject();
                            behavior.setStationMode(GsonHelper.getAsBoolean(stationJson, "enabled", false));
                            if (stationJson.has("worker_type")) behavior.setStationWorkerType(GsonHelper.getAsString(stationJson, "worker_type"));
                            if (stationJson.has("harvest_radius")) behavior.setHarvestRadius(GsonHelper.getAsInt(stationJson, "harvest_radius"));
                            if (stationJson.has("wander_radius")) behavior.setWanderRadius(GsonHelper.getAsInt(stationJson, "wander_radius"));
                            if (stationJson.has("log_cap")) behavior.setLogCap(GsonHelper.getAsInt(stationJson, "log_cap"));
                            if (stationJson.has("ore_tag")) {
                                String oreTagStr = GsonHelper.getAsString(stationJson, "ore_tag");
                                if (ResourceLocation.tryParse(oreTagStr) != null) {
                                    behavior.setOreTag(oreTagStr);
                                } else {
                                    LOGGER.warn("Invalid ore_tag '{}' in pal behavior '{}', keeping default forge:ores", oreTagStr, fileId);
                                }
                            }
                        }
                    }
                }

                // Parse "time_stop" block (ZA WARUDO ultimate)
                if (jsonObject.has("time_stop")) {
                    JsonObject tsJson = GsonHelper.getAsJsonObject(jsonObject, "time_stop");
                    if (tsJson.has("duration_ticks")) behavior.setTimeStopDurationTicks(GsonHelper.getAsInt(tsJson, "duration_ticks"));
                    if (tsJson.has("radius")) behavior.setTimeStopRadius(GsonHelper.getAsFloat(tsJson, "radius"));
                    if (tsJson.has("cooldown_ticks")) behavior.setTimeStopCooldownTicks(GsonHelper.getAsInt(tsJson, "cooldown_ticks"));
                    if (tsJson.has("hunger_cost")) behavior.setTimeStopHungerCost(GsonHelper.getAsFloat(tsJson, "hunger_cost"));
                    if (tsJson.has("on_summon")) behavior.setTimeStopOnSummon(GsonHelper.getAsBoolean(tsJson, "on_summon"));
                }

                // Parse "aura" block (owner aura granted while the Pal is out & fed)
                if (jsonObject.has("aura")) {
                    JsonObject auraJson = GsonHelper.getAsJsonObject(jsonObject, "aura");
                    behavior.setAuraType(GsonHelper.getAsString(auraJson, "type", ""));
                    if (auraJson.has("radius")) behavior.setAuraRadius(GsonHelper.getAsFloat(auraJson, "radius"));
                    if (auraJson.has("interval_ticks")) behavior.setAuraIntervalTicks(GsonHelper.getAsInt(auraJson, "interval_ticks"));
                    if (auraJson.has("potency")) behavior.setAuraPotency(GsonHelper.getAsInt(auraJson, "potency"));
                    if (auraJson.has("hunger_cost")) behavior.setAuraHungerCost(GsonHelper.getAsFloat(auraJson, "hunger_cost"));
                    // Any other numeric key is an aura-specific parameter
                    for (Map.Entry<String, JsonElement> auraEntry : auraJson.entrySet()) {
                        String key = auraEntry.getKey();
                        if (key.equals("type") || key.equals("radius") || key.equals("interval_ticks")
                                || key.equals("potency") || key.equals("hunger_cost")) continue;
                        if (auraEntry.getValue().isJsonPrimitive() && auraEntry.getValue().getAsJsonPrimitive().isNumber()) {
                            behavior.setAuraParam(key, auraEntry.getValue().getAsFloat());
                        }
                    }
                }

                // Parse "wild" block (wild-catch difficulty layer)
                if (jsonObject.has("wild")) {
                    JsonObject wildJson = GsonHelper.getAsJsonObject(jsonObject, "wild");
                    behavior.setWildCategory(GsonHelper.getAsString(wildJson, "category", ""));
                    behavior.setWildType(GsonHelper.getAsString(wildJson, "type", ""));
                    if (wildJson.has("counter_ability")) behavior.setWildCounterAbility(GsonHelper.getAsString(wildJson, "counter_ability"));
                    if (wildJson.has("sight_range")) behavior.setWildSightRange(GsonHelper.getAsFloat(wildJson, "sight_range"));
                    if (wildJson.has("hearing_range")) behavior.setWildHearingRange(GsonHelper.getAsFloat(wildJson, "hearing_range"));
                    if (wildJson.has("flee_speed")) behavior.setWildFleeSpeed(GsonHelper.getAsDouble(wildJson, "flee_speed"));
                    if (wildJson.has("health_multiplier")) behavior.setWildHealthMultiplier(GsonHelper.getAsFloat(wildJson, "health_multiplier"));
                    if (wildJson.has("resistance")) behavior.setWildResistance(GsonHelper.getAsInt(wildJson, "resistance"));
                    if (wildJson.has("use_combat_ability")) behavior.setWildUseCombatAbility(GsonHelper.getAsBoolean(wildJson, "use_combat_ability"));
                    if (wildJson.has("radius")) behavior.setWildOpRadius(GsonHelper.getAsFloat(wildJson, "radius"));
                    if (wildJson.has("cooldown_ticks")) behavior.setWildOpCooldownTicks(GsonHelper.getAsInt(wildJson, "cooldown_ticks"));
                    if (wildJson.has("hide_range")) behavior.setWildHideRange(GsonHelper.getAsFloat(wildJson, "hide_range"));
                    if (wildJson.has("lure_range")) behavior.setWildLureRange(GsonHelper.getAsFloat(wildJson, "lure_range"));
                }

                // Parse "stats" block
                if (jsonObject.has("stats")) {
                    JsonObject statsJson = GsonHelper.getAsJsonObject(jsonObject, "stats");
                    if (statsJson.has("hunger_decay_per_minute")) behavior.setHungerDecayPerMinute(GsonHelper.getAsFloat(statsJson, "hunger_decay_per_minute"));
                    if (statsJson.has("mood_decay_per_day")) behavior.setMoodDecayPerDay(GsonHelper.getAsFloat(statsJson, "mood_decay_per_day"));
                }

                // Parse "hunger_costs" block — every key becomes a per-action cost
                // (harvest, attack, move_per_block, chop, mine, ...)
                if (jsonObject.has("hunger_costs")) {
                    JsonObject costsJson = GsonHelper.getAsJsonObject(jsonObject, "hunger_costs");
                    for (Map.Entry<String, JsonElement> costEntry : costsJson.entrySet()) {
                        behavior.setHungerCost(costEntry.getKey(), GsonHelper.getAsFloat(costsJson, costEntry.getKey()));
                    }
                }
                
                BEHAVIORS.put(entityType, behavior);
                LOGGER.info("Registered Pal behavior for {} from {}", entityStr, fileId);

            } catch (Exception e) {
                LOGGER.error("Failed to parse Pal behavior config from '{}'", fileId, e);
            }
        }
        anyWildTimeStop = BEHAVIORS.values().stream().anyMatch(
                b -> "op".equals(b.getWildCategory()) && "time_stop".equals(b.getWildType()));
        LOGGER.info("Loaded {} Pal behaviors.", BEHAVIORS.size());
    }
}
