package world.bentobox.chunkblock.chunks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.chunkblock.ChunkBlock;
import world.bentobox.chunkblock.dataobjects.OneBlockIslands;

/**
 * The chunk gating heart of ChunkBlock. Every island starts with just its center chunk.
 * Island levels are a spendable currency: the owner claims the next chunk by going to the
 * border and hitting it — any direction they like, as long as the chunk touches their
 * territory and fits inside the island's protection range. Each claim costs
 * levels-per-chunk levels of credit (credit = island level minus levels already spent).
 * <p>
 * The unlock order is recorded per island. If the island level drops below what has been
 * spent, chunks re-lock in exact reverse order — last claimed, first lost.
 *
 * @author tastybento
 */
public class ChunkManager {

    /**
     * Permission that exempts moderators from chunk locking entirely.
     */
    public static final String BYPASS_PERMISSION = "chunkblock.mod.bypasschunks";

    /**
     * Block offset within a chunk that marks its center (both axes). Island centers are
     * always placed here (see Settings offset handling).
     */
    public static final int CHUNK_CENTER = 8;

    /**
     * Why a chunk can or cannot be claimed right now.
     */
    public enum ClaimResult {
        /** The chunk can be (or was) claimed */
        OK,
        /** The chunk is already unlocked */
        ALREADY_UNLOCKED,
        /** The chunk does not touch the island's unlocked territory */
        NOT_ADJACENT,
        /** The chunk is outside the island's protection range */
        BEYOND_LIMIT,
        /** Not enough level credit */
        NO_CREDIT
    }

    private final ChunkBlock addon;
    /**
     * Players holding {@link #BYPASS_PERMISSION} who have switched enforcement back on for
     * themselves with the admin bypass command.
     */
    private final Set<UUID> bypassSwitchedOff = new HashSet<>();

    public ChunkManager(ChunkBlock addon) {
        this.addon = addon;
    }

    /**
     * Checks whether a player is exempt from chunk locking: spectators always are, and
     * holders of {@link #BYPASS_PERMISSION} are unless they toggled enforcement back on
     * with the admin bypass command.
     *
     * @param player the player
     * @return true if chunk locks do not apply to this player
     */
    public boolean isExempt(Player player) {
        return player.getGameMode() == GameMode.SPECTATOR
                || (player.hasPermission(BYPASS_PERMISSION) && !bypassSwitchedOff.contains(player.getUniqueId()));
    }

    /**
     * Toggles chunk lock enforcement for a bypass-permission holder.
     *
     * @param uuid the player's UUID
     * @return true if the player is now bypassing locks, false if enforcement was re-enabled
     */
    public boolean toggleBypass(UUID uuid) {
        if (bypassSwitchedOff.remove(uuid)) {
            return true;
        }
        bypassSwitchedOff.add(uuid);
        return false;
    }

    // ------------------------------------------------------------------
    // Lock queries
    // ------------------------------------------------------------------

    private int centerChunkX(Island island) {
        return island.getCenter().getBlockX() >> 4;
    }

    private int centerChunkZ(Island island) {
        return island.getCenter().getBlockZ() >> 4;
    }

    /**
     * Checks whether the chunk containing the given location is unlocked for the island.
     * Lock checks are 2D: a locked chunk is locked for every y from the void to beyond the
     * build limit.
     *
     * @param island the island the location belongs to
     * @param location the location to check
     * @return true if the location's chunk is unlocked
     */
    public boolean isUnlocked(Island island, Location location) {
        return isUnlocked(island, location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    /**
     * Checks whether the chunk at world chunk coordinates (chunkX, chunkZ) is unlocked for
     * the island.
     *
     * @param island the island the chunk belongs to
     * @param chunkX world chunk x coordinate
     * @param chunkZ world chunk z coordinate
     * @return true if the chunk is unlocked
     */
    public boolean isUnlocked(Island island, int chunkX, int chunkZ) {
        return addon.getOneBlocksIsland(island).isChunkUnlocked(chunkX - centerChunkX(island),
                chunkZ - centerChunkZ(island));
    }

    /**
     * Convenience opposite of {@link #isUnlocked(Island, Location)}.
     *
     * @param island the island the location belongs to
     * @param location the location to check
     * @return true if the location's chunk is locked
     */
    public boolean isLocked(Island island, Location location) {
        return !isUnlocked(island, location);
    }

    /**
     * @param island the island
     * @return the number of unlocked chunks including the center chunk, always &gt;= 1
     */
    public int getUnlockedChunkCount(Island island) {
        return addon.getOneBlocksIsland(island).getUnlockedChunkCount();
    }

    /**
     * @param island the island
     * @return the ring (Chebyshev distance from the center chunk) of the island's
     *         outermost unlocked chunk
     */
    public int currentRing(Island island) {
        int ring = 0;
        for (Vector offset : getUnlockedOffsets(island)) {
            ring = Math.max(ring, Math.max(Math.abs(offset.getBlockX()), Math.abs(offset.getBlockZ())));
        }
        return ring;
    }

    /**
     * A ring is the square of chunks at a fixed Chebyshev distance from the center chunk:
     * ring 1 is the eight chunks surrounding the center, ring 2 the sixteen around those.
     * Ring 0 is the center chunk, which is always unlocked.
     *
     * @param island the island
     * @param ring the ring radius in chunks
     * @return true if every chunk in the ring is unlocked; false for rings that do not fit
     *         inside the island's protection range
     */
    public boolean isRingComplete(Island island, int ring) {
        if (ring <= 0) {
            return true;
        }
        if (ring > maxRingRadius(island)) {
            return false;
        }
        OneBlockIslands data = addon.getOneBlocksIsland(island);
        for (int d = -ring; d <= ring; d++) {
            // North and south edges cover the corners, so the east and west edges only
            // need the same sweep to close the square
            if (!data.isChunkUnlocked(d, -ring) || !data.isChunkUnlocked(d, ring)
                    || !data.isChunkUnlocked(-ring, d) || !data.isChunkUnlocked(ring, d)) {
                return false;
            }
        }
        return true;
    }

    /**
     * @param island the island
     * @return the number of whole rings completed outwards from the center without a gap.
     *         A claimed chunk two rings out does not count while ring 1 has a hole in it.
     */
    public int completedRings(Island island) {
        int ring = 0;
        while (isRingComplete(island, ring + 1)) {
            ring++;
        }
        return ring;
    }

    /**
     * @param island the island
     * @param ring the ring radius to check
     * @return how many chunks in the ring are still locked; 0 means the ring is complete
     */
    public int chunksRemainingInRing(Island island, int ring) {
        if (ring <= 0) {
            return 0;
        }
        OneBlockIslands data = addon.getOneBlocksIsland(island);
        int missing = 0;
        for (int d = -ring; d <= ring; d++) {
            if (!data.isChunkUnlocked(d, -ring)) missing++;
            if (!data.isChunkUnlocked(d, ring)) missing++;
            if (d != -ring && d != ring) {
                if (!data.isChunkUnlocked(-ring, d)) missing++;
                if (!data.isChunkUnlocked(ring, d)) missing++;
            }
        }
        return missing;
    }

    /**
     * @param island the island
     * @return the island's unlocked chunk offsets in unlock order (x and z are chunk
     *         offsets relative to the center chunk)
     */
    public List<Vector> getUnlockedOffsets(Island island) {
        List<Vector> result = new ArrayList<>();
        for (String entry : addon.getOneBlocksIsland(island).getUnlockedChunks()) {
            int comma = entry.indexOf(',');
            result.add(new Vector(Integer.parseInt(entry.substring(0, comma)), 0,
                    Integer.parseInt(entry.substring(comma + 1))));
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Level credit
    // ------------------------------------------------------------------

    /**
     * @return how many levels one chunk costs
     */
    public int getChunkCost() {
        return addon.getSettings().getLevelsPerChunk();
    }

    /**
     * @param island the island
     * @return the levels this island has already spent on chunks
     */
    public long getSpentLevels(Island island) {
        return (long) (getUnlockedChunkCount(island) - 1) * getChunkCost();
    }

    /**
     * @param island the island
     * @param level the island's current level
     * @return the level credit available to spend on chunks (can be negative after level
     *         loss until chunks re-lock)
     */
    public long getCredit(Island island, long level) {
        return level - getSpentLevels(island);
    }

    /**
     * @param island the island
     * @return the level credit available right now, reading the level from the Level addon
     */
    public long getCredit(Island island) {
        return getCredit(island, addon.getIslandLevel(island));
    }

    // ------------------------------------------------------------------
    // Claiming
    // ------------------------------------------------------------------

    /**
     * Checks whether the chunk at world chunk coordinates could be claimed by the island,
     * ignoring level credit — geometry only.
     *
     * @param island the island
     * @param chunkX world chunk x coordinate
     * @param chunkZ world chunk z coordinate
     * @return OK, ALREADY_UNLOCKED, NOT_ADJACENT or BEYOND_LIMIT
     */
    public ClaimResult checkGeometry(Island island, int chunkX, int chunkZ) {
        int dx = chunkX - centerChunkX(island);
        int dz = chunkZ - centerChunkZ(island);
        OneBlockIslands data = addon.getOneBlocksIsland(island);
        if (data.isChunkUnlocked(dx, dz)) {
            return ClaimResult.ALREADY_UNLOCKED;
        }
        if (Math.max(Math.abs(dx), Math.abs(dz)) > maxRingRadius(island)) {
            return ClaimResult.BEYOND_LIMIT;
        }
        // Must share a face with territory the island already owns
        if (!data.isChunkUnlocked(dx + 1, dz) && !data.isChunkUnlocked(dx - 1, dz)
                && !data.isChunkUnlocked(dx, dz + 1) && !data.isChunkUnlocked(dx, dz - 1)) {
            return ClaimResult.NOT_ADJACENT;
        }
        return ClaimResult.OK;
    }

    /**
     * Attempts to claim the chunk at world chunk coordinates for the island, spending
     * level credit. On success the chunk is recorded as unlocked (and saved); the caller
     * handles celebration and events.
     *
     * @param island the island
     * @param chunkX world chunk x coordinate
     * @param chunkZ world chunk z coordinate
     * @return OK if the chunk was claimed, otherwise the reason it was not
     */
    public ClaimResult claim(Island island, int chunkX, int chunkZ) {
        ClaimResult geometry = checkGeometry(island, chunkX, chunkZ);
        if (geometry != ClaimResult.OK) {
            return geometry;
        }
        if (getCredit(island) < getChunkCost()) {
            return ClaimResult.NO_CREDIT;
        }
        addon.getOneBlocksIsland(island).addUnlockedChunk(chunkX - centerChunkX(island),
                chunkZ - centerChunkZ(island));
        addon.getBlockListener().saveIsland(island);
        return ClaimResult.OK;
    }

    /**
     * Re-locks chunks (last claimed first) until the levels spent fit within the given
     * island level. Does not eject players or fire events — the caller does that with the
     * returned offsets.
     *
     * @param island the island
     * @param level the island's new level
     * @return the re-locked chunk offsets, most recently claimed first
     */
    public List<Vector> relockToBudget(Island island, long level) {
        List<Vector> removed = new ArrayList<>();
        OneBlockIslands data = addon.getOneBlocksIsland(island);
        while (getSpentLevels(island) > Math.max(0, level)) {
            int[] offset = data.removeLastUnlockedChunk();
            if (offset == null) {
                break;
            }
            removed.add(new Vector(offset[0], 0, offset[1]));
        }
        if (!removed.isEmpty()) {
            addon.getBlockListener().saveIsland(island);
        }
        return removed;
    }

    // ------------------------------------------------------------------
    // Limits and geometry helpers
    // ------------------------------------------------------------------

    /**
     * Returns the largest ring radius (in chunks) that fits entirely inside the island's
     * protection range.
     *
     * @param island the island
     * @return the maximum ring radius, &gt;= 0
     */
    public int maxRingRadius(Island island) {
        return Math.max(0, (island.getProtectionRange() - CHUNK_CENTER) / 16);
    }

    /**
     * Returns the maximum number of chunks this island can unlock, determined by
     * what fits inside the island's protection range.
     *
     * @param island the island
     * @return the maximum unlockable chunk count, always &gt;= 1
     */
    public int getMaxChunks(Island island) {
        int rangeRadius = maxRingRadius(island);
        return Math.max(1, (2 * rangeRadius + 1) * (2 * rangeRadius + 1));
    }

    /**
     * Finds the closest position inside the island's unlocked territory to the given
     * location. Scans every unlocked chunk and clamps the location's x/z into the chunk's
     * interior (one block in from the edge, so the returned spot is not on the boundary).
     * The returned location keeps the given y; callers are responsible for making it safe
     * to stand on.
     *
     * @param island the island
     * @param from the location to move inside from
     * @return the nearest unlocked position, or the island center if from is degenerate
     */
    public Location nearestUnlockedSpot(Island island, Location from) {
        int centerChunkX = centerChunkX(island);
        int centerChunkZ = centerChunkZ(island);
        double bestDist = Double.MAX_VALUE;
        Location best = island.getCenter().clone();
        best.setY(from.getY());
        for (Vector offset : getUnlockedOffsets(island)) {
            int minX = (centerChunkX + offset.getBlockX()) << 4;
            int minZ = (centerChunkZ + offset.getBlockZ()) << 4;
            // Clamp one block inside the chunk so the spot is off the locked boundary
            double x = Math.clamp(from.getX(), minX + 1.5, minX + 14.5);
            double z = Math.clamp(from.getZ(), minZ + 1.5, minZ + 14.5);
            double dist = (x - from.getX()) * (x - from.getX()) + (z - from.getZ()) * (z - from.getZ());
            if (dist < bestDist) {
                bestDist = dist;
                best = new Location(from.getWorld(), x, from.getY(), z, from.getYaw(), from.getPitch());
            }
        }
        return best;
    }
}
