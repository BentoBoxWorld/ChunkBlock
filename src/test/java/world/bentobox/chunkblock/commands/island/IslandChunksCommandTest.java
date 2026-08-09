package world.bentobox.chunkblock.commands.island;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.bukkit.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.chunkblock.ChunkBlock;
import world.bentobox.chunkblock.CommonTestSetup;
import world.bentobox.chunkblock.Settings;
import world.bentobox.chunkblock.chunks.ChunkManager;
import world.bentobox.chunkblock.dataobjects.OneBlockIslands;
import world.bentobox.chunkblock.listeners.BlockListener;

/**
 * Tests the territory map drawn by {@code /ch chunks} — the glyph each chunk gets and how
 * far the map reaches.
 */
class IslandChunksCommandTest extends CommonTestSetup {

    @Mock
    private CompositeCommand ac;
    @Mock
    private User user;
    @Mock
    private ChunkBlock addon;
    @Mock
    private Location playerLocation;

    private IslandChunksCommand command;
    private OneBlockIslands data;
    private ChunkManager cm;
    private long level;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        when(ac.getAddon()).thenReturn(addon);
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
        when(location.getBlockX()).thenReturn(8);
        when(location.getBlockZ()).thenReturn(8);
        when(island.getProtectionRange()).thenReturn(240);
        // The player's own location is a separate mock, so moving them leaves the island put
        when(playerLocation.getBlockX()).thenReturn(8);
        when(playerLocation.getBlockZ()).thenReturn(8);
        when(user.getLocation()).thenReturn(playerLocation);
        when(user.getWorld()).thenReturn(world);
        when(im.getIslandAt(playerLocation)).thenReturn(Optional.of(island));

        command = new IslandChunksCommand(ac, "chunks", new String[] { "chunks" });
    }

    @Test
    void testSetup() {
        assertEquals("island.chunks", command.getPermission());
        assertEquals("chunkblock.commands.chunks.description", command.getDescription());
        assertTrue(command.isOnlyPlayer());
    }

    @Test
    void testCenterChunkIsMarkedWhenThePlayerStandsOnIt() {
        assertTrue(command.execute(user, "", List.of()));
        List<String> rows = mapRows();
        // Fresh island: one claimed chunk, so the map reaches one ring out — 3 x 3
        assertEquals(3, rows.size());
        assertEquals("&b◉", middleGlyph(rows.get(1)));
    }

    @Test
    void testCenterChunkIsMarkedWhenThePlayerIsElsewhere() {
        // Stand one chunk east of the center
        when(playerLocation.getBlockX()).thenReturn(24);
        assertTrue(command.execute(user, "", List.of()));
        List<String> rows = mapRows();
        assertEquals("&6◎", middleGlyph(rows.get(1)));
    }

    @Test
    void testCenterKeepsItsMarkWhileTerritoryGrows() {
        level = 8;
        for (int[] offset : new int[][] { { 1, 0 }, { 0, 1 }, { -1, 0 }, { 0, -1 }, { 1, 1 }, { -1, 1 },
                { -1, -1 }, { 1, -1 } }) {
            cm.claim(island, offset[0], offset[1]);
        }
        assertTrue(command.execute(user, "", List.of()));
        List<String> rows = mapRows();
        // Ring 1 claimed, so the map reaches ring 2 — 5 x 5
        assertEquals(5, rows.size());
        String center = rows.get(2);
        assertEquals("&b◉", middleGlyph(center));
        // The eight chunks around the center are owned, not confused with the center itself
        assertEquals("&a■&b◉&a■", center.substring(center.indexOf("&b◉") - 3, center.indexOf("&b◉") + 6));
    }

    @Test
    void testMapIsCappedAtTheWidestRowThatFitsChat() {
        // A far-flung claim would otherwise draw a map wider than chat can hold
        when(island.getProtectionRange()).thenReturn(2000);
        level = 500;
        for (int dx = 1; dx <= 20; dx++) {
            cm.claim(island, dx, 0);
        }
        assertTrue(command.execute(user, "", List.of()));
        // MAX_MAP_RADIUS is 7, so 15 rows however far the territory reaches
        assertEquals(15, mapRows().size());
    }

    /** The rendered map rows, in order, as passed to the row locale key */
    private List<String> mapRows() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(user, org.mockito.Mockito.atLeastOnce()).sendMessage(org.mockito.ArgumentMatchers.eq(
                "chunkblock.chunks.map.row"), org.mockito.ArgumentMatchers.eq("[row]"), captor.capture());
        return new ArrayList<>(captor.getAllValues());
    }

    /** The glyph at the middle of a row, colour code included */
    private String middleGlyph(String row) {
        // Every glyph is a two-character colour code plus one character
        int glyphs = row.length() / 3;
        int middle = (glyphs / 2) * 3;
        return row.substring(middle, middle + 3);
    }
}
