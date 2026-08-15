package world.bentobox.chunkblock.events;

import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.eclipse.jdt.annotation.NonNull;

import world.bentobox.bentobox.api.events.BentoBoxEvent;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.chunkblock.trophies.Trophy;

/**
 * Fired once when an island earns a trophy. Trophies are earned once and stay earned: the
 * trophy is persisted whether or not this event is cancelled, so it can never be awarded
 * again — mirroring {@link RingCompleteEvent}.
 * <p>
 * Cancelling suppresses the addon's own award handling (messages, the celebration sound
 * and the configured reward commands), so another plugin can take the reward over
 * entirely without the addon also acting.
 *
 * @author tastybento
 */
public class TrophyAwardEvent extends BentoBoxEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    private final Island island;
    private final Trophy trophy;
    private boolean cancelled;

    /**
     * @param island the island that earned the trophy
     * @param trophy the trophy earned
     */
    public TrophyAwardEvent(@NonNull Island island, @NonNull Trophy trophy) {
        this.island = island;
        this.trophy = trophy;
    }

    @Override
    public HandlerList getHandlers() {
        return getHandlerList();
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    /**
     * @return the island that earned the trophy
     */
    @NonNull
    public Island getIsland() {
        return island;
    }

    /**
     * @return the trophy earned
     */
    @NonNull
    public Trophy getTrophy() {
        return trophy;
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
