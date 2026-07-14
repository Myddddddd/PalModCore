package com.mx.palmod.pal;

import com.mx.palmod.behavior.PalBehavior;
import com.mx.palmod.stats.PalStats;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Per-pal passive abilities ticked from onLivingTick (every 20 ticks):
 *  - XP collector: absorbs nearby XP orbs into ForgeData "PalStoredXp"
 *  - Item magnet: pulls nearby drops into the pal's pouch (PalChestItems)
 *  - Greedy boom: at full belly the pal explodes and "becomes" one of the
 *    owner's recent kills — dying its death and dropping its loot
 */
public final class PalPassives {

    public static final String KEY_STORED_XP = "PalStoredXp";

    private PalPassives() {}

    public static void tick(Mob mob, PalBehavior behavior) {
        if (!(mob.level() instanceof ServerLevel level)) return;

        if (behavior.getXpCollectRadius() > 0) {
            absorbXp(level, mob, behavior);
        }
        if (behavior.getMagnetRadius() > 0) {
            magnetItems(level, mob, behavior);
        }
        if (behavior.isGreedyBoom() && mob.getPersistentData().getBoolean("BoomPrimed")
                && PalStats.getHunger(mob) >= behavior.getBoomAtHunger()) {
            greedyBoom(level, mob);
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  XP collector
    // ──────────────────────────────────────────────────────────────

    private static void absorbXp(ServerLevel level, Mob mob, PalBehavior behavior) {
        CompoundTag data = mob.getPersistentData();
        int stored = data.getInt(KEY_STORED_XP);
        if (stored >= behavior.getXpMaxStored()) return;

        double r = behavior.getXpCollectRadius();
        List<ExperienceOrb> orbs = level.getEntitiesOfClass(ExperienceOrb.class,
                mob.getBoundingBox().inflate(r));
        for (ExperienceOrb orb : orbs) {
            // Never clip an orb's value at the cap — leave what doesn't fit
            if (stored + orb.getValue() > behavior.getXpMaxStored()) continue;
            stored += orb.getValue();
            orb.discard();
            level.sendParticles(ParticleTypes.ENCHANT,
                    mob.getX(), mob.getY() + mob.getBbHeight() * 0.6, mob.getZ(), 6, 0.3, 0.3, 0.3, 0.4);
        }
        data.putInt(KEY_STORED_XP, Math.min(stored, behavior.getXpMaxStored()));
    }

    /** Sneak-click with an empty hand: pop the stored XP out as bottles o' enchanting. */
    public static void extractXpBottles(ServerPlayer player, Mob mob) {
        CompoundTag data = mob.getPersistentData();
        int stored = data.getInt(KEY_STORED_XP);
        int bottles = stored / 8; // a bottle averages ~7-8 xp
        if (bottles <= 0) {
            player.displayClientMessage(Component.literal(
                    mob.getName().getString() + " has no XP stored yet (" + stored + "/8)."), true);
            return;
        }
        data.putInt(KEY_STORED_XP, stored - bottles * 8);
        net.minecraftforge.items.ItemHandlerHelper.giveItemToPlayer(player,
                new ItemStack(net.minecraft.world.item.Items.EXPERIENCE_BOTTLE, bottles));
        player.displayClientMessage(Component.literal(
                mob.getName().getString() + " condensed " + bottles + " bottle(s) o' enchanting."), true);
        mob.level().playSound(null, mob.blockPosition(),
                SoundEvents.EXPERIENCE_BOTTLE_THROW, SoundSource.NEUTRAL, 0.8F, 1.2F);
    }

    // ──────────────────────────────────────────────────────────────
    //  Item magnet
    // ──────────────────────────────────────────────────────────────

    private static void magnetItems(ServerLevel level, Mob mob, PalBehavior behavior) {
        double r = behavior.getMagnetRadius();
        int slots = behavior.getMagnetSlots();
        // A magnet pal hoards ONE item type at a time. While the pouch holds
        // something, it only reels in more of that same type; when empty, the
        // first thing it grabs locks the type until the pouch is deposited.
        ItemStack lockType = PalChest.pouchFirstStack(mob);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class,
                mob.getBoundingBox().inflate(r), e -> e.isAlive() && !e.hasPickUpDelay()
                        && (lockType.isEmpty() || ItemStack.isSameItemSameTags(e.getItem(), lockType)));
        for (ItemEntity item : items) {
            if (PalChest.pouchFullFor(mob, slots, item.getItem())) break; // no room — go deposit
            double distSqr = item.distanceToSqr(mob);
            if (distSqr < 2.25) {
                ItemStack leftover = PalChest.insertStack(mob, slots, item.getItem());
                if (leftover.isEmpty()) {
                    item.discard();
                    level.playSound(null, mob.blockPosition(),
                            SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.4F, 1.4F);
                } else {
                    item.setItem(leftover);
                }
            } else {
                // Reel it in
                Vec3 pull = mob.position().add(0, mob.getBbHeight() * 0.5, 0)
                        .subtract(item.position()).normalize().scale(0.35);
                item.setDeltaMovement(item.getDeltaMovement().scale(0.4).add(pull));
            }
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Greedy boom
    // ──────────────────────────────────────────────────────────────

    private static void greedyBoom(ServerLevel level, Mob mob) {
        Vec3 pos = mob.position();
        ServerPlayer owner = null;
        if (mob.getPersistentData().hasUUID("PalOwner")) {
            owner = (ServerPlayer) level.getPlayerByUUID(mob.getPersistentData().getUUID("PalOwner"));
        }

        // Pick one of the owner's recent kills to mimic
        EntityType<?> mimicType = null;
        if (owner != null) {
            List<EntityType<?>> kills = KillTracker.recentKills(owner.getUUID(), level.getGameTime());
            if (!kills.isEmpty()) {
                mimicType = kills.get(mob.getRandom().nextInt(kills.size()));
            }
        }

        // Boom is pure spectacle (a real explosion would nuke the owner who
        // just hand-fed it at point-blank) — then the pal dies its death
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y + 0.5, pos.z, 2, 0.3, 0.3, 0.3, 0.0);
        level.playSound(null, mob.blockPosition(),
                SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, 1.5F, 0.9F);
        mob.hurt(level.damageSources().genericKill(), Float.MAX_VALUE);

        if (mimicType == null) {
            if (owner != null) {
                owner.displayClientMessage(Component.literal(
                        "It burst... but you hadn't killed anything recently."), true);
            }
            return;
        }

        // "It becomes the mob you killed — and dies once more."
        Entity mimic = mimicType.create(level);
        if (mimic instanceof LivingEntity livingMimic) {
            livingMimic.moveTo(pos.x, pos.y, pos.z, mob.getYRot(), 0);
            // Mark it so its death doesn't re-enter the kill tracker (a boss kill
            // would otherwise become a self-sustaining loot loop)
            livingMimic.getPersistentData().putBoolean("PalMimic", true);
            level.addFreshEntity(livingMimic);
            livingMimic.hurt(owner != null
                            ? level.damageSources().playerAttack(owner)
                            : level.damageSources().genericKill(),
                    Float.MAX_VALUE);
            if (owner != null) {
                owner.displayClientMessage(Component.literal(
                        "It gorged itself, burst — and became a "
                                + mimicType.getDescription().getString() + "!"), true);
            }
        }
    }
}
