# ChunkBlock — Design & Implementation Plan

A BentoBox gamemode addon. Like OneBlock, but the world around you is a forbidden zone:
you start confined to a single chunk containing the magic block, and you unlock the
surrounding chunks — one chunk per island level — in concentric rings around the center.
Lose levels, lose chunks.

This document is the working brief for Claude Code. Decisions marked **DECIDED** are settled;
items marked *verify* should be checked against current BentoBox/Level source at implementation time.

---

## 1. Prior art (what others did, what worked, what didn't)

**Chunklock (Spigot/Paper plugin, also a popular datapack + YouTube format).**
Every chunk in a normal world is locked except the starting one. Unlocks cost
biome-specific items/currency, paid by right-clicking the border; costs scale with biome,
distance from spawn, and chunk contents. Borders are shown with colored glass walls and
holographic cost previews; it has its own team system.
Lessons:

- The *concept* is proven and popular — the "pay to expand outward" loop is sticky.
- Physical glass borders were a constant source of update churn ("chunk border
  improvements" patches) — real blocks in the world need cleanup, fight with pistons,
  water, mobs, and generate bug reports. We avoid real blocks entirely.
- It reimplemented teams, GUIs, protection, and world management from scratch — the
  weakest, buggiest parts per reviews. BentoBox gives us all of that for free; ChunkBlock
  should only implement what is genuinely new (chunk gating + spiral progression).
- Item-payment unlocks needed lots of balancing (per-biome cost tables, AI pricing(!),
  reroll commands). Using island Level as the single currency sidesteps all of that and
  ties expansion to the already-understood Level economy.

**Chunk By Chunk (Forge/Fabric mod, immortius).** Single-chunk sky world that grows: the mod
keeps a hidden *generation dimension* and copies chunks from it into the play world as you
unlock. Interesting inversion: terrain exists, access doesn't. Not needed for ChunkBlock —
our world is a void OneBlock-style world, so a locked chunk is just empty space the player
can't enter yet. No terrain copying required.

**Chunklock datapack (Modrinth).** Used item-display entities for borders — client-side-ish
visuals rather than blocks. Confirms the "visual-only border" approach works and performs
fine; their issues were client-mod rendering (Optifine/Lunar), which particle-based or
display-entity borders on Paper largely avoid.

Net design takeaways adopted below: no physical border blocks; unlock currency = island
level (not items); lean on BentoBox for everything that isn't chunk gating; deterministic
spiral order rather than player-picked chunks (no border GUI/holograms needed).

---

## 2. Decisions (settled)

1. **Standalone gamemode addon** — `world.bentobox.chunkblock`, artifactId `chunkblock`,
   repo `BentoBoxWorld/ChunkBlock`. **Not** an option inside ChunkBlock (a server must be able
   to run both simultaneously; ChunkBlock's config/phases/placeholders stay untouched).
   Start by copying the magic-block engine from ChunkBlock (see §4). Accept the code
   duplication for v1; extracting a shared library is a possible later refactor.
2. **Level addon is a hard dependency.** Island level is the one and only unlock currency.
3. **Enforcement = block entry + visual border.** Locked chunks are never entered and never
   modified. No barrier/glass blocks are ever placed.
4. **Unlock order = deterministic spiral** in concentric rings around the center chunk.
   Level N unlocks the Nth chunk in the spiral (scaled by `levels-per-chunk`). Level loss
   re-locks in exact reverse order. No player choice, no unlock GUI needed in v1.
5. **Magic block engine kept as-is** from ChunkBlock: phases, `oneblocks/*.yml` phase files,
   holograms, particles, per-island block counts.
6. **Offset start**: islands are positioned so the magic block sits at the center of a chunk
   (~x=8, z=8 within the chunk), via `WorldSettings` offsets, hardcoded / not exposed in
   config (see §5).
7. **Border addon is unsupported** in ChunkBlock worlds (unlocked area is a plus-shaped /
   ragged region, not a square). ChunkBlock ships its own boundary visuals and, if Border is
   present, disables itself-relevant conflicts (see §9).

---

## 3. Core game loop

1. New island: player spawns on the magic block at the center of their **center chunk**.
   Only that chunk is accessible. Everything beyond is fog-of-war/forbidden.
2. Player mines the magic block (standard OneBlock loop) → builds → island level rises
   (Level addon; magic-block count and placed blocks both contribute as usual).
3. On level-up: `unlockedChunks = floor(level / levels-per-chunk) + 1` (the +1 is the free
   center chunk). Newly affordable chunks in the spiral unlock with a small celebration
   (sound + message + particle sweep along the vanishing border).
4. On level-down (death penalty, block removal): chunks past the new allowance **re-lock**.
   Builds inside remain intact but the chunks become inaccessible again — an incentive to
   regain the level. Players standing in a re-locked chunk are moved to the nearest
   unlocked chunk (never teleported blindly; use BentoBox safe-spot logic).
5. Expansion proceeds ring by ring until `max-chunks` (bounded by island protection range)
   is reached.

Teams, invites, coops, protection flags, resets, blueprints on reset, placeholders — all
standard BentoBox, nothing to build.

---

## 4. Architecture

### 4.1 New addon skeleton

Standard BentoBox gamemode addon (same shape as ChunkBlock):

```
world.bentobox.chunkblock
├── ChunkBlock.java            // extends GameModeAddon
├── Settings.java              // @ConfigEntry settings, implements WorldSettings
├── generators/                // void chunk generator (copy from ChunkBlock)
├── oneblocks/                 // copied magic-block engine (manager, phases, custom blocks)
│   ├── MagicBlocksManager.java   (from OneBlocksManager)
│   ├── MagicBlockPhase.java      (from OneBlockPhase)
│   └── ...
├── dataobjects/
│   └── ChunkBlockIsland.java  // per-island: block count, phase, unlockedChunkCount
├── listeners/
│   ├── MagicBlockListener.java   (from ChunkBlock BlockListener — the magic block itself)
│   ├── ChunkGuardListener.java   // NEW — entry/interaction enforcement
│   ├── LevelListener.java        // NEW — reacts to Level addon events
│   └── JoinLeaveListener.java
├── chunks/
│   ├── ChunkManager.java      // NEW — spiral math, lock queries, unlock/relock
│   └── BorderDisplay.java     // NEW — particle/display-entity border rendering
├── commands/                  // /cb player + /cbadmin admin commands
└── requests/                  // request handlers (unlocked-chunks for other plugins)
```

Copy from ChunkBlock (develop branch): the oneblocks package (`OneBlocksManager`,
`OneBlockPhase`, `OneBlockObject`, `OneBlockCustomBlockCreator` and the ItemsAdder/Nexo/
CraftEngine custom-block support), `BlockListener` + block-protection listeners for the
magic block, hologram/particle handling, the phase YML files under `src/main/resources/oneblocks/`,
and the void `ChunkGeneratorWorld`. Rename `ChunkBlock`→`ChunkBlock` prefixes; keep the
`OneBlocks` phase file format **identical** so existing community phase packs work verbatim.
Do *not* copy ChunkBlock's placeholders wholesale — re-register the phase ones and add
chunk ones (§10).

Dependencies in `addon.yml`: `depend: [Level]` (hard), soft-depend Bank if the copied code
references it (*verify — likely only via Level*). pom mirrors ChunkBlock's (BentoBox
3.15.0-SNAPSHOT era API, Paper 1.21.x, Level 2.6.2+, ItemsAdder/Nexo/CraftEngine provided).

### 4.2 The two-addon question, resolved

Running ChunkBlock and ChunkBlock together must be clean: separate worlds
(`chunkblock_world`, `_nether`, `_the_end`), separate configs, separate permissions
(`chunkblock.*`), separate command labels (`cb`, `chunkblock` / `cbadmin`). No shared
static state — this falls out naturally from the standalone-copy decision.

---

## 5. Island placement: centering on a chunk

The magic block must sit at the center of a chunk so the playable chunk surrounds the
player evenly and the ring geometry is symmetric.

- BentoBox `WorldSettings` already exposes `getIslandXOffset()` / `getIslandZOffset()`
  (plus `getIslandStartX/Z()`). Islands are placed on the grid `n * islandDistance + offset`.
- **Constraint:** `island-distance` must be a multiple of 16 so *every* island lands at a
  chunk center, not just the first. Then set offset so the island center ≡ 8 (mod 16) on
  both axes.
- **DECIDED — non-user-changeable:** do not expose these in `config.yml`. In
  `Settings.java`, the offset getters return computed constants (`(distance % 16)` math →
  return 8-aligned values) with no `@ConfigEntry`, and `setIslandDistance()` snaps/validates
  to a multiple of 16 on load (log a warning if the config value was adjusted). Because
  `Settings` implements `WorldSettings`, overriding the getters is sufficient — nothing in
  BentoBox core needs to change. (*verify: ConfigObject serialization skips fields without
  @ConfigEntry — it does, only annotated fields round-trip.*)
- Default `island-distance: 512` (32 chunks) — allows max radius ~15 rings ≈ 961 chunks
  ≈ level 960 at defaults before hitting the neighbor's zone; cap below that with
  `max-chunks` (default e.g. 441 = radius 10) and keep protection range ≤ distance/2 as usual.
- Spawn/teleport position: island center at (8.5, y, 8.5) within the chunk — the magic
  block goes at the island center exactly as in ChunkBlock, the offsets take care of the rest.

## 6. Chunk model & spiral math

All coordinates are *relative chunk offsets* from the island's center chunk
(`center = island.getCenter() >> 4`). Nothing is stored per-chunk; everything derives from
one integer.

**Single source of truth:** `unlockedChunkCount` (int) on the island. A chunk at relative
offset (dx, dz) is unlocked iff `spiralIndex(dx, dz) < unlockedChunkCount`.

- `spiralIndex(dx, dz)`: ring `r = max(|dx|,|dz|)` (Chebyshev distance); index 0 is the
  center; ring r contains 8r chunks, indexed clockwise from a fixed anchor (e.g. north of
  the previous ring's anchor). Closed-form: chunks before ring r = `(2r-1)²`; position
  within the ring by walking the perimeter. Implement `spiralIndex(dx,dz)` and its inverse
  `chunkAt(index)`; unit-test them exhaustively against each other for indices 0..10 000.
- Rings fill **completely and deterministically**: level loss re-locks from the highest
  index down — always the most recently earned chunks. No ambiguity, no stored unlock
  history needed.
- `unlockedChunkCount = min(1 + floor(level / levelsPerChunk), maxChunks)` where
  `levels-per-chunk` is configurable (default **1**) and level comes from the Level addon.
  Negative levels clamp to 1 — the center chunk can never lock.
- Optionally (config, default off for v1): `unlock-mode: RING` — a level buys the *whole
  next ring* instead of one chunk, for servers that want chunkier (sorry) milestones.
  Same math, different step function. Cheap to include, skip if it complicates v1.

`ChunkManager` API (all O(1), no collections of chunks):

```java
boolean isUnlocked(Island is, int chunkX, int chunkZ);
int     unlockedCount(Island is);
List<Vector> chunksBetween(int oldCount, int newCount); // for celebration/border FX
int     maxRingRadius(Island is);                       // from maxChunks
```

Per-island persistence: `ChunkBlockIsland` dataobject = ChunkBlock's `OneBlockIslands`
fields (blockNumber, phaseName, lifetime, holograms) **plus** `unlockedChunkCount` (cached;
recomputed from Level on load anyway — the cache exists so enforcement works before the
first level event after restart).

## 7. Level integration (the unlock trigger)

- Hard dependency on **Level**. Read level via the Level addon API
  (`Level#getManager().getIslandLevel(world, ownerUUID)` — *verify exact accessor*) and the
  request handler `island-level` as fallback documentation for third parties.
- Listen to `world.bentobox.level.events.IslandLevelCalculatedEvent` (fires after every
  calculation, before results are saved; cancellable — we never cancel, we only read).
  On event: compute new `unlockedChunkCount`; if it rose → unlock flow (message
  `chunkblock.chunks.unlocked`, sound, border FX along the retreating edge); if it fell →
  re-lock flow (warning message, eject players from newly locked chunks to nearest safe
  unlocked spot, kill border displays there).
- Also handle `IslandPreLevelEvent`? No — calculated is sufficient.
- Recompute on: island load, owner change, `ISLAND_RESET`/`ISLAND_CREATED` (reset to 1),
  team join/leave is irrelevant (level is per-island).
- Config `levels-per-chunk: 1` (double? keep it int, min 1). Death-penalty level drops
  therefore directly shrink territory — this is a **feature** (the brief: "If you lose
  level, then a chunk will become inaccessible") — call it out in the README so admins
  understand `zero-death-penalty`-style Level settings interact with it.
- Level's `zerolevel`/initial offset: a fresh island is level 0 → exactly 1 chunk. Good
  default; no special casing.

## 8. Enforcement — the forbidden zone (ChunkGuardListener)

Principle: **players never exist inside a locked chunk** in survival flow, and locked
chunks are never modified from outside. Implement as one listener package with cheap
early-exit checks (`world != chunkBlockWorld` → return; bypass → return, see 8.1).

**Keeping players inside is not trivial.** The Border addon (`world.bentobox.border`,
`listeners/PlayerListener.java` + `listeners/ShowBarrier.java`) has already solved most of
the hard cases for the rectangular-border version of this problem — study it and port its
techniques to chunk geometry rather than reinventing them. The specific tricks to steal:

### 8.1 Bypass & exemptions

- Permission `chunkblock.mod.bypasslock` (mods/admins) → completely exempt from movement
  and interaction gating; they can walk/fly into locked chunks freely. Also exempt
  `GameMode.SPECTATOR` unconditionally (Border does this in its `outsideCheck`).
- Admin command `/cbadmin bypass` toggles it at runtime (BentoBox convention), and the
  border visuals are suppressed while bypassing so staff can inspect cleanly.
- Bypassing players inside a locked chunk are never ejected by the re-lock sweep.

### 8.2 Movement — the core gate (port of Border's `onPlayerLeaveIsland`)

- `PlayerMoveEvent`: ignore head-only movement by comparing from/to on **XZ only**
  (Border's `Vector XZ = (1,0,1)` multiply trick); only evaluate when the chunk key
  changes. If the destination chunk is locked: **cancel AND teleport back to `from`** via
  `Util.teleportAsync`, guarded by an `inTeleport` set (UUID) so the resulting teleport
  doesn't re-trigger the listener. Cancel alone is not sufficient at speed (sprint-jumping,
  elytra, trident riptide can tunnel through cancelled move events).
- **Backtrack for "already outside" states** (Border's `backtrackToIsland`): players can
  *end up* in a locked chunk without a gated move firing — piston push, logout/login,
  re-lock under their feet, plugin teleports at LOWEST priority. When a check finds the
  player already inside locked territory: ray-trace from the player toward the island
  center (Border uses `BoundingBox.rayTrace` against the protection box; ours traces to the
  nearest unlocked-chunk face), validate the hit with a finite-coords check, verify it's a
  safe location, and if the landing spot is unsafe **create a landing block** under them
  (stone / netherrack / end stone by environment — Border's `safeLandingMaterial`). Fall
  back to `/cb go` home teleport if the trace fails.
- `PlayerTeleportEvent` at **HIGH priority**: blacklist causes `ENDER_PEARL` and
  `CONSUMABLE_EFFECT` (chorus fruit) into locked chunks → cancel (+ refund pearl). Do the
  island/chunk lookup one tick later via scheduler where needed (Border does this because
  some state isn't settled during the event).
- **Mounts** (Border's `onEntityMount`): horses/pigs/striders/boats don't reliably fire
  gated player-move events. On `EntityMountEvent`, start a 1s repeating task that checks
  the mount's chunk; if locked, `eject()` the player (force an `EntityDismountEvent` for
  stubborn custom entities) and backtrack them. Cancel the task on dismount/quit.
  Additionally gate `VehicleMoveEvent` itself at the chunk edge (cancel by zeroing
  velocity + teleporting vehicle back) so empty-ish vehicles don't drift across.
- `PlayerJoinEvent`/`PlayerRespawnEvent`: run the outside-check **one tick after** join
  (Border schedules join processing next tick — metadata/state isn't usable on the join
  tick). Covers "chunk re-locked while offline".

### 8.3 Flight, elytra, and the vertical dimension

- **Lock checks are 2D — a locked chunk is locked for all y, void to build limit and
  beyond.** All gates compare chunk coords only, so flying over at `y > maxHeight` or
  dropping below `y < minHeight` (void bridges, elytra skimming the roof) is impossible by
  construction. State this as an explicit invariant with a unit test; do not add y checks
  that would create a fly-over corridor. Border's particle renderer even colors border
  particles red when `y` is outside world min/max heights specifically because players go
  there — our border curtain must likewise render beyond build limits (extend the curtain
  from `world.getMinHeight() - 16` to `world.getMaxHeight() + 32` when the player is near
  those extremes).
- **Elytra**: high-speed gliding is the tunnel-through risk. The teleport-back (not just
  cancel) approach handles it; port BentoBox core's `ElytraListener` patterns
  (`EntityToggleGlideEvent`, plus the gliding-teleport case). After a mid-air
  intercept, either keep the player gliding inward or apply slow-falling briefly —
  don't drop them.
- **Fly addon / creative fly**: players with fly (IslandFly addon, essentials fly,
  `mayfly`) obey the same move gate — no special code path, but test it explicitly.
  Re-lock ejection of a flying player must preserve their fly state (`getAllowFlight`)
  across the teleport so they don't fall to their death; Border's fall-damage trick is the
  companion safety net: if a player takes fall damage right at the boundary with air below
  their landing block, cancel it and teleport them inside (Border's `onPlayerDamage`).
- IslandFly compatibility note for README: IslandFly toggles fly on protection-range
  entry/exit; that's coarser than chunk locks but not in conflict — chunk gating still
  applies inside the protection range.

### 8.4 Blocks, machines, and world physics (port of BentoBox core patterns)

BentoBox core already guards the island *protection range* against all of these — the
listeners in `world.bentobox.bentobox.listeners.flags.{protection,worldsettings}` are the
reference implementations. ChunkBlock needs the same events, with `island.onIsland(loc)`
swapped for `chunkManager.isUnlocked(island, loc)`:

- **Pistons** — core's `PistonPushListener`: on `BlockPistonExtendEvent`, map every moved
  block through `getRelative(direction)` and cancel unless **allMatch(unlocked)**. Add the
  mirror for `BlockPistonRetractEvent` (sticky pistons pulling blocks out of locked
  chunks).
- **Liquids** — core's `LiquidsFlowingOutListener`: `BlockFromToEvent`, but **skip vertical
  flows** (y-delta ≠ 0 → return, too expensive and can't cross a chunk border anyway);
  cancel horizontal flow into a locked chunk. Also its `BlockDispenseEvent` companion:
  dispensers firing water/lava/powder-snow buckets across the border → cancel.
- **Growth/spread** — core's `TreesGrowingOutsideRangeListener`: on `StructureGrowEvent`
  and `BlockSpreadEvent` (chorus), **`removeIf` the blocks in locked chunks** instead of
  cancelling the whole event — trees grow, just pruned at the border.
- **Projectiles & thrown items** — core's `ThrowingListener`/`HurtingListener` patterns for
  anything that changes blocks or spawns entities on landing (thrown pearls handled in 8.2;
  wind charges via core's `WindChargeListener` pattern). Plain arrows flying over locked
  land: allow, harmless.
- **Item bounce-back** (Border's `trackItem`): on `PlayerDropItemEvent` (and death drops,
  `PlayerDeathEvent`, re-dropped via `dropItemNaturally`), start a short per-item task
  (every tick, hard 400-tick cap, stop when invalid/picked up) that reverses the item's
  velocity (`multiply(-0.5)`) the moment it crosses into a locked chunk. Config
  `bounce-back-items: true`. This is the Border-proven answer to "my stuff flew into the
  forbidden zone".
- Block place/break, buckets, `PlayerInteractEvent` targeting locked-chunk blocks
  (reach-across griefing), frames/armor stands: cancel — one generic
  "block/entity change in locked chunk → cancel" guard covers most of these.
- Explosions: strip affected blocks in locked chunks from `blockList()` (core's
  `ExplosionListener` shape). Entity spawns in locked chunks: cancel natural spawns —
  keeps the zone sterile and cheap.
- Respawn/home/teleport targets: homes can't be set in locked chunks (check on
  `/cb sethome`), and go-home / respawn targets get the backtrack treatment if stale.

### 8.5 Re-lock sweep

When chunks re-lock: for each affected chunk, backtrack (8.2) any players inside to the
nearest unlocked safe spot — never blind-teleport; preserve fly state; leave
entities/items in place (inaccessible until the level is regained is the intended
incentive). Players offline inside get caught by the join-tick check.

**Visual border (BorderDisplay):** per-player, client-side only — port Border's
`ShowBarrier` rendering model to chunk-face geometry:

- Render only near the player (Border uses `BARRIER_RADIUS = 5` blocks; only walls the
  player is within radius of get drawn) and refresh from `PlayerMoveEvent` /
  `VehicleMoveEvent` with the head-only-movement filter. Load target chunks async
  (`Util.getChunkAtAsync`) before rendering, as ShowBarrier does.
- Vertical `Particle.DUST` curtain on locked-chunk faces (red by default / config color);
  when `y` is beyond world min/max heights switch dust color (Border colors out-of-world
  particles red vs blue in-world) so fliers above the roof still see the wall.
- Optional `use-barrier-blocks: true` (Border-style): client-side `sendBlockChange` of
  `BARRIER` blocks on the boundary faces near the player, caching each replaced position's
  old `BlockData` per-player and restoring on hide/quit/teleport/respawn (ShowBarrier's
  `barrierBlocks` map + `hideBorder`). Physical presence without touching the world —
  this also gives free "can't walk through" enforcement as a belt-and-braces layer over
  the move gate.
- On unlock: one-shot green particle sweep + `ENTITY_PLAYER_LEVELUP` sound; hide/redraw on
  protection-range-style changes (Border listens to `IslandProtectionRangeChangeEvent`;
  ours redraws on ChunkUnlock/RelockEvent).

**Why not Border addon:** Border draws one rectangle/square per island; our frontier is a
ragged ring that changes shape chunk by chunk — geometrically incompatible (brief §6
agrees). Detect Border hooking our world and log a clear warning telling admins to disable
it for chunkblock worlds (add our world to its blacklist programmatically if its API
allows; *verify* — otherwise document it).

## 9. What stays stock BentoBox

Teams/invites/kicks/coop/trust; island protection flags & ranks; island resets +
blueprints (blueprint = the starting magic block setup, copied from ChunkBlock's);
`/cb go`, `sethome`, banners, settings GUI; Invites to visit: **visitors are gated by the
same chunk rules** (they're in the world, ChunkGuardListener doesn't care whose island —
it checks the island the chunk belongs to). Nether/End: v1 ships with nether/end
**disabled by default** in config (the chunk-gating story there is unclear; enabling them
works but each dimension gets its own center chunk + same spiral tied to the same level —
implement the world creation like ChunkBlock does, gate identically, just leave default off).

Compatible addons to note in README: Level (required), Likes, Warps, Challenges, TopTen —
all fine. Border — unsupported (above). Greenhouses/Biomes — fine within unlocked chunks.

## 10. Commands, placeholders, API surface

Player `/cb` (aliases `chunkblock`): standard gamemode set (from ChunkBlock) **plus**
`/cb chunks` — GUI or chat map: a small text/GUI minimap of the ring layout, showing
unlocked (■), next-to-unlock (▣ + "level X needed"), locked (□). v1: chat-rendered map +
`/cb chunks` info line ("Chunks: 12/441 — next chunk at level 12").
Admin `/cbadmin`: ChunkBlock's set (setcount/phases etc. adapted) plus
`/cbadmin chunks <player> [set <n>|recalc]` for support/debug, and `/cbadmin bypass`.

Placeholders: `chunkblock_island_chunks`, `chunkblock_island_max_chunks`,
`chunkblock_island_next_chunk_level`, `chunkblock_island_ring`, plus the copied phase
placeholders (`chunkblock_phase`, `chunkblock_count`, …).

Request handlers: `chunkblock.unlocked-chunks` (map: count, ring, list not included).
Public events for other plugins: `ChunkUnlockEvent(island, chunkVector, index)`,
`ChunkRelockEvent(...)` — fired on transitions, not cancellable in v1.

## 11. Config (config.yml sketch — beyond standard gamemode settings)

```yaml
chunkblock:
  levels-per-chunk: 1        # island levels needed per chunk (min 1)
  max-chunks: 441            # radius-10 ring cap; -1 = limited only by island distance
  relock-on-level-loss: true # false = ratchet mode, chunks never re-lock
  eject-players-on-relock: true
  border:
    show-particles: true
    particle-color: RED
    client-side-outline: false
  deny-mob-spawns-in-locked: true
world:
  island-distance: 512       # snapped to a multiple of 16 on load; offsets are managed internally
  # NOTE: no offset settings here — deliberately not configurable
```

`relock-on-level-loss: false` ("ratchet mode") costs almost nothing to implement
(`unlockedChunkCount = max(old, computed)`) and defuses the harshest complaint Chunklock-
likes get (losing land feels terrible to some communities). Default **true** per the brief.

## 12. Edge cases checklist (for tests)

- Level jumps by >1 chunk in one calculation (big build) → unlock several, FX once.
- Level drops multiple chunks → eject from all, reverse order.
- Player offline inside a chunk that re-locks → on join, move-out check in JoinLeaveListener.
- Island reset → count back to 1, dataobject reset, borders refresh.
- Ownership transfer / team changes → level is island-scoped; nothing changes. Recalc anyway.
- `max-chunks` reduced in config after islands exceeded it → clamp, but never eject on
  startup unless `relock-on-level-loss` (log instead).
- Two islands' rings can never meet: enforce `max-chunks` radius ≤ (island-distance/2)/16
  −1 at config load; warn and clamp.
- Magic block chunk is always index 0; magic block never in a locked chunk by construction.
- Pearl thrown before re-lock lands after → target check happens at teleport time. ✔
- Elytra at full speed into the border → teleport-back intercept, no tunnel-through
  (test with riptide tridents too).
- Flying player (IslandFly/creative) ejected by re-lock → fly state preserved, no fall
  death; fall-damage-at-boundary cancel works (Border's onPlayerDamage trick).
- Player above max build height / below min height cannot cross into locked chunks
  (2D-lock invariant test).
- Horse/boat/strider ridden toward the border → mount-watch task ejects/returns.
- Piston pushes player (or blocks) into locked chunk → block push cancelled; player
  backtrack recovers if it somehow happens.
- Mod with chunkblock.mod.bypasslock walks/flies out and back → no ejection, no visuals.
- The End/Nether disabled default; if enabled, per-dimension center chunks, same count.

## 13. Implementation phases for Claude Code

1. **Skeleton + copy** — new repo from ChunkBlock develop: rename packages/classes/resources,
   strip ChunkBlock-specifics, boot as a working "OneBlock clone" gamemode named ChunkBlock
   (worlds, config, commands, blueprints, phases all functional). *Milestone: plays exactly
   like OneBlock under the new name.*
2. **Placement math** — WorldSettings offset overrides + distance snapping + tests proving
   every island center is at a chunk center. *Milestone: new islands spawn player mid-chunk.*
3. **ChunkManager + spiral** — spiralIndex/chunkAt + exhaustive unit tests; dataobject field;
   admin debug command. No enforcement yet.
4. **Enforcement** — ChunkGuardListener event set from §8, ported from Border's
   `PlayerListener`/`ShowBarrier` and BentoBox core's protection/worldsettings listeners
   (keep checkouts of both repos next to this one as reference source); bypass perm,
   re-lock ejection. *Milestone: can't leave the center chunk on a fresh island — walking,
   sprint-jumping, elytra, pearls, mounts, or flying at any y.*
5. **Level hookup** — IslandLevelCalculatedEvent → count changes → unlock/re-lock flows,
   messages, sounds. *Milestone: full loop playable.*
6. **Border visuals** — particle curtain + unlock FX; `/cb chunks` map.
7. **Polish** — placeholders, request handlers, public events, locales (en-US first),
   Border-addon conflict warning, README, addon.yml metadata, CI (same GitHub workflow as
   other BentoBox addons).
8. **Test pass** — unit tests (spiral, count math, settings snapping) + the §12 checklist
   manually on a Paper 1.21.x test server with ChunkBlock installed alongside.

Suggested first prompt to Claude Code: point it at this file plus a checkout of ChunkBlock
develop as the copy source, and run phases 1–2 in one session, 3–5 next, 6–8 last.

---

## Appendix: sources reviewed

- Chunklock plugin — SpigotMC resource 125966 and Hangar (Lunary1/Chunklock): dynamic
  biome/distance costs, glass borders, own team system; reviews note border-maintenance
  churn and reimplemented-infrastructure bugs.
- Chunklock datapack (Modrinth): item-display borders, biome-item costs, client-mod
  rendering issues.
- Chunk By Chunk mod (immortius, GitHub/Modrinth): generation-dimension chunk copying;
  terrain-exists-access-doesn't model.
- BentoBox WorldSettings (develop): getIslandXOffset/ZOffset/StartX/StartZ available.
- Level addon: IslandLevelCalculatedEvent fires post-calculation, readable island level,
  request handler `island-level` for non-addon consumers.
- ChunkBlock (develop): pom deps (BentoBox 3.15-SNAPSHOT, Level 2.6.2, Bank 1.3.0,
  ItemsAdder/Nexo/CraftEngine, Paper 1.21.11), oneblocks engine to copy.
- Border addon source (develop, read in full): PlayerListener — XZ move filter,
  cancel+teleport-back with inTeleport re-entrancy guard, ray-trace backtrack with safe
  landing block creation, pearl/chorus teleport blacklist, mount-watch task,
  join-processing one tick late, fall-damage boundary trick, item bounce-back tracker,
  spectator exemption; ShowBarrier — proximity-limited rendering, client-side barrier
  blocks with BlockData restore cache, out-of-world red particles, async chunk loads.
- BentoBox core protection listeners (develop, read): PistonPushListener (allMatch
  pattern), LiquidsFlowingOutListener (horizontal-only + dispenser), 
  TreesGrowingOutsideRangeListener (removeIf pruning), ElytraListener, plus the general
  FlagListener protection set as the catalogue of events to gate.
