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
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads trade tables from data/<namespace>/pal_trades/<name>.json.
 *
 * JSON structure:
 * {
 *   "entity": "alexsmobs:crow",
 *   "payment": "minecraft:emerald",
 *   "results": [
 *     { "item": "minecraft:ender_pearl", "count": 1, "weight": 8 }
 *   ]
 * }
 */
public class PalTradeManager extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static final PalTradeManager INSTANCE = new PalTradeManager();

    private static final Map<EntityType<?>, TradeTable> TRADE_TABLES = new HashMap<>();

    private PalTradeManager() {
        super(GSON, "pal_trades");
    }

    // ─────────────────────────────────────────────────────────────────
    //  Data classes
    // ─────────────────────────────────────────────────────────────────

    public record TradeResult(Item item, int count, int weight) {}

    public static class TradeTable {
        private Item payment;
        private final List<TradeResult> results = new ArrayList<>();

        public Item getPayment() {
            return payment;
        }

        /** Rolls a weighted random result stack (EMPTY if the table is empty). */
        public ItemStack rollResult(RandomSource random) {
            int total = 0;
            for (TradeResult r : results) total += r.weight();
            if (total <= 0) return ItemStack.EMPTY;
            int roll = random.nextInt(total);
            for (TradeResult r : results) {
                roll -= r.weight();
                if (roll < 0) return new ItemStack(r.item(), r.count());
            }
            return ItemStack.EMPTY;
        }
    }

    // ─────────────────────────────────────────────────────────────────
    //  Query API
    // ─────────────────────────────────────────────────────────────────

    @Nullable
    public static TradeTable getTable(EntityType<?> type) {
        return TRADE_TABLES.get(type);
    }

    // ─────────────────────────────────────────────────────────────────
    //  Loading
    // ─────────────────────────────────────────────────────────────────

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> pObject, ResourceManager pResourceManager, ProfilerFiller pProfiler) {
        TRADE_TABLES.clear();
        LOGGER.info("Loading Pal trade tables...");

        for (Map.Entry<ResourceLocation, JsonElement> entry : pObject.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            try {
                JsonObject json = GsonHelper.convertToJsonObject(entry.getValue(), "pal_trade");

                String entityStr = GsonHelper.getAsString(json, "entity");
                ResourceLocation entityId = new ResourceLocation(entityStr);
                if (!ForgeRegistries.ENTITY_TYPES.containsKey(entityId)) {
                    LOGGER.warn("Unknown entity '{}' in pal_trade config '{}'", entityStr, fileId);
                    continue;
                }
                EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(entityId);

                TradeTable table = new TradeTable();

                String paymentStr = GsonHelper.getAsString(json, "payment");
                Item payment = ForgeRegistries.ITEMS.getValue(new ResourceLocation(paymentStr));
                if (payment == null) {
                    LOGGER.warn("Unknown payment item '{}' in pal_trade config '{}'", paymentStr, fileId);
                    continue;
                }
                table.payment = payment;

                JsonArray results = GsonHelper.getAsJsonArray(json, "results");
                for (JsonElement resultEl : results) {
                    JsonObject resultObj = resultEl.getAsJsonObject();
                    String itemStr = GsonHelper.getAsString(resultObj, "item");
                    Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemStr));
                    if (item == null) {
                        LOGGER.warn("Unknown result item '{}' in pal_trade config '{}'", itemStr, fileId);
                        continue;
                    }
                    int count = GsonHelper.getAsInt(resultObj, "count", 1);
                    int weight = GsonHelper.getAsInt(resultObj, "weight", 1);
                    table.results.add(new TradeResult(item, count, weight));
                }

                TRADE_TABLES.put(entityType, table);
                LOGGER.info("Loaded trade table for {}: {} results for payment {}",
                        entityStr, table.results.size(), paymentStr);

            } catch (Exception e) {
                LOGGER.error("Failed to parse pal_trade config from '{}'", fileId, e);
            }
        }
        LOGGER.info("Loaded trade tables for {} entity types.", TRADE_TABLES.size());
    }
}
