package world.bentobox.chunkblock.panels;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

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
import world.bentobox.chunkblock.panels.ChunksDialog.Cell;
import world.bentobox.chunkblock.panels.ChunksDialog.Kind;

/**
 * Tests the grid behind the {@code /ch chunks} dialog — what each chunk button is, and how
 * far the map reaches.
 */
class ChunksDialogTest extends CommonTestSetup {

    @Mock
    private User user;
    @Mock
    private ChunkBlock addon;
    @Mock
    private Location playerLocation;

    private OneBlockIslands data;
    private ChunkManager cm;
    private long level;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        Settings settings = new Settings();
        when(addon.getSettings()).thenReturn(settings);
        data = new OneBlockIslands("test");
        when(addon.getOneBlocksIsland(island)).thenReturn(data);
        when(addon.getBlockListener()).thenReturn(mock(BlockListener.class));
        level = 0;
        when(addon.getIslandLevel(island)).thenAnswer(i -> level);
        cm = new ChunkManager(addon);
        when(addon.getChunkManager()).thenReturn(cm);

        // Island center chunk-centered at chunk (0, 0)
        when(island.getCenter()).thenReturn(location);
        when(island.getWorld()).thenReturn(world);
        when(location.getBlockX()).thenReturn(8);
        when(location.getBlockZ()).thenReturn(8);
        when(island.getProtectionRange()).thenReturn(240);
        // The player's own location is a separate mock, so moving them leaves the island put
        when(playerLocation.getBlockX()).thenReturn(8);
        when(playerLocation.getBlockZ()).thenReturn(8);
        when(playerLocation.getWorld()).thenReturn(world);
        when(user.getLocation()).thenReturn(playerLocation);
        when(world.getName()).thenReturn("chunkblock_world");
    }

    @Test
    void testFreshIslandOffersTheChunksTouchingTheCenter() {
        List<Cell> cells = new ChunksDialog(addon, user, island).cells();
        // One claimed chunk, so the map reaches one ring out — 3 x 3
        assertEquals(9, cells.size());
        assertEquals(Kind.CENTER, kindOf(cells, 0, 0));
        // Claims run along the edges, so the four side-by-side chunks are the frontier...
        for (int[] offset : new int[][] { { 1, 0 }, { 0, 1 }, { -1, 0 }, { 0, -1 } }) {
            assertEquals(Kind.CLAIMABLE, kindOf(cells, offset[0], offset[1]),
                    "chunk " + offset[0] + "," + offset[1]);
        }
        // ...and the corners, which touch nothing but a diagonal, are not
        for (int[] offset : new int[][] { { 1, 1 }, { -1, 1 }, { -1, -1 }, { 1, -1 } }) {
            assertEquals(Kind.LOCKED, kindOf(cells, offset[0], offset[1]), "chunk " + offset[0] + "," + offset[1]);
        }
    }

    @Test
    void testClaimedChunksAreOwnedAndTheMapGrowsOutwards() {
        level = 8;
        for (int[] offset : new int[][] { { 1, 0 }, { 0, 1 }, { -1, 0 }, { 0, -1 }, { 1, 1 }, { -1, 1 }, { -1, -1 },
                { 1, -1 } }) {
            cm.claim(island, offset[0], offset[1]);
        }
        List<Cell> cells = new ChunksDialog(addon, user, island).cells();
        // Ring 1 claimed, so the map reaches ring 2 — 5 x 5
        assertEquals(25, cells.size());
        assertEquals(Kind.CENTER, kindOf(cells, 0, 0));
        assertEquals(Kind.OWNED, kindOf(cells, 1, 0));
        assertEquals(Kind.CLAIMABLE, kindOf(cells, 2, 0));
    }

    @Test
    void testChunksOutOfReachAreLocked() {
        // Only one chunk claimed, so the far side of a 5 x 5 map is nobody's frontier
        level = 8;
        cm.claim(island, 1, 0);
        List<Cell> cells = new ChunksDialog(addon, user, island).cells();
        assertEquals(25, cells.size());
        assertEquals(Kind.LOCKED, kindOf(cells, -2, -2));
        assertEquals(Kind.CLAIMABLE, kindOf(cells, 2, 0));
    }

    @Test
    void testMapIsCappedAtTheWidestGridTheDialogHolds() {
        when(island.getProtectionRange()).thenReturn(2000);
        level = 500;
        for (int dx = 1; dx <= 20; dx++) {
            cm.claim(island, dx, 0);
        }
        List<Cell> cells = new ChunksDialog(addon, user, island).cells();
        int width = 2 * ChunksDialog.MAX_RADIUS + 1;
        assertEquals(width * width, cells.size());
    }

    @Test
    void testThePlayersOwnChunkIsMarked() {
        // Stand one chunk east of the center
        when(playerLocation.getBlockX()).thenReturn(24);
        List<Cell> cells = new ChunksDialog(addon, user, island).cells();
        assertTrue(cellAt(cells, 1, 0).here());
        assertFalse(cellAt(cells, 0, 0).here());
        // The mark rides on top of what the chunk is, it does not replace it
        assertEquals(Kind.CLAIMABLE, kindOf(cells, 1, 0));
        assertEquals("&b◆", ChunksDialog.glyphReference(cellAt(cells, 1, 0)));
        assertEquals("&6◎", ChunksDialog.glyphReference(cellAt(cells, 0, 0)));
    }

    @Test
    void testAPlayerInAnotherWorldMarksNoChunk() {
        org.bukkit.World elsewhere = mock(org.bukkit.World.class);
        when(elsewhere.getName()).thenReturn("somewhere_else");
        when(playerLocation.getWorld()).thenReturn(elsewhere);
        List<Cell> cells = new ChunksDialog(addon, user, island).cells();
        assertTrue(cells.stream().noneMatch(Cell::here));
    }

    @Test
    void testGlyphsMatchTheChatMapLegend() {
        assertEquals("&a■", ChunksDialog.glyphReference(new Cell(1, 0, Kind.OWNED, false)));
        assertEquals("&e▣", ChunksDialog.glyphReference(new Cell(1, 0, Kind.CLAIMABLE, false)));
        assertEquals("&7□", ChunksDialog.glyphReference(new Cell(1, 0, Kind.LOCKED, false)));
        assertEquals("&b◉", ChunksDialog.glyphReference(new Cell(0, 0, Kind.CENTER, true)));
        assertEquals("&b◇", ChunksDialog.glyphReference(new Cell(3, 3, Kind.LOCKED, true)));
    }

    @Test
    void testNothingIsShownWhenTheUserIsNotAPlayer() {
        when(user.isPlayer()).thenReturn(false);
        assertFalse(ChunksDialog.show(addon, user, island));
    }

    @Test
    void testCellsAreOrderedRowByRowSoTheGridComesOutNorthUp() {
        List<Cell> cells = new ChunksDialog(addon, user, island).cells();
        // The dialog lays buttons out in list order, filling each row left to right
        assertEquals(new Cell(-1, -1, Kind.LOCKED, false), cells.get(0));
        assertEquals(new Cell(0, -1, Kind.CLAIMABLE, false), cells.get(1));
        assertEquals(new Cell(-1, 0, Kind.CLAIMABLE, false), cells.get(3));
        assertEquals(new Cell(1, 1, Kind.LOCKED, false), cells.get(8));
    }

    private Cell cellAt(List<Cell> cells, int dx, int dz) {
        return cells.stream().filter(c -> c.dx() == dx && c.dz() == dz).findFirst()
                .orElseThrow(() -> new AssertionError("no cell at " + dx + "," + dz));
    }

    private Kind kindOf(List<Cell> cells, int dx, int dz) {
        return cellAt(cells, dx, dz).kind();
    }
}
