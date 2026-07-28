package world.bentobox.chunkblock;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Proves the ChunkBlock placement invariant: whatever distance and start point an admin
 * configures, every island center produced by BentoBox's grid (start + offset + n * 2 *
 * distance on each axis) lands at the middle of a chunk (coordinate ≡ 8 mod 16), so the
 * magic block always sits at a chunk center and the chunk rings are symmetric.
 */
class IslandPlacementTest {

    private Settings s;

    @BeforeEach
    void setUp() {
        s = new Settings();
    }

    private void assertGridChunkCentered() {
        int spacing = 2 * s.getIslandDistance();
        assertEquals(0, spacing % 16, "grid spacing must be a whole number of chunks");
        for (int n = -50; n <= 50; n++) {
            int x = s.getIslandStartX() + s.getIslandXOffset() + n * spacing;
            int z = s.getIslandStartZ() + s.getIslandZOffset() + n * spacing;
            assertEquals(8, Math.floorMod(x, 16), "x center off mid-chunk at grid step " + n);
            assertEquals(8, Math.floorMod(z, 16), "z center off mid-chunk at grid step " + n);
        }
    }

    @Test
    void testDefaultsAreChunkCentered() {
        assertGridChunkCentered();
    }

    @Test
    void testEveryDistanceSnapsToChunkCenteredGrid() {
        for (int distance = 1; distance <= 1000; distance++) {
            s.setIslandDistance(distance);
            assertGridChunkCentered();
        }
    }

    @Test
    void testArbitraryStartPointsStayChunkCentered() {
        for (int start = -100; start <= 100; start += 7) {
            s.setIslandStartX(start);
            s.setIslandStartZ(-start);
            assertGridChunkCentered();
        }
    }
}
