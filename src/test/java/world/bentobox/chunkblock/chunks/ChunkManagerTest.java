package world.bentobox.chunkblock.chunks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.chunkblock.ChunkBlock;
import world.bentobox.chunkblock.Settings;
import world.bentobox.chunkblock.chunks.ChunkManager.ClaimResult;
import world.bentobox.chunkblock.dataobjects.OneBlockIslands;
import world.bentobox.chunkblock.listeners.BlockListener;

/**
 * Tests claiming, credit accounting and LIFO re-locking in {@link ChunkManager}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChunkManagerTest {

    @Mock
    private ChunkBlock addon;
    @Mock
    private Island island;
    @Mock
    private Location center;

    private Settings settings;
    private OneBlockIslands data;
    private ChunkManager cm;
    private long level;

    @BeforeEach
    void setUp() {
        settings = new Settings();
        data = new OneBlockIslands("test");
        level = 0;
        when(addon.getSettings()).thenReturn(settings);
        when(addon.getOneBlocksIsland(island)).thenReturn(data);
        when(addon.getBlockListener()).thenReturn(mock(BlockListener.class));
        when(addon.getIslandLevel(island)).thenAnswer(i -> level);
        // Island center chunk-centered at chunk (0, 0)
        when(center.getBlockX()).thenReturn(8);
        when(center.getBlockZ()).thenReturn(8);
        when(island.getCenter()).thenReturn(center);
        when(island.getProtectionRange()).thenReturn(240);
        cm = new ChunkManager(addon);
    }

    @Test
    void testFreshIslandHasOnlyCenterChunk() {
        assertEquals(1, cm.getUnlockedChunkCount(island));
        assertTrue(cm.isUnlocked(island, 0, 0));
        assertFalse(cm.isUnlocked(island, 1, 0));
        assertFalse(cm.isUnlocked(island, 0, -1));
        assertEquals(0, cm.getSpentLevels(island));
        assertEquals(0, cm.currentRing(island));
    }

    @Test
    void testClaimAdjacentChunkWithCredit() {
        level = 1;
        assertEquals(ClaimResult.OK, cm.claim(island, 1, 0));
        assertTrue(cm.isUnlocked(island, 1, 0));
        assertEquals(2, cm.getUnlockedChunkCount(island));
        assertEquals(1, cm.getSpentLevels(island));
        assertEquals(0, cm.getCredit(island));
    }

    @Test
    void testClaimAnyDirection() {
        level = 4;
        assertEquals(ClaimResult.OK, cm.claim(island, 0, -1)); // north
        assertEquals(ClaimResult.OK, cm.claim(island, 0, -2)); // further north
        assertEquals(ClaimResult.OK, cm.claim(island, -1, 0)); // west
        assertEquals(ClaimResult.OK, cm.claim(island, 1, 0)); // east
        assertEquals(5, cm.getUnlockedChunkCount(island));
        // A long arm north means ring 2 even though most rings are unclaimed
        assertEquals(2, cm.currentRing(island));
    }

    @Test
    void testClaimWithoutCreditDenied() {
        assertEquals(ClaimResult.NO_CREDIT, cm.claim(island, 1, 0));
        assertFalse(cm.isUnlocked(island, 1, 0));
        // Credit spent on one chunk cannot be spent on a second
        level = 1;
        assertEquals(ClaimResult.OK, cm.claim(island, 1, 0));
        assertEquals(ClaimResult.NO_CREDIT, cm.claim(island, -1, 0));
    }

    @Test
    void testClaimNotAdjacentDenied() {
        level = 100;
        assertEquals(ClaimResult.NOT_ADJACENT, cm.claim(island, 2, 0));
        // Diagonal-only contact is not adjacency
        assertEquals(ClaimResult.NOT_ADJACENT, cm.claim(island, 1, 1));
        // But once the gap chunk is claimed, both become claimable
        assertEquals(ClaimResult.OK, cm.claim(island, 1, 0));
        assertEquals(ClaimResult.OK, cm.claim(island, 2, 0));
        assertEquals(ClaimResult.OK, cm.claim(island, 1, 1));
    }

    @Test
    void testClaimAlreadyUnlockedDenied() {
        level = 100;
        assertEquals(ClaimResult.ALREADY_UNLOCKED, cm.claim(island, 0, 0));
    }

    @Test
    void testClaimBeyondProtectionRangeDenied() {
        level = 100000;
        when(island.getProtectionRange()).thenReturn(50);
        // Radius (50-8)/16 = 2: chunk offset 3 is out of bounds
        for (int d = 1; d <= 2; d++) {
            assertEquals(ClaimResult.OK, cm.claim(island, d, 0), "offset " + d);
        }
        assertEquals(ClaimResult.BEYOND_LIMIT, cm.claim(island, 3, 0));
    }

    @Test
    void testClaimBeyondMaxChunksDenied() {
        level = 100000;
        settings.setMaxChunks(3);
        assertEquals(ClaimResult.OK, cm.claim(island, 1, 0));
        assertEquals(ClaimResult.OK, cm.claim(island, -1, 0));
        assertEquals(ClaimResult.BEYOND_LIMIT, cm.claim(island, 0, 1));
    }

    @Test
    void testLevelsPerChunkCost() {
        settings.setLevelsPerChunk(10);
        level = 19;
        assertEquals(ClaimResult.OK, cm.claim(island, 1, 0));
        // 19 - 10 spent = 9 credit; next chunk costs 10
        assertEquals(9, cm.getCredit(island));
        assertEquals(ClaimResult.NO_CREDIT, cm.claim(island, -1, 0));
    }

    @Test
    void testRelockToBudgetIsLifo() {
        level = 3;
        cm.claim(island, 1, 0);
        cm.claim(island, 2, 0);
        cm.claim(island, 3, 0);
        // Level drops to 1: two most recent claims are lost, newest first
        List<Vector> removed = cm.relockToBudget(island, 1);
        assertEquals(2, removed.size());
        assertEquals(new Vector(3, 0, 0), removed.get(0));
        assertEquals(new Vector(2, 0, 0), removed.get(1));
        assertTrue(cm.isUnlocked(island, 1, 0));
        assertFalse(cm.isUnlocked(island, 2, 0));
        assertFalse(cm.isUnlocked(island, 3, 0));
    }

    @Test
    void testRelockNeverRemovesCenterChunk() {
        level = 2;
        cm.claim(island, 1, 0);
        cm.claim(island, -1, 0);
        List<Vector> removed = cm.relockToBudget(island, -50);
        assertEquals(2, removed.size());
        assertEquals(1, cm.getUnlockedChunkCount(island));
        assertTrue(cm.isUnlocked(island, 0, 0));
        assertNull(data.removeLastUnlockedChunk());
    }

    @Test
    void testRelockNoopWhenWithinBudget() {
        level = 5;
        cm.claim(island, 1, 0);
        assertTrue(cm.relockToBudget(island, 5).isEmpty());
        assertTrue(cm.relockToBudget(island, 1).isEmpty());
    }

    @Test
    void testUnlockOrderIsRecorded() {
        level = 3;
        cm.claim(island, 0, -1);
        cm.claim(island, 1, 0);
        cm.claim(island, 1, -1);
        assertEquals(List.of("0,0", "0,-1", "1,0", "1,-1"), data.getUnlockedChunks());
    }

    @Test
    void testOffsetIslandCenter() {
        // Island centered at chunk (32, -16)
        when(center.getBlockX()).thenReturn(32 * 16 + 8);
        when(center.getBlockZ()).thenReturn(-16 * 16 + 8);
        level = 1;
        assertTrue(cm.isUnlocked(island, 32, -16));
        assertFalse(cm.isUnlocked(island, 33, -16));
        assertEquals(ClaimResult.OK, cm.claim(island, 33, -16));
        assertTrue(cm.isUnlocked(island, 33, -16));
    }

    @Test
    void testMaxChunksCappedByProtectionRange() {
        assertEquals(441, cm.getMaxChunks(island));
        when(island.getProtectionRange()).thenReturn(50);
        assertEquals(25, cm.getMaxChunks(island));
        settings.setMaxChunks(-1);
        when(island.getProtectionRange()).thenReturn(240);
        assertEquals(841, cm.getMaxChunks(island));
    }

    @Test
    void testGetUnlockedOffsets() {
        level = 2;
        cm.claim(island, 1, 0);
        cm.claim(island, 1, 1);
        List<Vector> offsets = cm.getUnlockedOffsets(island);
        assertEquals(3, offsets.size());
        assertEquals(new Vector(0, 0, 0), offsets.get(0));
        assertEquals(new Vector(1, 0, 0), offsets.get(1));
        assertEquals(new Vector(1, 0, 1), offsets.get(2));
    }
}
