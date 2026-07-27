package world.bentobox.chunkblock.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import dev.lone.itemsadder.api.Events.ItemsAdderLoadDataEvent;
import world.bentobox.chunkblock.ChunkBlock;

/**
 * Handles ItemsAdderLoadDataEvent which fired when ItemsAdder loaded its data or reload its data
 *
 * @author Teenkung123
 */
public class ItemsAdderListener implements Listener {

    private final ChunkBlock addon;
    public ItemsAdderListener(ChunkBlock addon) {
        this.addon = addon;
    }

    /**
     * handle ItemsAdderLoadDataEvent then reload the addon if it's get triggered
     * @param e - ItemsAdderLoadDataEvent
     */
    @EventHandler
    public void onLoad(ItemsAdderLoadDataEvent e) {
        addon.loadData();
    }

}
