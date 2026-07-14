# Changelog

All notable changes to Palmod are documented in this file. Format loosely follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versioning follows
[SemVer](https://semver.org/) with a pre-`1.0.0` "beta" understanding — breaking changes to
datapack schemas or save data may still happen between minor versions until `1.0.0`.

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
