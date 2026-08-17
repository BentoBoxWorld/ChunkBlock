package world.bentobox.chunkblock.commands.island;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.util.Util;
import world.bentobox.chunkblock.ChunkBlock;
import world.bentobox.chunkblock.activity.ActivityManager;
import world.bentobox.chunkblock.activity.CounterType;

/**
 * /ch ledger — shows each team member's contributions to the island: blocks broken,
 * chunks claimed, and rings completed. Reads from the activity counters foundation;
 * the ledger itself adds no persistence.
 *
 * @author tastybento
 */
public class IslandLedgerCommand extends CompositeCommand {

    private static final String REF = "chunkblock.commands.ledger.";

    private ChunkBlock addon;

    public IslandLedgerCommand(CompositeCommand islandCommand, String label, String[] aliases) {
        super(islandCommand, label, aliases);
    }

    @Override
    public void setup() {
        setDescription(REF + "description");
        setOnlyPlayer(true);
        setPermission("island.ledger");
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
        Optional<Island> optionalIsland = getIslands().getIslandAt(user.getLocation());
        if (optionalIsland.isEmpty()) {
            user.sendMessage("general.errors.not-on-island");
            return false;
        }
        Island island = optionalIsland.get();
        ActivityManager am = addon.getActivityManager();
        if (am == null) {
            return false;
        }

        int window = parseWindow(args);
        String windowLabel = window <= 0
                ? user.getTranslation(REF + "all-time")
                : user.getTranslation(REF + "last-days", "[days]", String.valueOf(window));

        user.sendMessage(REF + "header", "[window]", windowLabel);

        List<MemberRow> rows = buildRows(island, am, window);
        if (rows.isEmpty()) {
            user.sendMessage(REF + "no-activity");
            return true;
        }

        for (MemberRow row : rows) {
            user.sendMessage(REF + "row",
                    "[name]", row.name,
                    "[blocks]", String.valueOf(row.blocks),
                    "[chunks]", String.valueOf(row.chunks),
                    "[rings]", String.valueOf(row.rings));
        }

        long totalBlocks = rows.stream().mapToLong(r -> r.blocks).sum();
        long totalChunks = rows.stream().mapToLong(r -> r.chunks).sum();
        long totalRings = rows.stream().mapToLong(r -> r.rings).sum();
        user.sendMessage(REF + "total",
                "[blocks]", String.valueOf(totalBlocks),
                "[chunks]", String.valueOf(totalChunks),
                "[rings]", String.valueOf(totalRings));

        return true;
    }

    private int parseWindow(List<String> args) {
        if (args.isEmpty()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(args.get(0)));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private List<MemberRow> buildRows(Island island, ActivityManager am, int window) {
        List<MemberRow> rows = new ArrayList<>();
        for (UUID uuid : am.getContributors(island)) {
            long blocks = am.getCount(island, uuid, CounterType.MAGIC_BLOCKS, window);
            long chunks = am.getCount(island, uuid, CounterType.CHUNKS_CLAIMED, window)
                    + am.getCount(island, uuid, CounterType.CHUNKS_RECLAIMED, window);
            long rings = am.getCount(island, uuid, CounterType.RINGS_COMPLETED, window);
            if (blocks > 0 || chunks > 0 || rings > 0) {
                String name = addon.getPlayers().getName(uuid);
                rows.add(new MemberRow(name == null || name.isEmpty() ? uuid.toString() : name,
                        blocks, chunks, rings));
            }
        }
        rows.sort(Comparator.comparingLong(MemberRow::blocks).reversed());
        return rows;
    }

    private record MemberRow(String name, long blocks, long chunks, long rings) {
    }
}
