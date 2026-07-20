# Changelog

All notable changes to Palmod are documented in this file. Format loosely follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versioning follows
[SemVer](https://semver.org/) with a pre-`1.0.0` "beta" understanding — breaking changes to
datapack schemas or save data may still happen between minor versions until `1.0.0`.

## [0.9.0] - 2026-07-20

A survival-pressure release: the wild is denser and far deadlier, catching is a real
weaken-then-catch fight (tanky mobs finally catchable), pals recover between fights, and nights
can turn into a hunt. All numbers are config-driven in `palmod-common.toml` (restart to apply).

### Added

- **Well-fed self-heal for pals** (`ForgeEvents.tryWellFedHeal`). A non-deployed pal at or above
  `palHealWellFedThreshold` (80) hunger regenerates health, scaling with fullness and mood, up to
  `palHealPerSecond` (2) HP/s and burning `palHealHungerCostPerHp` (0.5) hunger per HP so it
  self-limits. Per-mob override via `stats.heal_per_second`. Guarded against reviving a pal that is
  mid-death-animation.
- **Wild toughness for every catchable mob** (`WildCatchManager.applyWildToughness`). Catchable
  wild mobs become bruisers: `wildHealthMultiplier` (×4.5 HP), `wildDamageMultiplier` (×2.0 attack
  where present), and self-regen (`wildRegenFractionPerSecond`, suppressed `wildRegenSuppressTicks`
  after a hit) — so the fight to weaken a mob is the real challenge. Stripped on capture, so a
  tamed pal keeps its true base stats and never summons near-death.
- **Hunting Night event** (`hunt.HuntingNightManager` + `ai.HuntNightAttackGoal`). Each night has a
  `huntNightChance` (0.10) to become a hunt: a warning broadcasts ~5 min before dusk
  (`huntWarningLeadTicks`), then every mob — even normally-passive ones — turns on players (passives
  deal zombie-level `huntMinDamage`, attackers deal `×huntDamageMultiplier`), extra mobs spawn in
  waves (`huntSpawnPool`) around players, and at dawn the hunt ends and its spawns despawn. Fully
  config-driven (`huntEnabled` master switch).
- **Biome-distributed spawns for the Alex's Mobs roster** (`data/palmod/forge/biome_modifier/`,
  17× `forge:add_spawns`). The shipped roster is spread across biome groups (grizzly→taiga,
  capuchin/gorilla/ant→jungle, orca→cold ocean, mungus→mushroom, End/Nether mobs in their realms,
  …) at moderate weights so no region is empty and species must be sought out.
- **Feeder crafting recipe** (`recipes/pal_feeder.json` — planks ring + hopper); the feeder was
  previously uncraftable. (The work station stays deliberately pal-bound-only, never a standalone
  item.)

### Changed

- **Catch formula rebuilt to a fraction-based curve** (`PalSphereProjectile`). Replaces the old
  `level − maxHP + 0.7×%lost` subtraction — which made high-HP mobs (Warden, iron golem, …)
  uncatchable even at ~0 HP — with `catchLowHpMaxRate × weaken^p`, where the toughness exponent `p`
  grows with base max health. Guarantees: every mob reaches ~`catchLowHpMaxRate` (0.90) at/below
  `catchFullRateHpFraction` (~5% HP), tankier mobs are strictly harder at mid HP but never a hard
  0%, and full-HP mobs stay near-0% (weaken-first preserved). Charges wild-toughness-stripped base
  HP so the ×4.5 buff never compounds catch difficulty. Verified in-game by catching a Warden.
- **`catchableChance` default 0.40 → 0.65** — more of the wild is catchable.
- **Cheaper sphere recipes**: `pal_sphere` = 4 iron + 1 redstone; `pal_sphere_mid` = 4 iron + 1
  redstone block; `pal_sphere_advanced` = 8 iron + 1 redstone block (was 8-iron / gold / diamond
  rings).

## [0.8.1] - 2026-07-14

### Added

- **Addon extensibility overhaul.** Palmod is now built to be extended by third-party mods
  without ever touching its source:
  - `CombatAbilityRegistry` and `WildDefenseRegistry` replace the hardcoded
    `combat_ability.type` switch and the `wild.type` (op/predator_locked) if-chains — a new
    ability or defense is one `register(...)` call.
  - `PalAbilityRegistry` converts the "one proactive power per mob" family (clone, fetch,
    magnet, xp_collector, greedy_boom, warp_beacon, time_stop.on_summon) from scattered
    if-chains into a single registry with an `appliesTo`/`onJoin`/`tick`/`onSummon`/
    `forcedDeployMode` hook interface.
  - New `com.mx.palmod.api.event` package: `PalCaughtEvent` (cancelable), `PalSummonedEvent`,
    `PalRecalledEvent`, `PalDiedEvent`, `PalDeployedEvent` — standard Forge events any addon
    can subscribe to for a pal's core lifecycle moments.
  - Deliberately left alone: deploy mode (anchor/sentry) selection, `wild.category`, and
    interact-click routing — dense, stable UX/dispatch logic where the regression risk
    outweighed the addon payoff.
- Mod logo (`palmod.png`, `mods.toml` `logoFile`).

## [0.8.0] - 2026-07-14

First tracked release. Palmod's core loop — catch, own, care for, and deploy creatures as
real-time companions — is feature-complete on Forge 1.20.1 against Alex's Mobs + Citadel.

### Added

**Catching & Spheres**
- Wild-catch roll on mob spawn (config-controlled chance), with an enchant-glint shimmer
  marking catchable mobs instead of a through-wall glow outline
- HP-based catch-rate formula (sphere tier + Leveling level − max HP + 0.7 × % health lost)
  so full-health targets are a deliberate near-miss and weakening a target first matters
- Three sphere tiers (Pal Sphere / Mid / Advanced) plus five enchantments: Leveling,
  Infinite, FastBall, Following, Warp Tether
- Close-range interact-based throws (right-click directly on a mob) alongside ranged throws,
  with double-throw protection against modded-client packet duplication

**Pal ownership & lifecycle**
- Summon-by-throw / recall-by-right-click sphere lifecycle with full mob NBT snapshotting
- Three distinct visual sphere states (empty / filled+inside / filled+released) including a
  client-side egg tint on filled spheres sourced from each mob's spawn egg
- Orphan rule: released Pals despawn without their sphere in hand, except station-bound,
  deployed, or currently-ridden Pals
- Owner interact routing (fetch, storage, XP collection, hand-feeding, mounting, sit/stand)
  driven entirely by each Pal's datapack-defined capabilities

**Care**
- Hunger/Mood stat system with per-mob decay rates, tiered slowdown, and self-recovery even
  while critically hungry (crawl-to-food instead of freezing)
- Pal Feeder block (hopper-fed) and per-mob food tables for hand-feeding
- Self-feeding station producers and owner-priority hand-feeding for station-bound Pals

**Work Stations**
- Pal Work Station block with sphere-bound worker placement, hopper-pushed output, and
  automatic filled-sphere return if a worker goes missing
- Five worker jobs out of the box: harvester, lumberjack, trader, sorter, miner — with a
  one-file extension point (`WorkerGoalRegistry`) for adding more

**Powers & deploy modes**
- One proactive special power per Pal (no manual ability buttons): elemental combat casts,
  summon-triggered time stop, recall-triggered warp beacon, sentry/anchor deploy modes,
  swarm cloning, item magnet auto-delivery, XP collection, heal/storm auras, mountable rides
- Deployed Pals (anchor/sentry) skip hunger/mood decay entirely

**Wild difficulty**
- Four wild-catch difficulty categories for catchable mobs: brawler, skittish, op (time
  stop / storm dodge / blink), and predator-locked (damage immune / shy hide / ambush hide)
- Pal Compass (craftable) for locating the nearest catchable mob within 128 blocks

**Paldex & in-game docs**
- Standalone Paldex book item opening a full in-game guide inside Alex's Mobs' own dictionary
  book UI: guide pages for every major system plus a bestiary with a Pal Info page per shipped
  creature (20 pals across brawler/skittish/op/predator-locked/support roles)

**Server & compatibility**
- `catchableChance` and `allowVanillaClients` config options; the latter patches Forge's
  network handshake so vanilla clients and protocol bots can still join a modded server

### Known issues

See [README.md § Known issues](README.md#known-issues) and [CLAUDE.md](CLAUDE.md) for the full,
continually-updated technical list. Highlights: some Alex's Mobs creatures with strong native
AI (elephant, tasmanian devil) partially resist hand-feed/target routing; flying mobs are hard
to catch with a thrown sphere; the wild-catch roll currently fires on bred/egg-hatched mobs too,
not just natural spawns; orca riding and fireball/water-burst casts are implemented but not yet
confirmed on a live client.
