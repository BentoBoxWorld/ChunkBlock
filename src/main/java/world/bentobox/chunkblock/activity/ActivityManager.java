package world.bentobox.chunkblock.activity;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import world.bentobox.bentobox.database.Database;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.chunkblock.ChunkBlock;
import world.bentobox.chunkblock.dataobjects.IslandActivity;

/**
 * Records and answers questions about per-member island activity: the counting layer that
 * a contribution ledger, a season leaderboard or a trophy condition reads from. Counters
 * are data, not reward — nothing here grants anything.
 * <p>
 * Attribution rules: an amount recorded with a member is only kept while that player is
 * actually on the island's team, so a veteran joining cannot import progress and a member
 * who leaves keeps their contribution credited to the island they made it on. Activity no
 * member can be credited with (level changes, re-locks, minion breaks) is recorded at
 * island scope instead.
 *
 * @author tastybento
 */
public class ActivityManager {

    /** Member key for activity that cannot be attributed to a single member */
    public static final String ISLAND_SCOPE = "island";

    /** How many frequent-counter records may accumulate before an async save is forced */
    private static final int SAVE_EVERY = 20;

    private final ChunkBlock addon;
    private final Database<IslandActivity> handler;
    private final Map<String, IslandActivity> cache = new HashMap<>();
    /** Unsaved frequent-counter records per island, so block breaks don't save every hit */
    private final Map<String, Integer> unsavedCounts = new HashMap<>();

    /** Today as an epoch day in the server's zone; replaceable so tests can move time */
    private LongSupplier daySupplier = () -> LocalDate.now(ZoneId.systemDefault()).toEpochDay();

    public ActivityManager(ChunkBlock addon) {
        this.addon = addon;
        handler = new Database<>(addon, IslandActivity.class);
    }

    /**
     * Replaces the day source. Used only for testing.
     *
     * @param daySupplier supplier of today as an epoch day
     */
    public void setDaySupplier(LongSupplier daySupplier) {
        this.daySupplier = daySupplier;
    }

    /**
     * Records activity against an island. An amount attributed to a player who is not a
     * member of the island's team is dropped — that is the point, not an oversight.
     *
     * @param island the island the activity happened on
     * @param member the member who performed it, or null for island scope
     * @param type the counter
     * @param amount the amount to add, &gt; 0 (anything else is ignored)
     */
    public void recordActivity(@NonNull Island island, @Nullable UUID member, @NonNull CounterType type, long amount) {
        if (amount <= 0 || (member != null && !island.getMemberSet().contains(member))) {
            return;
        }
        IslandActivity data = getActivity(island.getUniqueId());
        long today = daySupplier.getAsLong();
        data.add(member == null ? ISLAND_SCOPE : member.toString(), type.name(), today, amount);
        data.prune(today - Math.max(1, addon.getSettings().getActivityRetentionDays()) + 1);
        save(data, type == CounterType.MAGIC_BLOCKS);
        if (addon.getTrophyManager() != null) {
            addon.getTrophyManager().check(island);
        }
    }

    /**
     * Records a chunk claim, distinguishing a first-ever claim from the recovery of a
     * chunk lost to a re-lock. "Chunks claimed" means distinct chunks: an island that
     * loses a chunk and claims it back scores a recovery, never a second claim.
     *
     * @param island the island
     * @param dx chunk x offset relative to the center chunk
     * @param dz chunk z offset relative to the center chunk
     * @param member the claiming member, or null if unknown
     * @return true if this was the chunk's first-ever claim
     */
    public boolean recordClaim(@NonNull Island island, int dx, int dz, @Nullable UUID member) {
        IslandActivity data = getActivity(island.getUniqueId());
        boolean first = data.getClaimedEver().add(dx + "," + dz);
        recordActivity(island, member, first ? CounterType.CHUNKS_CLAIMED : CounterType.CHUNKS_RECLAIMED, 1);
        // recordActivity() may have skipped saving (non-member) but the claimedEver set changed
        if (first) {
            save(data, false);
        }
        return first;
    }

    /**
     * Answers "how much of this did this member do for this island in the last N days" —
     * or in their lifetime on the team.
     *
     * @param island the island
     * @param member the member, or null for the island total across every member and the
     *        island scope
     * @param type the counter
     * @param windowDays how many days back to count, today included; 0 or less means
     *        lifetime. Days beyond the configured retention have been pruned and count 0.
     * @return the amount
     */
    public long getCount(@NonNull Island island, @Nullable UUID member, @NonNull CounterType type, int windowDays) {
        IslandActivity data = getActivity(island.getUniqueId());
        if (windowDays <= 0) {
            return member == null ? data.getLifetimeTotal(type.name())
                    : data.getLifetime(member.toString(), type.name());
        }
        long today = daySupplier.getAsLong();
        long from = today - windowDays + 1;
        return member == null ? data.sumWindowTotal(type.name(), from, today)
                : data.sumWindow(member.toString(), type.name(), from, today);
    }

    /**
     * @param island the island
     * @return every player who has recorded activity on this island, present or past
     *         members alike
     */
    @NonNull
    public Set<UUID> getContributors(@NonNull Island island) {
        Set<UUID> result = new HashSet<>();
        for (String key : getActivity(island.getUniqueId()).getMemberKeys()) {
            if (!ISLAND_SCOPE.equals(key)) {
                try {
                    result.add(UUID.fromString(key));
                } catch (IllegalArgumentException e) {
                    // Not a player key — ignore
                }
            }
        }
        return result;
    }

    /**
     * Wipes an island's activity. Called on island create and reset — a fresh start owes
     * nothing to the old island's history.
     *
     * @param islandId the island's uniqueId
     */
    public void resetIsland(@NonNull String islandId) {
        IslandActivity fresh = new IslandActivity(islandId);
        cache.put(islandId, fresh);
        unsavedCounts.remove(islandId);
        handler.saveObjectAsync(fresh);
    }

    /**
     * Deletes an island's activity outright.
     *
     * @param islandId the island's uniqueId
     */
    public void deleteIsland(@NonNull String islandId) {
        cache.remove(islandId);
        unsavedCounts.remove(islandId);
        handler.deleteID(islandId);
    }

    /**
     * Saves all cached activity asynchronously. Only safe while the server is running; on
     * shutdown use {@link #saveCacheNow()}.
     */
    public void saveCache() {
        cache.values().forEach(handler::saveObjectAsync);
        unsavedCounts.clear();
    }

    /**
     * Saves all cached activity on the calling thread. Used on shutdown, where a queued
     * asynchronous save could be lost — same reasoning as the block-count cache.
     */
    public void saveCacheNow() {
        cache.values().forEach(handler::saveObjectNow);
        unsavedCounts.clear();
    }

    @NonNull
    private IslandActivity getActivity(@NonNull String islandId) {
        IslandActivity data = cache.get(islandId);
        if (data != null) {
            return data;
        }
        if (handler.objectExists(islandId)) {
            data = handler.loadObject(islandId);
        }
        if (data == null) {
            data = new IslandActivity(islandId);
        }
        cache.put(islandId, data);
        return data;
    }

    /**
     * Saves now, unless this is a high-frequency counter, which saves every
     * {@link #SAVE_EVERY} records instead. Everything left over is flushed by the cache
     * saves on reload and shutdown.
     */
    private void save(IslandActivity data, boolean throttled) {
        if (throttled) {
            int count = unsavedCounts.merge(data.getUniqueId(), 1, Integer::sum);
            if (count < SAVE_EVERY) {
                return;
            }
        }
        unsavedCounts.remove(data.getUniqueId());
        handler.saveObjectAsync(data);
    }
}
