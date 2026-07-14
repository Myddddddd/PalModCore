---
name: palmod-dev
description: Build, deploy, and in-game-test the Palmod Forge mod on the MCMCP dedicated server using the minecraft MCP bot + RCON. Use whenever working on Palmod features, fixing bugs, or verifying mod behavior in a live world ("test mod", "deploy", "kiểm tra in-game").
---

# Palmod dev & in-game test workflow

Architecture/config reference lives in [CLAUDE.md](../../CLAUDE.md). This skill is the
operational loop and the field-tested gotchas.

## The loop

1. Edit code → `.\gradlew.bat build` **in the background** (~2-3 min; bundled JDK is
   auto-configured). Compile errors surface here — fix before touching the server.
2. `stop-server` (MCP tool) → copy `build\libs\palmod-1.0.0.jar` to
   `C:\Users\MX\Desktop\MCMCP\mcp-forge-server\mods\` → `start-server`.
3. Verify load: grep `logs\latest.log` for `Loaded N Pal behaviors` / `ERROR`.
   `allowVanillaClients: patched N network channel(s)` must appear or the bot can't join.
4. Any bot tool (e.g. `get-position`) reconnects **ClaudeBot** automatically.
5. Drive the bot for actions; **verify with RCON, never with the bot's client view.**

Consider spawning a code-review agent on the changed files in parallel with the build —
review rounds on this project have caught 5 blockers and 18+ majors pre-deploy.

## RCON — the source of truth

Port `25575`, password `mcmcp`. Helper: copy [rcon.mjs](rcon.mjs) to the scratchpad, then:

```bash
node rcon.mjs "data get entity @e[type=alexsmobs:gorilla,limit=1] ForgeData" "list"
```

- Pal state lives in entity `ForgeData`: `PalOwner`, `PalHunger`, `PalMood`, `PalSitting`,
  `SphereUUID`, `WorkStationPos`, `DeployMode`, `AnchorPos`, `PalStoredXp`, `PalChestItems`, `BoomPrimed`.
- `@e[type=X,limit=1]` picks an ARBITRARY entity — wild ones included. Target by
  `name=` (CustomName) or `execute positioned <x y z> run data get entity @e[...,sort=nearest,limit=1,distance=..15]`.
- Station/chest contents: `data get block <x y z> Inventory` (or `Items` for chests).
- The bot's op comes from `ops.json` (offline UUID `313c66c6-e732-3e25-9fbc-ee84b2e331bc`);
  `send-chat "/give ..."` works but returns no output — use RCON when you need the response.

## Bot control patterns

- Modded items/entities show as **"unknown"** in bot tools — drive spheres by
  `select-hotbar-slot` + `use-item`, target mobs by numeric id from `list-nearby-entities`
  (match against RCON positions).
- **Sneak interactions**: `set-control-state sneak true` → **do one RCON round-trip to let
  the sneak state sync** → `use-item`/`use-item-on-entity` → sneak false. Skipping the sync
  makes the server see a plain click (sit-toggle fires instead).
- Keep the sphere in the **offhand** (`item replace entity ClaudeBot weapon.offhand ...`)
  when a test needs sphere-activation + immediate weapon use — no hotbar switching.
- MCP tool round-trips are 5-8s each. **Timed mechanics need generous test windows**
  (e.g. time-stop tested at `duration_ticks: 1200`). Budget the whole click sequence.
- mineflayer's inventory view desyncs after RCON `item replace` ("holding: nothing") —
  trust RCON `data get entity ClaudeBot Inventory`, not the client.
- Keep the bot in **creative** unless a test needs survival; a hostile test mob WILL kill
  a survival bot while you read logs (it has happened — full inventory scatter).
- **Aim before `use-item-on-entity`**: the interact packet doesn't align the bot's server-side
  rotation, and thrown-sphere logic launches along the CURRENT look vector. `look-at` the target
  right before the click, and re-aim after ANY bot movement (walking rewrites yaw). Short mobs
  (pig 0.9, ant 0.2) need the look point at their body height or point-blank throws sail overhead.
- **The bot cannot reproduce real-client packet flows**: mineflayer sends a single interact (or
  use) packet, but a REAL modded client whose client-side interaction returns PASS follows the
  interact packet with a `ServerboundUseItemPacket` — interact-event handlers that only cancel
  server-side will double-fire `Item.use()`. Guard with client-side event cancel + item cooldown
  (see ForgeEvents.suppressFollowUpUse), and treat "bot test passed" as NOT covering this path.
- Rerolling wild-catch: kill/summon in a loop and check `ForgeData.PalCatchable` per spawn
  (40% default) — but killed test mobs drop loot (porkchops etc.) that pollutes later
  item-drop assertions; `kill @e[type=item]` between phases.

## Catching pals for tests

- Guaranteed catch: `/give` an advanced sphere with
  `{Enchantments:[{id:"palmod:leveling",lvl:200s},{id:"palmod:fastball",lvl:1s}]}` —
  effective level 210 ⇒ 100% on any mob.
- Ground mobs: RCON `effect give <mob> slowness 15 255 true` + `tp` it 3-4 blocks in front,
  then `look-at` + `use-item`. Check line of sight — leftover test structures eat throws.
- **Flyers dodge everything.** Skip the throw and craft the filled sphere directly:
  ```
  /give ClaudeBot palmod:filled_pal_sphere{IsReleased:0b,SphereLevel:210,SphereUUID:[I;1,2,3,4],CapturedEntity:{id:"alexsmobs:crow",Health:8.0f,PersistenceRequired:1b}} 1
  ```
  ⚠️ Minimal NBT kills mobs with internal timers (guster dissipates instantly). For those,
  catch a real one or don't use them at all.
- Survival throws consume spheres; the caught sphere drops WITH RANDOM MOTION (can bounce
  off pad edges into the void below) and auto-pickup fills the first empty slot —
  track slots via RCON.

## Fast iteration without rebuilding

Behavior/food/trade JSONs are datapack-reloadable: write overrides to
`mcp-forge-server\world\datapacks\palmod-test\data\palmod\pal_behaviors\*.json`
(pack.mcmeta pack_format 15 already exists there) + RCON `/reload`. World datapack
beats the jar's copy of the same file — used for test-tuned time-stop durations and
for testing goals on vanilla mobs (wolf = earth_spike, pillager = sentry, silverfish = miner).

## Test recipes (all previously proven)

- **Worker**: catch mob → `activate-block` on ground with the filled sphere selected →
  station appears + `ForgeData.WorkStationPos` set → seed inputs (`data merge block`, or
  drop payment items for the trader) → poll `data get block ... Inventory`.
- **Aura**: summon pal → `/damage ClaudeBot 10` (survival) → check bot `ActiveEffects`
  within one interval; hunger must only drain when the pulse does work.
- **Combat cast**: summon caster pal + a named tanky husk
  (`Attributes:[{Name:"minecraft:generic.max_health",Base:200.0}],Health:200f`; husks don't
  burn in daylight, zombies do) → bot (survival) pokes the husk with a stick → pal targets
  it via PalAttackOwnerTargetGoal → verify damage/hunger/blocks. **AM mobs with anger AI
  (elephant, devil) ignore fed targets — verify casts on a vanilla wolf via world datapack.**
- **Deploy modes**: sneak-throw = anchor/sentry (`DeployMode` + `AnchorPos` on BOTH mob and
  sphere); anchor pal must survive with the sphere stashed in main inventory; deliberate
  recall warps the player (verify `data get entity ClaudeBot Pos`).
- **Time stop**: sphere in offhand, sword in hand → sneak-use activates → mid-freeze mobs are
  identical to 15 decimals across polls → hits leave health UNCHANGED (banked) → burst lands
  exactly the banked total at expiry.
- **Greedy boom**: needs a **player kill within the last 10 min** (summon `{Health:1f}`
  chicken, punch it) → prime via feed or RCON (`ForgeData.BoomPrimed 1b` + hunger ≥ 95) →
  devil dies, mimic's loot drops, sphere reverts to empty.
- **Pal death always reverts its sphere to an empty pal_sphere** — during tests this looks
  like "the sphere vanished from the hotbar".

## Test environment setup (learned the hard way)

- **Sky arena**: a 31×31 smooth_stone platform with 2-high glass walls exists at
  `(-5..25, y=120, -5..25)` (floor y=120, stand on y=121). Use it — ground-level test mobs
  die to terrain (suffocate in hillsides), cramming (>24 stacked), and **night monsters**
  (a "mysteriously vanishing" test mob usually means zombies ate it). Before any mob test:
  `time set day`, `gamerule doMobSpawning false`, purge hostiles.
- **Config changes need a server restart** — the Forge file watcher does not fire on this
  dedicated server (verified with `catchableChance`; an n=1 lucky roll made it look live).
- **Wild-catch tests**: set `catchableChance = 1.0` in `config/palmod-common.toml` (+restart)
  so summoned mobs always roll catchable; goals inject at JOIN, so flags merged later via
  `data merge` don't add goals. Revert to 0.4 after.
- **NoAI mobs never set OnGround** — for grounded-only mechanics (eagle storm_dodge),
  `data merge entity <e> {OnGround:1b}` after summoning.
- **Fake an owned pal without the catch flow**: `data merge entity <mob>
  {ForgeData:{PalOwner:[I;826042054,-416137691,-1615008124,-1293733444]}}` (ClaudeBot's UUID).
- `/damage <target> <amt> minecraft:mob_attack by <entity>` drives attacker-conditional
  logic (e.g. damage_immune counter checks) without AI choreography.
- mineflayer may claim "empty hand" after RCON `item replace` — the server-side click still
  throws the real held item; trust RCON inventory + world state.

## Cleanup between tests

Old pals keep working (gorilla chops, sentries shoot, bears guard) and WILL interfere —
kill or recall leftovers before combat tests, and remember `kill @e[type=X]` hits wild
mobs across all loaded chunks too.
