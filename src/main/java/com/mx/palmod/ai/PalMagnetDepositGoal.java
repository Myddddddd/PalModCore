package com.mx.palmod.ai;

import com.mx.palmod.behavior.PalBehavior;
import com.mx.palmod.behavior.PalBehaviorManager;
import com.mx.palmod.pal.PalChest;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Magnet delivery run: once the magnet pal (mimicube) can't reel in any more of
 * its hoarded item type — pouch full, or nothing matching left on the ground —
 * it proactively carries the haul off to storage. Priority: the chest the owner
 * currently has OPEN (path to the owner and drop it in), otherwise the nearest
 * storage pal (kangaroo). It never waits to be full.
 */
public class PalMagnetDepositGoal extends Goal {

    private final Mob mob;
    private final PalBehavior behavior;

    // Resolved each activation
    private Player openChestOwner;   // owner with a chest menu open
    private Mob storagePal;          // nearest kangaroo-type pouch pal

    public PalMagnetDepositGoal(Mob mob, PalBehavior behavior) {
        this.mob = mob;
        this.behavior = behavior;
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!(mob.level() instanceof ServerLevel level)) return false;
        if (!mob.getPersistentData().hasUUID("PalOwner")) return false;
        ItemStack held = PalChest.pouchFirstStack(mob);
        if (held.isEmpty()) return false; // nothing to deliver

        // Still able to collect more of this type? then keep hoarding, don't run.
        boolean full = PalChest.pouchFullFor(mob, behavior.getMagnetSlots(), held);
        if (!full && hasMatchingItemNearby(level, held)) return false;

        return resolveDestination(level);
    }

    @Override
    public boolean canContinueToUse() {
        if (PalChest.pouchEmpty(mob)) return false;
        // Destination still valid?
        if (openChestOwner != null) {
            return openChestOwner.containerMenu instanceof ChestMenu && !openChestOwner.isRemoved();
        }
        return storagePal != null && storagePal.isAlive();
    }

    @Override
    public void stop() {
        openChestOwner = null;
        storagePal = null;
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        net.minecraft.world.entity.Entity dest = openChestOwner != null ? openChestOwner : storagePal;
        if (dest == null) return;
        mob.getLookControl().setLookAt(dest);
        double distSqr = mob.distanceToSqr(dest);
        if (distSqr > 9.0) {
            mob.getNavigation().moveTo(dest.getX(), dest.getY(), dest.getZ(), 1.1);
            return;
        }
        // Close enough — dump the haul
        boolean moved;
        if (openChestOwner != null && openChestOwner.containerMenu instanceof ChestMenu chestMenu) {
            moved = PalChest.depositPouchInto(mob, chestMenu.getContainer(), behavior.getMagnetSlots());
        } else if (storagePal != null) {
            int storageSlots = PalBehaviorManager.getBehavior(storagePal.getType()).getMobileChestSize();
            moved = PalChest.depositPouchIntoPal(mob, behavior.getMagnetSlots(), storagePal, storageSlots);
        } else {
            moved = false;
        }
        if (moved && mob.level() instanceof ServerLevel level) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                    mob.getX(), mob.getY() + mob.getBbHeight() * 0.6, mob.getZ(), 5, 0.3, 0.3, 0.3, 0.05);
            level.playSound(null, mob.blockPosition(),
                    net.minecraft.sounds.SoundEvents.ITEM_PICKUP,
                    net.minecraft.sounds.SoundSource.NEUTRAL, 0.5F, 0.8F);
        }
        if (!moved || PalChest.pouchEmpty(mob)) {
            openChestOwner = null;
            storagePal = null;
        }
    }

    private boolean hasMatchingItemNearby(ServerLevel level, ItemStack type) {
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class,
                mob.getBoundingBox().inflate(behavior.getMagnetRadius()),
                e -> e.isAlive() && !e.hasPickUpDelay() && ItemStack.isSameItemSameTags(e.getItem(), type));
        return !items.isEmpty();
    }

    /** Prefer the owner's open chest, else the nearest storage pal — but only a destination with ROOM. */
    private boolean resolveDestination(ServerLevel level) {
        openChestOwner = null;
        storagePal = null;
        ItemStack held = PalChest.pouchFirstStack(mob);
        if (held.isEmpty()) return false;
        UUID ownerId = mob.getPersistentData().getUUID("PalOwner");
        Player owner = level.getPlayerByUUID(ownerId);
        if (owner != null && owner.containerMenu instanceof ChestMenu cm
                && containerHasRoomFor(cm.getContainer(), held)) {
            openChestOwner = owner;
            return true;
        }
        // Nearest same-owner pal with a pouch (kangaroo) that can still take the haul
        List<Mob> pals = level.getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(24.0),
                other -> other != mob && other.isAlive()
                        && other.getPersistentData().hasUUID("PalOwner")
                        && other.getPersistentData().getUUID("PalOwner").equals(ownerId)
                        && PalBehaviorManager.getBehavior(other.getType()).getMobileChestSize() > 0);
        Mob best = null;
        double bestDistSqr = Double.MAX_VALUE;
        for (Mob pal : pals) {
            int slots = PalBehaviorManager.getBehavior(pal.getType()).getMobileChestSize();
            if (PalChest.pouchFullFor(pal, slots, held)) continue; // no room in that pouch
            double d = mob.distanceToSqr(pal);
            if (d < bestDistSqr) { bestDistSqr = d; best = pal; }
        }
        storagePal = best;
        return storagePal != null;
    }

    private static boolean containerHasRoomFor(net.minecraft.world.Container dest, ItemStack stack) {
        for (int i = 0; i < dest.getContainerSize(); i++) {
            ItemStack slot = dest.getItem(i);
            if (slot.isEmpty()) return true;
            if (ItemStack.isSameItemSameTags(slot, stack)
                    && slot.getCount() < Math.min(slot.getMaxStackSize(), dest.getMaxStackSize())) return true;
        }
        return false;
    }
}
