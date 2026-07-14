package com.mx.palmod.ai;

import com.mx.palmod.block.PalFeederBlock;
import com.mx.palmod.block.PalFeederBlockEntity;
import com.mx.palmod.registry.ModRegistries;
import com.mx.palmod.stats.PalFoodManager;
import com.mx.palmod.stats.PalStats;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.EnumSet;

/**
 * AI Goal: when hunger drops below HUNGER_SEEK_FEEDER (80%), the Pal pathfinds to
 * the nearest PalFeederBlock within 32 blocks and eats from it.
 * Priority 0 — overrides all other goals when hungry enough.
 */
public class PalSeekFeederGoal extends Goal {

    private static final int SEARCH_RADIUS = 32;
    private static final int EAT_COOLDOWN_TICKS = 40; // 2 seconds between bites

    private final Mob mob;
    private BlockPos targetFeeder = null;
    private int eatCooldown = 0;
    // When no feeder exists nearby, back off instead of re-scanning (and
    // grabbing the MOVE flag / stopping navigation) every single tick — the
    // start/stop flicker used to freeze hungry pals in place entirely.
    private int searchBackoff = 0;

    public PalSeekFeederGoal(Mob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!mob.getPersistentData().contains("PalOwner")) return false;
        if (mob.isVehicle()) return false; // don't wrestle the rider for control
        // NOTE: inactive (<5) pals ARE allowed here — crawling to a feeder is
        // the only way they can recover on their own.
        if (!PalStats.needsFood(mob)) return false;
        if (searchBackoff > 0) {
            searchBackoff--;
            return false;
        }
        targetFeeder = findNearestFeeder();
        if (targetFeeder == null) {
            // ~100 game ticks; canUse of idle goals only polls every other tick
            searchBackoff = adjustedTickDelay(100);
            return false;
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (!PalStats.needsFood(mob)) return false;
        if (targetFeeder == null) return false;
        // Check feeder still exists and still has food — don't camp an empty
        // feeder while holding the MOVE flag hostage
        Level level = mob.level();
        if (!(level.getBlockState(targetFeeder).getBlock() instanceof PalFeederBlock)) return false;
        BlockEntity be = level.getBlockEntity(targetFeeder);
        return be instanceof PalFeederBlockEntity feeder && !feeder.isEmpty();
    }

    @Override
    public void start() {
        eatCooldown = 0;
    }

    @Override
    public void stop() {
        targetFeeder = null;
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (targetFeeder == null) {
            targetFeeder = findNearestFeeder();
            if (targetFeeder == null) return;
        }

        Level level = mob.level();
        if (!(level.getBlockState(targetFeeder).getBlock() instanceof PalFeederBlock)) {
            targetFeeder = null;
            return;
        }

        // Navigate towards the feeder
        mob.getNavigation().moveTo(targetFeeder.getX() + 0.5, targetFeeder.getY(), targetFeeder.getZ() + 0.5, 1.0);
        mob.getLookControl().setLookAt(targetFeeder.getX() + 0.5, targetFeeder.getY() + 0.5, targetFeeder.getZ() + 0.5);

        // Close enough to eat?
        double distSq = mob.distanceToSqr(targetFeeder.getX() + 0.5, targetFeeder.getY(), targetFeeder.getZ() + 0.5);
        if (distSq <= 4.0) {
            if (eatCooldown > 0) {
                eatCooldown--;
                return;
            }
            BlockEntity be = level.getBlockEntity(targetFeeder);
            if (be instanceof PalFeederBlockEntity feeder && !feeder.isEmpty()) {
                ItemStack food = feeder.getFood();
                if (PalFoodManager.tryFeed(mob, food)) {
                    feeder.consumeOne();
                    eatCooldown = EAT_COOLDOWN_TICKS;

                    // Eating particles
                    if (level instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                                mob.getX(), mob.getY() + mob.getBbHeight(), mob.getZ(), 3, 0.2, 0.2, 0.2, 0.05);
                    }
                }
            }
        }
    }

    private BlockPos findNearestFeeder() {
        int cx = (int) mob.getX();
        int cy = (int) mob.getY();
        int cz = (int) mob.getZ();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.betweenClosed(
                cx - SEARCH_RADIUS, cy - 4, cz - SEARCH_RADIUS,
                cx + SEARCH_RADIUS, cy + 4, cz + SEARCH_RADIUS)) {
            if (mob.level().getBlockState(pos).getBlock() instanceof PalFeederBlock) {
                BlockEntity be = mob.level().getBlockEntity(pos);
                if (be instanceof PalFeederBlockEntity feeder && !feeder.isEmpty()) {
                    double d = mob.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                    if (d < bestDist) {
                        bestDist = d;
                        best = pos.immutable();
                    }
                }
            }
        }
        return best;
    }
}
