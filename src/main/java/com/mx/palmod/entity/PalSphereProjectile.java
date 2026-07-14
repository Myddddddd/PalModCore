package com.mx.palmod.entity;

import com.mx.palmod.enchantment.FollowingEnchantment;
import com.mx.palmod.enchantment.InfiniteEnchantment;
import com.mx.palmod.enchantment.LevelingEnchantment;
import com.mx.palmod.registry.ModRegistries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class PalSphereProjectile extends ThrowableItemProjectile {

    // ── Enchantment data (set from item before launch) ──────────────────
    private int sphereLevel   = 1;
    private int infiniteLevel  = 0;
    private int followingLevel = 0;
    private int levelingLevel  = 0;
    private int warpTetherLevel = 0;

    // Deploy mode for summoning throws ("" = normal combat summon)
    private String deployMode = "";

    // Following state
    private boolean isHoming   = false; // activates after first block-hit
    private static final int HOMING_MAX_TICKS = 60; // give up after 3s
    private int homingTicks    = 0;

    // Summoning state
    private boolean isSummoning = false;
    private UUID summonSphereUUID = null;

    // ── Constructors ─────────────────────────────────────────────────────

    public PalSphereProjectile(EntityType<? extends ThrowableItemProjectile> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
    }

    public PalSphereProjectile(Level pLevel, LivingEntity pShooter) {
        super(ModRegistries.PAL_SPHERE_PROJECTILE.get(), pShooter, pLevel);
    }

    public PalSphereProjectile(Level pLevel, double pX, double pY, double pZ) {
        super(ModRegistries.PAL_SPHERE_PROJECTILE.get(), pX, pY, pZ, pLevel);
    }

    @Override
    protected Item getDefaultItem() {
        return ModRegistries.PAL_SPHERE.get();
    }

    // ── Setup from item ───────────────────────────────────────────────────

    public void setSphereLevel(int level) {
        this.sphereLevel = level;
    }

    public int getWarpTetherLevel() {
        return warpTetherLevel;
    }

    /** Read enchantment levels from the thrown item stack. */
    public void setEnchantments(ItemStack stack) {
        this.infiniteLevel  = EnchantmentHelper.getItemEnchantmentLevel(ModRegistries.ENCHANT_INFINITE.get(), stack);
        this.followingLevel = EnchantmentHelper.getItemEnchantmentLevel(ModRegistries.ENCHANT_FOLLOWING.get(), stack);
        this.levelingLevel  = EnchantmentHelper.getItemEnchantmentLevel(ModRegistries.ENCHANT_LEVELING.get(), stack);
        this.warpTetherLevel = EnchantmentHelper.getItemEnchantmentLevel(ModRegistries.ENCHANT_WARP_TETHER.get(), stack);
    }

    public void setSummoning(boolean isSummoning, UUID sphereUUID) {
        this.isSummoning = isSummoning;
        this.summonSphereUUID = sphereUUID;
    }

    /** Special deploy mode for this summoning throw ("anchor"/"sentry"; "" = combat). */
    public void setDeployMode(String deployMode) {
        this.deployMode = deployMode == null ? "" : deployMode;
    }

    // ── NBT persistence (so enchant data survives chunk reload) ─────────

    @Override
    public void addAdditionalSaveData(CompoundTag pTag) {
        super.addAdditionalSaveData(pTag);
        pTag.putInt("SphereLevel",    sphereLevel);
        pTag.putInt("InfiniteLevel",  infiniteLevel);
        pTag.putInt("FollowingLevel", followingLevel);
        pTag.putInt("LevelingLevel",  levelingLevel);
        pTag.putInt("WarpTetherLevel", warpTetherLevel);
        pTag.putString("DeployMode",  deployMode);
        pTag.putBoolean("IsHoming",   isHoming);
        pTag.putInt("HomingTicks",    homingTicks);
        pTag.putBoolean("IsSummoning", isSummoning);
        if (summonSphereUUID != null) {
            pTag.putUUID("SummonSphereUUID", summonSphereUUID);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag pTag) {
        super.readAdditionalSaveData(pTag);
        sphereLevel   = pTag.getInt("SphereLevel");
        infiniteLevel  = pTag.getInt("InfiniteLevel");
        followingLevel = pTag.getInt("FollowingLevel");
        levelingLevel  = pTag.getInt("LevelingLevel");
        warpTetherLevel = pTag.getInt("WarpTetherLevel");
        deployMode     = pTag.getString("DeployMode");
        isHoming       = pTag.getBoolean("IsHoming");
        homingTicks    = pTag.getInt("HomingTicks");
        isSummoning    = pTag.getBoolean("IsSummoning");
        if (pTag.contains("SummonSphereUUID")) {
            summonSphereUUID = pTag.getUUID("SummonSphereUUID");
        }
    }

    // ── Tick (Following homing logic) ─────────────────────────────────────

    @Override
    public void tick() {
        super.tick();

        // A wild time-stopper (ZA WARUDO) freezes catching spheres in its radius
        if (!level().isClientSide && !isSummoning && (tickCount & 1) == 0
                && com.mx.palmod.pal.WildCatchManager.tryZaWarudoIntercept(this)) {
            return;
        }

        if (!level().isClientSide && isHoming && followingLevel > 0) {
            homingTicks++;
            if (homingTicks > HOMING_MAX_TICKS) {
                discard();
                return;
            }

            double radius = FollowingEnchantment.getHomingRadius(followingLevel);
            LivingEntity nearest = findNearestTarget(radius);
            if (nearest != null) {
                // Steer towards the nearest target
                Vec3 toTarget = nearest.position().add(0, nearest.getBbHeight() * 0.5, 0)
                        .subtract(this.position()).normalize().scale(0.5);
                this.setDeltaMovement(this.getDeltaMovement().add(toTarget));
            } else {
                // Nothing in range — remove after timeout
                if (homingTicks > 20) discard();
            }
        }
    }

    private LivingEntity findNearestTarget(double radius) {
        AABB box = this.getBoundingBox().inflate(radius);
        // Never home onto mobs the sphere would be wasted on
        List<LivingEntity> candidates = level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != getOwner() && !(e instanceof Player) && !e.getPersistentData().contains("PalOwner")
                        && !com.mx.palmod.pal.WildCatchManager.isUncatchable(e)
                        && !e.getPersistentData().getBoolean(com.mx.palmod.pal.WildCatchManager.KEY_HIDDEN));
        return candidates.stream()
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(this)))
                .orElse(null);
    }

    // ── Hit: Entity ───────────────────────────────────────────────────────

    @Override
    protected void onHitEntity(EntityHitResult pResult) {
        if (isSummoning) {
            handleSummoning(pResult.getLocation());
            return;
        }
        super.onHitEntity(pResult);
        if (this.level().isClientSide || !(pResult.getEntity() instanceof LivingEntity target)) return;
        if (target instanceof Player) return;

        // Villagers (and wandering traders) can never be caught
        if (target instanceof net.minecraft.world.entity.npc.Npc) {
            com.mx.palmod.pal.WildCatchManager.message(getOwner(), "Villagers cannot be caught!");
            spawnSmokeAt(target);
            discardOrReturn(false);
            return;
        }

        if (target.getPersistentData().contains("PalOwner")) {
            // Already tamed — can't catch
            spawnSmokeAt(target);
            discardOrReturn(false);
            return;
        }

        // ── Wild-catch gating ────────────────────────────────────────────
        // Hidden predators can't be tagged — the sphere passes through empty air
        // (a miss, so the Infinite enchant may still return it)
        if (target.getPersistentData().getBoolean(com.mx.palmod.pal.WildCatchManager.KEY_HIDDEN)) {
            com.mx.palmod.pal.WildCatchManager.message(getOwner(), "It slipped away before the sphere landed...");
            spawnSmokeAt(target);
            discardOrReturn(false);
            return;
        }
        // Only mobs that rolled catchable (enchanted shimmer) can be caught;
        // a sphere wasted on anything else is simply lost — no Infinite return.
        if (com.mx.palmod.pal.WildCatchManager.isUncatchable(target)) {
            com.mx.palmod.pal.WildCatchManager.message(getOwner(), "No enchanted shimmer — this one can't be caught!");
            spawnSmokeAt(target);
            this.discard();
            return;
        }
        // Wild mobs answer a direct sphere hit with their signature defense move —
        // unless a pal's time stop has them frozen (ZA WARUDO beats ZA WARUDO)
        com.mx.palmod.behavior.PalBehavior wildBehavior =
                com.mx.palmod.behavior.PalBehaviorManager.getBehavior(target.getType());
        if (target instanceof net.minecraft.world.entity.Mob targetMob
                && com.mx.palmod.pal.WildCatchManager.hasWildPower(target)
                && !com.mx.palmod.timestop.TimeStopManager.isFrozen(target)
                && this.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            com.mx.palmod.pal.WildDefenseRegistry.WildDefense defense =
                    com.mx.palmod.pal.WildDefenseRegistry.get(wildBehavior.getWildType());
            if (defense != null && defense.onSphereHit(targetMob, wildBehavior, this, serverLevel)) {
                discardOrReturn(false);
                return;
            }
        }

        // Brawlers charge their BASE health here, not the wild-buffed value
        float maxHealth     = com.mx.palmod.pal.WildCatchManager.getCatchMaxHealth(target);
        float currentHealth = target.getHealth();
        float healthLostPct = ((maxHealth - currentHealth) / maxHealth) * 100f;

        // Effective level including Leveling enchant bonus
        int effectiveLevel = sphereLevel + LevelingEnchantment.getLevelBonus(levelingLevel);

        // rate = effectiveLevel - maxHealth + 0.7 * percentHealthLost
        // (full-HP mobs are deliberately near-uncatchable — weaken the target first)
        float rate = effectiveLevel - maxHealth + 0.7f * healthLostPct;
        float catchChance = Math.max(0f, Math.min(100f, rate)) / 100f;

        if (this.random.nextFloat() <= catchChance) {
            // ── Success ──
            CompoundTag entityData = new CompoundTag();
            if (target.saveAsPassenger(entityData)) {
                // Determine which filled sphere item to give back based on the item that was thrown
                Item filledItem = ModRegistries.FILLED_PAL_SPHERE.get();
                ItemStack filledSphere = new ItemStack(filledItem);
                CompoundTag tag = filledSphere.getOrCreateTag();
                tag.put("CapturedEntity", entityData);
                tag.putUUID("SphereUUID", UUID.randomUUID());
                tag.putBoolean("IsReleased", false);
                tag.putInt("SphereLevel", effectiveLevel);
                if (warpTetherLevel > 0) {
                    // Caught with a WarpTether sphere: recalling the summoned pal
                    // will teleport the owner to its position
                    tag.putBoolean("WarpOnRecall", true);
                }

                dropItem(filledSphere, target);
                target.discard();

                if (this.level() instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                            target.getX(), target.getY() + 1, target.getZ(), 15, 0.5, 0.5, 0.5, 0.1);
                    sl.playSound(null, target.getX(), target.getY(), target.getZ(),
                            SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 1.0F, 1.0F);
                }
            }
            this.discard();
        } else {
            // ── Failure ──
            spawnSmokeAt(target);
            discardOrReturn(true);
        }
    }

    // ── Hit: Block ────────────────────────────────────────────────────────

    @Override
    protected void onHit(HitResult pResult) {
        if (isSummoning) {
            handleSummoning(pResult.getLocation());
            return;
        }
        super.onHit(pResult);
        if (this.level().isClientSide) return;

        if (pResult.getType() == HitResult.Type.BLOCK) {
            if (followingLevel > 0 && !isHoming) {
                // Activate homing mode instead of discarding
                isHoming = true;
                homingTicks = 0;
                // Slow down the sphere so it can steer
                this.setDeltaMovement(this.getDeltaMovement().scale(0.3));
                return; // don't discard yet
            }
            if (this.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.ITEM_SNOWBALL,
                        this.getX(), this.getY(), this.getZ(), 8, 0.0, 0.0, 0.0, 0.0);
            }
            discardOrReturn(false);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /** Discard the sphere, or return it to the owner if Infinite enchant fires. */
    private void discardOrReturn(boolean wasCatchFail) {
        if (infiniteLevel > 0) {
            float returnChance = InfiniteEnchantment.getReturnChance(infiniteLevel);
            if (this.random.nextFloat() <= returnChance) {
                returnSphereToOwner();
                return;
            }
        }
        this.discard();
    }

    private void returnSphereToOwner() {
        if (getOwner() instanceof Player player) {
            ItemStack sphere = getItem();
            if (!sphere.isEmpty()) {
                ItemStack toReturn = sphere.copy();
                toReturn.setCount(1);
                if (!player.getInventory().add(toReturn)) {
                    dropItem(toReturn, player);
                }
                if (this.level() instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.ENCHANT,
                            this.getX(), this.getY(), this.getZ(), 8, 0.3, 0.3, 0.3, 0.05);
                }
            }
        }
        this.discard();
    }

    private void spawnSmokeAt(LivingEntity target) {
        if (this.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.SMOKE,
                    target.getX(), target.getY() + 1, target.getZ(), 10, 0.5, 0.5, 0.5, 0.1);
            sl.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.GLASS_BREAK, SoundSource.NEUTRAL, 1.0F, 1.0F);
        }
    }

    private void dropItem(ItemStack stack, net.minecraft.world.entity.Entity near) {
        ItemEntity ie = new ItemEntity(level(), near.getX(), near.getY(), near.getZ(), stack);
        level().addFreshEntity(ie);
    }

    private void handleSummoning(Vec3 location) {
        if (this.level().isClientSide || !(this.level() instanceof ServerLevel serverLevel)) return;
        if (!(getOwner() instanceof Player player)) {
            this.discard();
            return;
        }
        
        ItemStack filledSphere = null;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() == ModRegistries.FILLED_PAL_SPHERE.get()) {
                CompoundTag tag = stack.getTag();
                if (tag != null && tag.contains("SphereUUID") && tag.getUUID("SphereUUID").equals(summonSphereUUID)) {
                    filledSphere = stack;
                    break;
                }
            }
        }
        if (filledSphere == null) {
            ItemStack offhand = player.getOffhandItem();
            if (offhand.getItem() == ModRegistries.FILLED_PAL_SPHERE.get()) {
                CompoundTag tag = offhand.getTag();
                if (tag != null && tag.contains("SphereUUID") && tag.getUUID("SphereUUID").equals(summonSphereUUID)) {
                    filledSphere = offhand;
                }
            }
        }
        
        if (filledSphere != null) {
            CompoundTag tag = filledSphere.getTag();
            // The throw landed — release the anti-double-summon lock
            tag.remove("SummonLockUntil");
            CompoundTag entityData = tag.getCompound("CapturedEntity");
            java.util.Optional<EntityType<?>> optionalType = EntityType.by(entityData);
            if (optionalType.isPresent()) {
                EntityType<?> entityType = optionalType.get();
                com.mx.palmod.behavior.PalBehavior summonBehavior =
                        com.mx.palmod.behavior.PalBehaviorManager.getBehavior(entityType);
                // Warp beacon: a plain summon roots the pal as a waystone (recall
                // warps the owner back to it) — reuse the anchor deploy machinery.
                if (deployMode.isEmpty() && summonBehavior.isWarpBeacon()) {
                    deployMode = "anchor";
                }
                net.minecraft.world.entity.Entity entity = entityType.create(serverLevel);
                if (entity instanceof LivingEntity livingEntity) {
                    livingEntity.load(entityData);
                    livingEntity.setUUID(UUID.randomUUID());
                    // An ex-station pal (station broken, sphere recovered) still
                    // carries WorkStationPos in its saved ForgeData — stale here,
                    // and it would get the pal auto-discarded by the orphan check
                    livingEntity.getPersistentData().remove("WorkStationPos");
                    livingEntity.setYRot(player.getYRot());
                    // Impact points sit on block faces — find a collision-free spot
                    com.mx.palmod.pal.SafeSpawn.place(serverLevel, livingEntity, location, player);
                    if (livingEntity.getCustomName() == null) {
                        livingEntity.setCustomName(entityType.getDescription().copy());
                    }
                    livingEntity.setCustomNameVisible(true);
                    livingEntity.getPersistentData().putUUID("PalOwner", player.getUUID());
                    livingEntity.getPersistentData().putUUID("SphereUUID", summonSphereUUID);
                    if (!deployMode.isEmpty()) {
                        // Special deployment: the pal roots where it landed. AnchorPos
                        // is also stored on the sphere so recall-warp works even when
                        // the pal's chunk is unloaded.
                        long anchorPos = livingEntity.blockPosition().asLong();
                        livingEntity.getPersistentData().putString("DeployMode", deployMode);
                        livingEntity.getPersistentData().putLong("AnchorPos", anchorPos);
                        if ("anchor".equals(deployMode)) {
                            livingEntity.getPersistentData().putBoolean("PalSitting", true);
                        }
                        tag.putString("DeployMode", deployMode);
                        tag.putLong("AnchorPos", anchorPos);
                        tag.putString("AnchorDim", serverLevel.dimension().location().toString());
                    } else {
                        // Normal summon: scrub any deployment state left over from a
                        // previous anchor/sentry cycle (it rides along in ForgeData)
                        livingEntity.getPersistentData().remove("DeployMode");
                        livingEntity.getPersistentData().remove("AnchorPos");
                        livingEntity.getPersistentData().putBoolean("PalSitting", false);
                        tag.remove("DeployMode");
                        tag.remove("AnchorPos");
                        tag.remove("AnchorDim");
                    }
                    if (livingEntity instanceof net.minecraft.world.entity.Mob m) {
                        m.setPersistenceRequired();
                    }
                    serverLevel.addFreshEntity(livingEntity);
                    tag.putBoolean("IsReleased", true);
                    tag.putUUID("EntityUUID", livingEntity.getUUID());

                    // Warp beacon: mark the sphere so a deliberate recall warps
                    // the owner to it (anchor deploy already implies this, but be
                    // explicit for the warp_beacon flag path).
                    if (summonBehavior.isWarpBeacon()) {
                        tag.putBoolean("WarpOnRecall", true);
                    }
                    // ZA WARUDO on summon: freeze around where the pal just landed.
                    if (summonBehavior.isTimeStopOnSummon()
                            && livingEntity instanceof net.minecraft.world.entity.Mob tsPal
                            && player instanceof net.minecraft.server.level.ServerPlayer tsCaster) {
                        com.mx.palmod.timestop.TimeStopManager.tryActivateOnSummon(
                                tsCaster, serverLevel, tsPal, tag, summonBehavior);
                    }

                    serverLevel.sendParticles(ParticleTypes.POOF, location.x, location.y + 1, location.z, 15, 0.5, 0.5, 0.5, 0.1);
                    serverLevel.playSound(null, location.x, location.y, location.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 1.0F, 1.0F);
                }
            }
        }
        this.discard();
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}

