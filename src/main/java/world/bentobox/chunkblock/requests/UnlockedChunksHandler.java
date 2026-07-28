package world.bentobox.chunkblock.requests;

import java.util.Collections;
import java.util.List;
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
 * <li>"spent" - Long levels already spent on chunks</li>
 * <li>"credit" - Long level credit available to spend</li>
 * <li>"chunks" - List&lt;String&gt; unlocked chunk offsets ("dx,dz") in claim order</li></ul>
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
        Map<String, Object> result = new HashMap<>();
        result.put("count", cm.getUnlockedChunkCount(island));
        result.put("max", cm.getMaxChunks(island));
        result.put("ring", cm.currentRing(island));
        result.put("spent", cm.getSpentLevels(island));
        result.put("credit", cm.getCredit(island));
        result.put("chunks", List.copyOf(addon.getOneBlocksIsland(island).getUnlockedChunks()));
        return result;
    }
}
