package world.bentobox.chunkblock.commands.island;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import net.kyori.adventure.key.Key;
import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.util.Util;
import world.bentobox.chunkblock.ChunkBlock;
import world.bentobox.chunkblock.chunks.ChunkManager;
import world.bentobox.chunkblock.chunks.ChunkManager.ClaimResult;

/**
 * /ch chunks — shows how big your island is, how much level credit you can spend, and a
 * little chat map of your territory with the chunks you could claim next.
 *
 * @author tastybento
 */
public class IslandChunksCommand extends CompositeCommand {

    /** Widest map that still fits comfortably in chat */
    private static final int MAX_MAP_RADIUS = 7;

    /**
     * Minecraft's built-in fixed-width font. Chat's default font is proportional, so a
     * grid built from mixed glyphs comes out ragged — a row's width depends on which
     * chunks happen to be claimed. Only the map rows use it; the rest of the chat stays
     * in the normal font.
     */
    private static final Key MONOSPACE_FONT = Key.key("minecraft", "uniform");

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
        long credit = Math.max(0, cm.getCredit(island));
        user.sendMessage("chunkblock.chunks.info", "[unlocked]", String.valueOf(unlocked), "[max]",
                String.valueOf(max), "[credit]", String.valueOf(credit), "[cost]",
                String.valueOf(cm.getChunkCost()));
        user.sendMessage("chunkblock.chunks.rings", "[rings]", String.valueOf(cm.completedRings(island)), "[max]",
                String.valueOf(cm.maxRingRadius(island)));
        showMap(user, island, unlocked, max);
        return true;
    }

    /**
     * Renders the territory as rows of colored glyphs: unlocked chunks, the frontier
     * chunks that could be claimed next, locked chunks, and the chunk the player is
     * standing on.
     */
    private void showMap(User user, Island island, int unlocked, int max) {
        ChunkManager cm = addon.getChunkManager();
        int radius = Math.min(MAX_MAP_RADIUS, cm.currentRing(island) + 1);
        int centerChunkX = island.getCenter().getBlockX() >> 4;
        int centerChunkZ = island.getCenter().getBlockZ() >> 4;
        int playerDx = (user.getLocation().getBlockX() >> 4) - centerChunkX;
        int playerDz = (user.getLocation().getBlockZ() >> 4) - centerChunkZ;
        user.sendMessage("chunkblock.chunks.map.title", "[unlocked]", String.valueOf(unlocked), "[max]",
                String.valueOf(max));
        for (int dz = -radius; dz <= radius; dz++) {
            StringBuilder row = new StringBuilder();
            for (int dx = -radius; dx <= radius; dx++) {
                boolean here = dx == playerDx && dz == playerDz;
                if (dx == 0 && dz == 0) {
                    // The center chunk holds the magic block and can never lock, so it is
                    // marked in its own right — without it the grid has nothing to orient by
                    row.append(here ? "&b◉" : "&6◎");
                } else if (addon.getOneBlocksIsland(island).isChunkUnlocked(dx, dz)) {
                    row.append(here ? "&b◆" : "&a■");
                } else if (cm.checkGeometry(island, centerChunkX + dx, centerChunkZ + dz) == ClaimResult.OK) {
                    row.append(here ? "&b◆" : "&e▣");
                } else {
                    row.append(here ? "&b◇" : "&7□");
                }
            }
            user.sendMessage(user.getTranslationAsComponent("chunkblock.chunks.map.row", "[row]", row.toString())
                    .font(MONOSPACE_FONT));
        }
        user.sendMessage("chunkblock.chunks.map.legend", "[cost]", String.valueOf(cm.getChunkCost()));
    }
}
