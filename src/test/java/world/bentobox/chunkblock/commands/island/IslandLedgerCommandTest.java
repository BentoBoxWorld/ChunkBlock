package world.bentobox.chunkblock.commands.island;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.managers.PlayersManager;
import world.bentobox.chunkblock.ChunkBlock;
import world.bentobox.chunkblock.CommonTestSetup;
import world.bentobox.chunkblock.Settings;
import world.bentobox.chunkblock.activity.ActivityManager;
import world.bentobox.chunkblock.activity.CounterType;
import world.bentobox.chunkblock.chunks.ChunkManager;
import world.bentobox.chunkblock.dataobjects.OneBlockIslands;
import world.bentobox.chunkblock.listeners.BlockListener;

class IslandLedgerCommandTest extends CommonTestSetup {

    @Mock
    private CompositeCommand ac;
    @Mock
    private User user;
    @Mock
    private ChunkBlock addon;
    @Mock
    private ActivityManager activityManager;
    @Mock
    private Location playerLocation;
    @Mock
    private PlayersManager playersManager;

    private IslandLedgerCommand command;
    private UUID member1;
    private UUID member2;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        when(ac.getAddon()).thenReturn(addon);
        Settings settings = new Settings();
        when(addon.getSettings()).thenReturn(settings);
        OneBlockIslands data = new OneBlockIslands("test");
        when(addon.getOneBlocksIsland(island)).thenReturn(data);
        when(addon.getBlockListener()).thenReturn(mock(BlockListener.class));
        when(addon.getActivityManager()).thenReturn(activityManager);
        when(addon.getPlayers()).thenReturn(playersManager);
        ChunkManager cm = new ChunkManager(addon);
        when(addon.getChunkManager()).thenReturn(cm);

        when(island.getCenter()).thenReturn(location);
        when(island.getWorld()).thenReturn(world);
        when(world.getName()).thenReturn("chunkblock_world");
        when(location.getBlockX()).thenReturn(8);
        when(location.getBlockZ()).thenReturn(8);
        when(island.getProtectionRange()).thenReturn(240);

        when(playerLocation.getWorld()).thenReturn(world);
        when(user.getLocation()).thenReturn(playerLocation);
        when(user.getWorld()).thenReturn(world);
        when(user.getTranslation(anyString(), any(String[].class))).thenAnswer(inv -> inv.getArgument(0, String.class));
        when(im.getIslandAt(playerLocation)).thenReturn(Optional.of(island));

        member1 = UUID.randomUUID();
        member2 = UUID.randomUUID();
        when(playersManager.getName(member1)).thenReturn("Alice");
        when(playersManager.getName(member2)).thenReturn("Bob");

        command = new IslandLedgerCommand(ac, "ledger", new String[] { "ledger" });
    }

    @Test
    void testSetup() {
        assertEquals("island.ledger", command.getPermission());
        assertEquals("chunkblock.commands.ledger.description", command.getDescription());
        assertTrue(command.isOnlyPlayer());
    }

    @Test
    void testExecuteNoIsland() {
        when(im.getIslandAt(any())).thenReturn(Optional.empty());
        assertFalse(command.execute(user, "ledger", Collections.emptyList()));
        verify(user).sendMessage("general.errors.not-on-island");
    }

    @Test
    void testExecuteNoActivity() {
        when(activityManager.getContributors(island)).thenReturn(Collections.emptySet());
        assertTrue(command.execute(user, "ledger", Collections.emptyList()));
        verify(user).sendMessage(eq("chunkblock.commands.ledger.header"), anyString(), anyString());
        verify(user).sendMessage("chunkblock.commands.ledger.no-activity");
    }

    @Test
    void testExecuteWithContributors() {
        when(activityManager.getContributors(island)).thenReturn(Set.of(member1, member2));
        when(activityManager.getCount(island, member1, CounterType.MAGIC_BLOCKS, 0)).thenReturn(100L);
        when(activityManager.getCount(island, member1, CounterType.CHUNKS_CLAIMED, 0)).thenReturn(3L);
        when(activityManager.getCount(island, member1, CounterType.CHUNKS_RECLAIMED, 0)).thenReturn(1L);
        when(activityManager.getCount(island, member1, CounterType.RINGS_COMPLETED, 0)).thenReturn(1L);
        when(activityManager.getCount(island, member2, CounterType.MAGIC_BLOCKS, 0)).thenReturn(50L);
        when(activityManager.getCount(island, member2, CounterType.CHUNKS_CLAIMED, 0)).thenReturn(0L);
        when(activityManager.getCount(island, member2, CounterType.CHUNKS_RECLAIMED, 0)).thenReturn(0L);
        when(activityManager.getCount(island, member2, CounterType.RINGS_COMPLETED, 0)).thenReturn(0L);

        assertTrue(command.execute(user, "ledger", Collections.emptyList()));
        verify(user).sendMessage(eq("chunkblock.commands.ledger.header"), anyString(), anyString());
        verify(user, never()).sendMessage("chunkblock.commands.ledger.no-activity");
        verify(user).sendMessage(eq("chunkblock.commands.ledger.total"),
                eq("[blocks]"), eq("150"),
                eq("[chunks]"), eq("4"),
                eq("[rings]"), eq("1"));
    }

    @Test
    void testExecuteWithWindowDays() {
        when(activityManager.getContributors(island)).thenReturn(Set.of(member1));
        when(activityManager.getCount(island, member1, CounterType.MAGIC_BLOCKS, 7)).thenReturn(20L);
        when(activityManager.getCount(island, member1, CounterType.CHUNKS_CLAIMED, 7)).thenReturn(1L);
        when(activityManager.getCount(island, member1, CounterType.CHUNKS_RECLAIMED, 7)).thenReturn(0L);
        when(activityManager.getCount(island, member1, CounterType.RINGS_COMPLETED, 7)).thenReturn(0L);

        assertTrue(command.execute(user, "ledger", List.of("7")));
        verify(user).sendMessage(eq("chunkblock.commands.ledger.header"), anyString(), anyString());
    }
}
