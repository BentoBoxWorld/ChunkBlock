package world.bentobox.chunkblock.dataobjects;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import org.bukkit.entity.EntityType;
import org.eclipse.jdt.annotation.NonNull;

import com.google.gson.annotations.Expose;

import world.bentobox.chunkblock.oneblocks.OneBlockObject;
import world.bentobox.chunkblock.oneblocks.customblock.MobCustomBlock;
import world.bentobox.bentobox.database.objects.DataObject;
import world.bentobox.bentobox.database.objects.Table;

/**
 * @author tastybento
 */
@Table(name = "ChunkBlockIslands")
public class OneBlockIslands implements DataObject {

    @Expose
    private String uniqueId;
    /**
     * The number of blocks broken in the current loop
     */
    @Expose
    private int blockNumber;
    /**
     * The lifetime number of blocks broken not including the current blockNumber.
     * This is unfortunately for backwards compatibility reasons.
     */
    @Expose
    private long lifetime;
    /**
     * Current phase number
     */
    @Expose
    private String phaseName = "";
    /**
     * Hologram text to show
     */
    @Expose
    private String hologram = "";

    /**
     * Timestamp of last phase change
     */
    @Expose
    private long lastPhaseChangeTime = 0;

    /**
     * The chunks this island has unlocked, in the order they were unlocked, as "dx,dz"
     * offsets relative to the island's center chunk. The center chunk "0,0" is always
     * first and can never be removed. When levels are lost, chunks re-lock from the end
     * of this list — last unlocked, first locked.
     */
    @Expose
    private List<String> unlockedChunks = new ArrayList<>();

    /**
     * The island level last seen from the Level addon, used to detect when new chunk
     * credit becomes available.
     */
    @Expose
    private long lastKnownLevel = 0;

    /**
     * The highest ring this island has already been rewarded for completing. Milestones
     * are earned once and stay earned: re-locking a ring and claiming it back does not pay
     * out again. Only an island create or reset clears it.
     */
    @Expose
    private int highestRingRewarded = 0;

    /** Fast membership view of {@link #unlockedChunks}; rebuilt lazily after loads/edits */
    private transient Set<Long> unlockedSet;

    private Queue<OneBlockObject> queue = new LinkedList<>();

    private static long chunkKey(int dx, int dz) {
        return (((long) dx) << 32) | (dz & 0xFFFFFFFFL);
    }

    /**
     * @return the ordered unlocked chunk list ("dx,dz" strings), never null, always
     *         starting with the center chunk "0,0"
     */
    @NonNull
    public List<String> getUnlockedChunks() {
        if (unlockedChunks == null) {
            unlockedChunks = new ArrayList<>();
        }
        if (unlockedChunks.isEmpty()) {
            unlockedChunks.add("0,0");
            unlockedSet = null;
        }
        return unlockedChunks;
    }

    private Set<Long> getUnlockedSet() {
        if (unlockedSet == null) {
            // Materialize the list first: getUnlockedChunks() seeds the center entry and
            // clears the set field while doing so
            List<String> list = getUnlockedChunks();
            Set<Long> set = new HashSet<>();
            for (String entry : list) {
                int comma = entry.indexOf(',');
                set.add(chunkKey(Integer.parseInt(entry.substring(0, comma)),
                        Integer.parseInt(entry.substring(comma + 1))));
            }
            unlockedSet = set;
        }
        return unlockedSet;
    }

    /**
     * @param dx chunk x offset relative to the center chunk
     * @param dz chunk z offset relative to the center chunk
     * @return true if this chunk has been unlocked
     */
    public boolean isChunkUnlocked(int dx, int dz) {
        return getUnlockedSet().contains(chunkKey(dx, dz));
    }

    /**
     * Records a chunk as unlocked (appended to the unlock order). Does nothing if it is
     * already unlocked.
     *
     * @param dx chunk x offset relative to the center chunk
     * @param dz chunk z offset relative to the center chunk
     */
    public void addUnlockedChunk(int dx, int dz) {
        if (!isChunkUnlocked(dx, dz)) {
            getUnlockedChunks().add(dx + "," + dz);
            getUnlockedSet().add(chunkKey(dx, dz));
        }
    }

    /**
     * Re-locks the most recently unlocked chunk. The center chunk is never removed.
     *
     * @return the removed offset as {dx, dz}, or null if only the center chunk is left
     */
    public int[] removeLastUnlockedChunk() {
        List<String> list = getUnlockedChunks();
        if (list.size() <= 1) {
            return null;
        }
        String entry = list.remove(list.size() - 1);
        unlockedSet = null;
        int comma = entry.indexOf(',');
        return new int[] { Integer.parseInt(entry.substring(0, comma)),
                Integer.parseInt(entry.substring(comma + 1)) };
    }

    /**
     * Re-locks everything except the center chunk.
     */
    public void resetUnlockedChunks() {
        getUnlockedChunks().subList(1, getUnlockedChunks().size()).clear();
        unlockedSet = null;
    }

    /**
     * @return the number of unlocked chunks including the center chunk, always &gt;= 1
     */
    public int getUnlockedChunkCount() {
        return getUnlockedChunks().size();
    }

    /**
     * @return the island level last seen from the Level addon
     */
    public long getLastKnownLevel() {
        return lastKnownLevel;
    }

    /**
     * @param lastKnownLevel the last seen island level
     */
    public void setLastKnownLevel(long lastKnownLevel) {
        this.lastKnownLevel = lastKnownLevel;
    }

    /**
     * @return the highest ring this island has already been rewarded for
     */
    public int getHighestRingRewarded() {
        return highestRingRewarded;
    }

    /**
     * @param highestRingRewarded the highest rewarded ring
     */
    public void setHighestRingRewarded(int highestRingRewarded) {
        this.highestRingRewarded = highestRingRewarded;
    }

    /**
     * @return the phaseName
     */
    @NonNull
    public String getPhaseName() {
        return phaseName == null ? "" : phaseName;
    }

    /**
     * @param phaseName the phaseName to set
     */
    public void setPhaseName(String phaseName) {
        this.phaseName = phaseName;
    }

    public OneBlockIslands(String uniqueId) {
        this.uniqueId = uniqueId;
    }

    /**
     * @return the blockNumber
     */
    public int getBlockNumber() {
        return blockNumber;
    }

    /**
     * @param blockNumber the blockNumber to set
     */
    public void setBlockNumber(int blockNumber) {
        this.blockNumber = blockNumber;
    }

    /**
     * Increments the block number
     */
    public void incrementBlockNumber() {
        // Ensure that lifetime is always at least blockNumber
        if (this.lifetime < this.blockNumber) {
            this.lifetime = this.blockNumber;
        }
        this.blockNumber++;
        this.lifetime++;
    }

    /**
     * @return the hologram Line
     */
    @NonNull
    public String getHologram() {
        return hologram == null ? "" : hologram;
    }

    /**
     * @param hologramLine Hologram line
     */
    public void setHologram(String hologramLine) {
        this.hologram = hologramLine;
    }

    /*
     * (non-Javadoc)
     * 
     * @see world.bentobox.bentobox.database.objects.DataObject#getUniqueId()
     */
    @Override
    public String getUniqueId() {
        return uniqueId;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * world.bentobox.bentobox.database.objects.DataObject#setUniqueId(java.lang.
     * String)
     */
    @Override
    public void setUniqueId(String uniqueId) {
        this.uniqueId = uniqueId;
    }

    /**
     * @return the queue
     */
    public Queue<OneBlockObject> getQueue() {
        if (queue == null)
            queue = new LinkedList<>();
        return queue;
    }

    /**
     * Get a list of nearby upcoming mobs
     * 
     * @param i - look ahead value
     * @return list of upcoming mobs
     */
    public List<EntityType> getNearestMob(int i) {
        return getQueue().stream().limit(i).filter(obo ->
        // Include OneBlockObjects that are Entity, or custom block of type
        // MobCustomBlock
        obo.isEntity() || (obo.isCustomBlock() && obo.getCustomBlock() instanceof MobCustomBlock)).map(obo -> {
            if (obo.isCustomBlock() && obo.getCustomBlock() instanceof MobCustomBlock mb) {
                return mb.getMob();
            }

            return obo.getEntityType();
        }).toList();
    }

    /**
     * Adds a OneBlockObject to the queue
     * 
     * @param nextBlock the OneBlockObject to be added
     */
    public void add(OneBlockObject nextBlock) {
        getQueue().add(nextBlock);
    }

    /**
     * Retrieves and removes the head of the queue, or returns null if this queue is
     * empty. Inserts the specified element into the queue if it is possible to do
     * so immediately without violating capacity restrictions, and throwing an
     * {@code IllegalStateException} if no space is currently available.
     * 
     * @param toAdd OneBlockObject
     * @return OneBlockObject head of the queue, or returns null if this queue is
     *         empty.
     */
    public OneBlockObject pollAndAdd(OneBlockObject toAdd) {
        getQueue();
        OneBlockObject b = queue.poll();
        queue.add(toAdd);
        return b;
    }

    /**
     * Clear the look ahead queue
     */
    public void clearQueue() {
        getQueue().clear();
    }

    /**
     * @return the lifetime number of blocks broken not including the current block
     *         count
     */
    public long getLifetime() {
        // Ensure that lifetime is always at least blockNumber
        if (this.lifetime < this.blockNumber) {
            this.lifetime = this.blockNumber;
        }
        return lifetime;
    }

    /**
     * @param lifetime lifetime number of blocks broken to set
     */
    public void setLifetime(long lifetime) {
        this.lifetime = lifetime;
    }

    /**
     * @return Timestamp of last phase change
     */
    public long getLastPhaseChangeTime() {
        return this.lastPhaseChangeTime;
    }

    /**
     * @param lastPhaseChangeTime Timestamp of last phase change
     */
    public void setLastPhaseChangeTime(long lastPhaseChangeTime) {
        this.lastPhaseChangeTime = lastPhaseChangeTime;
    }
}
