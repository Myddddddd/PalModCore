package com.mx.palmod.pal;

import com.mojang.logging.LogUtils;
import com.mx.palmod.behavior.PalBehavior;
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
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Registers a Pal's "proactive special power" — the one thing per mob that fires
 * on its own instead of needing a manual button (see CLAUDE.md's ability roster).
 * Adding a new one is a single {@link #register(PalAbility)} call from any mod's
 * init — no Palmod source changes required. A power only implements the hooks it
 * actually needs; everything on {@link PalAbility} defaults to a no-op.
 */
public final class PalAbilityRegistry {

    private static final Logger LOGGER = LogUtils.getLogger();

    public interface PalAbility {
        /** Whether this behavior config wants this power at all. */
        boolean appliesTo(PalBehavior behavior);

        /** Inject any AI goals this power needs when the mob (re)joins the level as an owned pal. */
        default void onJoin(Mob mob, PalBehavior behavior) {}

        /** Passive hook on Palmod's existing ~20-tick pal upkeep pulse. */
        default void tick(Mob mob, PalBehavior behavior, ServerLevel level) {}

        /** Fires once right after a fresh summon finishes placing the pal in the world. */
        default void onSummon(Mob mob, PalBehavior behavior, ServerPlayer owner, ServerLevel level, CompoundTag sphereTag) {}

        /**
         * Pure query, no side effects: does this power want to force a deploy mode
         * (e.g. "anchor") on a plain summon, before the entity even exists? Only
         * consulted when the throw didn't already request a deploy mode itself.
         */
        default Optional<String> forcedDeployMode(PalBehavior behavior) {
            return Optional.empty();
        }
    }

    private static final List<PalAbility> ABILITIES = new ArrayList<>();

    static {
        register(new CloneAbility());
        register(new FetchAbility());
        register(new MagnetAbility());
        register(new XpCollectorAbility());
        register(new GreedyBoomAbility());
        register(new WarpBeaconAbility());
        register(new TimeStopOnSummonAbility());
    }

    private PalAbilityRegistry() {}

    public static void register(PalAbility ability) {
        ABILITIES.add(ability);
    }

    /** All registered powers whose config the given behavior actually wants. */
    public static List<PalAbility> applicable(PalBehavior behavior) {
        List<PalAbility> result = new ArrayList<>();
        for (PalAbility ability : ABILITIES) {
            try {
                if (ability.appliesTo(behavior)) {
                    result.add(ability);
                }
            } catch (Exception e) {
                LOGGER.error("PalAbility {} threw in appliesTo()", ability.getClass().getName(), e);
            }
        }
        return result;
    }

    // ── Built-in powers ──────────────────────────────────────────────────

    private static final class CloneAbility implements PalAbility {
        @Override
        public boolean appliesTo(PalBehavior behavior) {
            return behavior.getCloneMax() > 0;
        }

        @Override
        public void onJoin(Mob mob, PalBehavior behavior) {
            mob.goalSelector.addGoal(2, new com.mx.palmod.ai.PalCloneGoal(mob, behavior));
        }
    }

    private static final class FetchAbility implements PalAbility {
        @Override
        public boolean appliesTo(PalBehavior behavior) {
            return behavior.isCanFetch();
        }

        @Override
        public void onJoin(Mob mob, PalBehavior behavior) {
            mob.goalSelector.addGoal(1, new com.mx.palmod.ai.FetchGoal(mob, behavior.getFetchRadius()));
        }
    }

    /** Hoards one item type into the pouch, then a goal (added at onJoin) carries it to deliver. */
    private static final class MagnetAbility implements PalAbility {
        @Override
        public boolean appliesTo(PalBehavior behavior) {
            return behavior.getMagnetRadius() > 0;
        }

        @Override
        public void onJoin(Mob mob, PalBehavior behavior) {
            mob.goalSelector.addGoal(2, new com.mx.palmod.ai.PalMagnetDepositGoal(mob, behavior));
        }

        @Override
        public void tick(Mob mob, PalBehavior behavior, ServerLevel level) {
            double r = behavior.getMagnetRadius();
            int slots = behavior.getMagnetSlots();
            // While the pouch holds something, only reel in more of that same type;
            // when empty, the first thing it grabs locks the type until deposited.
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
    }

    /** Absorbs nearby XP orbs into ForgeData; sneak-click (unchanged, in ForgeEvents) extracts bottles. */
    private static final class XpCollectorAbility implements PalAbility {
        @Override
        public boolean appliesTo(PalBehavior behavior) {
            return behavior.getXpCollectRadius() > 0;
        }

        @Override
        public void tick(Mob mob, PalBehavior behavior, ServerLevel level) {
            CompoundTag data = mob.getPersistentData();
            int stored = data.getInt(PalPassives.KEY_STORED_XP);
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
            data.putInt(PalPassives.KEY_STORED_XP, Math.min(stored, behavior.getXpMaxStored()));
        }
    }

    /** At full belly the pal explodes and "becomes" one of the owner's recent kills. */
    private static final class GreedyBoomAbility implements PalAbility {
        @Override
        public boolean appliesTo(PalBehavior behavior) {
            return behavior.isGreedyBoom();
        }

        @Override
        public void tick(Mob mob, PalBehavior behavior, ServerLevel level) {
            if (!mob.getPersistentData().getBoolean("BoomPrimed")
                    || com.mx.palmod.stats.PalStats.getHunger(mob) < behavior.getBoomAtHunger()) {
                return;
            }

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

    /** Roots on a normal summon (reusing the anchor machinery) so a deliberate recall warps the owner to it. */
    private static final class WarpBeaconAbility implements PalAbility {
        @Override
        public boolean appliesTo(PalBehavior behavior) {
            return behavior.isWarpBeacon();
        }

        @Override
        public Optional<String> forcedDeployMode(PalBehavior behavior) {
            return Optional.of("anchor");
        }

        @Override
        public void onSummon(Mob mob, PalBehavior behavior, ServerPlayer owner, ServerLevel level, CompoundTag sphereTag) {
            sphereTag.putBoolean("WarpOnRecall", true);
        }
    }

    /** ZA WARUDO on summon: freezes the area around wherever the pal just landed. */
    private static final class TimeStopOnSummonAbility implements PalAbility {
        @Override
        public boolean appliesTo(PalBehavior behavior) {
            return behavior.isTimeStopOnSummon();
        }

        @Override
        public void onSummon(Mob mob, PalBehavior behavior, ServerPlayer owner, ServerLevel level, CompoundTag sphereTag) {
            com.mx.palmod.timestop.TimeStopManager.tryActivateOnSummon(owner, level, mob, sphereTag, behavior);
        }
    }
}
