package world.bentobox.chunkblock.requests;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import world.bentobox.bentobox.api.addons.request.AddonRequestHandler;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.chunkblock.ChunkBlock;
import world.bentobox.chunkblock.activity.CounterType;

/**
 * Answers "how much of this activity did this member do for this island in the last N
 * days" for other plugins.<br>
 * Submit to {@link #handle(Map)}:
 * <ul>
 * <li>"island-id" - String island uniqueId, OR "player" - UUID of an island member (the
 * member's island in this gamemode's world is used)</li>
 * <li>"member" - UUID (optional; omit for the island's total)</li>
 * <li>"counter" - String, a {@link CounterType} name</li>
 * <li>"days" - Integer (optional; how many days back to count, today included; omit or
 * &lt;= 0 for lifetime)</li>
 * </ul>
 * Returns a Long amount, or null if the island or counter cannot be resolved.
 *
 * @author tastybento
 */
public class MemberActivityHandler extends AddonRequestHandler {

    private static final String ISLAND_ID = "island-id";
    private static final String PLAYER = "player";
    private static final String MEMBER = "member";
    private static final String COUNTER = "counter";
    private static final String DAYS = "days";

    private final ChunkBlock addon;

    public MemberActivityHandler(ChunkBlock addon) {
        super("member-activity");
        this.addon = addon;
    }

    @Override
    public Object handle(Map<String, Object> map) {
        if (map == null || !(map.get(COUNTER) instanceof String counterName)) {
            return null;
        }
        CounterType counter;
        try {
            counter = CounterType.valueOf(counterName.toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException e) {
            return null;
        }
        Island island = getIsland(map);
        if (island == null) {
            return null;
        }
        UUID member = map.get(MEMBER) instanceof UUID uuid ? uuid : null;
        int days = map.get(DAYS) instanceof Number number ? number.intValue() : 0;
        return addon.getActivityManager().getCount(island, member, counter, days);
    }

    private Island getIsland(Map<String, Object> map) {
        if (map.get(ISLAND_ID) instanceof String id) {
            return addon.getIslands().getIslandById(id).orElse(null);
        }
        if (map.get(PLAYER) instanceof UUID uuid) {
            return addon.getIslands().getIsland(addon.getOverWorld(), uuid);
        }
        return null;
    }
}
