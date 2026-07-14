package com.mx.palmod.pal;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Temporary blocks (earth-bender spikes) that revert to their original state
 * after a delay. Keyed by (dimension, pos); a block replaced by something else
 * in the meantime (player build) is left alone.
 */
public final class TempBlockReverter {

    private record PosKey(ResourceKey<Level> dimension, BlockPos pos) {}

    private record Entry(BlockState original, BlockState placed, long revertAtGameTime) {}

    private static final Map<PosKey, Entry> PENDING = new ConcurrentHashMap<>();

    private TempBlockReverter() {}

    public static void place(ServerLevel level, BlockPos pos, BlockState state, int durationTicks) {
        PosKey key = new PosKey(level.dimension(), pos.immutable());
        // Never chain-overwrite an already-temporary block's "original"
        PENDING.computeIfAbsent(key, k -> new Entry(
                level.getBlockState(k.pos()), state, level.getGameTime() + durationTicks));
        level.setBlock(pos, state, 3);
    }

    public static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) return;
        Iterator<Map.Entry<PosKey, Entry>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<PosKey, Entry> mapEntry = it.next();
            PosKey key = mapEntry.getKey();
            Entry entry = mapEntry.getValue();
            ServerLevel level = server.getLevel(key.dimension());
            if (level == null) {
                it.remove();
                continue;
            }
            if (level.getGameTime() >= entry.revertAtGameTime()) {
                // Only crumble what is still OUR placed block
                if (level.isLoaded(key.pos()) && level.getBlockState(key.pos()) == entry.placed()) {
                    level.setBlock(key.pos(), entry.original(), 3);
                }
                it.remove();
            }
        }
    }
}
