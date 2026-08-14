package world.bentobox.chunkblock.panels;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * Tests how far the dialog map reaches and when it declines to show at all. What each chunk
 * is and how it is drawn belongs to the shared grid, and is covered by {@code ChunkMapTest}.
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
        when(addon.getBlockListener()).thenReturn(mock(BlockListener.class));
        level = 0;
        when(addon.getIslandLevel(island)).thenAnswer(i -> level);
        cm = new ChunkManager(addon);
        when(addon.getChunkManager()).thenReturn(cm);

        when(island.getCenter()).thenReturn(location);
        when(island.getWorld()).thenReturn(world);
        when(location.getBlockX()).thenReturn(8);
        when(location.getBlockZ()).thenReturn(8);
        when(island.getProtectionRange()).thenReturn(240);
        when(playerLocation.getBlockX()).thenReturn(8);
        when(playerLocation.getBlockZ()).thenReturn(8);
        when(playerLocation.getWorld()).thenReturn(world);
        when(user.getLocation()).thenReturn(playerLocation);
        when(world.getName()).thenReturn("chunkblock_world");
    }

    @Test
    void testAFreshIslandShowsTheRingAroundTheCenter() {
        // One claimed chunk, so the map reaches one ring out — 3 x 3
        assertEquals(9, new ChunksDialog(addon, user, island).cells().size());
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
    void testNothingIsShownWhenTheUserIsNotAPlayer() {
        when(user.isPlayer()).thenReturn(false);
        assertFalse(ChunksDialog.show(addon, user, island));
    }
}
