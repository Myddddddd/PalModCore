package com.mx.palmod.behavior;

public class PalBehavior {
    private boolean followOwner = true;
    private double followSpeed = 1.0D;
    private float startDistance = 15.0F;
    private float stopDistance = 6.0F;
    
    private boolean protectOwner = true;
    private boolean attackTarget = true;

    // Guard-only mode: retaliate against the owner's attacker, never initiate
    private boolean guardOnly = false;
    private float guardLeash = 12.0F;

    // Special deploy mode on sneak-throw: "" (none), "anchor", "sentry"
    private String deployMode = "";
    private float sentryRadius = 16.0F;

    // Mobile chest ("pouch") size in slots; 0 = no pouch
    private int mobileChestSize = 0;
    // Miner: which block tag counts as ore
    private String oreTag = "forge:ores";

    // ZA WARUDO — time stop ultimate (0 duration = the pal can't stop time)
    private int timeStopDurationTicks = 0;
    private float timeStopRadius = 24.0F;
    private int timeStopCooldownTicks = 1200;
    private float timeStopHungerCost = 40.0F;
    // When true, the time stop fires automatically on summon (centered on the
    // pal) instead of via a manual trigger — the player picks the moment by
    // choosing when/where to throw the sphere.
    private boolean timeStopOnSummon = false;

    // Warp beacon: on normal summon the pal roots where thrown and stamps the
    // sphere so a deliberate recall warps the owner to it (a mobile waystone).
    private boolean warpBeacon = false;

    // Clone/swarm: a small fighter splits into temporary copies that pile onto
    // its target. 0 = off.
    private int cloneMax = 0;
    private int cloneDurationTicks = 200;
    private int cloneCooldownTicks = 300;
    private float cloneHungerCost = 4.0F;

    // Combat ability cast ("" = none): lightning / fireball / earth_spike / water_burst
    private String combatAbilityType = "";
    private float combatDamage = 5.0F;
    private float combatRange = 12.0F;
    private int combatCooldownTicks = 60;
    private float combatHungerCost = 2.0F;

    // Rideable mount (steered by rider look; swims)
    private boolean rideable = false;
    private double rideSpeed = 1.2D;

    // Fetch: sneak-click with a sample item -> pal fetches it from a nearby chest
    private boolean canFetch = false;
    private int fetchRadius = 16;

    // XP collector: absorbs nearby XP orbs; sneak-click empty hand extracts bottles
    private float xpCollectRadius = 0.0F; // 0 = off
    private int xpMaxStored = 1395;

    // Item magnet: pulls nearby drops into the pal's pouch
    private float magnetRadius = 0.0F; // 0 = off
    private int magnetSlots = 6;

    // Greedy boom: at boom_at_hunger the pal explodes and mimics a recent kill
    private boolean greedyBoom = false;
    private float boomAtHunger = 95.0F;

    private boolean meleeAttack = false;
    private double meleeAttackSpeed = 1.0D;

    // Station mode
    private boolean stationMode = false;
    private String stationWorkerType = "harvester";
    private int harvestRadius = 8;
    private int wanderRadius = 32;
    // Lumberjack: max logs felled from a single tree
    private int logCap = 64;

    // Owner aura ("" = none). Pulses every auraIntervalTicks while the Pal is
    // summoned, fed, and within auraRadius of its owner.
    private String auraType = "";
    private float auraRadius = 8.0f;
    private int auraIntervalTicks = 100;
    private int auraPotency = 0;
    private float auraHungerCost = 1.0f;
    // Aura-specific numeric extras (storm_cooldown_ticks, storm_damage, ...)
    private final java.util.Map<String, Float> auraParams = new java.util.HashMap<>();

    // Hunger decay (server-side tick rate)
    // hungerDecayPerMinute: how many hunger points are lost per real-world minute
    private float hungerDecayPerMinute = 5.0f;
    // moodDecayPerDay: how many mood points are lost per Minecraft day (24000 ticks)
    private float moodDecayPerDay = 2.0f;

    // Per-action hunger costs (extensible map; keys match hunger_costs JSON keys)
    private final java.util.Map<String, Float> hungerCosts = new java.util.HashMap<>();

    // ── Wild-catch difficulty ("" = plain wild mob) ───────────────────────
    // category: brawler / skittish / op / predator_locked
    private String wildCategory = "";
    // type: op → time_stop / storm_dodge / blink;
    //       predator_locked → damage_immune / shy_hide / ambush_hide
    private String wildType = "";
    // damage_immune: only an owned pal with this combat_ability can hurt it
    private String wildCounterAbility = "";
    // skittish detection & escape
    private float wildSightRange = 32.0f;
    private float wildHearingRange = 20.0f;
    private double wildFleeSpeed = 1.4D;
    // brawler buffs (resistance -1 = none; amplifier 0 = Resistance I)
    private float wildHealthMultiplier = 1.5f;
    private int wildResistance = -1;
    private boolean wildUseCombatAbility = false;
    // op numbers (time_stop intercept radius + recharge)
    private float wildOpRadius = 24.0f;
    private int wildOpCooldownTicks = 600;
    // hide/lure ranges for shy_hide & ambush_hide
    private float wildHideRange = 16.0f;
    private float wildLureRange = 8.0f;

    public PalBehavior() {
    }

    public boolean isFollowOwner() {
        return followOwner;
    }

    public void setFollowOwner(boolean followOwner) {
        this.followOwner = followOwner;
    }

    public double getFollowSpeed() {
        return followSpeed;
    }

    public void setFollowSpeed(double followSpeed) {
        this.followSpeed = followSpeed;
    }

    public float getStartDistance() {
        return startDistance;
    }

    public void setStartDistance(float startDistance) {
        this.startDistance = startDistance;
    }

    public float getStopDistance() {
        return stopDistance;
    }

    public void setStopDistance(float stopDistance) {
        this.stopDistance = stopDistance;
    }

    public boolean isProtectOwner() {
        return protectOwner;
    }

    public void setProtectOwner(boolean protectOwner) {
        this.protectOwner = protectOwner;
    }

    public boolean isAttackTarget() {
        return attackTarget;
    }

    public void setAttackTarget(boolean attackTarget) {
        this.attackTarget = attackTarget;
    }

    public boolean isGuardOnly() { return guardOnly; }
    public void setGuardOnly(boolean guardOnly) { this.guardOnly = guardOnly; }

    public float getGuardLeash() { return guardLeash; }
    public void setGuardLeash(float guardLeash) { this.guardLeash = guardLeash; }

    public String getDeployMode() { return deployMode; }
    public void setDeployMode(String deployMode) { this.deployMode = deployMode; }

    public float getSentryRadius() { return sentryRadius; }
    public void setSentryRadius(float sentryRadius) { this.sentryRadius = sentryRadius; }

    public int getMobileChestSize() { return mobileChestSize; }
    public void setMobileChestSize(int mobileChestSize) { this.mobileChestSize = mobileChestSize; }

    public String getOreTag() { return oreTag; }
    public void setOreTag(String oreTag) { this.oreTag = oreTag; }

    public int getTimeStopDurationTicks() { return timeStopDurationTicks; }
    public void setTimeStopDurationTicks(int v) { this.timeStopDurationTicks = v; }

    public float getTimeStopRadius() { return timeStopRadius; }
    public void setTimeStopRadius(float v) { this.timeStopRadius = v; }

    public int getTimeStopCooldownTicks() { return timeStopCooldownTicks; }
    public void setTimeStopCooldownTicks(int v) { this.timeStopCooldownTicks = v; }

    public float getTimeStopHungerCost() { return timeStopHungerCost; }
    public void setTimeStopHungerCost(float v) { this.timeStopHungerCost = v; }

    public boolean isTimeStopOnSummon() { return timeStopOnSummon; }
    public void setTimeStopOnSummon(boolean v) { this.timeStopOnSummon = v; }

    public boolean isWarpBeacon() { return warpBeacon; }
    public void setWarpBeacon(boolean v) { this.warpBeacon = v; }

    public int getCloneMax() { return cloneMax; }
    public void setCloneMax(int v) { this.cloneMax = v; }

    public int getCloneDurationTicks() { return cloneDurationTicks; }
    public void setCloneDurationTicks(int v) { this.cloneDurationTicks = v; }

    public int getCloneCooldownTicks() { return cloneCooldownTicks; }
    public void setCloneCooldownTicks(int v) { this.cloneCooldownTicks = v; }

    public float getCloneHungerCost() { return cloneHungerCost; }
    public void setCloneHungerCost(float v) { this.cloneHungerCost = v; }

    public String getCombatAbilityType() { return combatAbilityType; }
    public void setCombatAbilityType(String v) { this.combatAbilityType = v; }

    public float getCombatDamage() { return combatDamage; }
    public void setCombatDamage(float v) { this.combatDamage = v; }

    public float getCombatRange() { return combatRange; }
    public void setCombatRange(float v) { this.combatRange = v; }

    public int getCombatCooldownTicks() { return combatCooldownTicks; }
    public void setCombatCooldownTicks(int v) { this.combatCooldownTicks = v; }

    public float getCombatHungerCost() { return combatHungerCost; }
    public void setCombatHungerCost(float v) { this.combatHungerCost = v; }

    public boolean isRideable() { return rideable; }
    public void setRideable(boolean v) { this.rideable = v; }

    public double getRideSpeed() { return rideSpeed; }
    public void setRideSpeed(double v) { this.rideSpeed = v; }

    public boolean isCanFetch() { return canFetch; }
    public void setCanFetch(boolean v) { this.canFetch = v; }

    public int getFetchRadius() { return fetchRadius; }
    public void setFetchRadius(int v) { this.fetchRadius = v; }

    public float getXpCollectRadius() { return xpCollectRadius; }
    public void setXpCollectRadius(float v) { this.xpCollectRadius = v; }

    public int getXpMaxStored() { return xpMaxStored; }
    public void setXpMaxStored(int v) { this.xpMaxStored = v; }

    public float getMagnetRadius() { return magnetRadius; }
    public void setMagnetRadius(float v) { this.magnetRadius = v; }

    public int getMagnetSlots() { return magnetSlots; }
    public void setMagnetSlots(int v) { this.magnetSlots = v; }

    public boolean isGreedyBoom() { return greedyBoom; }
    public void setGreedyBoom(boolean v) { this.greedyBoom = v; }

    public float getBoomAtHunger() { return boomAtHunger; }
    public void setBoomAtHunger(float v) { this.boomAtHunger = v; }

    public boolean isMeleeAttack() {
        return meleeAttack;
    }

    public void setMeleeAttack(boolean meleeAttack) {
        this.meleeAttack = meleeAttack;
    }

    public double getMeleeAttackSpeed() {
        return meleeAttackSpeed;
    }

    public void setMeleeAttackSpeed(double meleeAttackSpeed) {
        this.meleeAttackSpeed = meleeAttackSpeed;
    }

    public boolean isStationMode() { return stationMode; }
    public void setStationMode(boolean stationMode) { this.stationMode = stationMode; }

    public String getStationWorkerType() { return stationWorkerType; }
    public void setStationWorkerType(String stationWorkerType) { this.stationWorkerType = stationWorkerType; }

    public int getHarvestRadius() { return harvestRadius; }
    public void setHarvestRadius(int harvestRadius) { this.harvestRadius = harvestRadius; }

    public int getWanderRadius() { return wanderRadius; }
    public void setWanderRadius(int wanderRadius) { this.wanderRadius = wanderRadius; }

    public int getLogCap() { return logCap; }
    public void setLogCap(int logCap) { this.logCap = logCap; }

    public String getAuraType() { return auraType; }
    public void setAuraType(String auraType) { this.auraType = auraType; }

    public float getAuraRadius() { return auraRadius; }
    public void setAuraRadius(float auraRadius) { this.auraRadius = auraRadius; }

    public int getAuraIntervalTicks() { return auraIntervalTicks; }
    public void setAuraIntervalTicks(int auraIntervalTicks) { this.auraIntervalTicks = auraIntervalTicks; }

    public int getAuraPotency() { return auraPotency; }
    public void setAuraPotency(int auraPotency) { this.auraPotency = auraPotency; }

    public float getAuraHungerCost() { return auraHungerCost; }
    public void setAuraHungerCost(float auraHungerCost) { this.auraHungerCost = auraHungerCost; }

    public float getAuraParam(String key, float defaultValue) {
        return auraParams.getOrDefault(key, defaultValue);
    }

    public void setAuraParam(String key, float value) { auraParams.put(key, value); }

    public float getHungerDecayPerMinute() { return hungerDecayPerMinute; }
    public void setHungerDecayPerMinute(float v) { this.hungerDecayPerMinute = v; }

    public float getMoodDecayPerDay() { return moodDecayPerDay; }
    public void setMoodDecayPerDay(float v) { this.moodDecayPerDay = v; }

    /** Generic per-action hunger cost lookup; key matches the hunger_costs JSON key. */
    public float getHungerCost(String action, float defaultCost) {
        return hungerCosts.getOrDefault(action, defaultCost);
    }

    public void setHungerCost(String action, float cost) { hungerCosts.put(action, cost); }

    public float getHungerCostHarvest() { return getHungerCost("harvest", 2.0f); }
    public void setHungerCostHarvest(float v) { setHungerCost("harvest", v); }

    public float getHungerCostAttack() { return getHungerCost("attack", 3.0f); }
    public void setHungerCostAttack(float v) { setHungerCost("attack", v); }

    public float getHungerCostMovePerBlock() { return getHungerCost("move_per_block", 0.05f); }
    public void setHungerCostMovePerBlock(float v) { setHungerCost("move_per_block", v); }

    public String getWildCategory() { return wildCategory; }
    public void setWildCategory(String v) { this.wildCategory = v; }

    public String getWildType() { return wildType; }
    public void setWildType(String v) { this.wildType = v; }

    public String getWildCounterAbility() { return wildCounterAbility; }
    public void setWildCounterAbility(String v) { this.wildCounterAbility = v; }

    public float getWildSightRange() { return wildSightRange; }
    public void setWildSightRange(float v) { this.wildSightRange = v; }

    public float getWildHearingRange() { return wildHearingRange; }
    public void setWildHearingRange(float v) { this.wildHearingRange = v; }

    public double getWildFleeSpeed() { return wildFleeSpeed; }
    public void setWildFleeSpeed(double v) { this.wildFleeSpeed = v; }

    public float getWildHealthMultiplier() { return wildHealthMultiplier; }
    public void setWildHealthMultiplier(float v) { this.wildHealthMultiplier = v; }

    public int getWildResistance() { return wildResistance; }
    public void setWildResistance(int v) { this.wildResistance = v; }

    public boolean isWildUseCombatAbility() { return wildUseCombatAbility; }
    public void setWildUseCombatAbility(boolean v) { this.wildUseCombatAbility = v; }

    public float getWildOpRadius() { return wildOpRadius; }
    public void setWildOpRadius(float v) { this.wildOpRadius = v; }

    public int getWildOpCooldownTicks() { return wildOpCooldownTicks; }
    public void setWildOpCooldownTicks(int v) { this.wildOpCooldownTicks = v; }

    public float getWildHideRange() { return wildHideRange; }
    public void setWildHideRange(float v) { this.wildHideRange = v; }

    public float getWildLureRange() { return wildLureRange; }
    public void setWildLureRange(float v) { this.wildLureRange = v; }
}
