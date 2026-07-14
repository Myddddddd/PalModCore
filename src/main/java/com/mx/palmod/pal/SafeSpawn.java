package com.mx.palmod.pal;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Places a freshly summoned entity at the nearest collision-free spot around a
 * desired position. Projectile impact points sit on block faces, so spawning
 * there directly clips the mob into the wall/floor it hit; likewise a station
 * pal must not spawn inside the station block that was just placed.
 */
public final class SafeSpawn {

    private static final double[] Y_OFFSETS = {0.0, 0.5, 1.0, 2.0, -1.0};
    private static final double[][] XZ_OFFSETS = {
            {0, 0},
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {-1, -1}, {1, -1}, {-1, 1},
            {2, 0}, {-2, 0}, {0, 2}, {0, -2},
    };

    private SafeSpawn() {}

    /**
     * Moves the entity to a collision-free spot at/around pos, trying upward and
     * sideways offsets, then the summoner's own position as a last resort.
     */
    public static void place(ServerLevel level, LivingEntity entity, Vec3 pos, @Nullable Player fallback) {
        for (double dy : Y_OFFSETS) {
            for (double[] off : XZ_OFFSETS) {
                entity.moveTo(pos.x + off[0], pos.y + dy, pos.z + off[1], entity.getYRot(), 0);
                if (level.noCollision(entity)) {
                    return;
                }
            }
        }
        if (fallback != null) {
            entity.moveTo(fallback.getX(), fallback.getY(), fallback.getZ(), entity.getYRot(), 0);
            if (level.noCollision(entity)) {
                return;
            }
        }
        // Nothing free nearby — leave it one block above the impact point
        entity.moveTo(pos.x, pos.y + 1.0, pos.z, entity.getYRot(), 0);
    }
}
