package world.bentobox.chunkblock.listeners;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.bukkit.block.Block;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import world.bentobox.bentobox.api.events.island.IslandCreatedEvent;
import world.bentobox.bentobox.api.events.island.IslandDeleteEvent;
import world.bentobox.bentobox.api.events.island.IslandResettedEvent;
import world.bentobox.chunkblock.ChunkBlock;
import world.bentobox.chunkblock.CommonTestSetup;
import world.bentobox.chunkblock.activity.ActivityManager;
import world.bentobox.chunkblock.activity.CounterType;
import world.bentobox.chunkblock.events.ChunkRelockEvent;
import world.bentobox.chunkblock.events.ChunkUnlockEvent;
import world.bentobox.chunkblock.events.MagicBlockEvent;
import world.bentobox.chunkblock.events.RingCompleteEvent;

/**
 * Checks that the addon's own events are routed into the right counters with the right
 * attribution.
 *
 * @author tastybento
 */
class ActivityListenerTest extends CommonTestSetup {

    @Mock
    private ChunkBlock addon;
    @Mock
    private ActivityManager am;
    @Mock
    private Block block;

    private ActivityListener listener;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        when(addon.getActivityManager()).thenReturn(am);
        when(addon.inWorld(world)).thenReturn(true);
        when(island.getWorld()).thenReturn(world);
        when(island.getUniqueId()).thenReturn("island-id");
        listener = new ActivityListener(addon);
    }

    @Test
    void testMagicBlockBreakByPlayer() {
        listener.onMagicBlock(new MagicBlockEvent(island, uuid, null, block, null));
        verify(am).recordActivity(island, uuid, CounterType.MAGIC_BLOCKS, 1);
    }

    @Test
    void testMagicBlockBreakByMinionIsIslandScope() {
        listener.onMagicBlock(new MagicBlockEvent(island, null, null, block, null));
        verify(am).recordActivity(island, null, CounterType.MAGIC_BLOCKS, 1);
    }

    @Test
    void testChunkUnlockRecordsClaimWithClaimer() {
        listener.onChunkUnlock(new ChunkUnlockEvent(island, new Vector(2, 0, -3), 5, uuid));
        verify(am).recordClaim(island, 2, -3, uuid);
    }

    @Test
    void testChunkRelockIsIslandScope() {
        listener.onChunkRelock(new ChunkRelockEvent(island, new Vector(1, 0, 0), 3));
        verify(am).recordActivity(island, null, CounterType.CHUNKS_RELOCKED, 1);
    }

    @Test
    void testRingCompleteCountsEvenWhenCancelled() {
        RingCompleteEvent event = new RingCompleteEvent(island, 1, 9);
        event.setCancelled(true);
        listener.onRingComplete(event);
        verify(am).recordActivity(island, null, CounterType.RINGS_COMPLETED, 1);
    }

    @Test
    void testWrongWorldIsIgnored() {
        when(addon.inWorld(world)).thenReturn(false);
        listener.onMagicBlock(new MagicBlockEvent(island, uuid, null, block, null));
        listener.onChunkUnlock(new ChunkUnlockEvent(island, new Vector(1, 0, 0), 1, uuid));
        listener.onChunkRelock(new ChunkRelockEvent(island, new Vector(1, 0, 0), 1));
        listener.onRingComplete(new RingCompleteEvent(island, 1, 9));
        verifyNoInteractions(am);
    }

    @Test
    void testIslandCreatedResetsLedger() {
        IslandCreatedEvent event = mock(IslandCreatedEvent.class);
        when(event.getIsland()).thenReturn(island);
        listener.onIslandCreated(event);
        verify(am).resetIsland("island-id");
    }

    @Test
    void testIslandResettedResetsLedger() {
        IslandResettedEvent event = mock(IslandResettedEvent.class);
        when(event.getIsland()).thenReturn(island);
        listener.onIslandResetted(event);
        verify(am).resetIsland("island-id");
    }

    @Test
    void testIslandDeletedDeletesLedger() {
        IslandDeleteEvent event = mock(IslandDeleteEvent.class);
        when(event.getIsland()).thenReturn(island);
        listener.onIslandDeleted(event);
        verify(am).deleteIsland("island-id");
    }

    @Test
    void testUnlockWithoutPlayerStillRecords() {
        listener.onChunkUnlock(new ChunkUnlockEvent(island, new Vector(1, 0, 0), 1));
        verify(am).recordClaim(island, 1, 0, null);
    }

    @Test
    void testNullWorldNeverRecords() {
        when(island.getWorld()).thenReturn(null);
        when(addon.inWorld((org.bukkit.World) null)).thenReturn(false);
        listener.onMagicBlock(new MagicBlockEvent(island, uuid, null, block, null));
        verify(am, never()).recordActivity(any(), any(), any(), anyLong());
    }
}
