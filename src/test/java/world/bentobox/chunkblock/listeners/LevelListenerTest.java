package world.bentobox.chunkblock.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import world.bentobox.chunkblock.chunks.ChunkManager.ClaimResult;
import world.bentobox.chunkblock.dataobjects.OneBlockIslands;
import world.bentobox.chunkblock.events.ChunkRelockEvent;
import world.bentobox.chunkblock.events.ChunkUnlockEvent;

/**
 * Tests the credit-announcement and LIFO re-lock flows in {@link LevelListener} and the
 * claim celebration wiring.
 */
class LevelListenerTest extends CommonTestSetup {

    private ChunkBlock addon;
    private LevelListener listener;
    private OneBlockIslands data;
    private Settings settings;
    private ChunkManager cm;
    private long level;

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
        cm = new ChunkManager(addon);
        when(addon.getChunkManager()).thenReturn(cm);
        data = new OneBlockIslands("test");
        when(addon.getOneBlocksIsland(island)).thenReturn(data);
        when(addon.getBlockListener()).thenReturn(mock(BlockListener.class));
        level = 0;
        when(addon.getIslandLevel(island)).thenAnswer(i -> level);

        when(island.getCenter()).thenReturn(location);
        when(location.getBlockX()).thenReturn(8);
        when(location.getBlockZ()).thenReturn(8);
        when(island.getProtectionRange()).thenReturn(240);
        when(island.getWorld()).thenReturn(world);
        when(world.getPlayers()).thenReturn(Collections.emptyList());

        listener = new LevelListener(addon);
        when(addon.getLevelListener()).thenReturn(listener);
    }

    @Test
    void testLevelGainDoesNotAutoUnlock() {
        level = 5;
        listener.applyLevel(island, 5);
        // Levels are credit, not automatic chunks
        assertEquals(1, data.getUnlockedChunkCount());
        assertEquals(5, cm.getCredit(island));
        verify(pim, never()).callEvent(any(ChunkUnlockEvent.class));
    }

    @Test
    void testLastKnownLevelIsTracked() {
        listener.applyLevel(island, 7);
        assertEquals(7, data.getLastKnownLevel());
        listener.applyLevel(island, 3);
        assertEquals(3, data.getLastKnownLevel());
    }

    @Test
    void testLevelLossRelocksNewestClaimsFirst() {
        level = 3;
        cm.claim(island, 1, 0);
        cm.claim(island, 2, 0);
        cm.claim(island, 3, 0);
        level = 1;
        listener.applyLevel(island, 1);
        assertEquals(2, data.getUnlockedChunkCount());
        assertTrue(data.isChunkUnlocked(1, 0));
        verify(pim, times(2)).callEvent(any(ChunkRelockEvent.class));
    }

    @Test
    void testCenterChunkNeverLocks() {
        level = 2;
        cm.claim(island, 1, 0);
        cm.claim(island, -1, 0);
        level = -100;
        listener.applyLevel(island, -100);
        assertEquals(1, data.getUnlockedChunkCount());
        assertTrue(data.isChunkUnlocked(0, 0));
    }

    @Test
    void testRatchetModeNeverRelocks() {
        settings.setRelockOnLevelLoss(false);
        level = 2;
        cm.claim(island, 1, 0);
        cm.claim(island, -1, 0);
        level = 0;
        listener.applyLevel(island, 0);
        assertEquals(3, data.getUnlockedChunkCount());
        verify(pim, never()).callEvent(any(ChunkRelockEvent.class));
        // And no new claims until the level recovers past what was spent
        assertEquals(ClaimResult.NO_CREDIT, cm.claim(island, 0, 1));
    }

    @Test
    void testLevelLossWithinCreditDoesNotRelock() {
        level = 5;
        cm.claim(island, 1, 0);
        // Level falls but stays at or above the 1 level spent
        level = 2;
        listener.applyLevel(island, 2);
        assertEquals(2, data.getUnlockedChunkCount());
        verify(pim, never()).callEvent(any(ChunkRelockEvent.class));
    }

    @Test
    void testCelebrateClaimFiresUnlockEvent() {
        level = 1;
        cm.claim(island, 1, 0);
        listener.celebrateClaim(island, 1, 0);
        verify(pim).callEvent(any(ChunkUnlockEvent.class));
    }

    @Test
    void testIslandResetClearsClaimsAndLevel() {
        level = 3;
        cm.claim(island, 1, 0);
        cm.claim(island, 2, 0);
        data.setLastKnownLevel(3);
        // Simulate what the island reset handler does
        data.resetUnlockedChunks();
        data.setLastKnownLevel(0);
        assertEquals(1, data.getUnlockedChunkCount());
        assertEquals(0, data.getLastKnownLevel());
    }
}
