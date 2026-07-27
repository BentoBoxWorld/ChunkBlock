package world.bentobox.chunkblock.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import com.nexomc.nexo.api.events.NexoItemsLoadedEvent;
import world.bentobox.chunkblock.ChunkBlock;

/**
 * Handles NexoItemsLoadedEvent which is fired when Nexo loads or reloads its data
 */
public class NexoListener implements Listener {

    private final ChunkBlock addon;

    public NexoListener(ChunkBlock addon) {
        this.addon = addon;
    }

    /**
     * Handle NexoItemsLoadedEvent then reload the addon if it gets triggered
     * @param e - NexoItemsLoadedEvent
     */
    @EventHandler
    public void onLoad(NexoItemsLoadedEvent e) {
        addon.loadData();
    }
}
