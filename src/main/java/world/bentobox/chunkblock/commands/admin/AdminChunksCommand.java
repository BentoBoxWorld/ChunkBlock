package world.bentobox.chunkblock.commands.admin;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.util.Util;
import world.bentobox.chunkblock.ChunkBlock;
import world.bentobox.chunkblock.chunks.ChunkManager;

/**
 * /chadmin chunks &lt;player&gt; [reset] — support and debug tool: shows a player's
 * unlocked chunks, spending and credit, or re-locks everything back to the center chunk.
 *
 * @author tastybento
 */
public class AdminChunksCommand extends CompositeCommand {

    private ChunkBlock addon;

    public AdminChunksCommand(CompositeCommand adminCommand) {
        super(adminCommand, "chunks");
    }

    @Override
    public void setup() {
        setDescription("chunkblock.commands.admin.chunks.description");
        setParametersHelp("chunkblock.commands.admin.chunks.parameters");
        setPermission("admin.chunks");
        addon = getAddon();
    }

    @Override
    public boolean canExecute(User user, String label, List<String> args) {
        if (args.isEmpty() || args.size() > 2) {
            showHelp(this, user);
            return false;
        }
        return true;
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        UUID targetUUID = Util.getUUID(args.getFirst());
        if (targetUUID == null) {
            user.sendMessage("general.errors.unknown-player", TextVariables.NAME, args.getFirst());
            return false;
        }
        Island island = getIslands().getIsland(getWorld(), targetUUID);
        if (island == null) {
            user.sendMessage("general.errors.player-has-no-island");
            return false;
        }
        ChunkManager cm = addon.getChunkManager();
        if (args.size() == 1) {
            user.sendMessage("chunkblock.commands.admin.chunks.info", TextVariables.NAME, args.getFirst(),
                    TextVariables.NUMBER, String.valueOf(cm.getUnlockedChunkCount(island)),
                    "[max]", String.valueOf(cm.getMaxChunks(island)),
                    "[spent]", String.valueOf(cm.getSpentLevels(island)),
                    "[credit]", String.valueOf(cm.getCredit(island)));
            return true;
        }
        if ("reset".equals(args.get(1).toLowerCase(Locale.ENGLISH))) {
            addon.getOneBlocksIsland(island).resetUnlockedChunks();
            addon.getBlockListener().saveIsland(island);
            user.sendMessage("chunkblock.commands.admin.chunks.reset", TextVariables.NAME, args.getFirst());
            return true;
        }
        showHelp(this, user);
        return false;
    }

    @Override
    public Optional<List<String>> tabComplete(User user, String alias, List<String> args) {
        if (args.size() == 2) {
            return Optional.of(Util.getOnlinePlayerList(user));
        }
        if (args.size() == 3) {
            return Optional.of(List.of("reset"));
        }
        return Optional.empty();
    }
}
