package world.bentobox.chunkblock.events;

import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.eclipse.jdt.annotation.NonNull;

import world.bentobox.bentobox.api.events.BentoBoxEvent;
import world.bentobox.bentobox.database.objects.Island;

/**
 * Fired once when an island completes a whole ring of chunks around its center — every
 * chunk at Chebyshev distance {@code ring} from the center chunk is unlocked. Rings are
 * only ever rewarded once per island: re-locking and re-claiming the same chunks does not
 * fire this again.
 * <p>
 * Cancelling suppresses the addon's own milestone handling (reward commands, messages and
 * the celebration); the ring itself stays complete either way.
 *
 * @author tastybento
 */
public class RingCompleteEvent extends BentoBoxEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    private final Island island;
    private final int ring;
    private final int unlockedChunkCount;
    private boolean cancelled;

    /**
     * @param island the island that completed the ring
     * @param ring the ring's radius in chunks, always &gt;= 1
     * @param unlockedChunkCount how many chunks the island has unlocked in total
     */
    public RingCompleteEvent(@NonNull Island island, int ring, int unlockedChunkCount) {
        this.island = island;
        this.ring = ring;
        this.unlockedChunkCount = unlockedChunkCount;
    }

    @Override
    public HandlerList getHandlers() {
        return getHandlerList();
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    /**
     * @return the island that completed the ring
     */
    @NonNull
    public Island getIsland() {
        return island;
    }

    /**
     * @return the completed ring's radius in chunks (ring 1 is the eight chunks around the
     *         center chunk)
     */
    public int getRing() {
        return ring;
    }

    /**
     * @return the island's total unlocked chunk count including the center chunk
     */
    public int getUnlockedChunkCount() {
        return unlockedChunkCount;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
