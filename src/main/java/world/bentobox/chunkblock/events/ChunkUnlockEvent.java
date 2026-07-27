package world.bentobox.chunkblock.events;

import org.bukkit.event.HandlerList;
import org.bukkit.util.Vector;
import org.eclipse.jdt.annotation.NonNull;

import world.bentobox.bentobox.api.events.BentoBoxEvent;
import world.bentobox.bentobox.database.objects.Island;

/**
 * Fired once for each chunk an island unlocks. Not cancellable — the unlock has already
 * been decided by the island level; this is a notification for other plugins.
 *
 * @author tastybento
 */
public class ChunkUnlockEvent extends BentoBoxEvent {

    private static final HandlerList handlers = new HandlerList();

    private final Island island;
    private final Vector chunkOffset;
    private final int spiralIndex;

    /**
     * @param island the island that unlocked the chunk
     * @param chunkOffset the chunk offset relative to the island's center chunk (x and z)
     * @param spiralIndex the chunk's index in the unlock spiral
     */
    public ChunkUnlockEvent(@NonNull Island island, @NonNull Vector chunkOffset, int spiralIndex) {
        this.island = island;
        this.chunkOffset = chunkOffset;
        this.spiralIndex = spiralIndex;
    }

    @Override
    public HandlerList getHandlers() {
        return getHandlerList();
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    /**
     * @return the island that unlocked the chunk
     */
    @NonNull
    public Island getIsland() {
        return island;
    }

    /**
     * @return the chunk offset relative to the island's center chunk
     */
    @NonNull
    public Vector getChunkOffset() {
        return chunkOffset;
    }

    /**
     * @return the chunk's index in the unlock spiral (0 is the center chunk)
     */
    public int getSpiralIndex() {
        return spiralIndex;
    }
}
