package world.bentobox.chunkblock.chunks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
import world.bentobox.chunkblock.dataobjects.OneBlockIslands;

/**
 * Tests for the spiral math and chunk counting in {@link ChunkManager}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChunkManagerTest {

    private static final int EXHAUSTIVE_LIMIT = 10000;

    @Mock
    private ChunkBlock addon;
    @Mock
    private Island island;
    @Mock
    private Location center;

    private Settings settings;
    private OneBlockIslands data;
    private ChunkManager cm;

    @BeforeEach
    void setUp() {
        settings = new Settings();
        data = new OneBlockIslands("test");
        when(addon.getSettings()).thenReturn(settings);
        when(addon.getOneBlocksIsland(island)).thenReturn(data);
        // Island center chunk-centered at chunk (0, 0)
        when(center.getBlockX()).thenReturn(8);
        when(center.getBlockZ()).thenReturn(8);
        when(island.getCenter()).thenReturn(center);
        when(island.getProtectionRange()).thenReturn(240);
        cm = new ChunkManager(addon);
    }

    /**
     * spiralIndex and chunkAt must be exact inverses over the whole tested range.
     */
    @Test
    void testSpiralRoundTripExhaustive() {
        for (int i = 0; i < EXHAUSTIVE_LIMIT; i++) {
            Vector v = ChunkManager.chunkAt(i);
            assertEquals(i, ChunkManager.spiralIndex(v.getBlockX(), v.getBlockZ()),
                    "chunkAt(" + i + ") = " + v + " does not map back");
        }
    }

    /**
     * Every offset in a large square must map to a unique index, and the index must be in
     * range for the square's rings.
     */
    @Test
    void testSpiralIndexUniqueAndComplete() {
        int radius = 49; // (2*49+1)^2 = 9801 indices
        Set<Integer> seen = new HashSet<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int index = ChunkManager.spiralIndex(dx, dz);
                assertTrue(index >= 0 && index < (2 * radius + 1) * (2 * radius + 1),
                        "index out of range at " + dx + "," + dz);
                assertTrue(seen.add(index), "duplicate index " + index + " at " + dx + "," + dz);
            }
        }
        assertEquals((2 * radius + 1) * (2 * radius + 1), seen.size());
    }

    /**
     * The spiral must fill rings completely before starting the next one, and the index of
     * any chunk on ring r must be at least the index of every chunk on rings inside r.
     */
    @Test
    void testRingsFillInOrder() {
        for (int i = 1; i < EXHAUSTIVE_LIMIT; i++) {
            Vector v = ChunkManager.chunkAt(i);
            int r = Math.max(Math.abs(v.getBlockX()), Math.abs(v.getBlockZ()));
            assertEquals(r, ChunkManager.ringOf(i));
            // All indices in [(2r-1)^2, (2r+1)^2) are exactly ring r
            assertTrue(i >= (2 * r - 1) * (2 * r - 1), "index " + i + " below its ring band");
            assertTrue(i < (2 * r + 1) * (2 * r + 1), "index " + i + " above its ring band");
        }
    }

    @Test
    void testCenterIsIndexZero() {
        assertEquals(0, ChunkManager.spiralIndex(0, 0));
        assertEquals(new Vector(0, 0, 0), ChunkManager.chunkAt(0));
        // Ring 1 anchor is due north of the center
        assertEquals(new Vector(0, 0, -1), ChunkManager.chunkAt(1));
    }

    @Test
    void testIsUnlockedFreshIsland() {
        // Fresh island: only the center chunk (containing block 8,8 → chunk 0,0)
        assertTrue(cm.isUnlocked(island, 0, 0));
        assertFalse(cm.isUnlocked(island, 0, -1));
        assertFalse(cm.isUnlocked(island, 1, 0));
        assertFalse(cm.isUnlocked(island, 1000, 1000));
    }

    @Test
    void testIsUnlockedAfterUnlocks() {
        data.setUnlockedChunkCount(9); // full ring 1
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                assertTrue(cm.isUnlocked(island, dx, dz), "ring-1 chunk " + dx + "," + dz);
            }
        }
        assertFalse(cm.isUnlocked(island, 2, 0));
        assertFalse(cm.isUnlocked(island, -2, -2));
    }

    @Test
    void testComputeUnlockedCount() {
        assertEquals(1, cm.computeUnlockedCount(island, 0));
        assertEquals(1, cm.computeUnlockedCount(island, -50));
        assertEquals(2, cm.computeUnlockedCount(island, 1));
        assertEquals(101, cm.computeUnlockedCount(island, 100));
        // Caps at max-chunks (441 by default)
        assertEquals(441, cm.computeUnlockedCount(island, 100000));
    }

    @Test
    void testComputeUnlockedCountLevelsPerChunk() {
        settings.setLevelsPerChunk(10);
        assertEquals(1, cm.computeUnlockedCount(island, 9));
        assertEquals(2, cm.computeUnlockedCount(island, 10));
        assertEquals(3, cm.computeUnlockedCount(island, 25));
    }

    @Test
    void testMaxChunksCappedByProtectionRange() {
        // Protection range 240 → ring radius (240-8)/16 = 14 → cap 841; config 441 wins
        assertEquals(441, cm.getMaxChunks(island));
        // Small protection range caps below config: 50 → radius 2 → 25 chunks
        when(island.getProtectionRange()).thenReturn(50);
        assertEquals(25, cm.getMaxChunks(island));
        // Unlimited config uses the range cap alone
        settings.setMaxChunks(-1);
        when(island.getProtectionRange()).thenReturn(240);
        assertEquals(841, cm.getMaxChunks(island));
    }

    @Test
    void testMaxRingRadius() {
        when(island.getProtectionRange()).thenReturn(240);
        assertEquals(14, cm.maxRingRadius(island));
        when(island.getProtectionRange()).thenReturn(168);
        assertEquals(10, cm.maxRingRadius(island));
        when(island.getProtectionRange()).thenReturn(8);
        assertEquals(0, cm.maxRingRadius(island));
    }

    @Test
    void testLevelForChunkNumber() {
        assertEquals(0, cm.levelForChunkNumber(1));
        assertEquals(1, cm.levelForChunkNumber(2));
        settings.setLevelsPerChunk(5);
        assertEquals(5, cm.levelForChunkNumber(2));
        assertEquals(50, cm.levelForChunkNumber(11));
    }

    @Test
    void testChunksBetween() {
        List<Vector> gained = cm.chunksBetween(1, 9);
        assertEquals(8, gained.size());
        assertEquals(new Vector(0, 0, -1), gained.get(0));
        // Re-lock order is just the reverse view of the same list
        assertTrue(cm.chunksBetween(5, 5).isEmpty());
        assertTrue(cm.chunksBetween(9, 5).isEmpty());
    }

    @Test
    void testIsUnlockedOffsetIslandCenter() {
        // Island centered at chunk (32, -16): block coords 32*16+8, -16*16+8
        when(center.getBlockX()).thenReturn(32 * 16 + 8);
        when(center.getBlockZ()).thenReturn(-16 * 16 + 8);
        assertTrue(cm.isUnlocked(island, 32, -16));
        assertFalse(cm.isUnlocked(island, 33, -16));
        data.setUnlockedChunkCount(2);
        // Index 1 is due north of the center
        assertTrue(cm.isUnlocked(island, 32, -17));
    }
}
