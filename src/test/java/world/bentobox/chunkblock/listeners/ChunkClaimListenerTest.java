package world.bentobox.chunkblock.listeners;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.chunkblock.ChunkBlock;
import world.bentobox.chunkblock.CommonTestSetup;
import world.bentobox.chunkblock.Settings;
import world.bentobox.chunkblock.chunks.BorderDisplay;
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
    private BorderDisplay borderDisplay;
    private Settings settings;
    private long level;
    /** Virtual clock the listener reads, so confirmation windows need no sleeping */
    private long now;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        addon = mock(ChunkBlock.class);
        when(addon.getPlugin()).thenReturn(plugin);
        when(addon.inWorld(world)).thenReturn(true);
        when(addon.getIslands()).thenReturn(im);
        settings = new Settings();
        when(addon.getSettings()).thenReturn(settings);
        borderDisplay = mock(BorderDisplay.class);
        when(addon.getBorderDisplay()).thenReturn(borderDisplay);
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
        now = 1_000_000L;
        listener.setClock(() -> now);
    }

    private PlayerInteractEvent hit(Action action) {
        return new PlayerInteractEvent(mockPlayer, action, null, null, org.bukkit.block.BlockFace.EAST,
                EquipmentSlot.HAND);
    }

    /**
     * Performs the whole default gesture: a hit to preview the chunk, then a sneaking hit a
     * second later to pay for it.
     */
    private void hitAndConfirm(Action action) {
        listener.onBorderHit(hit(action));
        now += 1000;
        when(mockPlayer.isSneaking()).thenReturn(true);
        listener.onBorderHit(hit(action));
        when(mockPlayer.isSneaking()).thenReturn(false);
    }

    @Test
    void testOwnerPunchingBorderClaimsChunk() {
        level = 1;
        hitAndConfirm(Action.LEFT_CLICK_AIR);
        assertTrue(data.isChunkUnlocked(1, 0));
        verify(levelListener).celebrateClaim(island, 1, 0);
    }

    @Test
    void testRightClickAlsoClaims() {
        level = 1;
        hitAndConfirm(Action.RIGHT_CLICK_AIR);
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
        hitAndConfirm(Action.LEFT_CLICK_AIR);
        assertTrue(data.isChunkUnlocked(1, 0));
        // Move to the east edge of the newly claimed chunk and punch again
        when(mockPlayer.getLocation()).thenReturn(new Location(world, 30.5, 65, 8.5, -90F, 0F));
        when(mockPlayer.getEyeLocation()).thenReturn(new Location(world, 30.5, 66.6, 8.5, -90F, 0F));
        hitAndConfirm(Action.LEFT_CLICK_AIR);
        assertTrue(data.isChunkUnlocked(2, 0));
    }

    private PlayerInteractEvent hitBlock(Action action, int x, int z) {
        Block block = mock(Block.class);
        when(block.getX()).thenReturn(x);
        when(block.getZ()).thenReturn(z);
        return new PlayerInteractEvent(mockPlayer, action, null, block, org.bukkit.block.BlockFace.EAST,
                EquipmentSlot.HAND);
    }

    @Test
    void testMiningOwnBlockNearBorderIsNotAClaim() {
        // The generator-mining bug: clicked block is in the player's own chunk, but the
        // aim line would cross into the locked neighbor. Must not probe a claim.
        level = 0;
        listener.onBorderHit(hitBlock(Action.LEFT_CLICK_BLOCK, 14, 8));
        assertFalse(data.isChunkUnlocked(1, 0));
        verify(notifier, never()).notify(any(), any());
    }

    @Test
    void testPunchingBlockInLockedChunkClaimsIt() {
        level = 1;
        listener.onBorderHit(hitBlock(Action.LEFT_CLICK_BLOCK, 17, 8));
        now += 1000;
        when(mockPlayer.isSneaking()).thenReturn(true);
        listener.onBorderHit(hitBlock(Action.LEFT_CLICK_BLOCK, 17, 8));
        assertTrue(data.isChunkUnlocked(1, 0));
    }

    @Test
    void testNoCreditFeedbackIsThrottled() {
        level = 0;
        listener.onBorderHit(hit(Action.LEFT_CLICK_AIR));
        listener.onBorderHit(hit(Action.LEFT_CLICK_AIR));
        // Two rapid failed probes, one nag
        verify(notifier, times(1)).notify(any(), any());
    }

    @Test
    void testAimLineBlockedBySolidWallDoesNotClaim() {
        level = 1;
        // A solid wall of the player's own blocks between eye and border
        Block wall = mock(Block.class);
        when(wall.isPassable()).thenReturn(false);
        when(world.getBlockAt(any(Location.class))).thenReturn(wall);
        listener.onBorderHit(hit(Action.LEFT_CLICK_AIR));
        assertFalse(data.isChunkUnlocked(1, 0));
    }

    // ------------------------------------------------------------------
    // Claim confirmation
    // ------------------------------------------------------------------

    @Test
    void testFirstHitOnlyPreviewsAndSpendsNothing() {
        level = 1;
        listener.onBorderHit(hit(Action.LEFT_CLICK_AIR));
        assertFalse(data.isChunkUnlocked(1, 0));
        verify(levelListener, never()).celebrateClaim(any(), anyInt(), anyInt());
        // The player is quoted a price and shown which chunk they are buying
        verify(notifier).notify(any(), eq("chunkblock.chunks.claim-confirm"));
        verify(borderDisplay).showPreview(eq(mockPlayer), eq(1), eq(0), anyLong());
    }

    @Test
    void testSecondHitWithoutSneakingDoesNotClaim() {
        level = 1;
        listener.onBorderHit(hit(Action.LEFT_CLICK_AIR));
        now += 5000;
        listener.onBorderHit(hit(Action.LEFT_CLICK_AIR));
        assertFalse(data.isChunkUnlocked(1, 0));
    }

    @Test
    void testDoubleFireOfOneSwingDoesNotClaim() {
        // A single swing can raise both LEFT_CLICK_AIR and LEFT_CLICK_BLOCK on the same
        // tick: the arming delay must stop that from previewing and paying at once
        level = 1;
        when(mockPlayer.isSneaking()).thenReturn(true);
        listener.onBorderHit(hit(Action.LEFT_CLICK_AIR));
        listener.onBorderHit(hit(Action.LEFT_CLICK_AIR));
        assertFalse(data.isChunkUnlocked(1, 0));
    }

    @Test
    void testConfirmationExpires() {
        level = 1;
        listener.onBorderHit(hit(Action.LEFT_CLICK_AIR));
        // Well past the confirmation window
        now += settings.getClaimConfirmationTimeout() * 1000L + 1;
        when(mockPlayer.isSneaking()).thenReturn(true);
        listener.onBorderHit(hit(Action.LEFT_CLICK_AIR));
        // The stale quote is re-priced rather than paid
        assertFalse(data.isChunkUnlocked(1, 0));
        // Hitting again inside the fresh window does claim it
        now += 1000;
        listener.onBorderHit(hit(Action.LEFT_CLICK_AIR));
        assertTrue(data.isChunkUnlocked(1, 0));
    }

    @Test
    void testConfirmingWhileAimingAtADifferentChunkDoesNotClaimEither() {
        level = 2;
        listener.onBorderHit(hit(Action.LEFT_CLICK_AIR));
        // Turn to the south border (yaw 0 faces +z) and sneak-hit: the east chunk was the
        // one quoted, so this is a fresh preview, not a confirmation
        now += 1000;
        when(mockPlayer.getLocation()).thenReturn(new Location(world, 8.5, 65, 14.5, 0F, 0F));
        when(mockPlayer.getEyeLocation()).thenReturn(new Location(world, 8.5, 66.6, 14.5, 0F, 0F));
        when(mockPlayer.isSneaking()).thenReturn(true);
        listener.onBorderHit(hit(Action.LEFT_CLICK_AIR));
        assertFalse(data.isChunkUnlocked(1, 0));
        assertFalse(data.isChunkUnlocked(0, 1));
        // Confirming that new quote claims the north chunk and leaves the east one locked
        now += 1000;
        listener.onBorderHit(hit(Action.LEFT_CLICK_AIR));
        assertTrue(data.isChunkUnlocked(0, 1));
        assertFalse(data.isChunkUnlocked(1, 0));
    }

    @Test
    void testPreviewIsNotReAnnouncedOnEverySwing() {
        level = 1;
        listener.onBorderHit(hit(Action.LEFT_CLICK_AIR));
        now += 100;
        listener.onBorderHit(hit(Action.LEFT_CLICK_AIR));
        now += 100;
        listener.onBorderHit(hit(Action.LEFT_CLICK_AIR));
        verify(notifier, times(1)).notify(any(), eq("chunkblock.chunks.claim-confirm"));
    }

    @Test
    void testQuitDropsThePendingClaim() {
        level = 1;
        listener.onBorderHit(hit(Action.LEFT_CLICK_AIR));
        listener.onQuit(new org.bukkit.event.player.PlayerQuitEvent(mockPlayer, (String) null));
        verify(borderDisplay).clearPreview(uuid);
        now += 1000;
        when(mockPlayer.isSneaking()).thenReturn(true);
        listener.onBorderHit(hit(Action.LEFT_CLICK_AIR));
        // Nothing left to confirm, so the hit only re-previews
        assertFalse(data.isChunkUnlocked(1, 0));
    }

    @Test
    void testConfirmationCanBeSwitchedOff() {
        settings.setRequireClaimConfirmation(false);
        level = 1;
        listener.onBorderHit(hit(Action.LEFT_CLICK_AIR));
        assertTrue(data.isChunkUnlocked(1, 0));
        verify(borderDisplay, never()).showPreview(any(), anyInt(), anyInt(), anyLong());
    }
}
