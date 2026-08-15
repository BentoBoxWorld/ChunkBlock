package world.bentobox.chunkblock.trophies;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import world.bentobox.bentobox.managers.PlayersManager;
import world.bentobox.chunkblock.ChunkBlock;
import world.bentobox.chunkblock.CommonTestSetup;
import world.bentobox.chunkblock.activity.ActivityManager;
import world.bentobox.chunkblock.activity.CounterType;
import world.bentobox.chunkblock.chunks.ChunkManager;
import world.bentobox.chunkblock.dataobjects.OneBlockIslands;
import world.bentobox.chunkblock.events.TrophyAwardEvent;
import world.bentobox.chunkblock.listeners.BlockListener;

/**
 * The spec for trophies: config parsing, criteria over rings and counters, the
 * earned-once guarantee, the cancellable award event, and titles.
 *
 * @author tastybento
 */
class TrophyManagerTest extends CommonTestSetup {

    private static final String TROPHY_YAML = """
            trophies:
              first-ring:
                name: "<gold>First Ring"
                description: "Complete ring 1"
                icon: GOLD_INGOT
                title: "<gold>Ring Bearer"
                criteria:
                  type: RING
                  ring: 1
                rewards:
                  commands:
                    - "eco give [owner] 100"
              homesteader:
                name: "<green>Homesteader"
                icon: GRASS_BLOCK
                criteria:
                  type: COUNTER
                  counter: CHUNKS_CLAIMED
                  scope: ISLAND
                  threshold: 10
              breaker:
                name: "<aqua>Breaker"
                icon: DIAMOND_PICKAXE
                criteria:
                  type: COUNTER
                  counter: MAGIC_BLOCKS
                  scope: MEMBER
                  threshold: 100
                  window-days: 7
              bad-icon:
                name: "Bad"
                icon: NOT_A_MATERIAL
                criteria:
                  type: RING
                  ring: 1
              bad-counter:
                name: "Bad"
                icon: STONE
                criteria:
                  type: COUNTER
                  counter: NOT_A_COUNTER
                  threshold: 1
              no-criteria:
                name: "Bad"
                icon: STONE
            """;

    @Mock
    private ChunkBlock addon;
    @Mock
    private ChunkManager cm;
    @Mock
    private ActivityManager am;
    @Mock
    private BlockListener blockListener;
    @Mock
    private PlayersManager playersManager;

    private OneBlockIslands data;
    private TrophyManager tm;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        when(addon.getChunkManager()).thenReturn(cm);
        when(addon.getActivityManager()).thenReturn(am);
        when(addon.getBlockListener()).thenReturn(blockListener);
        when(addon.getPlayers()).thenReturn(playersManager);
        when(playersManager.getName(any())).thenReturn("tastybento");
        data = new OneBlockIslands("island-id");
        when(addon.getOneBlocksIsland(island)).thenReturn(data);
        when(island.getUniqueId()).thenReturn("island-id");

        tm = new TrophyManager(addon);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(TROPHY_YAML);
        tm.loadTrophies(yaml.getConfigurationSection("trophies"));
    }

    @Test
    void testInvalidTrophiesAreSkippedValidOnesLoad() {
        assertEquals(3, tm.getTrophies().size());
        assertTrue(tm.getTrophy("first-ring").isPresent());
        assertTrue(tm.getTrophy("bad-icon").isEmpty());
        assertTrue(tm.getTrophy("bad-counter").isEmpty());
        assertTrue(tm.getTrophy("no-criteria").isEmpty());
        verify(addon, times(3)).logError(anyString());
    }

    @Test
    void testNothingAwardedWhenNothingIsMet() {
        tm.check(island);
        assertTrue(data.getEarnedTrophies().isEmpty());
        verify(pim, never()).callEvent(any(TrophyAwardEvent.class));
    }

    @Test
    void testRingTrophyAwardedOnceAndOnlyOnce() {
        when(cm.completedRings(island)).thenReturn(1);
        tm.check(island);
        assertTrue(data.getEarnedTrophies().contains("first-ring"));
        verify(pim, times(1)).callEvent(any(TrophyAwardEvent.class));
        verify(blockListener).saveIsland(island);
        // Earned once, stays earned: a re-lock and re-claim cannot re-award
        tm.check(island);
        verify(pim, times(1)).callEvent(any(TrophyAwardEvent.class));
    }

    @Test
    void testIslandCounterTrophy() {
        when(am.getCount(island, null, CounterType.CHUNKS_CLAIMED, 0)).thenReturn(10L);
        tm.check(island);
        assertTrue(data.getEarnedTrophies().contains("homesteader"));
        assertFalse(data.getEarnedTrophies().contains("breaker"));
    }

    @Test
    void testMemberCounterTrophyReadsEachMembersOwnCount() {
        UUID mate = UUID.randomUUID();
        when(am.getContributors(island)).thenReturn(Set.of(uuid, mate));
        // The island total would pass a naive check, but no single member has 100
        when(am.getCount(eq(island), any(UUID.class), eq(CounterType.MAGIC_BLOCKS), anyInt()))
                .thenReturn(60L);
        tm.check(island);
        assertFalse(data.getEarnedTrophies().contains("breaker"));
        // Now one member crosses the line, inside the 7-day window
        when(am.getCount(island, mate, CounterType.MAGIC_BLOCKS, 7)).thenReturn(100L);
        tm.check(island);
        assertTrue(data.getEarnedTrophies().contains("breaker"));
    }

    @Test
    void testAwardEventCarriesTheTrophy() {
        when(cm.completedRings(island)).thenReturn(1);
        tm.check(island);
        ArgumentCaptor<TrophyAwardEvent> captor = ArgumentCaptor.forClass(TrophyAwardEvent.class);
        verify(pim).callEvent(captor.capture());
        assertEquals("first-ring", captor.getValue().getTrophy().id());
        assertEquals(island, captor.getValue().getIsland());
    }

    @Test
    void testCancelledAwardStaysEarnedButRunsNoCommands() {
        doAnswer(invocation -> {
            invocation.getArgument(0, TrophyAwardEvent.class).setCancelled(true);
            return null;
        }).when(pim).callEvent(any(TrophyAwardEvent.class));
        when(cm.completedRings(island)).thenReturn(1);
        tm.check(island);
        // The trophy persists — it can never be awarded twice — but the addon did not act
        assertTrue(data.getEarnedTrophies().contains("first-ring"));
        mockedBukkit.verify(() -> Bukkit.dispatchCommand(any(), anyString()), never());
        tm.check(island);
        verify(pim, times(1)).callEvent(any(TrophyAwardEvent.class));
    }

    @Test
    void testRewardCommandsRunWithPlaceholders() {
        when(cm.completedRings(island)).thenReturn(1);
        tm.check(island);
        mockedBukkit.verify(() -> Bukkit.dispatchCommand(any(), eq("eco give tastybento 100")));
    }

    @Test
    void testFirstTitledTrophyBecomesActiveTitle() {
        when(cm.completedRings(island)).thenReturn(1);
        tm.check(island);
        assertEquals("first-ring", data.getActiveTitle());
        assertEquals("<gold>Ring Bearer", tm.getActiveTitleText(island));
    }

    @Test
    void testUntitledTrophyDoesNotSetTitle() {
        when(am.getCount(island, null, CounterType.CHUNKS_CLAIMED, 0)).thenReturn(10L);
        tm.check(island);
        assertTrue(data.getEarnedTrophies().contains("homesteader"));
        assertEquals("", data.getActiveTitle());
        assertEquals("", tm.getActiveTitleText(island));
    }

    @Test
    void testSetActiveTitleValidation() {
        // Not earned yet
        assertFalse(tm.setActiveTitle(island, "first-ring"));
        // Unknown trophy
        assertFalse(tm.setActiveTitle(island, "nope"));
        data.getEarnedTrophies().add("homesteader");
        // Earned but carries no title
        assertFalse(tm.setActiveTitle(island, "homesteader"));
        data.getEarnedTrophies().add("first-ring");
        assertTrue(tm.setActiveTitle(island, "first-ring"));
        assertEquals("first-ring", data.getActiveTitle());
        // Clearing always works
        assertTrue(tm.setActiveTitle(island, null));
        assertEquals("", data.getActiveTitle());
    }

    @Test
    void testGetEarnedKeepsConfigOrder() {
        data.getEarnedTrophies().add("breaker");
        data.getEarnedTrophies().add("first-ring");
        assertEquals(2, tm.getEarned(island).size());
        assertEquals("first-ring", tm.getEarned(island).get(0).id());
        assertEquals("breaker", tm.getEarned(island).get(1).id());
    }

    @Test
    void testActiveTitleOfRemovedTrophyIsEmpty() {
        data.getEarnedTrophies().add("first-ring");
        data.setActiveTitle("first-ring");
        // The admin removes the trophy from trophies.yml and reloads
        tm.loadTrophies(mock(org.bukkit.configuration.ConfigurationSection.class));
        assertEquals("", tm.getActiveTitleText(island));
    }
}
