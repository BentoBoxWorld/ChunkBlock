package world.bentobox.chunkblock.requests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import world.bentobox.chunkblock.ChunkBlock;
import world.bentobox.chunkblock.CommonTestSetup;
import world.bentobox.chunkblock.activity.ActivityManager;
import world.bentobox.chunkblock.activity.CounterType;

/**
 * The API check for the counters foundation: another plugin can ask "how many chunks did
 * this member claim for this island in the last 7 days" and get an answer.
 *
 * @author tastybento
 */
class MemberActivityHandlerTest extends CommonTestSetup {

    @Mock
    private ChunkBlock addon;
    @Mock
    private ActivityManager am;

    private MemberActivityHandler handler;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        when(addon.getIslands()).thenReturn(im);
        when(addon.getOverWorld()).thenReturn(world);
        when(addon.getActivityManager()).thenReturn(am);
        when(im.getIslandById("island-id")).thenReturn(Optional.of(island));
        when(im.getIsland(world, uuid)).thenReturn(island);
        handler = new MemberActivityHandler(addon);
    }

    @Test
    void testMemberWindowQueryByIslandId() {
        when(am.getCount(island, uuid, CounterType.CHUNKS_CLAIMED, 7)).thenReturn(3L);
        Object result = handler.handle(Map.of("island-id", "island-id", "member", uuid,
                "counter", "CHUNKS_CLAIMED", "days", 7));
        assertEquals(3L, result);
    }

    @Test
    void testIslandLifetimeQueryByPlayer() {
        when(am.getCount(island, null, CounterType.MAGIC_BLOCKS, 0)).thenReturn(500L);
        Object result = handler.handle(Map.of("player", uuid, "counter", "MAGIC_BLOCKS"));
        assertEquals(500L, result);
    }

    @Test
    void testCounterNameIsCaseInsensitive() {
        when(am.getCount(island, null, CounterType.LEVELS_EARNED, 0)).thenReturn(9L);
        assertEquals(9L, handler.handle(Map.of("island-id", "island-id", "counter", "levels_earned")));
    }

    @Test
    void testBadInputReturnsNull() {
        assertNull(handler.handle(null));
        assertNull(handler.handle(Map.of("island-id", "island-id")));
        assertNull(handler.handle(Map.of("island-id", "island-id", "counter", "NOT_A_COUNTER")));
        assertNull(handler.handle(Map.of("counter", "MAGIC_BLOCKS")));
        when(im.getIslandById("gone")).thenReturn(Optional.empty());
        assertNull(handler.handle(Map.of("island-id", "gone", "counter", "MAGIC_BLOCKS")));
    }
}
