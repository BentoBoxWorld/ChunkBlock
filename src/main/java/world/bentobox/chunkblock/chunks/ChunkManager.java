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

/**
 * The chunk gating heart of ChunkBlock. Chunks unlock in a deterministic clockwise
 * spiral of concentric rings around the island's center chunk. Nothing is stored
 * per-chunk: a chunk at relative offset (dx, dz) is unlocked iff its spiral index is
 * less than the island's unlocked chunk count, which itself derives from the island
 * level. Level loss re-locks from the highest index down, so the most recently earned
 * chunks are always the first to go.
 * <p>
 * Spiral layout: index 0 is the center chunk. Ring r (Chebyshev distance r from the
 * center) holds the 8r indices starting at (2r-1)^2, walked clockwise from the ring's
 * anchor chunk due north of the center.
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

    /**
     * Returns the spiral index of the chunk at relative chunk offset (dx, dz) from the
     * center chunk. Index 0 is the center; lower indices unlock first.
     *
     * @param dx relative chunk x offset
     * @param dz relative chunk z offset
     * @return the spiral index, always &gt;= 0
     */
    public static int spiralIndex(int dx, int dz) {
        int r = Math.max(Math.abs(dx), Math.abs(dz));
        if (r == 0) {
            return 0;
        }
        // Chunks in all rings closer than r
        int base = (2 * r - 1) * (2 * r - 1);
        // Position along ring r's perimeter, walking clockwise from the anchor at (0, -r):
        // east along the north edge, down the east edge, west along the south edge, up the
        // west edge, then east back toward the anchor.
        int p;
        if (dz == -r && dx >= 0) {
            p = dx;
        } else if (dx == r) {
            p = 2 * r + dz;
        } else if (dz == r) {
            p = 4 * r - dx;
        } else if (dx == -r) {
            p = 6 * r - dz;
        } else { // dz == -r && dx < 0
            p = 8 * r + dx;
        }
        return base + p;
    }

    /**
     * Returns the relative chunk offset of the chunk with the given spiral index. This is
     * the exact inverse of {@link #spiralIndex(int, int)}.
     *
     * @param index the spiral index, &gt;= 0
     * @return a vector whose x and z are the relative chunk offsets (y is 0)
     */
    public static Vector chunkAt(int index) {
        if (index <= 0) {
            return new Vector(0, 0, 0);
        }
        // Find the ring: smallest r with index < (2r+1)^2
        int r = (int) Math.ceil((Math.sqrt(index + 1d) - 1) / 2);
        int p = index - (2 * r - 1) * (2 * r - 1);
        if (p <= r) {
            return new Vector(p, 0, -r);
        }
        if (p <= 3 * r) {
            return new Vector(r, 0, p - 2 * r);
        }
        if (p <= 5 * r) {
            return new Vector(4 * r - p, 0, r);
        }
        if (p <= 7 * r) {
            return new Vector(-r, 0, 6 * r - p);
        }
        return new Vector(p - 8 * r, 0, -r);
    }

    /**
     * Returns the ring (Chebyshev distance from the center chunk) that the given spiral
     * index sits on.
     *
     * @param index the spiral index, &gt;= 0
     * @return the ring number; 0 is the center chunk
     */
    public static int ringOf(int index) {
        if (index <= 0) {
            return 0;
        }
        return (int) Math.ceil((Math.sqrt(index + 1d) - 1) / 2);
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
        Location center = island.getCenter();
        int dx = chunkX - (center.getBlockX() >> 4);
        int dz = chunkZ - (center.getBlockZ() >> 4);
        return spiralIndex(dx, dz) < getUnlockedChunkCount(island);
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
     * Returns the island's current unlocked chunk count (cached on the island data
     * object), clamped to the effective maximum.
     *
     * @param island the island
     * @return the number of unlocked chunks including the center chunk, always &gt;= 1
     */
    public int getUnlockedChunkCount(Island island) {
        return Math.min(addon.getOneBlocksIsland(island).getUnlockedChunkCount(), getMaxChunks(island));
    }

    /**
     * Computes how many chunks a given island level affords, honoring levels-per-chunk and
     * the effective maximum. Negative levels clamp to the free center chunk.
     *
     * @param island the island
     * @param level the island level from the Level addon
     * @return the unlocked chunk count the level affords, always &gt;= 1
     */
    public int computeUnlockedCount(Island island, long level) {
        long affordable = 1 + Math.max(0, level) / addon.getSettings().getLevelsPerChunk();
        return (int) Math.min(affordable, getMaxChunks(island));
    }

    /**
     * Returns the island level required to unlock the chunk with the given 1-based number
     * (i.e. an unlocked chunk count of {@code chunkNumber}).
     *
     * @param chunkNumber the chunk count to reach, &gt;= 1
     * @return the required island level
     */
    public long levelForChunkNumber(int chunkNumber) {
        return (long) (chunkNumber - 1) * addon.getSettings().getLevelsPerChunk();
    }

    /**
     * Returns the effective maximum number of chunks this island can unlock: the
     * configured max-chunks, additionally capped so the outermost full ring fits inside
     * the island's protection range.
     *
     * @param island the island
     * @return the maximum unlockable chunk count, always &gt;= 1
     */
    public int getMaxChunks(Island island) {
        int rangeRadius = maxRingRadius(island);
        int rangeCap = (2 * rangeRadius + 1) * (2 * rangeRadius + 1);
        int configured = addon.getSettings().getMaxChunks();
        return Math.max(1, configured < 0 ? rangeCap : Math.min(configured, rangeCap));
    }

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
        Location center = island.getCenter();
        int centerChunkX = center.getBlockX() >> 4;
        int centerChunkZ = center.getBlockZ() >> 4;
        int count = getUnlockedChunkCount(island);
        double bestDist = Double.MAX_VALUE;
        Location best = center.clone();
        best.setY(from.getY());
        for (int i = 0; i < count; i++) {
            Vector offset = chunkAt(i);
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

    /**
     * Returns the relative chunk offsets of the spiral indices in [oldCount, newCount) —
     * the chunks gained by an unlock from oldCount to newCount, or (swapped) the chunks
     * lost by a re-lock. Used for celebration and border effects.
     *
     * @param oldCount the lower chunk count
     * @param newCount the higher chunk count
     * @return relative chunk offsets in spiral order
     */
    public List<Vector> chunksBetween(int oldCount, int newCount) {
        List<Vector> result = new ArrayList<>();
        for (int i = Math.max(0, oldCount); i < newCount; i++) {
            result.add(chunkAt(i));
        }
        return result;
    }
}
