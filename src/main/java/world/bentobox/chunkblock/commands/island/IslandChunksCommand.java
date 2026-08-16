package world.bentobox.chunkblock.commands.island;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import net.kyori.adventure.key.Key;
import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.dialogs.Dialogs;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.util.Util;
import world.bentobox.chunkblock.ChunkBlock;
import world.bentobox.chunkblock.chunks.ChunkManager;
import world.bentobox.chunkblock.chunks.ChunkMap;
import world.bentobox.chunkblock.chunks.ChunkMap.Cell;
import world.bentobox.chunkblock.panels.ChunksDialog;

/**
 * /ch chunks — shows how big your island is, how much level credit you can spend, and a
 * map of your territory with the chunks you could claim next: a dialog of one button per
 * chunk where the server supports dialogs, a chat map of glyphs where it does not.
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
        // The dialog map is the good one: buttons are the same size on every client. The
        // chat map is what servers too old for dialogs get instead.
        if (Dialogs.isSupported() && ChunksDialog.show(addon, user, island)) {
            return true;
        }
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
        int width = 2 * radius + 1;
        List<Cell> cells = ChunkMap.cells(addon, island, user.getLocation(), radius);
        user.sendMessage("chunkblock.chunks.map.title", "[unlocked]", String.valueOf(unlocked), "[max]",
                String.valueOf(max));
        for (int row = 0; row < width; row++) {
            // A row goes into the [row] variable of a translation, so it has to be text by
            // then. It is MiniMessage text, the same format the locale files are written
            // in — color codes spliced into a MiniMessage line would show up raw.
            String glyphs = cells.subList(row * width, (row + 1) * width).stream().map(ChunkMap::glyphText)
                    .collect(Collectors.joining());
            user.sendMessage(
                    user.getTranslationAsComponent("chunkblock.chunks.map.row", "[row]", glyphs).font(MONOSPACE_FONT));
        }
        user.sendMessage("chunkblock.chunks.map.legend", "[cost]", String.valueOf(cm.getChunkCost()));
    }
}
