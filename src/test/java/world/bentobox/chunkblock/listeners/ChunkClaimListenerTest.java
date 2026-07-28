package world.bentobox.chunkblock.listeners;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.chunkblock.ChunkBlock;
import world.bentobox.chunkblock.CommonTestSetup;
import world.bentobox.chunkblock.Settings;
import world.bentobox.chunkblock.chunks.ChunkManager;
import world.bentobox.chunkblock.dataobjects.OneBlockIslands;

/**
 * Tests the hit-the-border claim interaction in {@link ChunkClaimListener}.
 */
class ChunkClaimListenerTest extends CommonTestSetup {

    private ChunkBlock addon;
    private ChunkClaimListener listener;
    private OneBlockIslands data;
    private LevelListener levelListener;
    private long level;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        addon = mock(ChunkBlock.class);
        when(addon.getPlugin()).thenReturn(plugin);
        when(addon.inWorld(world)).thenReturn(true);
        when(addon.getIslands()).thenReturn(im);
        when(addon.getSettings()).thenReturn(new Settings());
        ChunkManager cm = new ChunkManager(addon);
        when(addon.getChunkManager()).thenReturn(cm);
        data = new OneBlockIslands("test");
        when(addon.getOneBlocksIsland(island)).thenReturn(data);
        when(addon.getBlockListener()).thenReturn(mock(BlockListener.class));
        levelListener = mock(LevelListener.class);
        when(addon.getLevelListener()).thenReturn(levelListener);
        level = 0;
        when(addon.getIslandLevel(island)).thenAnswer(i -> level);

        // Island center chunk (0, 0)
        when(island.getCenter()).thenReturn(location);
        when(location.getBlockX()).thenReturn(8);
        when(location.getBlockZ()).thenReturn(8);
        when(island.getProtectionRange()).thenReturn(240);
        when(island.getOwner()).thenReturn(uuid);
        when(im.getIslandAt(any())).thenReturn(Optional.of(island));

        // Player stands near the east edge of the center chunk, looking east (+x):
        // yaw -90 in Bukkit faces +x
        Location playerLoc = new Location(world, 14.5, 65, 8.5, -90F, 0F);
        when(mockPlayer.getLocation()).thenReturn(playerLoc);
        when(mockPlayer.getEyeLocation()).thenReturn(new Location(world, 14.5, 66.6, 8.5, -90F, 0F));
        when(mockPlayer.getGameMode()).thenReturn(GameMode.SURVIVAL);

        listener = new ChunkClaimListener(addon);
    }

    private PlayerInteractEvent hit(Action action) {
        return new PlayerInteractEvent(mockPlayer, action, null, null, org.bukkit.block.BlockFace.EAST,
                EquipmentSlot.HAND);
    }

    @Test
    void testOwnerPunchingBorderClaimsChunk() {
        level = 1;
        listener.onBorderHit(hit(Action.LEFT_CLICK_AIR));
        assertTrue(data.isChunkUnlocked(1, 0));
        verify(levelListener).celebrateClaim(island, 1, 0);
    }

    @Test
    void testRightClickAlsoClaims() {
        level = 1;
        listener.onBorderHit(hit(Action.RIGHT_CLICK_AIR));
        assertTrue(data.isChunkUnlocked(1, 0));
    }

    @Test
    void testNoCreditNoClaim() {
        level = 0;
        listener.onBorderHit(hit(Action.LEFT_CLICK_AIR));
        assertFalse(data.isChunkUnlocked(1, 0));
        verify(levelListener, never()).celebrateClaim(any(), anyInt(), anyInt());
        // The player is told what they are missing
        verify(notifier).notify(any(), any());
    }

    @Test
    void testNonOwnerCannotClaim() {
        level = 100;
        when(island.getOwner()).thenReturn(UUID.randomUUID());
        listener.onBorderHit(hit(Action.LEFT_CLICK_AIR));
        assertFalse(data.isChunkUnlocked(1, 0));
    }

    @Test
    void testOffHandIgnored() {
        level = 1;
        PlayerInteractEvent e = new PlayerInteractEvent(mockPlayer, Action.LEFT_CLICK_AIR, null, null,
                org.bukkit.block.BlockFace.EAST, EquipmentSlot.OFF_HAND);
        listener.onBorderHit(e);
        assertFalse(data.isChunkUnlocked(1, 0));
    }

    @Test
    void testAimingAwayFromBorderDoesNothing() {
        level = 1;
        // Look west (+yaw 90 faces -x): nothing but own territory within reach
        when(mockPlayer.getEyeLocation()).thenReturn(new Location(world, 14.5, 66.6, 8.5, 90F, 0F));
        listener.onBorderHit(hit(Action.LEFT_CLICK_AIR));
        assertFalse(data.isChunkUnlocked(1, 0));
        assertFalse(data.isChunkUnlocked(-1, 0));
    }

    @Test
    void testClaimingChainsOutward() {
        level = 2;
        listener.onBorderHit(hit(Action.LEFT_CLICK_AIR));
        assertTrue(data.isChunkUnlocked(1, 0));
        // Move to the east edge of the newly claimed chunk and punch again
        when(mockPlayer.getLocation()).thenReturn(new Location(world, 30.5, 65, 8.5, -90F, 0F));
        when(mockPlayer.getEyeLocation()).thenReturn(new Location(world, 30.5, 66.6, 8.5, -90F, 0F));
        listener.onBorderHit(hit(Action.LEFT_CLICK_AIR));
        assertTrue(data.isChunkUnlocked(2, 0));
    }
}
