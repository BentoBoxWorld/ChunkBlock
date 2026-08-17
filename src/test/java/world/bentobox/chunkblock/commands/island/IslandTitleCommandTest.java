package world.bentobox.chunkblock.commands.island;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.chunkblock.ChunkBlock;
import world.bentobox.chunkblock.CommonTestSetup;
import world.bentobox.chunkblock.trophies.Trophy;
import world.bentobox.chunkblock.trophies.Trophy.Criteria;
import world.bentobox.chunkblock.trophies.Trophy.CriteriaType;
import world.bentobox.chunkblock.trophies.Trophy.Scope;
import world.bentobox.chunkblock.trophies.TrophyManager;

/**
 * Tests /ch title: listing earned trophies and choosing or clearing the island's title.
 */
class IslandTitleCommandTest extends CommonTestSetup {

    private static final Trophy TITLED = new Trophy("first-ring", "<gold>First Ring", "",
            Material.GOLD_INGOT, "<gold>Ring Bearer",
            new Criteria(CriteriaType.RING, 1, null, Scope.ISLAND, 0, 0), List.of(), List.of());
    private static final Trophy UNTITLED = new Trophy("homesteader", "<green>Homesteader", "",
            Material.GRASS_BLOCK, null,
            new Criteria(CriteriaType.RING, 2, null, Scope.ISLAND, 0, 0), List.of(), List.of());

    @Mock
    private CompositeCommand ac;
    @Mock
    private User user;
    @Mock
    private ChunkBlock addon;
    @Mock
    private TrophyManager tm;

    private IslandTitleCommand command;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        when(ac.getAddon()).thenReturn(addon);
        when(ac.getWorld()).thenReturn(world);
        when(world.getName()).thenReturn("chunkblock_world");
        when(addon.getTrophyManager()).thenReturn(tm);
        when(im.getIsland(any(), any(User.class))).thenReturn(island);
        command = new IslandTitleCommand(ac, "title", new String[] { "title" });
    }

    @Test
    void testSetup() {
        assertEquals("island.title", command.getPermission());
        assertEquals("chunkblock.commands.title.description", command.getDescription());
        assertTrue(command.isOnlyPlayer());
    }

    @Test
    void testToggleOnWhenNothingEarned() {
        when(tm.getActiveTitleText(island)).thenReturn("");
        when(tm.getEarned(island)).thenReturn(List.of());
        assertFalse(command.execute(user, "title", List.of()));
        verify(user).sendMessage("chunkblock.commands.title.none-earned-yet");
    }

    @Test
    void testToggleOffWhenActive() {
        when(tm.getActiveTitleText(island)).thenReturn("<gold>The Outpost");
        assertTrue(command.execute(user, "title", List.of()));
        verify(tm).setActiveTitle(island, null);
        verify(user).sendMessage("chunkblock.commands.title.toggled-off", "[title]", "<gold>The Outpost");
    }

    @Test
    void testToggleOnWhenInactive() {
        when(tm.getActiveTitleText(island)).thenReturn("");
        when(tm.getEarned(island)).thenReturn(List.of(TITLED, UNTITLED));
        when(tm.setActiveTitle(island, "first-ring")).thenReturn(true);
        assertTrue(command.execute(user, "title", List.of()));
        verify(user).sendMessage("chunkblock.commands.title.toggled-on", "[title]", "<gold>Ring Bearer");
    }

    @Test
    void testListShowsTrophiesTitlesAndActiveTitle() {
        when(tm.getEarned(island)).thenReturn(List.of(TITLED, UNTITLED));
        when(tm.getActiveTitleText(island)).thenReturn("<gold>Ring Bearer");
        assertTrue(command.execute(user, "title", List.of("list")));
        verify(user).sendMessage("chunkblock.commands.title.header");
        verify(user).sendMessage("chunkblock.commands.title.title-entry", "[name]", "<gold>First Ring",
                "[title]", "<gold>Ring Bearer", "[id]", "first-ring");
        verify(user).sendMessage("chunkblock.commands.title.trophy-entry", "[name]", "<green>Homesteader");
        verify(user).sendMessage("chunkblock.commands.title.active", "[title]", "<gold>Ring Bearer");
    }

    @Test
    void testListMentionsWhenNoTitleIsActive() {
        when(tm.getEarned(island)).thenReturn(List.of(UNTITLED));
        when(tm.getActiveTitleText(island)).thenReturn("");
        assertTrue(command.execute(user, "title", List.of("list")));
        verify(user).sendMessage("chunkblock.commands.title.no-active");
    }

    @Test
    void testSetTitle() {
        when(tm.setActiveTitle(island, "first-ring")).thenReturn(true);
        when(tm.getTrophy("first-ring")).thenReturn(Optional.of(TITLED));
        assertTrue(command.execute(user, "title", List.of("first-ring")));
        verify(user).sendMessage("chunkblock.commands.title.set", "[title]", "<gold>Ring Bearer");
    }

    @Test
    void testSetUnearnedTitleFails() {
        when(tm.setActiveTitle(island, "first-ring")).thenReturn(false);
        assertFalse(command.execute(user, "title", List.of("first-ring")));
        verify(user).sendMessage("chunkblock.commands.title.not-earned");
    }

    @Test
    void testClearTitle() {
        assertTrue(command.execute(user, "title", List.of("none")));
        verify(tm).setActiveTitle(island, null);
        verify(user).sendMessage("chunkblock.commands.title.cleared");
    }

    @Test
    void testTabCompleteOffersNoneAndTitledTrophies() {
        when(tm.getEarned(island)).thenReturn(List.of(TITLED, UNTITLED));
        Optional<List<String>> options = command.tabComplete(user, "title", List.of(""));
        assertTrue(options.isPresent());
        assertTrue(options.get().contains("list"));
        assertTrue(options.get().contains("none"));
        assertTrue(options.get().contains("first-ring"));
        // A trophy with no title is not offered
        assertFalse(options.get().contains("homesteader"));
    }

    @Test
    void testNoIslandCannotExecute() {
        when(im.getIsland(any(), any(User.class))).thenReturn(null);
        when(user.getWorld()).thenReturn(world);
        assertFalse(command.canExecute(user, "title", List.of()));
        verify(user).sendMessage("general.errors.no-island");
        verify(tm, never()).setActiveTitle(any(), any());
    }
}
