package world.bentobox.chunkblock.panels;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import world.bentobox.bentobox.api.user.User;
import world.bentobox.chunkblock.ChunkBlock;
import world.bentobox.chunkblock.CommonTestSetup;
import world.bentobox.chunkblock.Settings;
import world.bentobox.chunkblock.chunks.ChunkManager;
import world.bentobox.chunkblock.dataobjects.OneBlockIslands;
import world.bentobox.chunkblock.listeners.BlockListener;

/**
 * Tests how far the dialog map reaches, when it scrolls, and when it declines to show at
 * all. What each chunk is and how it is drawn belongs to the shared grid, and is covered
 * by {@code ChunkMapTest}.
 */
class ChunksDialogTest extends CommonTestSetup {

    @Mock
    private User user;
    @Mock
    private ChunkBlock addon;
    @Mock
    private Location playerLocation;

    private ChunkManager cm;
    private long level;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        Settings settings = new Settings();
        when(addon.getSettings()).thenReturn(settings);
        when(addon.getOneBlocksIsland(island)).thenReturn(new OneBlockIslands("test"));
        BlockListener blockListener = mock(BlockListener.class);
        when(addon.getBlockListener()).thenReturn(blockListener);
        level = 0;
        when(addon.getIslandLevel(island)).thenAnswer(i -> level);
        cm = new ChunkManager(addon);
        when(addon.getChunkManager()).thenReturn(cm);

        when(island.getCenter()).thenReturn(location);
        when(island.getWorld()).thenReturn(world);
        when(location.getBlockX()).thenReturn(8);
        when(location.getBlockZ()).thenReturn(8);
        when(playerLocation.getBlockX()).thenReturn(8);
        when(playerLocation.getBlockZ()).thenReturn(8);
        when(playerLocation.getWorld()).thenReturn(world);
        when(user.getLocation()).thenReturn(playerLocation);
        when(world.getName()).thenReturn("chunkblock_world");
    }

    @Test
    void testSmallIslandShowsTheRingAroundTheCenter() {
        when(island.getProtectionRange()).thenReturn(100);
        // maxRingRadius = (100-8)/16 = 5, not scrollable
        assertEquals(9, new ChunksDialog(addon, user, island).cells().size());
    }

    @Test
    void testScrollableIslandShowsFullViewport() {
        when(island.getProtectionRange()).thenReturn(240);
        int width = 2 * ChunksDialog.MAX_RADIUS + 1;
        assertEquals(width * width, new ChunksDialog(addon, user, island).cells().size());
    }

    @Test
    void testIsScrollableWhenTerritoryExceedsViewport() {
        when(island.getProtectionRange()).thenReturn(240);
        assertTrue(new ChunksDialog(addon, user, island).isScrollable());
    }

    @Test
    void testIsNotScrollableWhenTerritoryFitsViewport() {
        when(island.getProtectionRange()).thenReturn(100);
        assertFalse(new ChunksDialog(addon, user, island).isScrollable());
    }

    @Test
    void testPlayerCenteredViewportShiftsMap() {
        when(island.getProtectionRange()).thenReturn(240);
        when(playerLocation.getBlockX()).thenReturn(8 + 160);
        when(playerLocation.getBlockZ()).thenReturn(8);
        level = 100000;
        // Claim enough to reach ring 10
        for (int d = 1; d <= 10; d++) {
            cm.claim(island, d, 0);
        }
        ChunksDialog dialog = new ChunksDialog(addon, user, island, ChunksDialog.ViewMode.PLAYER_CENTER);
        int width = 2 * ChunksDialog.MAX_RADIUS + 1;
        assertEquals(width * width, dialog.cells().size());
        // The player is at chunk offset +10, so the viewport should be centered there.
        // The center chunk (0,0) should be visible if it's within radius of the viewport center.
        // viewDx=10, radius=6 → visible range is 4..16, so island center at 0 is NOT visible.
        boolean centerVisible = dialog.cells().stream()
                .anyMatch(c -> c.dx() == 0 && c.dz() == 0);
        assertFalse(centerVisible);
    }

    @Test
    void testNothingIsShownWhenTheUserIsNotAPlayer() {
        when(island.getProtectionRange()).thenReturn(240);
        when(user.isPlayer()).thenReturn(false);
        assertFalse(ChunksDialog.show(addon, user, island));
    }

    @Test
    void testDirectionGlyphCardinals() {
        assertEquals("▶", ChunksDialog.directionGlyph(10, 0));
        assertEquals("◀", ChunksDialog.directionGlyph(-10, 0));
        assertEquals("▲", ChunksDialog.directionGlyph(0, -10));
        assertEquals("▼", ChunksDialog.directionGlyph(0, 10));
    }

    @Test
    void testDirectionGlyphDiagonals() {
        assertEquals("↗", ChunksDialog.directionGlyph(10, -10));
        assertEquals("↘", ChunksDialog.directionGlyph(10, 10));
        assertEquals("↖", ChunksDialog.directionGlyph(-10, -10));
        assertEquals("↙", ChunksDialog.directionGlyph(-10, 10));
    }

    @Test
    void testMapIsCappedAtTheWidestGridTheDialogHolds() {
        when(island.getProtectionRange()).thenReturn(2000);
        level = 500;
        for (int dx = 1; dx <= 20; dx++) {
            cm.claim(island, dx, 0);
        }
        int width = 2 * ChunksDialog.MAX_RADIUS + 1;
        assertEquals(width * width, new ChunksDialog(addon, user, island).cells().size());
    }

    @Test
    void testPlayerCenteredFallsBackToIslandCenterWhenNotOnIsland() {
        when(island.getProtectionRange()).thenReturn(240);
        when(playerLocation.getWorld()).thenReturn(null);
        // Player is not in the island world → viewport should center on island
        ChunksDialog dialog = new ChunksDialog(addon, user, island, ChunksDialog.ViewMode.PLAYER_CENTER);
        boolean centerVisible = dialog.cells().stream()
                .anyMatch(c -> c.dx() == 0 && c.dz() == 0);
        assertTrue(centerVisible);
    }
}
