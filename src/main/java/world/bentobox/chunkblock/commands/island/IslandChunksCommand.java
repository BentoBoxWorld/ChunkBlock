package world.bentobox.chunkblock.commands.island;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.util.Util;
import world.bentobox.chunkblock.ChunkBlock;
import world.bentobox.chunkblock.chunks.ChunkManager;

/**
 * /cb chunks — shows how big your island is, what unlocks next, and a little chat map of
 * your territory.
 *
 * @author tastybento
 */
public class IslandChunksCommand extends CompositeCommand {

    /** Widest map that still fits comfortably in chat */
    private static final int MAX_MAP_RADIUS = 7;

    private ChunkBlock addon;

    public IslandChunksCommand(CompositeCommand islandCommand, String label, String[] aliases) {
        super(islandCommand, label, aliases);
    }

    @Override
    public void setup() {
        setDescription("chunkblock.commands.chunks.description");
        setOnlyPlayer(true);
        setPermission("island.chunks");
        addon = getAddon();
    }

    @Override
    public boolean canExecute(User user, String label, List<String> args) {
        if (!Util.sameWorld(getWorld(), user.getWorld())) {
            user.sendMessage("general.errors.wrong-world");
            return false;
        }
        return true;
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        Optional<Island> optionalIsland = getIslands().getIslandAt(Objects.requireNonNull(user.getLocation()));
        if (optionalIsland.isEmpty()) {
            user.sendMessage("general.errors.not-on-island");
            return false;
        }
        Island island = optionalIsland.get();
        ChunkManager cm = addon.getChunkManager();
        int unlocked = cm.getUnlockedChunkCount(island);
        int max = cm.getMaxChunks(island);
        int ring = ChunkManager.ringOf(unlocked - 1);
        long nextLevel = cm.levelForChunkNumber(unlocked + 1);
        user.sendMessage("chunkblock.chunks.info", "[unlocked]", String.valueOf(unlocked), "[max]",
                String.valueOf(max), "[ring]", String.valueOf(ring), "[level]", String.valueOf(nextLevel));
        showMap(user, island, unlocked, max);
        return true;
    }

    /**
     * Renders the territory as rows of colored glyphs: unlocked chunks, the next chunk the
     * spiral will unlock, locked chunks, and the chunk the player is standing on.
     */
    private void showMap(User user, Island island, int unlocked, int max) {
        ChunkManager cm = addon.getChunkManager();
        int radius = Math.min(MAX_MAP_RADIUS, ChunkManager.ringOf(unlocked - 1) + 1);
        int centerChunkX = island.getCenter().getBlockX() >> 4;
        int centerChunkZ = island.getCenter().getBlockZ() >> 4;
        int playerDx = (user.getLocation().getBlockX() >> 4) - centerChunkX;
        int playerDz = (user.getLocation().getBlockZ() >> 4) - centerChunkZ;
        int nextIndex = unlocked < max ? unlocked : -1;
        user.sendMessage("chunkblock.chunks.map.title", "[unlocked]", String.valueOf(unlocked), "[max]",
                String.valueOf(max));
        for (int dz = -radius; dz <= radius; dz++) {
            StringBuilder row = new StringBuilder();
            for (int dx = -radius; dx <= radius; dx++) {
                int index = ChunkManager.spiralIndex(dx, dz);
                boolean here = dx == playerDx && dz == playerDz;
                if (index < unlocked) {
                    row.append(here ? "&b◆" : "&a■");
                } else if (index == nextIndex) {
                    row.append(here ? "&b◆" : "&e▣");
                } else {
                    row.append(here ? "&b◇" : "&7□");
                }
            }
            user.sendMessage("chunkblock.chunks.map.row", "[row]", row.toString());
        }
        user.sendMessage("chunkblock.chunks.map.legend", "[level]",
                String.valueOf(cm.levelForChunkNumber(unlocked + 1)));
    }
}
