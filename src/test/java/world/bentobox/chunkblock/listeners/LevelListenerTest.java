package world.bentobox.chunkblock.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.chunkblock.ChunkBlock;
import world.bentobox.chunkblock.CommonTestSetup;
import world.bentobox.chunkblock.Settings;
import world.bentobox.chunkblock.chunks.ChunkManager;
import world.bentobox.chunkblock.dataobjects.OneBlockIslands;
import world.bentobox.chunkblock.events.ChunkRelockEvent;
import world.bentobox.chunkblock.events.ChunkUnlockEvent;
import world.bentobox.chunkblock.listeners.BlockListener;

/**
 * Tests the unlock and re-lock flows in {@link LevelListener}.
 */
class LevelListenerTest extends CommonTestSetup {

    private ChunkBlock addon;
    private LevelListener listener;
    private OneBlockIslands data;
    private Settings settings;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        addon = mock(ChunkBlock.class);
        when(addon.getPlugin()).thenReturn(plugin);
        when(addon.inWorld(world)).thenReturn(true);
        when(addon.getIslands()).thenReturn(im);
        settings = new Settings();
        when(addon.getSettings()).thenReturn(settings);
        ChunkManager cm = new ChunkManager(addon);
        when(addon.getChunkManager()).thenReturn(cm);
        data = new OneBlockIslands("test");
        when(addon.getOneBlocksIsland(island)).thenReturn(data);
        when(addon.getBlockListener()).thenReturn(mock(BlockListener.class));

        when(island.getCenter()).thenReturn(location);
        when(island.getProtectionRange()).thenReturn(240);
        when(island.getWorld()).thenReturn(world);
        when(world.getPlayers()).thenReturn(Collections.emptyList());

        listener = new LevelListener(addon);
    }

    @Test
    void testLevelGainUnlocksChunks() {
        listener.applyLevel(island, 5);
        assertEquals(6, data.getUnlockedChunkCount());
        verify(pim, times(5)).callEvent(any(ChunkUnlockEvent.class));
        verify(pim, never()).callEvent(any(ChunkRelockEvent.class));
    }

    @Test
    void testNoChangeNoEvents() {
        data.setUnlockedChunkCount(6);
        listener.applyLevel(island, 5);
        assertEquals(6, data.getUnlockedChunkCount());
        verify(pim, never()).callEvent(any());
    }

    @Test
    void testLevelLossRelocksChunks() {
        data.setUnlockedChunkCount(10);
        listener.applyLevel(island, 4);
        assertEquals(5, data.getUnlockedChunkCount());
        verify(pim, times(5)).callEvent(any(ChunkRelockEvent.class));
        verify(pim, never()).callEvent(any(ChunkUnlockEvent.class));
    }

    @Test
    void testCenterChunkNeverLocks() {
        data.setUnlockedChunkCount(3);
        listener.applyLevel(island, -100);
        assertEquals(1, data.getUnlockedChunkCount());
    }

    @Test
    void testRatchetModeNeverRelocks() {
        settings.setRelockOnLevelLoss(false);
        data.setUnlockedChunkCount(10);
        listener.applyLevel(island, 0);
        assertEquals(10, data.getUnlockedChunkCount());
        verify(pim, never()).callEvent(any());
    }

    @Test
    void testLevelsPerChunkScaling() {
        settings.setLevelsPerChunk(10);
        listener.applyLevel(island, 35);
        assertEquals(4, data.getUnlockedChunkCount());
    }

    @Test
    void testCapAtMaxChunks() {
        listener.applyLevel(island, 1_000_000);
        assertEquals(441, data.getUnlockedChunkCount());
        verify(pim, times(440)).callEvent(any(ChunkUnlockEvent.class));
    }

    @Test
    void testSmallProtectionRangeCapsUnlocks() {
        when(island.getProtectionRange()).thenReturn(50);
        // Radius (50-8)/16 = 2 → 5x5 = 25 chunks max
        listener.applyLevel(island, 1000);
        assertEquals(25, data.getUnlockedChunkCount());
    }
}
