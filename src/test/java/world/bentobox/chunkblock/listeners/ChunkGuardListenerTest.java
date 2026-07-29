package world.bentobox.chunkblock.listeners;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableSet;

import world.bentobox.bentobox.managers.RanksManager;
import world.bentobox.bentobox.util.Util;
import world.bentobox.chunkblock.ChunkBlock;
import world.bentobox.chunkblock.CommonTestSetup;
import world.bentobox.chunkblock.Settings;
import world.bentobox.chunkblock.chunks.ChunkManager;
import world.bentobox.chunkblock.dataobjects.OneBlockIslands;

/**
 * Tests the movement and teleport gates of {@link ChunkGuardListener}.
 */
class ChunkGuardListenerTest extends CommonTestSetup {

    private ChunkBlock addon;
    private ChunkGuardListener listener;
    private OneBlockIslands data;
    private Location lockedTo;
    private Location unlockedTo;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        addon = mock(ChunkBlock.class);
        when(addon.getPlugin()).thenReturn(plugin);
        when(addon.inWorld(world)).thenReturn(true);
        when(addon.getIslands()).thenReturn(im);
        Settings settings = new Settings();
        when(addon.getSettings()).thenReturn(settings);
        ChunkManager cm = new ChunkManager(addon);
        when(addon.getChunkManager()).thenReturn(cm);
        data = new OneBlockIslands("test");
        when(addon.getOneBlocksIsland(island)).thenReturn(data);

        // Island centered at block (8, y, 8) → center chunk (0, 0); only that chunk unlocked
        when(island.getCenter()).thenReturn(location);
        when(location.getBlockX()).thenReturn(8);
        when(location.getBlockZ()).thenReturn(8);
        when(island.getProtectionRange()).thenReturn(240);
        when(im.getIslandAt(any())).thenReturn(Optional.of(island));

        // Destination in the locked chunk east of the center
        lockedTo = mock(Location.class);
        when(lockedTo.getWorld()).thenReturn(world);
        when(lockedTo.getBlockX()).thenReturn(17);
        when(lockedTo.getBlockZ()).thenReturn(8);
        when(lockedTo.toVector()).thenReturn(new Vector(17, 0, 8));

        // Destination inside the unlocked center chunk
        unlockedTo = mock(Location.class);
        when(unlockedTo.getWorld()).thenReturn(world);
        when(unlockedTo.getBlockX()).thenReturn(10);
        when(unlockedTo.getBlockZ()).thenReturn(8);
        when(unlockedTo.toVector()).thenReturn(new Vector(10, 0, 8));

        when(mockPlayer.getGameMode()).thenReturn(GameMode.SURVIVAL);
        mockedUtil.when(() -> Util.teleportAsync(any(Player.class), any(Location.class)))
                .thenReturn(CompletableFuture.completedFuture(true));

        listener = new ChunkGuardListener(addon);
    }

    @Test
    void testMoveIntoLockedChunkCancelledAndTeleportedBack() {
        PlayerMoveEvent e = new PlayerMoveEvent(mockPlayer, location, lockedTo);
        listener.onPlayerMove(e);
        assertTrue(e.isCancelled());
        mockedUtil.verify(() -> Util.teleportAsync(mockPlayer, location));
    }

    @Test
    void testMoveWithinUnlockedChunkAllowed() {
        PlayerMoveEvent e = new PlayerMoveEvent(mockPlayer, location, unlockedTo);
        listener.onPlayerMove(e);
        assertFalse(e.isCancelled());
    }

    @Test
    void testMoveIntoUnlockedNeighborAllowedAfterUnlock() {
        // Claim the east neighbor chunk: it becomes reachable
        data.addUnlockedChunk(1, 0);
        PlayerMoveEvent e = new PlayerMoveEvent(mockPlayer, location, lockedTo);
        listener.onPlayerMove(e);
        assertFalse(e.isCancelled());
    }

    @Test
    void testHeadOnlyMovementIgnored() {
        // Same XZ vector as from → the gate must not even do a lock lookup
        Location to = mock(Location.class);
        when(to.getWorld()).thenReturn(world);
        when(to.toVector()).thenReturn(new Vector(0, 0, 0));
        PlayerMoveEvent e = new PlayerMoveEvent(mockPlayer, location, to);
        listener.onPlayerMove(e);
        assertFalse(e.isCancelled());
    }

    @Test
    void testSpectatorExempt() {
        when(mockPlayer.getGameMode()).thenReturn(GameMode.SPECTATOR);
        PlayerMoveEvent e = new PlayerMoveEvent(mockPlayer, location, lockedTo);
        listener.onPlayerMove(e);
        assertFalse(e.isCancelled());
    }

    @Test
    void testBypassPermissionExempt() {
        when(mockPlayer.hasPermission(ChunkManager.BYPASS_PERMISSION)).thenReturn(true);
        PlayerMoveEvent e = new PlayerMoveEvent(mockPlayer, location, lockedTo);
        listener.onPlayerMove(e);
        assertFalse(e.isCancelled());
    }

    @Test
    void testBypassToggleReenablesEnforcement() {
        when(mockPlayer.hasPermission(ChunkManager.BYPASS_PERMISSION)).thenReturn(true);
        addon.getChunkManager().toggleBypass(uuid);
        PlayerMoveEvent e = new PlayerMoveEvent(mockPlayer, location, lockedTo);
        listener.onPlayerMove(e);
        assertTrue(e.isCancelled());
    }

    @Test
    void testEnderPearlIntoLockedChunkCancelledAndRefunded() {
        PlayerTeleportEvent e = new PlayerTeleportEvent(mockPlayer, location, lockedTo,
                TeleportCause.ENDER_PEARL);
        listener.onPlayerTeleport(e);
        assertTrue(e.isCancelled());
        verify(inv).addItem(new ItemStack(Material.ENDER_PEARL));
    }

    @Test
    void testEnderPearlIntoUnlockedChunkAllowed() {
        PlayerTeleportEvent e = new PlayerTeleportEvent(mockPlayer, location, unlockedTo,
                TeleportCause.ENDER_PEARL);
        listener.onPlayerTeleport(e);
        assertFalse(e.isCancelled());
        verify(inv, never()).addItem(any(ItemStack.class));
    }

    @Test
    void testChorusFruitIntoLockedChunkCancelledWithoutRefund() {
        PlayerTeleportEvent e = new PlayerTeleportEvent(mockPlayer, location, lockedTo,
                TeleportCause.CONSUMABLE_EFFECT);
        listener.onPlayerTeleport(e);
        assertTrue(e.isCancelled());
        verify(inv, never()).addItem(any(ItemStack.class));
    }

    @Test
    void testLockIsTwoDimensional() {
        // Same locked chunk but far above the build limit — still locked
        Location highTo = mock(Location.class);
        when(highTo.getWorld()).thenReturn(world);
        when(highTo.getBlockX()).thenReturn(17);
        when(highTo.getBlockY()).thenReturn(1000);
        when(highTo.getBlockZ()).thenReturn(8);
        when(highTo.toVector()).thenReturn(new Vector(17, 1000, 8));
        PlayerMoveEvent e = new PlayerMoveEvent(mockPlayer, location, highTo);
        listener.onPlayerMove(e);
        assertTrue(e.isCancelled());
    }

    @Test
    void testBacktrackNonMemberSentHomeNotEjectedLocally() {
        // Player is inside a locked chunk of an island they have no rank on (e.g. a
        // respawn that landed on a stranger's or abandoned island)
        when(mockPlayer.getLocation()).thenReturn(lockedTo);
        when(island.getMemberSet(RanksManager.COOP_RANK)).thenReturn(ImmutableSet.of());
        listener.backtrack(mockPlayer);
        verify(im).homeTeleportAsync(world, mockPlayer);
        mockedUtil.verify(() -> Util.teleportAsync(any(Player.class), any(Location.class)), never());
    }

    @Test
    void testBacktrackMemberEjectedWithinIsland() {
        when(mockPlayer.getLocation()).thenReturn(lockedTo);
        when(island.getMemberSet(RanksManager.COOP_RANK)).thenReturn(ImmutableSet.of(uuid));
        when(im.isSafeLocation(any())).thenReturn(true);
        listener.backtrack(mockPlayer);
        verify(im, never()).homeTeleportAsync(any(), any(Player.class));
        mockedUtil.verify(() -> Util.teleportAsync(any(Player.class), any(Location.class)));
    }
}
