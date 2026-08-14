package world.bentobox.chunkblock.chunks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import world.bentobox.chunkblock.ChunkBlock;
import world.bentobox.chunkblock.CommonTestSetup;
import world.bentobox.chunkblock.Settings;
import world.bentobox.chunkblock.chunks.ChunkMap.Cell;
import world.bentobox.chunkblock.chunks.ChunkMap.Kind;
import world.bentobox.chunkblock.dataobjects.OneBlockIslands;
import world.bentobox.chunkblock.listeners.BlockListener;

/**
 * Tests the territory grid behind both maps of {@code /ch chunks} — what each chunk is, and
 * the glyph it is drawn with.
 */
class ChunkMapTest extends CommonTestSetup {

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
        when(world.getName()).thenReturn("chunkblock_world");
    }

    @Test
    void testFreshIslandOffersTheChunksTouchingTheCenter() {
        List<Cell> cells = ChunkMap.cells(addon, island, playerLocation, 1);
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
    void testClaimedChunksAreOwnedAndTheFrontierMovesOut() {
        level = 8;
        for (int[] offset : new int[][] { { 1, 0 }, { 0, 1 }, { -1, 0 }, { 0, -1 }, { 1, 1 }, { -1, 1 }, { -1, -1 },
                { 1, -1 } }) {
            cm.claim(island, offset[0], offset[1]);
        }
        List<Cell> cells = ChunkMap.cells(addon, island, playerLocation, 2);
        assertEquals(25, cells.size());
        assertEquals(Kind.CENTER, kindOf(cells, 0, 0));
        assertEquals(Kind.OWNED, kindOf(cells, 1, 0));
        assertEquals(Kind.CLAIMABLE, kindOf(cells, 2, 0));
    }

    @Test
    void testChunksOutOfReachAreLocked() {
        level = 8;
        cm.claim(island, 1, 0);
        List<Cell> cells = ChunkMap.cells(addon, island, playerLocation, 2);
        assertEquals(Kind.LOCKED, kindOf(cells, -2, -2));
        assertEquals(Kind.CLAIMABLE, kindOf(cells, 2, 0));
    }

    @Test
    void testThePlayersOwnChunkIsMarked() {
        // Stand one chunk east of the center
        when(playerLocation.getBlockX()).thenReturn(24);
        List<Cell> cells = ChunkMap.cells(addon, island, playerLocation, 1);
        assertTrue(cellAt(cells, 1, 0).here());
        assertFalse(cellAt(cells, 0, 0).here());
        // The mark rides on top of what the chunk is, it does not replace it
        assertEquals(Kind.CLAIMABLE, kindOf(cells, 1, 0));
    }

    @Test
    void testAPlayerInAnotherWorldMarksNoChunk() {
        World elsewhere = mock(World.class);
        when(elsewhere.getName()).thenReturn("somewhere_else");
        when(playerLocation.getWorld()).thenReturn(elsewhere);
        assertTrue(ChunkMap.cells(addon, island, playerLocation, 1).stream().noneMatch(Cell::here));
    }

    @Test
    void testCellsAreOrderedRowByRowSoTheGridComesOutNorthUp() {
        List<Cell> cells = ChunkMap.cells(addon, island, playerLocation, 1);
        // Both maps lay the cells out in list order, filling each row from west to east
        assertEquals(new Cell(-1, -1, Kind.LOCKED, false), cells.get(0));
        assertEquals(new Cell(0, -1, Kind.CLAIMABLE, false), cells.get(1));
        assertEquals(new Cell(-1, 0, Kind.CLAIMABLE, false), cells.get(3));
        assertEquals(new Cell(1, 1, Kind.LOCKED, false), cells.get(8));
    }

    @Test
    void testGlyphsAreColouredComponentsNotColourCodes() {
        assertGlyph("■", NamedTextColor.GREEN, new Cell(1, 0, Kind.OWNED, false));
        assertGlyph("▣", NamedTextColor.YELLOW, new Cell(1, 0, Kind.CLAIMABLE, false));
        assertGlyph("□", NamedTextColor.GRAY, new Cell(1, 0, Kind.LOCKED, false));
        assertGlyph("◎", NamedTextColor.GOLD, new Cell(0, 0, Kind.CENTER, false));
    }

    @Test
    void testTheChunkThePlayerIsOnKeepsItsOutlineInTheMarkerColour() {
        assertGlyph("◉", NamedTextColor.AQUA, new Cell(0, 0, Kind.CENTER, true));
        assertGlyph("◆", NamedTextColor.AQUA, new Cell(1, 0, Kind.OWNED, true));
        assertGlyph("◇", NamedTextColor.AQUA, new Cell(3, 3, Kind.LOCKED, true));
    }

    @Test
    void testGlyphTextIsMiniMessageSoItCanGoIntoATranslation() {
        // Legacy colour codes here would show up raw in a MiniMessage locale line
        assertEquals("<green>■", ChunkMap.glyphText(new Cell(1, 0, Kind.OWNED, false)));
        assertEquals("<aqua>◆", ChunkMap.glyphText(new Cell(1, 0, Kind.CLAIMABLE, true)));
    }

    private void assertGlyph(String mark, NamedTextColor colour, Cell cell) {
        Component glyph = ChunkMap.glyph(cell);
        assertEquals(mark, ((TextComponent) glyph).content());
        assertEquals(colour, glyph.color());
    }

    private Cell cellAt(List<Cell> cells, int dx, int dz) {
        return cells.stream().filter(c -> c.dx() == dx && c.dz() == dz).findFirst()
                .orElseThrow(() -> new AssertionError("no cell at " + dx + "," + dz));
    }

    private Kind kindOf(List<Cell> cells, int dx, int dz) {
        return cellAt(cells, dx, dz).kind();
    }
}
