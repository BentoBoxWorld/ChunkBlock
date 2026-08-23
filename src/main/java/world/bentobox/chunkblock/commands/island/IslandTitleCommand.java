package world.bentobox.chunkblock.commands.island;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.util.Util;
import world.bentobox.chunkblock.ChunkBlock;
import world.bentobox.chunkblock.trophies.Trophy;

/**
 * /ch title — shows the island's earned trophies and the titles they carry, and lets a
 * member pick which title the island shows. One active title per island, chosen from the
 * trophies it has earned; "none" clears it.
 *
 * @author tastybento
 */
public class IslandTitleCommand extends CompositeCommand {

    private static final String CLEAR = "none";
    private static final String TITLE_VAR = "[title]";

    private ChunkBlock addon;

    public IslandTitleCommand(CompositeCommand islandCommand, String label, String[] aliases) {
        super(islandCommand, label, aliases);
    }

    @Override
    public void setup() {
        setDescription("chunkblock.commands.title.description");
        setParametersHelp("chunkblock.commands.title.parameters");
        setOnlyPlayer(true);
        setPermission("island.title");
        addon = getAddon();
    }

    @Override
    public boolean canExecute(User user, String label, List<String> args) {
        if (!Util.sameWorld(getWorld(), user.getWorld())) {
            user.sendMessage("general.errors.wrong-world");
            return false;
        }
        if (getIslands().getIsland(getWorld(), user) == null) {
            user.sendMessage("general.errors.no-island");
            return false;
        }
        return true;
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        Island island = getIslands().getIsland(getWorld(), user);
        if (island == null) {
            // canExecute already refused this, but never spend credit on a null island
            user.sendMessage("general.errors.no-island");
            return false;
        }
        if (args.isEmpty()) {
            return toggleTitle(user, island);
        }
        if ("list".equalsIgnoreCase(args.getFirst())) {
            showTitles(user, island);
            return true;
        }
        String id = args.getFirst();
        if (CLEAR.equalsIgnoreCase(id)) {
            addon.getTrophyManager().setActiveTitle(island, null);
            user.sendMessage("chunkblock.commands.title.cleared");
            return true;
        }
        if (!addon.getTrophyManager().setActiveTitle(island, id)) {
            user.sendMessage("chunkblock.commands.title.not-earned");
            return false;
        }
        addon.getTrophyManager().getTrophy(id).ifPresent(trophy -> user
                .sendMessage("chunkblock.commands.title.set", TITLE_VAR, trophy.title()));
        return true;
    }

    /**
     * Lists the island's earned trophies, marking the ones that carry a title and which
     * title is active.
     */
    private void showTitles(User user, Island island) {
        List<Trophy> earned = addon.getTrophyManager().getEarned(island);
        if (earned.isEmpty()) {
            user.sendMessage("chunkblock.commands.title.none-earned-yet");
            return;
        }
        user.sendMessage("chunkblock.commands.title.header");
        for (Trophy trophy : earned) {
            if (trophy.title() == null) {
                user.sendMessage("chunkblock.commands.title.trophy-entry", "[name]", trophy.name());
            } else {
                user.sendMessage("chunkblock.commands.title.title-entry", "[name]", trophy.name(),
                        TITLE_VAR, trophy.title(), "[id]", trophy.id());
            }
        }
        String active = addon.getTrophyManager().getActiveTitleText(island);
        if (active.isEmpty()) {
            user.sendMessage("chunkblock.commands.title.no-active");
        } else {
            user.sendMessage("chunkblock.commands.title.active", TITLE_VAR, active);
        }
    }

    private boolean toggleTitle(User user, Island island) {
        String active = addon.getTrophyManager().getActiveTitleText(island);
        if (!active.isEmpty()) {
            addon.getTrophyManager().setActiveTitle(island, null);
            user.sendMessage("chunkblock.commands.title.toggled-off", TITLE_VAR, active);
            return true;
        }
        // No active title — try to activate the first earned trophy that carries one
        Optional<Trophy> first = addon.getTrophyManager().getEarned(island).stream()
                .filter(t -> t.title() != null).findFirst();
        if (first.isEmpty()) {
            user.sendMessage("chunkblock.commands.title.none-earned-yet");
            return false;
        }
        addon.getTrophyManager().setActiveTitle(island, first.get().id());
        user.sendMessage("chunkblock.commands.title.toggled-on", TITLE_VAR, first.get().title());
        return true;
    }

    @Override
    public Optional<List<String>> tabComplete(User user, String alias, List<String> args) {
        Island island = getIslands().getIsland(getWorld(), user);
        if (island == null) {
            return Optional.empty();
        }
        List<String> options = new ArrayList<>();
        options.add("list");
        options.add(CLEAR);
        addon.getTrophyManager().getEarned(island).stream().filter(t -> t.title() != null)
                .map(Trophy::id).forEach(options::add);
        String last = args.isEmpty() ? "" : args.getLast();
        return Optional.of(Util.tabLimit(options, last));
    }
}
