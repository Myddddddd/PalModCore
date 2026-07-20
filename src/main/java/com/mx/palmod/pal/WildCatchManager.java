package com.mx.palmod.pal;

import com.mx.palmod.Config;
import com.mx.palmod.ai.PalCastAttackGoal;
import com.mx.palmod.ai.WildFleeGoal;
import com.mx.palmod.behavior.PalBehavior;
import com.mx.palmod.behavior.PalBehaviorManager;
import com.mx.palmod.entity.PalSphereProjectile;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import java.util.List;
import java.util.UUID;

/**
 * Wild-catch system: only a config-chance fraction of naturally spawning mobs
 * roll "catchable" (marked by a glowing outline + enchant shimmer). Catchable
 * mobs with a behaviors "wild" block fight back with an extra difficulty layer:
 *
 *  - "brawler":          stat buffs + their own combat_ability cast, wild
 *  - "skittish":         see/hear players from far away and flee (counter: sneak)
 *  - "op" / "predator_locked": a signature counter-required move, keyed by
 *                        wild.type and resolved via {@link WildDefenseRegistry}
 *                        (time_stop/storm_dodge/blink/damage_immune/shy_hide/
 *                        ambush_hide ship built in — third-party mods can
 *                        register their own types with zero changes here)
 */
public final class WildCatchManager {

    public static final String KEY_ROLLED      = "WildRolled";
    public static final String KEY_CATCHABLE   = "PalCatchable";
    public static final String KEY_HIDDEN      = "WildHidden";
    public static final String KEY_ZW_READY_AT = "WildZWReadyAt";
    public static final String KEY_LAST_HURT   = "WildLastHurt";

    // Per-mob brawler tank multiplier (from behavior JSON health_multiplier)
    private static final UUID WILD_HP_MODIFIER = UUID.fromString("8d1a3c52-6f0b-4b6e-9a4e-2f7c1d5b9e01");
    // Global toughness applied to EVERY catchable mob (Config.wildHealthMultiplier / wildDamageMultiplier)
    private static final UUID WILD_GLOBAL_HP_MODIFIER  = UUID.fromString("2b7e9c14-3d5a-4f81-8c6b-1a2f7d3e9b02");
    private static final UUID WILD_GLOBAL_DMG_MODIFIER = UUID.fromString("6f4c1a83-9b2e-4d70-a5c1-8e3f2b7d1c03");

    private WildCatchManager() {}

    // ── Spawn roll + wild goal injection (EntityJoinLevel) ────────────────

    public static void onWildJoin(Mob mob, PalBehavior behavior) {
        rollIfNeeded(mob, behavior);
        if (!hasWildPower(mob)) return;

        String category = behavior.getWildCategory();
        if ("brawler".equals(category)) {
            if (behavior.isWildUseCombatAbility() && !behavior.getCombatAbilityType().isEmpty()) {
                mob.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(mob, Player.class, true));
                mob.goalSelector.addGoal(2, new PalCastAttackGoal(mob, behavior));
            }
        } else if ("skittish".equals(category) && mob instanceof PathfinderMob pathfinderMob) {
            mob.goalSelector.addGoal(1, new WildFleeGoal(pathfinderMob, behavior));
        }
    }

    private static void rollIfNeeded(Mob mob, PalBehavior behavior) {
        CompoundTag data = mob.getPersistentData();
        // Villagers/wandering traders are never catchable (also scrubs the flag
        // off villagers rolled catchable before this rule existed)
        if (mob instanceof net.minecraft.world.entity.npc.Npc) {
            data.putBoolean(KEY_ROLLED, true);
            data.putBoolean(KEY_CATCHABLE, false);
            return;
        }
        if (data.getBoolean(KEY_ROLLED)) return;
        data.putBoolean(KEY_ROLLED, true);
        boolean catchable = mob.getRandom().nextDouble() < Config.catchableChance;
        data.putBoolean(KEY_CATCHABLE, catchable);

        if (catchable) {
            applyWildToughness(mob, behavior);
        }
    }

    /**
     * Turns a freshly-rolled catchable mob into a tanky, hard-hitting bruiser:
     * a global max-health multiplier (every catchable mob), a global attack-damage
     * multiplier (only mobs that own an attack-damage attribute), and any per-mob
     * brawler health multiplier stacked on top. All are permanent modifiers guarded
     * against re-application so re-joins never re-stack. Catch ODDS are unaffected —
     * {@link #getCatchMaxHealth} strips these back out so the catch math always
     * charges the mob's true base health.
     */
    private static void applyWildToughness(Mob mob, PalBehavior behavior) {
        AttributeInstance maxHealth = mob.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            double globalHp = Config.wildHealthMultiplier;
            if (globalHp > 1.0 && maxHealth.getModifier(WILD_GLOBAL_HP_MODIFIER) == null) {
                maxHealth.addPermanentModifier(new AttributeModifier(WILD_GLOBAL_HP_MODIFIER,
                        "palmod.wild_global_health", globalHp - 1.0,
                        AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
            // Per-mob brawler toughness stacks on top of the global buff.
            if ("brawler".equals(behavior.getWildCategory())
                    && behavior.getWildHealthMultiplier() > 1.0f
                    && maxHealth.getModifier(WILD_HP_MODIFIER) == null) {
                maxHealth.addPermanentModifier(new AttributeModifier(WILD_HP_MODIFIER,
                        "palmod.wild_brawler_health",
                        behavior.getWildHealthMultiplier() - 1.0,
                        AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
        }

        AttributeInstance attackDamage = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        double globalDmg = Config.wildDamageMultiplier;
        if (attackDamage != null && globalDmg > 1.0
                && attackDamage.getModifier(WILD_GLOBAL_DMG_MODIFIER) == null) {
            attackDamage.addPermanentModifier(new AttributeModifier(WILD_GLOBAL_DMG_MODIFIER,
                    "palmod.wild_global_damage", globalDmg - 1.0,
                    AttributeModifier.Operation.MULTIPLY_TOTAL));
        }

        // Start the buffed mob at full (buffed) health.
        if (maxHealth != null) {
            mob.setHealth(mob.getMaxHealth());
        }
    }

    /**
     * Removes the wild-toughness attribute modifiers and heals the mob to its true
     * base max. Called at CATCH time (before the mob is serialized into the sphere)
     * so a tamed pal carries its real base HP/damage — not the 4.5x/2x fight buffs —
     * and doesn't summon near-death (mobs are caught weakened, and a tiny saved
     * health against an inflated max would otherwise appear at ~1 HP on summon).
     */
    public static void stripWildToughness(LivingEntity entity) {
        AttributeInstance maxHealth = entity.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.removePermanentModifier(WILD_GLOBAL_HP_MODIFIER);
            maxHealth.removePermanentModifier(WILD_HP_MODIFIER);
        }
        AttributeInstance attackDamage = entity.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackDamage != null) {
            attackDamage.removePermanentModifier(WILD_GLOBAL_DMG_MODIFIER);
        }
        // Freshly tamed: come back at full (true base) health.
        entity.setHealth(entity.getMaxHealth());
    }

    /** Records the tick a catchable wild mob last took damage (suppresses its regen). */
    public static void stampHurt(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        if (!hasWildPower(entity)) return;
        // gameTime 0 is our "never hurt" sentinel; clamp to 1 so it stays truthy.
        entity.getPersistentData().putLong(KEY_LAST_HURT, Math.max(1L, level.getGameTime()));
    }

    // ── State queries ─────────────────────────────────────────────────────

    /** Rolled and failed — a sphere thrown at this mob is simply lost. */
    public static boolean isUncatchable(LivingEntity entity) {
        if (entity instanceof net.minecraft.world.entity.npc.Npc) return true;
        CompoundTag data = entity.getPersistentData();
        return data.getBoolean(KEY_ROLLED) && !data.getBoolean(KEY_CATCHABLE);
    }

    /** Catchable AND still wild — the only mobs whose wild powers are live. */
    public static boolean hasWildPower(LivingEntity entity) {
        CompoundTag data = entity.getPersistentData();
        return data.getBoolean(KEY_CATCHABLE) && !data.contains("PalOwner");
    }

    /**
     * Max health for the catch formula. Brawlers carry a wild HP multiplier that
     * would otherwise push the flat maxHealth penalty into never-catchable
     * territory — the formula charges their BASE health instead.
     */
    public static float getCatchMaxHealth(LivingEntity target) {
        AttributeInstance maxHealth = target.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) return target.getMaxHealth();
        // Charge the mob's TRUE (unbuffed) health so wild toughness never bleeds
        // into catch odds — divide out our MULTIPLY_TOTAL toughness modifiers
        // (global + per-mob brawler). Any innate modifiers the mob has are kept.
        double effective = target.getMaxHealth();
        AttributeModifier global = maxHealth.getModifier(WILD_GLOBAL_HP_MODIFIER);
        if (global != null) effective /= (1.0 + global.getAmount());
        AttributeModifier brawler = maxHealth.getModifier(WILD_HP_MODIFIER);
        if (brawler != null) effective /= (1.0 + brawler.getAmount());
        return (float) effective;
    }

    // ── 20-tick wild upkeep (glow, shimmer, hide/reveal, buffs) ───────────

    public static void tickWild(Mob mob) {
        if (!(mob.level() instanceof ServerLevel level)) return;
        CompoundTag data = mob.getPersistentData();
        PalBehavior behavior = PalBehaviorManager.getBehavior(mob.getType());
        String category = behavior.getWildCategory();

        // op/predator_locked "signature move" telegraphs, hide/reveal state, etc. —
        // fully owned by whatever's registered under this mob's wild.type
        WildDefenseRegistry.WildDefense defense = WildDefenseRegistry.get(behavior.getWildType());
        if (defense != null) {
            defense.tick(mob, behavior, level);
        }

        // Tanky wild bruisers self-heal a slice of max HP each second unless they
        // took damage recently — forcing a burst-then-catch loop rather than chip
        // damage. Runs even while hidden (shy/ambush) so a stalled ambush recovers.
        tickWildRegen(mob, level);

        if (data.getBoolean(KEY_HIDDEN)) {
            // Hidden: no glow, no shimmer, hold still
            mob.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 45, 0, false, false));
            mob.getNavigation().stop();
            return;
        }

        // The enchanted shimmer that marks a catchable mob up close. (The old
        // through-wall Glowing outline was removed — the Pal Compass is now the
        // long-range way to find catchable mobs.)
        level.sendParticles(ParticleTypes.ENCHANT,
                mob.getX(), mob.getY() + mob.getBbHeight() * 0.7, mob.getZ(), 4, 0.3, 0.4, 0.3, 0.4);

        if ("brawler".equals(category) && behavior.getWildResistance() >= 0) {
            mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 30,
                    behavior.getWildResistance(), false, false));
        }
    }

    /**
     * Per-second wild self-heal (called from the 20-tick {@link #tickWild}).
     * Heals a config fraction of max health, suppressed for a window after the
     * mob last took damage. Disabled entirely when the fraction is 0.
     */
    private static void tickWildRegen(Mob mob, ServerLevel level) {
        double frac = Config.wildRegenFractionPerSecond;
        if (frac <= 0.0) return;
        if (mob.getHealth() >= mob.getMaxHealth() || !mob.isAlive()) return;
        long lastHurt = mob.getPersistentData().getLong(KEY_LAST_HURT);
        if (lastHurt != 0L && level.getGameTime() - lastHurt < Config.wildRegenSuppressTicks) return;

        float heal = (float) (mob.getMaxHealth() * frac);
        if (heal <= 0f) return;
        mob.setHealth(Math.min(mob.getMaxHealth(), mob.getHealth() + heal));
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                mob.getX(), mob.getY() + mob.getBbHeight() * 0.8, mob.getZ(),
                2, 0.3, 0.3, 0.3, 0.0);
    }

    // ── Sphere interactions ───────────────────────────────────────────────

    /**
     * ZA WARUDO interception: a wild time-stopper freezes any catching sphere
     * that enters its radius, drops it as an item, and blinks away. Returns true
     * if the sphere was consumed.
     */
    public static boolean tryZaWarudoIntercept(PalSphereProjectile sphere) {
        if (!(sphere.level() instanceof ServerLevel level)) return false;
        // Skip the entity scan entirely unless some datapack defines an op-category defense
        if (!PalBehaviorManager.hasWildOpDefense()) return false;
        List<Mob> nearby = level.getEntitiesOfClass(Mob.class,
                sphere.getBoundingBox().inflate(32),
                mob -> hasWildPower(mob) && !com.mx.palmod.timestop.TimeStopManager.isFrozen(mob));
        for (Mob mob : nearby) {
            PalBehavior behavior = PalBehaviorManager.getBehavior(mob.getType());
            WildDefenseRegistry.WildDefense defense = WildDefenseRegistry.get(behavior.getWildType());
            if (defense == null) continue;
            if (defense.onSphereNearby(mob, behavior, sphere, level)) {
                return true;
            }
        }
        return false;
    }

    /** Enderman-style escape teleport (blink dodge, post-ZA-WARUDO reposition). */
    public static void blinkAway(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        level.sendParticles(ParticleTypes.PORTAL,
                entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                30, 0.4, 0.6, 0.4, 0.1);
        for (int i = 0; i < 16; i++) {
            double x = entity.getX() + (entity.getRandom().nextDouble() - 0.5) * 16.0;
            double y = entity.getY() + (entity.getRandom().nextInt(9) - 4);
            double z = entity.getZ() + (entity.getRandom().nextDouble() - 0.5) * 16.0;
            if (entity.randomTeleport(x, y, z, false)) break;
        }
        level.playSound(null, entity.blockPosition(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 1.0F, 1.0F);
    }

    /** Mid-air sphere dodge + retaliation bolt on the thrower. */
    public static void stormDodge(LivingEntity target, Entity thrower) {
        if (!(target.level() instanceof ServerLevel level)) return;
        double angle = target.getRandom().nextDouble() * Math.PI * 2;
        target.setDeltaMovement(Math.cos(angle) * 0.9, 0.5, Math.sin(angle) * 0.9);
        target.hurtMarked = true;
        level.sendParticles(ParticleTypes.CLOUD,
                target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                15, 0.4, 0.4, 0.4, 0.1);
        if (thrower instanceof LivingEntity living) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(living.getX(), living.getY(), living.getZ());
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
            }
            living.hurt(level.damageSources().lightningBolt(), 4.0F);
        }
    }

    // ── Damage immunity (predator_locked / damage_immune) ─────────────────

    /**
     * Cancels damage to a wild damage-immune mob unless it comes from an owned
     * pal whose combat_ability matches the configured counter. Returns true if
     * the event was canceled.
     */
    public static boolean cancelIfImmune(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (!hasWildPower(victim)) return false;
        // /kill, the void, and other invulnerability-bypassing sources must
        // still work, or the mob is un-removable even for admins
        if (event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        if (!(victim instanceof Mob mob) || !(victim.level() instanceof ServerLevel level)) return false;

        PalBehavior behavior = PalBehaviorManager.getBehavior(mob.getType());
        WildDefenseRegistry.WildDefense defense = WildDefenseRegistry.get(behavior.getWildType());
        if (defense == null) return false;

        boolean canceled = defense.onDamage(mob, behavior, event, level);
        if (canceled) {
            event.setCanceled(true);
        }
        return canceled;
    }

    // ── Pal Compass ───────────────────────────────────────────────────────

    private static final double COMPASS_RANGE = 128.0;

    /**
     * Points a PalCompass-tagged vanilla compass at the nearest catchable wild
     * mob (lodestone NBT with LodestoneTracked=false so the server never strips
     * it). With no target in range the dimension is mismatched on purpose so the
     * needle spins. NBT is only written on change to avoid re-sync churn.
     */
    public static void updatePalCompass(ServerLevel level, Player player, ItemStack compass) {
        Mob target = nearestCatchable(level, player);
        CompoundTag tag = compass.getOrCreateTag();
        if (target != null) {
            // Hysteresis: only rewrite once the target drifted a few blocks —
            // every NBT write re-syncs the stack and replays the re-equip
            // animation, so per-second exact updates would make the held
            // compass visibly dip nonstop while tracking a wandering mob.
            net.minecraft.core.BlockPos targetPos = target.blockPosition();
            CompoundTag stored = tag.getCompound("LodestonePos");
            if (stored.isEmpty()
                    || net.minecraft.nbt.NbtUtils.readBlockPos(stored).distSqr(targetPos) > 8 * 8) {
                tag.put("LodestonePos", net.minecraft.nbt.NbtUtils.writeBlockPos(targetPos));
            }
            String dim = level.dimension().location().toString();
            if (!dim.equals(tag.getString("LodestoneDimension"))) {
                tag.putString("LodestoneDimension", dim);
            }
        } else if (tag.contains("LodestonePos")) {
            tag.remove("LodestonePos");
            tag.putString("LodestoneDimension", "palmod:nowhere");
        }
        if (!tag.contains("LodestoneTracked") || tag.getBoolean("LodestoneTracked")) {
            tag.putBoolean("LodestoneTracked", false);
        }
    }

    @javax.annotation.Nullable
    private static Mob nearestCatchable(ServerLevel level, Player player) {
        List<Mob> mobs = level.getEntitiesOfClass(Mob.class,
                player.getBoundingBox().inflate(COMPASS_RANGE),
                m -> m.isAlive()
                        && m.getPersistentData().getBoolean(KEY_CATCHABLE)
                        // Hidden shy/ambush predators are un-taggable — pointing
                        // at them would spoil the hide mechanic (see homing filter)
                        && !m.getPersistentData().getBoolean(KEY_HIDDEN)
                        && !m.getPersistentData().contains("PalOwner")
                        && !(m instanceof net.minecraft.world.entity.npc.Npc));
        Mob best = null;
        double bestDistSqr = Double.MAX_VALUE;
        for (Mob mob : mobs) {
            double distSqr = player.distanceToSqr(mob);
            if (distSqr < bestDistSqr) {
                bestDistSqr = distSqr;
                best = mob;
            }
        }
        return best;
    }

    /** A fresh Pal Compass item (used by the creative tab; the recipe builds the same NBT). */
    public static ItemStack createPalCompass() {
        ItemStack stack = new ItemStack(net.minecraft.world.item.Items.COMPASS);
        CompoundTag tag = stack.getOrCreateTag();
        tag.putBoolean("PalCompass", true);
        tag.putBoolean("LodestoneTracked", false);
        tag.putString("LodestoneDimension", "palmod:nowhere");
        CompoundTag display = new CompoundTag();
        display.putString("Name", Component.Serializer.toJson(
                Component.literal("Pal Compass")
                        .withStyle(style -> style.withColor(net.minecraft.ChatFormatting.AQUA).withItalic(false))));
        tag.put("display", display);
        return stack;
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    public static void message(Entity entity, String text) {
        if (entity instanceof ServerPlayer player) {
            player.displayClientMessage(Component.literal(text), true);
        }
    }
}
