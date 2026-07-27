# ChunkBlock

A BentoBox gamemode by tastybento. Magic-block engine based on AOneBlock; original OneBlock idea by IJAminecraft.

[![Build Status](https://ci.codemc.org/buildStatus/icon?job=BentoBoxWorld/ChunkBlock)](https://ci.codemc.org/job/BentoBoxWorld/job/ChunkBlock/)[
![Bugs](https://sonarcloud.io/api/project_badges/measure?project=BentoBoxWorld_ChunkBlock&metric=bugs)](https://sonarcloud.io/dashboard?id=BentoBoxWorld_ChunkBlock)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=BentoBoxWorld_ChunkBlock&metric=reliability_rating)](https://sonarcloud.io/dashboard?id=BentoBoxWorld_ChunkBlock)
[![Lines of Code](https://sonarcloud.io/api/project_badges/measure?project=BentoBoxWorld_ChunkBlock&metric=ncloc)](https://sonarcloud.io/dashboard?id=BentoBoxWorld_ChunkBlock)

## About

You start on a single magic block, confined to a single chunk. Everything beyond it is a
forbidden zone — you literally cannot leave. Mine the magic block, build, and raise your
island level: **every level unlocks the next chunk** in a spiral of rings around your
starting chunk. Lose levels (deaths, removing blocks) and your outermost chunks lock
again — your builds stay, but you can't reach them until you earn the level back.

- The classic OneBlock loop: 18 phases, thousands of blocks, mobs, and treasure chests.
- Territory that grows with your island level — one chunk per level by default.
- A particle curtain shows the frontier; unlocking chunks is celebrated in style.
- `/cb chunks` shows a live map of your territory in chat.

## Installation

1. Install [BentoBox](https://github.com/BentoBoxWorld/BentoBox) and the
   [Level](https://github.com/BentoBoxWorld/Level) addon (**required** — island level is
   the unlock currency).
2. Drop the ChunkBlock jar into the `plugins/BentoBox/addons` folder and restart.

ChunkBlock runs happily alongside AOneBlock and other gamemodes — separate worlds,
commands, and permissions.

## Commands

The player command is `/cb` (alias `/chunkblock`), the admin command `/cbadmin`
(aliases `/chunkblockadmin`, `/cba`). Beyond the standard BentoBox island commands:

| Command | Description |
|---|---|
| `/cb chunks` | Your chunk count, next unlock level, and a chat map of your territory |
| `/cb count` | Magic block count and phase |
| `/cbadmin chunks <player> [set <n> \| recalc]` | Inspect, set, or recalculate a player's unlocked chunks |
| `/cbadmin bypass` | Toggle chunk-lock enforcement for yourself (needs `chunkblock.mod.bypasschunks`) |

## How unlocking works

- A fresh island (level 0) has exactly one chunk — the one with the magic block.
- `unlocked chunks = 1 + level ÷ levels-per-chunk` (configurable, default 1), capped by
  `max-chunks` (default 441 — a full 10-ring, 21×21-chunk square) and by the island
  protection range.
- Chunks unlock in a fixed clockwise spiral, ring by ring. No choices to make, nothing
  to buy — just play and grow.
- If the island level drops, chunks re-lock in exact reverse order. Set
  `relock-on-level-loss: false` in the config for "ratchet mode" where territory never
  shrinks. Note that Level's death penalty settings therefore directly affect territory —
  a death that costs a level costs a chunk.
- Players never get stuck: anyone standing in a chunk that re-locks is moved to the
  nearest unlocked spot safely (flight state preserved, no fall damage).

## Compatibility notes

- **Level** is a hard dependency.
- **Border** is unsupported in ChunkBlock worlds: its wall is one rectangle per island,
  which cannot match ChunkBlock's chunk-by-chunk frontier. Add ChunkBlock to Border's
  `disabled-gamemodes` list; ChunkBlock draws its own frontier.
- Likes, Warps, Challenges, TopTen, Greenhouses, Biomes and friends work as usual
  (within unlocked chunks).
- The nether and end are **disabled by default**. If enabled, each dimension gets its own
  center chunk and the same spiral, driven by the same island level.
- The moderator bypass permission is `chunkblock.mod.bypasschunks` (deliberately not
  `mod.bypasslock`, which is BentoBox core's island *lock* bypass — a different feature).

## Placeholders

In addition to the phase placeholders inherited from the magic-block engine
(`chunkblock_my_island_phase`, `chunkblock_my_island_count`, ...):

| Placeholder | Value |
|---|---|
| `chunkblock_island_chunks` | Unlocked chunk count |
| `chunkblock_island_max_chunks` | Maximum unlockable chunks |
| `chunkblock_island_next_chunk_level` | Island level needed for the next chunk |
| `chunkblock_island_ring` | Ring number of the outermost unlocked chunk |

## For developers

- Request handler `unlocked-chunks` (submit `"player"` → UUID; returns `count`, `max`,
  `ring`, `nextLevel`).
- Events `ChunkUnlockEvent` and `ChunkRelockEvent` fire once per chunk transition.
- Phase files are format-compatible with AOneBlock's `oneblocks` YAML, so community
  phase packs work verbatim.

## FAQ

Q: What phases are there?

A: The same 18-phase progression as AOneBlock: Plains, Underground, Winter, Ocean,
Jungle, Swamp, Dungeon, Desert, The Nether, Plenty, Desolation, Deep Dark, The End,
Lush Caves, Dripstone Caves, Mangrove Swamp, Meadow, Cherry Grove, and Jagged Peaks.

Q: Why can't I walk past the glowing red wall?

A: That chunk is still locked! Check `/cb chunks` — it shows the level you need for the
next chunk.

Q: I lost a level and my farm is behind the wall now. Is it gone?

A: No. Nothing in a re-locked chunk is touched — regain the level and it's all still
there. (Admins can turn re-locking off entirely with `relock-on-level-loss: false`.)

Q: Why do I keep falling and dying?

A: There are tricks to surviving, but it might be difficult! You need to build space so
you don't fall — and remember deaths can cost levels, and levels are territory.
