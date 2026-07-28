# ChunkBlock

A BentoBox gamemode by tastybento. Magic-block engine based on AOneBlock; original OneBlock idea by IJAminecraft.

[![Build Status](https://ci.codemc.org/buildStatus/icon?job=BentoBoxWorld/ChunkBlock)](https://ci.codemc.org/job/BentoBoxWorld/job/ChunkBlock/)[
![Bugs](https://sonarcloud.io/api/project_badges/measure?project=BentoBoxWorld_ChunkBlock&metric=bugs)](https://sonarcloud.io/dashboard?id=BentoBoxWorld_ChunkBlock)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=BentoBoxWorld_ChunkBlock&metric=reliability_rating)](https://sonarcloud.io/dashboard?id=BentoBoxWorld_ChunkBlock)
[![Lines of Code](https://sonarcloud.io/api/project_badges/measure?project=BentoBoxWorld_ChunkBlock&metric=ncloc)](https://sonarcloud.io/dashboard?id=BentoBoxWorld_ChunkBlock)

## About

You start on a single magic block, confined to a single chunk. Everything beyond it is a
forbidden zone — you literally cannot leave. Mine the magic block, build, and raise your
island level: **levels are chunk currency**. When you have credit, walk up to the border,
hit it, and the chunk on the other side opens up — expand in whatever direction you like,
up to your island's protection range. Lose levels (deaths, removing blocks) and your most
recently claimed chunks lock again — your builds stay, but you can't reach them until you
earn the levels back.

- The classic OneBlock loop: 18 phases, thousands of blocks, mobs, and treasure chests.
- Territory that grows the way *you* choose — punch the border to claim the next chunk.
- A particle curtain shows the frontier; claiming chunks is celebrated in style.
- `/cb chunks` shows a live map of your territory and what you can claim next.

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
| `/cb chunks` | Your chunk count, spendable credit, and a chat map of your territory |
| `/cb count` | Magic block count and phase |
| `/cbadmin chunks <player> [reset]` | Inspect a player's chunks and credit, or re-lock them back to the start |
| `/cbadmin bypass` | Toggle chunk-lock enforcement for yourself (needs `chunkblock.mod.bypasschunks`) |

## How claiming works

- A fresh island (level 0) has exactly one chunk — the one with the magic block.
- Island levels are spendable credit: `credit = island level − levels already spent`.
  One chunk costs `levels-per-chunk` levels (default 1).
- The **island owner** expands by walking to the border and hitting it (left- or
  right-click toward the wall). The chunk on the other side opens if it touches your
  territory, fits inside the island protection range and `max-chunks` (default 441),
  and you have the credit. Any direction, your choice.
- Every claim is recorded in order. If the island level drops below what you have spent,
  chunks re-lock in exact reverse claim order — last claimed, first lost. Set
  `relock-on-level-loss: false` in the config for "ratchet mode" where territory never
  shrinks. Note that Level's death penalty settings therefore directly affect territory.
- Players never get stuck: anyone standing in a chunk that re-locks is moved to the
  nearest unlocked spot safely (flight state preserved, no fall damage).

## Compatibility notes

- **Level** is a hard dependency.
- **Border** works fine: it shows the island's overall protection limit. The chunk
  frontier inside it is drawn by ChunkBlock's own particle curtain.
- Likes, Warps, Challenges, TopTen, Greenhouses, Biomes and friends work as usual
  (within unlocked chunks).
- The nether and end are **disabled by default**. If enabled, each dimension gets its own
  center chunk and the same claim rules, driven by the same island level.
- The moderator bypass permission is `chunkblock.mod.bypasschunks` (deliberately not
  `mod.bypasslock`, which is BentoBox core's island *lock* bypass — a different feature).
  It is **not** given to ops by default: grant it explicitly (e.g. via your permissions
  plugin) so staff play by the same rules until they opt in.

## Placeholders

In addition to the phase placeholders inherited from the magic-block engine
(`chunkblock_my_island_phase`, `chunkblock_my_island_count`, ...):

| Placeholder | Value |
|---|---|
| `chunkblock_island_chunks` | Unlocked chunk count |
| `chunkblock_island_max_chunks` | Maximum claimable chunks |
| `chunkblock_island_chunk_credit` | Level credit available to spend |
| `chunkblock_island_next_chunk_level` | Total island level needed to afford the next chunk |
| `chunkblock_island_ring` | Ring number of the outermost claimed chunk |

## For developers

- Request handler `unlocked-chunks` (submit `"player"` → UUID; returns `count`, `max`,
  `ring`, `spent`, `credit`, and `chunks` — the claim-ordered offset list).
- Events `ChunkUnlockEvent` and `ChunkRelockEvent` fire once per chunk transition.
- Phase files are format-compatible with AOneBlock's `oneblocks` YAML, so community
  phase packs work verbatim.

## FAQ

Q: What phases are there?

A: The same 18-phase progression as AOneBlock: Plains, Underground, Winter, Ocean,
Jungle, Swamp, Dungeon, Desert, The Nether, Plenty, Desolation, Deep Dark, The End,
Lush Caves, Dripstone Caves, Mangrove Swamp, Meadow, Cherry Grove, and Jagged Peaks.

Q: Why can't I walk past the glowing red wall?

A: That chunk is still locked! If you're the island owner and have level credit, hit the
wall to claim the chunk. Check `/cb chunks` to see your credit and what's claimable.

Q: I lost levels and my farm is behind the wall now. Is it gone?

A: No. Nothing in a re-locked chunk is touched — regain the levels and claim it back;
re-locking always takes your newest chunks first. (Admins can turn re-locking off
entirely with `relock-on-level-loss: false`.)

Q: Why do I keep falling and dying?

A: There are tricks to surviving, but it might be difficult! You need to build space so
you don't fall — and remember deaths can cost levels, and levels are territory.
