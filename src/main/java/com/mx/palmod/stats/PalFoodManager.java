package com.mx.palmod.stats;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads food preferences from data/<namespace>/pal_foods/<name>.json.
 *
 * JSON structure:
 * {
 *   "entity": "minecraft:zombie",
 *   "default_hunger_restore": 3.0,
 *   "default_mood_bonus": 0.0,
 *   "foods": [
 *     { "item": "minecraft:rotten_flesh", "hunger_restore": 10.0, "mood_bonus": 2.0 }
 *   ]
 * }
 */
public class PalFoodManager extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static final PalFoodManager INSTANCE = new PalFoodManager();

    /** Per-entity food table: EntityType -> (Item -> FoodEntry) */
    private static final Map<EntityType<?>, FoodTable> FOOD_TABLES = new HashMap<>();

    private PalFoodManager() {
        super(GSON, "pal_foods");
    }

    // ─────────────────────────────────────────────────────────────────
    //  Data class
    // ─────────────────────────────────────────────────────────────────

    public record FoodEntry(float hungerRestore, float moodBonus) {}

    public static class FoodTable {
        private final Map<Item, FoodEntry> entries = new HashMap<>();
        private float defaultHungerRestore = 3.0f;
        private float defaultMoodBonus = 0.0f;

        public FoodEntry getEntry(Item item) {
            return entries.getOrDefault(item, new FoodEntry(defaultHungerRestore, defaultMoodBonus));
        }

        /** Returns true if this item is at all edible by this mob. */
        public boolean canEat(Item item) {
            return entries.containsKey(item) || defaultHungerRestore > 0;
        }

        /** True only for items EXPLICITLY listed as this mob's food (hand-feeding). */
        public boolean canHandFeed(Item item) {
            return entries.containsKey(item);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    //  Query API
    // ─────────────────────────────────────────────────────────────────

    public static FoodTable getTable(EntityType<?> type) {
        return FOOD_TABLES.getOrDefault(type, new FoodTable());
    }

    /**
     * Feeds the entity the given item.
     * @return true if the entity ate it (item should be consumed by caller).
     */
    public static boolean tryFeed(net.minecraft.world.entity.LivingEntity entity, ItemStack food) {
        FoodTable table = getTable(entity.getType());
        if (!table.canEat(food.getItem())) return false;

        FoodEntry entry = table.getEntry(food.getItem());
        float newHunger = PalStats.modifyHunger(entity, entry.hungerRestore());
        PalStats.modifyMood(entity, entry.moodBonus());

        // Greedy pals only detonate when a FEED pushes them over the line —
        // never on the default-full hunger of a fresh summon.
        com.mx.palmod.behavior.PalBehavior behavior =
                com.mx.palmod.behavior.PalBehaviorManager.getBehavior(entity.getType());
        if (behavior.isGreedyBoom() && newHunger >= behavior.getBoomAtHunger()) {
            entity.getPersistentData().putBoolean("BoomPrimed", true);
        }
        return true;
    }

    // ─────────────────────────────────────────────────────────────────
    //  Loading
    // ─────────────────────────────────────────────────────────────────

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> pObject, ResourceManager pResourceManager, ProfilerFiller pProfiler) {
        FOOD_TABLES.clear();
        LOGGER.info("Loading Pal food preferences...");

        for (Map.Entry<ResourceLocation, JsonElement> entry : pObject.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            try {
                JsonObject json = GsonHelper.convertToJsonObject(entry.getValue(), "pal_food");

                String entityStr = GsonHelper.getAsString(json, "entity");
                ResourceLocation entityId = new ResourceLocation(entityStr);
                if (!ForgeRegistries.ENTITY_TYPES.containsKey(entityId)) {
                    LOGGER.warn("Unknown entity '{}' in pal_food config '{}'", entityStr, fileId);
                    continue;
                }
                EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(entityId);

                FoodTable table = new FoodTable();
                if (json.has("default_hunger_restore")) {
                    table.defaultHungerRestore = GsonHelper.getAsFloat(json, "default_hunger_restore");
                }
                if (json.has("default_mood_bonus")) {
                    table.defaultMoodBonus = GsonHelper.getAsFloat(json, "default_mood_bonus");
                }

                if (json.has("foods")) {
                    JsonArray foods = json.getAsJsonArray("foods");
                    for (JsonElement foodEl : foods) {
                        JsonObject foodObj = foodEl.getAsJsonObject();
                        String itemStr = GsonHelper.getAsString(foodObj, "item");
                        ResourceLocation itemId = new ResourceLocation(itemStr);
                        Item item = ForgeRegistries.ITEMS.getValue(itemId);
                        if (item == null) {
                            LOGGER.warn("Unknown item '{}' in pal_food config '{}'", itemStr, fileId);
                            continue;
                        }
                        float hungerRestore = GsonHelper.getAsFloat(foodObj, "hunger_restore", table.defaultHungerRestore);
                        float moodBonus = GsonHelper.getAsFloat(foodObj, "mood_bonus", table.defaultMoodBonus);
                        table.entries.put(item, new FoodEntry(hungerRestore, moodBonus));
                    }
                }

                FOOD_TABLES.put(entityType, table);
                LOGGER.info("Loaded {} food entries for {}", table.entries.size(), entityStr);

            } catch (Exception e) {
                LOGGER.error("Failed to parse pal_food config from '{}'", fileId, e);
            }
        }
        LOGGER.info("Loaded food tables for {} entity types.", FOOD_TABLES.size());
    }
}
