# CLAUDE.md

ChunkBlock is a BentoBox gamemode addon: a OneBlock-style magic block in a single chunk,
where island levels are spendable currency — the island owner claims adjacent chunks by
hitting the border in the direction they want to expand. The magic-block engine is copied
from AOneBlock (phase file format is compatible); the chunk gating is original.

## Build and test

```bash
mvn clean package        # builds target/ChunkBlock-<version>-SNAPSHOT-LOCAL.jar
mvn test                 # full test suite (JUnit 5 + Mockito + MockBukkit)
mvn test -Dtest=ChunkManagerTest   # single test class
```

Java 21, Maven. Version lives in `pom.xml` at `<build.version>`. Branches: `develop`
(default, SNAPSHOT builds) → `master` (releases). CI: GitHub Actions (`build.yml` runs
mvn verify + SonarCloud), Jenkins at ci.codemc.org builds release jars on master.

## Architecture

- `ChunkBlock.java` — GameModeAddon main class; wires everything in `onEnable`.
- `chunks/ChunkManager.java` — the game's core: lock queries, level-credit accounting
  (`credit = island level − levels spent`), claim validation (`ClaimResult`), LIFO
  re-lock (`relockToBudget`), bypass exemptions, `nearestUnlockedSpot` for safe ejection.
- `chunks/BorderDisplay.java` — client-side particle curtain / optional barrier blocks on
  locked-chunk faces; never modifies the world.
- `dataobjects/OneBlockIslands.java` — per-island data (JSON, table `ChunkBlockIslands`):
  magic-block state plus `unlockedChunks`, the ordered "dx,dz" claim list ("0,0" always
  first) and `lastKnownLevel`.
- `listeners/ChunkGuardListener.java` — keeps players out of locked chunks (move gate
  with teleport-back, backtrack, teleport blacklist, mounts, join/respawn checks, item
  bounce-back). Techniques ported from the Border addon.
- `listeners/LockedChunkProtect.java` — world physics guards (pistons, liquids, spread,
  explosions, spawns, reach-across) ported from BentoBox core protection listeners.
- `listeners/ChunkClaimListener.java` — the hit-the-border claim interaction (eye-ray
  finds the aimed locked chunk).
- `listeners/LevelListener.java` — Level addon integration: credit announcements,
  LIFO re-locks on level loss, claim celebrations, island create/reset handling.
- `oneblocks/`, `listeners/BlockListener.java`, phases under `src/main/resources/phases/`
  — the magic-block engine copied from AOneBlock; avoid diverging from upstream so fixes
  can be ported.

## Invariants and gotchas

- **Chunk locks are 2D** — a locked chunk is locked at every y. Never add y checks that
  would create a fly-over corridor.
- **Island centers sit mid-chunk** (x ≡ 8, z ≡ 8 mod 16). `Settings` computes offsets
  from start-x/z and snaps island distance to a multiple of 8 (BentoBox grid spacing is
  2× distance). Offsets are deliberately not config-exposed.
- **The center chunk can never lock**; `unlockedChunks` always starts with "0,0".
- **BentoBox's YAML config loader introspects every declared field** of `Settings` via
  JavaBeans `PropertyDescriptor` — a static constant or a field without public
  getter+setter in `Settings` breaks config loading at runtime (tests catch it via
  `ChunkBlockTest.testOnLoad`). Put constants in `ChunkManager` instead.
- The bypass permission is `chunkblock.mod.bypasschunks` with `default: false` — ops
  must be granted it explicitly. Do not reuse `mod.bypasslock` (BentoBox core's island
  lock bypass).
- Locked chunks are never modified: enforcement is cancel/teleport/client-side only.
  The one exception is creating a single landing block in an *unlocked* chunk when
  ejecting a player onto an unsafe spot.
- Never place physical border blocks; visuals are per-player `sendBlockChange`/particles.

## Conventions

- Locale keys live under `chunkblock.chunks.*` for gating messages (en-US.yml is the
  source of truth; other locales lag until synced).
- Public API for other plugins: `ChunkUnlockEvent`/`ChunkRelockEvent` (per chunk, carry
  claim-order index), request handler `unlocked-chunks`.
- Tests extend `CommonTestSetup` (mocked Bukkit/BentoBox); `ChunkManagerTest` and the
  listener tests are the spec for claim/relock behavior.
