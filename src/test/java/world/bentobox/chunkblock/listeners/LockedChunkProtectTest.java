package world.bentobox.chunkblock.listeners;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.chunkblock.ChunkBlock;
import world.bentobox.chunkblock.CommonTestSetup;
import world.bentobox.chunkblock.Settings;
import world.bentobox.chunkblock.chunks.ChunkManager;
import world.bentobox.chunkblock.dataobjects.OneBlockIslands;

/**
 * Tests the world-physics and reach-across guards in {@link LockedChunkProtect}.
 */
class LockedChunkProtectTest extends CommonTestSetup {

    private ChunkBlock addon;
    private LockedChunkProtect listener;
    private OneBlockIslands data;
    private Block lockedBlock;
    private Block unlockedBlock;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        addon = mock(ChunkBlock.class);
        when(addon.inWorld(world)).thenReturn(true);
        when(addon.getIslands()).thenReturn(im);
        when(addon.getSettings()).thenReturn(new Settings());
        ChunkManager cm = new ChunkManager(addon);
        when(addon.getChunkManager()).thenReturn(cm);
        data = new OneBlockIslands("test");
        when(addon.getOneBlocksIsland(island)).thenReturn(data);

        // Island center chunk (0,0); only that chunk unlocked
        when(island.getCenter()).thenReturn(location);
        when(location.getBlockX()).thenReturn(8);
        when(location.getBlockZ()).thenReturn(8);
        when(island.getProtectionRange()).thenReturn(240);
        when(im.getIslandAt(any())).thenReturn(Optional.of(island));

        lockedBlock = blockAt(20, 8);
        unlockedBlock = blockAt(10, 8);

        when(mockPlayer.getGameMode()).thenReturn(GameMode.SURVIVAL);
        listener = new LockedChunkProtect(addon);
    }

    private Block blockAt(int x, int z) {
        Block block = mock(Block.class);
        Location loc = mock(Location.class);
        when(loc.getWorld()).thenReturn(world);
        when(loc.getBlockX()).thenReturn(x);
        when(loc.getBlockZ()).thenReturn(z);
        when(block.getLocation()).thenReturn(loc);
        when(block.getWorld()).thenReturn(world);
        return block;
    }

    @Test
    void testBlockPlaceInLockedChunkCancelled() {
        BlockPlaceEvent e = new BlockPlaceEvent(lockedBlock, mock(org.bukkit.block.BlockState.class),
                unlockedBlock, new ItemStack(Material.STONE), mockPlayer, true, EquipmentSlot.HAND);
        listener.onBlockPlace(e);
        assertTrue(e.isCancelled());
    }

    @Test
    void testBlockPlaceInUnlockedChunkAllowed() {
        BlockPlaceEvent e = new BlockPlaceEvent(unlockedBlock, mock(org.bukkit.block.BlockState.class),
                unlockedBlock, new ItemStack(Material.STONE), mockPlayer, true, EquipmentSlot.HAND);
        listener.onBlockPlace(e);
        assertFalse(e.isCancelled());
    }

    @Test
    void testBlockBreakInLockedChunkCancelled() {
        BlockBreakEvent e = new BlockBreakEvent(lockedBlock, mockPlayer);
        listener.onBlockBreak(e);
        assertTrue(e.isCancelled());
    }

    @Test
    void testExemptPlayerMayBuildInLockedChunk() {
        when(mockPlayer.hasPermission(ChunkManager.BYPASS_PERMISSION)).thenReturn(true);
        BlockBreakEvent e = new BlockBreakEvent(lockedBlock, mockPlayer);
        listener.onBlockBreak(e);
        assertFalse(e.isCancelled());
    }

    @Test
    void testPistonPushIntoLockedChunkCancelled() {
        // Piston at x=14 pushing a block at x=15 east: the block would land at x=16 (locked)
        Block piston = blockAt(14, 8);
        Block pushed = blockAt(15, 8);
        Block destination = blockAt(16, 8);
        when(pushed.getRelative(BlockFace.EAST)).thenReturn(destination);
        when(piston.getRelative(BlockFace.EAST)).thenReturn(pushed);
        List<Block> blocks = new ArrayList<>(List.of(pushed));
        BlockPistonExtendEvent e = new BlockPistonExtendEvent(piston, blocks, BlockFace.EAST);
        listener.onPistonExtend(e);
        assertTrue(e.isCancelled());
    }

    @Test
    void testPistonPushWithinUnlockedChunkAllowed() {
        Block piston = blockAt(4, 8);
        Block pushed = blockAt(5, 8);
        Block destination = blockAt(6, 8);
        when(pushed.getRelative(BlockFace.EAST)).thenReturn(destination);
        when(piston.getRelative(BlockFace.EAST)).thenReturn(pushed);
        List<Block> blocks = new ArrayList<>(List.of(pushed));
        BlockPistonExtendEvent e = new BlockPistonExtendEvent(piston, blocks, BlockFace.EAST);
        listener.onPistonExtend(e);
        assertFalse(e.isCancelled());
    }

    @Test
    void testUnlockingChunkAllowsBuilding() {
        data.addUnlockedChunk(1, 0); // block at x=20 is in chunk (1,0)
        BlockBreakEvent e = new BlockBreakEvent(lockedBlock, mockPlayer);
        listener.onBlockBreak(e);
        assertFalse(e.isCancelled());
    }
}
