package com.mx.palmod.pal;

import net.minecraft.world.entity.EntityType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers which mob types each player killed recently (rolling 10-minute
 * window) — the greedy pal's boom mimics one of them.
 */
public final class KillTracker {

    private static final long WINDOW_TICKS = 12000; // 10 minutes

    private record Kill(EntityType<?> type, long gameTime) {}

    private static final Map<UUID, Deque<Kill>> KILLS = new ConcurrentHashMap<>();

    private KillTracker() {}

    public static void record(UUID playerId, EntityType<?> type, long gameTime) {
        Deque<Kill> deque = KILLS.computeIfAbsent(playerId, id -> new ArrayDeque<>());
        synchronized (deque) {
            deque.addLast(new Kill(type, gameTime));
            while (deque.size() > 64) deque.removeFirst(); // hard cap
        }
    }

    /** Mob types this player killed within the last 10 minutes. */
    public static List<EntityType<?>> recentKills(UUID playerId, long now) {
        Deque<Kill> deque = KILLS.get(playerId);
        if (deque == null) return List.of();
        List<EntityType<?>> result = new ArrayList<>();
        synchronized (deque) {
            deque.removeIf(kill -> now - kill.gameTime() > WINDOW_TICKS);
            for (Kill kill : deque) result.add(kill.type());
        }
        return result;
    }
}
