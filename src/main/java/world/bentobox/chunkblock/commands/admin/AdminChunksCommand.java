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
import world.bentobox.chunkblock.dataobjects.OneBlockIslands;

/**
 * /cbadmin chunks &lt;player&gt; [set &lt;n&gt; | recalc] — support and debug tool: shows a
 * player's unlocked chunk count, sets it directly, or recalculates it from their island
 * level.
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
        if (args.isEmpty() || args.size() > 3) {
            showHelp(this, user);
            return false;
        }
        return true;
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        UUID targetUUID = Util.getUUID(args.get(0));
        if (targetUUID == null) {
            user.sendMessage("general.errors.unknown-player", TextVariables.NAME, args.get(0));
            return false;
        }
        Island island = getIslands().getIsland(getWorld(), targetUUID);
        if (island == null) {
            user.sendMessage("general.errors.player-has-no-island");
            return false;
        }
        OneBlockIslands data = addon.getOneBlocksIsland(island);
        if (args.size() == 1) {
            user.sendMessage("chunkblock.commands.admin.chunks.info", TextVariables.NAME, args.get(0),
                    TextVariables.NUMBER, String.valueOf(addon.getChunkManager().getUnlockedChunkCount(island)),
                    "[max]", String.valueOf(addon.getChunkManager().getMaxChunks(island)));
            return true;
        }
        String action = args.get(1).toLowerCase(Locale.ENGLISH);
        if ("set".equals(action) && args.size() == 3) {
            Optional<Integer> count = Optional.of(args.get(2)).filter(a -> Util.isInteger(a, true))
                    .map(Integer::parseInt);
            if (count.isEmpty() || count.get() < 1) {
                user.sendMessage("general.errors.must-be-positive-number", TextVariables.NUMBER, args.get(2));
                return false;
            }
            data.setUnlockedChunkCount(Math.min(count.get(), addon.getChunkManager().getMaxChunks(island)));
            addon.getBlockListener().saveIsland(island);
            user.sendMessage("chunkblock.commands.admin.chunks.set", TextVariables.NAME, args.get(0),
                    TextVariables.NUMBER, String.valueOf(data.getUnlockedChunkCount()));
            return true;
        }
        if ("recalc".equals(action) && args.size() == 2) {
            long level = addon.getIslandLevel(island);
            data.setUnlockedChunkCount(addon.getChunkManager().computeUnlockedCount(island, level));
            addon.getBlockListener().saveIsland(island);
            user.sendMessage("chunkblock.commands.admin.chunks.recalc", TextVariables.NAME, args.get(0),
                    TextVariables.NUMBER, String.valueOf(data.getUnlockedChunkCount()));
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
            return Optional.of(List.of("set", "recalc"));
        }
        return Optional.empty();
    }
}
