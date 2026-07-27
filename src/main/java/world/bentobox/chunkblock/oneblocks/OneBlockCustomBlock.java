package world.bentobox.chunkblock.oneblocks;

import org.bukkit.block.Block;

import world.bentobox.chunkblock.ChunkBlock;

/**
 * Represents a custom block with custom executable
 *
 * @author HSGamer
 */
public interface OneBlockCustomBlock {
    /**
     * Executes the custom logic
     *
     * @param block the block
     */
    void execute(ChunkBlock addon, Block block);
}
