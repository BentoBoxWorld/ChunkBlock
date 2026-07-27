package world.bentobox.chunkblock.requests;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import world.bentobox.bentobox.api.addons.request.AddonRequestHandler;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.chunkblock.ChunkBlock;
import world.bentobox.chunkblock.chunks.ChunkManager;

/**
 * Tells other plugins how big a player's island territory is.<br>
 * Submit "player" -&gt; UUID to {@link #handle(Map)}.<br>
 * Return map is a {@code Map<String, Object>} of the following:
 * <ul><li>"count" - Integer number of unlocked chunks including the center</li>
 * <li>"max" - Integer maximum unlockable chunks for the island</li>
 * <li>"ring" - Integer ring number of the outermost unlocked chunk</li>
 * <li>"nextLevel" - Long island level needed for the next chunk</li></ul>
 *
 * @author tastybento
 */
public class UnlockedChunksHandler extends AddonRequestHandler {

    private static final String PLAYER = "player";

    private final ChunkBlock addon;

    public UnlockedChunksHandler(ChunkBlock addon) {
        super("unlocked-chunks");
        this.addon = addon;
    }

    @Override
    public Object handle(Map<String, Object> map) {
        if (map == null || map.isEmpty() || !(map.get(PLAYER) instanceof UUID uuid)) {
            return Collections.emptyMap();
        }
        Island island = addon.getIslands().getIsland(addon.getOverWorld(), uuid);
        if (island == null) {
            return Collections.emptyMap();
        }
        ChunkManager cm = addon.getChunkManager();
        int count = cm.getUnlockedChunkCount(island);
        Map<String, Object> result = new HashMap<>();
        result.put("count", count);
        result.put("max", cm.getMaxChunks(island));
        result.put("ring", ChunkManager.ringOf(count - 1));
        result.put("nextLevel", cm.levelForChunkNumber(count + 1));
        return result;
    }
}
