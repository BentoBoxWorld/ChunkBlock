package world.bentobox.chunkblock.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import world.bentobox.bentobox.api.events.island.IslandCreatedEvent;
import world.bentobox.bentobox.api.events.island.IslandDeleteEvent;
import world.bentobox.bentobox.api.events.island.IslandResettedEvent;
import world.bentobox.chunkblock.ChunkBlock;
import world.bentobox.chunkblock.activity.CounterType;
import world.bentobox.chunkblock.events.ChunkRelockEvent;
import world.bentobox.chunkblock.events.ChunkUnlockEvent;
import world.bentobox.chunkblock.events.MagicBlockEvent;
import world.bentobox.chunkblock.events.RingCompleteEvent;

/**
 * Feeds the addon's own events into the activity counters. Everything here runs at
 * MONITOR: counters observe what happened, they never change it.
 * <p>
 * Level gains are the one write not routed through this listener — the level delta is
 * only known inside {@code LevelListener#applyLevel}, which records it directly.
 *
 * @author tastybento
 */
public class ActivityListener implements Listener {

    private final ChunkBlock addon;

    public ActivityListener(ChunkBlock addon) {
        this.addon = addon;
    }

    /**
     * A magic block was broken: credited to the breaking member, or to the island itself
     * when a minion did the breaking.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onMagicBlock(MagicBlockEvent e) {
        if (addon.inWorld(e.getIsland().getWorld())) {
            addon.getActivityManager().record(e.getIsland(), e.getPlayerUUID(), CounterType.MAGIC_BLOCKS, 1);
        }
    }

    /**
     * A chunk was claimed: a first-ever claim and the recovery of a re-locked chunk are
     * scored as different counters, so nothing is ever counted twice.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnlock(ChunkUnlockEvent e) {
        if (addon.inWorld(e.getIsland().getWorld())) {
            addon.getActivityManager().recordClaim(e.getIsland(), e.getChunkOffset().getBlockX(),
                    e.getChunkOffset().getBlockZ(), e.getPlayerUUID());
        }
    }

    /**
     * A chunk was lost to level loss. Island scope — nobody performs a re-lock.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkRelock(ChunkRelockEvent e) {
        if (addon.inWorld(e.getIsland().getWorld())) {
            addon.getActivityManager().record(e.getIsland(), null, CounterType.CHUNKS_RELOCKED, 1);
        }
    }

    /**
     * A ring was completed for the first time. Counted even if a plugin cancels the
     * addon's reward handling: the completion is a fact, and counters are data, not
     * reward.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRingComplete(RingCompleteEvent e) {
        if (addon.inWorld(e.getIsland().getWorld())) {
            addon.getActivityManager().record(e.getIsland(), null, CounterType.RINGS_COMPLETED, 1);
        }
    }

    /**
     * A brand-new island starts with an empty ledger.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onIslandCreated(IslandCreatedEvent e) {
        if (addon.inWorld(e.getIsland().getWorld())) {
            addon.getActivityManager().resetIsland(e.getIsland().getUniqueId());
        }
    }

    /**
     * A reset island starts its ledger over.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onIslandResetted(IslandResettedEvent e) {
        if (addon.inWorld(e.getIsland().getWorld())) {
            addon.getActivityManager().resetIsland(e.getIsland().getUniqueId());
        }
    }

    /**
     * A deleted island takes its ledger with it.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onIslandDeleted(IslandDeleteEvent e) {
        if (addon.inWorld(e.getIsland().getWorld())) {
            addon.getActivityManager().deleteIsland(e.getIsland().getUniqueId());
        }
    }
}
