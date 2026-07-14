package com.mx.palmod.pal;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * Mobile chest ("pouch") for pals like the kangaroo: sneak-right-click your own
 * pal to open a chest inventory that lives in the pal's ForgeData — so it rides
 * the sphere NBT through catch/recall/resummon untouched.
 *
 * Uses the VANILLA generic chest menu, so it works for every client (including
 * vanilla/protocol clients) with zero custom GUI or network code.
 */
public final class PalChest {

    private static final String KEY_ITEMS = "PalChestItems";

    private PalChest() {}

    public static void open(ServerPlayer player, Mob pal, int size) {
        int rows = Math.max(1, Math.min(6, size / 9));
        int slots = rows * 9;

        // stillValid is re-checked every tick by the open menu: the GUI force-
        // closes the moment the pal dies, is recalled, or the player walks away —
        // otherwise a stale open menu is an item-duplication hole.
        SimpleContainer container = new SimpleContainer(slots) {
            @Override
            public boolean stillValid(net.minecraft.world.entity.player.Player viewer) {
                return pal.isAlive() && !pal.isRemoved() && viewer.distanceToSqr(pal) <= 64.0;
            }
        };
        CompoundTag data = pal.getPersistentData();
        if (data.contains(KEY_ITEMS)) {
            container.fromTag(data.getList(KEY_ITEMS, 10));
        }
        // Flush to ForgeData on EVERY change, not just on close — a recall
        // snapshots ForgeData at that instant and must never see stale items.
        container.addListener(c -> data.put(KEY_ITEMS, container.createTag()));

        MenuType<ChestMenu> menuType = switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            case 6 -> MenuType.GENERIC_9x6;
            default -> MenuType.GENERIC_9x3;
        };
        int finalRows = rows;
        player.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new ChestMenu(menuType, id, inv, container, finalRows),
                pal.getDisplayName()));
    }

    /**
     * Inserts a stack directly into the pal's pouch NBT (used by the item
     * magnet). Merges with existing stacks first, then fills empty slots up to
     * maxSlots. Returns the leftover that didn't fit.
     */
    public static ItemStack insertStack(Mob pal, int maxSlots, ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        CompoundTag data = pal.getPersistentData();
        // Size the container to hold EVERY existing slot — a pouch may be larger
        // than the magnet's slot budget, and fromTag silently drops out-of-range
        // slots (which would delete the player's items on rewrite).
        int containerSize = maxSlots;
        ListTag existing = data.getList(KEY_ITEMS, 10);
        for (int i = 0; i < existing.size(); i++) {
            containerSize = Math.max(containerSize, (existing.getCompound(i).getByte("Slot") & 255) + 1);
        }
        SimpleContainer container = new SimpleContainer(containerSize);
        if (!existing.isEmpty()) {
            container.fromTag(existing);
        }
        ItemStack remaining = stack.copy();
        // Merge into matching stacks, then empty slots
        for (int pass = 0; pass < 2 && !remaining.isEmpty(); pass++) {
            for (int i = 0; i < maxSlots && !remaining.isEmpty(); i++) {
                ItemStack slot = container.getItem(i);
                if (pass == 0 && !slot.isEmpty() && ItemStack.isSameItemSameTags(slot, remaining)) {
                    int space = slot.getMaxStackSize() - slot.getCount();
                    int moved = Math.min(space, remaining.getCount());
                    slot.grow(moved);
                    remaining.shrink(moved);
                } else if (pass == 1 && slot.isEmpty()) {
                    container.setItem(i, remaining.copy());
                    remaining = ItemStack.EMPTY;
                }
            }
        }
        data.put(KEY_ITEMS, container.createTag());
        return remaining;
    }

    // ── Magnet helpers ────────────────────────────────────────────────────

    /** The item type currently held in the pouch (first non-empty slot), or EMPTY. */
    public static ItemStack pouchFirstStack(Mob pal) {
        CompoundTag data = pal.getPersistentData();
        if (!data.contains(KEY_ITEMS)) return ItemStack.EMPTY;
        ListTag list = data.getList(KEY_ITEMS, 10);
        for (int i = 0; i < list.size(); i++) {
            ItemStack s = ItemStack.of(list.getCompound(i));
            if (!s.isEmpty()) return s;
        }
        return ItemStack.EMPTY;
    }

    public static boolean pouchEmpty(Mob pal) {
        return pouchFirstStack(pal).isEmpty();
    }

    /** True if the pouch (maxSlots) has no room to accept more of the given stack. */
    public static boolean pouchFullFor(Mob pal, int maxSlots, ItemStack stack) {
        if (stack.isEmpty()) return false;
        SimpleContainer c = loadPouch(pal, maxSlots);
        for (int i = 0; i < maxSlots; i++) {
            ItemStack slot = c.getItem(i);
            if (slot.isEmpty()) return false;
            if (ItemStack.isSameItemSameTags(slot, stack) && slot.getCount() < slot.getMaxStackSize()) return false;
        }
        return true;
    }

    private static SimpleContainer loadPouch(Mob pal, int maxSlots) {
        CompoundTag data = pal.getPersistentData();
        ListTag existing = data.getList(KEY_ITEMS, 10);
        int size = maxSlots;
        for (int i = 0; i < existing.size(); i++) {
            size = Math.max(size, (existing.getCompound(i).getByte("Slot") & 255) + 1);
        }
        SimpleContainer c = new SimpleContainer(size);
        if (!existing.isEmpty()) c.fromTag(existing);
        return c;
    }

    /**
     * Empties the pal's pouch into the destination handler (a chest or another
     * pal's pouch). Returns true if at least one item was moved; leftover stays
     * in the pouch.
     */
    public static boolean depositPouchInto(Mob pal, net.minecraftforge.items.IItemHandler dest, int maxSlots) {
        SimpleContainer c = loadPouch(pal, maxSlots);
        boolean moved = false;
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack slot = c.getItem(i);
            if (slot.isEmpty()) continue;
            ItemStack leftover = net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(dest, slot, false);
            if (leftover.getCount() != slot.getCount()) moved = true;
            c.setItem(i, leftover);
        }
        if (moved) pal.getPersistentData().put(KEY_ITEMS, c.createTag());
        return moved;
    }

    /** Empties the pal's pouch into a vanilla Container (e.g. an open chest). */
    public static boolean depositPouchInto(Mob pal, net.minecraft.world.Container dest, int maxSlots) {
        SimpleContainer c = loadPouch(pal, maxSlots);
        boolean moved = false;
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack slot = c.getItem(i);
            if (slot.isEmpty()) continue;
            ItemStack leftover = addToContainer(dest, slot);
            if (leftover.getCount() != slot.getCount()) moved = true;
            c.setItem(i, leftover);
        }
        if (moved) pal.getPersistentData().put(KEY_ITEMS, c.createTag());
        return moved;
    }

    /** Deposits a stack into another pal's pouch (kangaroo). Returns leftover. */
    public static ItemStack insertIntoPalPouch(Mob storagePal, int storageSlots, ItemStack stack) {
        return insertStack(storagePal, storageSlots, stack);
    }

    /** Drains one pal's pouch into another pal's pouch. Returns true if anything moved. */
    public static boolean depositPouchIntoPal(Mob from, int fromSlots, Mob to, int toSlots) {
        SimpleContainer c = loadPouch(from, fromSlots);
        boolean moved = false;
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack slot = c.getItem(i);
            if (slot.isEmpty()) continue;
            ItemStack leftover = insertStack(to, toSlots, slot);
            if (leftover.getCount() != slot.getCount()) moved = true;
            c.setItem(i, leftover);
        }
        if (moved) from.getPersistentData().put(KEY_ITEMS, c.createTag());
        return moved;
    }

    private static ItemStack addToContainer(net.minecraft.world.Container dest, ItemStack stack) {
        ItemStack rem = stack.copy();
        // merge
        for (int i = 0; i < dest.getContainerSize() && !rem.isEmpty(); i++) {
            ItemStack slot = dest.getItem(i);
            if (!slot.isEmpty() && ItemStack.isSameItemSameTags(slot, rem)) {
                int space = Math.min(slot.getMaxStackSize(), dest.getMaxStackSize()) - slot.getCount();
                int moved = Math.min(space, rem.getCount());
                if (moved > 0) { slot.grow(moved); rem.shrink(moved); dest.setChanged(); }
            }
        }
        for (int i = 0; i < dest.getContainerSize() && !rem.isEmpty(); i++) {
            if (dest.getItem(i).isEmpty()) { dest.setItem(i, rem.copy()); rem = ItemStack.EMPTY; dest.setChanged(); }
        }
        return rem;
    }

    /** Scatter the pouch contents when the pal dies. */
    public static void dropContents(ServerLevel level, Mob pal) {
        CompoundTag data = pal.getPersistentData();
        if (!data.contains(KEY_ITEMS)) return;
        ListTag list = data.getList(KEY_ITEMS, 10);
        for (int i = 0; i < list.size(); i++) {
            ItemStack stack = ItemStack.of(list.getCompound(i));
            if (!stack.isEmpty()) {
                Block.popResource(level, pal.blockPosition(), stack);
            }
        }
        data.remove(KEY_ITEMS);
    }
}
