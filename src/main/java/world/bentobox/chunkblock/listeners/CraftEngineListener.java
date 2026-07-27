package world.bentobox.chunkblock.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import net.momirealms.craftengine.bukkit.api.event.CraftEngineReloadEvent;
import world.bentobox.chunkblock.ChunkBlock;

/**
 * Handles CraftEngineReloadEvent which is fired when CraftEngine loads or reloads its data
 */
public class CraftEngineListener implements Listener {

    private final ChunkBlock addon;

    public CraftEngineListener(ChunkBlock addon) {
        this.addon = addon;
    }

    /**
     * Handle CraftEngineReloadEvent and reload the addon when triggered
     * @param e - CraftEngineReloadEvent
     */
    @EventHandler
    public void onReload(CraftEngineReloadEvent e) {
        addon.loadData();
    }
}
