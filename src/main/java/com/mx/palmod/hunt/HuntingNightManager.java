package com.mx.palmod.hunt;

import com.mx.palmod.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * Hunting Night: each in-game day, ~5 real minutes before dusk, a roll decides
 * whether TONIGHT the mobs hunt. If so a warning is broadcast; at nightfall the
 * hunt goes active — every mob (see {@link com.mx.palmod.ai.HuntNightAttackGoal})
 * turns on players, and extra mobs spawn in waves around them. At dawn the hunt
 * ends and everything it spawned despawns (handled per-entity in ForgeEvents).
 *
 * State is a small set of static fields keyed off the overworld's day clock, so
 * driving {@code /time set} in a test moves the phases deterministically.
 */
public final class HuntingNightManager {

    // Vanilla day clock: 0 dawn, 6000 noon, 13000 night begins, 23000 sunrise.
    private static final long NIGHT_START = 13000L;
    private static final long DAWN_TICK   = 23000L;

    private static volatile boolean active = false;
    private static boolean tonightIsHunt = false;
    private static boolean warned = false;
    private static long lastRolledDay = Long.MIN_VALUE;

    private HuntingNightManager() {}

    /** True while a hunt is live — read by HuntNightAttackGoal and the despawn check. */
    public static boolean isActive() {
        return active;
    }

    public static void reset() {
        active = false;
        tonightIsHunt = false;
        warned = false;
        lastRolledDay = Long.MIN_VALUE;
    }

    public static void tick(MinecraftServer server) {
        if (!Config.huntEnabled) {
            if (active) active = false;
            return;
        }
        ServerLevel overworld = server.overworld();
        long absTime = overworld.getDayTime();
        long dayTime = Math.floorMod(absTime, 24000L);
        long dayIndex = Math.floorDiv(absTime, 24000L);

        // Phase + spawn logic throttled off the server tick counter.
        if (server.getTickCount() % 20 == 0) {
            long warnTick = Math.max(0L, NIGHT_START - Config.huntWarningLeadTicks);
            boolean nightWindow = dayTime >= NIGHT_START && dayTime < DAWN_TICK;

            // (1) Fresh day (while not already hunting): roll tonight's hunt and
            // clear the warning latch. Rolling the instant a new day is first seen
            // — even if the clock jumped past dawn — keeps a stale tonightIsHunt
            // from a never-activated day from leaking in, and keeps the hunt
            // working regardless of huntWarningLeadTicks (the warning is decoupled,
            // so lead=0 simply means no advance warning, not a disabled feature).
            if (dayIndex != lastRolledDay && !active) {
                lastRolledDay = dayIndex;
                warned = false;
                tonightIsHunt = dayTime < NIGHT_START
                        && overworld.getRandom().nextDouble() < Config.huntNightChance;
            }

            // (2) Warning ~lead ticks before nightfall (once per hunt).
            if (tonightIsHunt && !warned && dayTime >= warnTick && dayTime < NIGHT_START) {
                warned = true;
                broadcast(server, Component.literal(
                        "⚠ Đêm nay bầy Pal sẽ đi săn! Hãy tìm chỗ trú ẩn.")
                        .withStyle(ChatFormatting.GOLD));
            }

            // (3) Activate at nightfall.
            if (tonightIsHunt && !active && nightWindow) {
                active = true;
                broadcast(server, Component.literal(
                        "🌑 ĐÊM SĂN BẮT ĐẦU — mọi sinh vật trở nên hung dữ!")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            }

            // (4) Deactivate at dawn (hunt spawns self-despawn via the per-entity check).
            if (active && !nightWindow) {
                active = false;
                tonightIsHunt = false;
                broadcast(server, Component.literal(
                        "☀ Bình minh lên — đêm săn đã kết thúc.")
                        .withStyle(ChatFormatting.YELLOW));
            }
        }

        // Spawn waves around players while the hunt is live.
        if (active && Config.huntSpawnIntervalTicks > 0
                && server.getTickCount() % Config.huntSpawnIntervalTicks == 0) {
            spawnWaves(overworld);
        }
    }

    // ── Wave spawning ──────────────────────────────────────────────────────

    private static void spawnWaves(ServerLevel level) {
        List<String> pool = Config.huntSpawnPool;
        if (pool.isEmpty() || Config.huntSpawnPerWave <= 0) return;
        int minR = Config.huntSpawnMinRadius;
        int maxR = Math.max(minR + 1, Config.huntSpawnMaxRadius);

        for (ServerPlayer player : level.players()) {
            if (player.isCreative() || player.isSpectator()) continue;

            int nearby = level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(maxR),
                    m -> m.getPersistentData().getBoolean("HuntNightSpawned")).size();
            int room = Config.huntSpawnMaxPerPlayer - nearby;
            if (room <= 0) continue;
            int toSpawn = Math.min(Config.huntSpawnPerWave, room);

            for (int i = 0; i < toSpawn; i++) {
                double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
                double r = minR + level.getRandom().nextDouble() * (maxR - minR);
                int x = Mth.floor(player.getX() + Math.cos(angle) * r);
                int z = Mth.floor(player.getZ() + Math.sin(angle) * r);
                if (!level.isLoaded(new BlockPos(x, level.getMinBuildHeight() + 1, z))) continue;

                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos pos = new BlockPos(x, y, z);
                if (!isSpawnable(level, pos)) continue;

                EntityType<?> type = pickType(level, pool);
                if (type == null) continue;
                Entity spawned = type.spawn(level, pos, MobSpawnType.EVENT);
                if (spawned instanceof Mob mob) {
                    mob.getPersistentData().putBoolean("HuntNightSpawned", true);
                }
            }
        }
    }

    private static boolean isSpawnable(ServerLevel level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        if (!below.isFaceSturdy(level, pos.below(), Direction.UP)) return false;
        if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) return false;
        if (!level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) return false;
        return level.getFluidState(pos).isEmpty();
    }

    private static EntityType<?> pickType(ServerLevel level, List<String> pool) {
        String id = pool.get(level.getRandom().nextInt(pool.size()));
        // tryParse returns null (not throws) on a malformed id, so a bad config
        // entry is skipped via the caller's null-check instead of crashing the tick.
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? null : ForgeRegistries.ENTITY_TYPES.getValue(rl);
    }

    private static void broadcast(MinecraftServer server, Component message) {
        server.getPlayerList().broadcastSystemMessage(message, false);
    }
}
