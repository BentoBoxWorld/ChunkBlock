package world.bentobox.chunkblock.dataobjects;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNull;

import com.google.gson.annotations.Expose;

import world.bentobox.bentobox.database.objects.DataObject;
import world.bentobox.bentobox.database.objects.Table;

/**
 * Per-island activity counters, keyed by (member, counter): the counting substrate that
 * ledgers, leaderboards and trophies read from. One object per island, uniqueId is the
 * island's uniqueId.
 * <p>
 * Counters are stored twice: a lifetime running total, and per-day buckets so time
 * windows (daily, weekly, a season) can be summed. Old daily buckets are pruned after a
 * configurable retention period; pruning never touches the lifetime totals.
 * <p>
 * Both maps are deliberately flat {@code String -> Long} with composite keys — every
 * database backend serializes that shape without custom adapters. Keys are
 * {@code member|COUNTER} for lifetime and {@code member|COUNTER|epochDay} for daily
 * buckets, where {@code member} is a player UUID or the island-scope key for activity no
 * member can be credited with.
 *
 * @author tastybento
 */
@Table(name = "ChunkBlockActivity")
public class IslandActivity implements DataObject {

    /** Key separator inside composite map keys. Never appears in a UUID or counter name. */
    private static final char SEP = '|';

    @Expose
    private String uniqueId;

    /** Lifetime totals: {@code member|COUNTER -> amount} */
    @Expose
    private Map<String, Long> lifetime = new HashMap<>();

    /** Daily buckets: {@code member|COUNTER|epochDay -> amount} */
    @Expose
    private Map<String, Long> daily = new HashMap<>();

    /**
     * Every chunk offset ("dx,dz") this island has ever claimed, so a chunk recovered
     * after a re-lock can be told apart from a first claim. Cleared only on island
     * create or reset — surviving admin re-locks is the point.
     */
    @Expose
    private Set<String> claimedEver = new HashSet<>();

    public IslandActivity() {
        // Required by the database loader
    }

    public IslandActivity(String uniqueId) {
        this.uniqueId = uniqueId;
    }

    @Override
    public String getUniqueId() {
        return uniqueId;
    }

    @Override
    public void setUniqueId(String uniqueId) {
        this.uniqueId = uniqueId;
    }

    /**
     * @return the lifetime totals map, never null
     */
    @NonNull
    public Map<String, Long> getLifetime() {
        if (lifetime == null) {
            lifetime = new HashMap<>();
        }
        return lifetime;
    }

    public void setLifetime(Map<String, Long> lifetime) {
        this.lifetime = lifetime;
    }

    /**
     * @return the daily buckets map, never null
     */
    @NonNull
    public Map<String, Long> getDaily() {
        if (daily == null) {
            daily = new HashMap<>();
        }
        return daily;
    }

    public void setDaily(Map<String, Long> daily) {
        this.daily = daily;
    }

    /**
     * @return the set of chunk offsets ever claimed, never null
     */
    @NonNull
    public Set<String> getClaimedEver() {
        if (claimedEver == null) {
            claimedEver = new HashSet<>();
        }
        return claimedEver;
    }

    public void setClaimedEver(Set<String> claimedEver) {
        this.claimedEver = claimedEver;
    }

    /**
     * Adds an amount to a member's counter: the lifetime total and today's bucket.
     *
     * @param memberKey player UUID string, or the island-scope key
     * @param counter counter name
     * @param epochDay today as an epoch day
     * @param amount amount to add, &gt; 0
     */
    public void add(String memberKey, String counter, long epochDay, long amount) {
        getLifetime().merge(memberKey + SEP + counter, amount, Long::sum);
        getDaily().merge(memberKey + SEP + counter + SEP + epochDay, amount, Long::sum);
    }

    /**
     * @param memberKey player UUID string, or the island-scope key
     * @param counter counter name
     * @return the member's lifetime total for the counter
     */
    public long getLifetime(String memberKey, String counter) {
        return getLifetime().getOrDefault(memberKey + SEP + counter, 0L);
    }

    /**
     * @param counter counter name
     * @return the island's lifetime total for the counter across every member and the
     *         island scope
     */
    public long getLifetimeTotal(String counter) {
        String suffix = SEP + counter;
        return getLifetime().entrySet().stream().filter(e -> e.getKey().endsWith(suffix))
                .mapToLong(Map.Entry::getValue).sum();
    }

    /**
     * Sums a member's daily buckets over an inclusive day range.
     *
     * @param memberKey player UUID string, or the island-scope key
     * @param counter counter name
     * @param fromDay first epoch day, inclusive
     * @param toDay last epoch day, inclusive
     * @return the summed amount; days already pruned contribute nothing
     */
    public long sumWindow(String memberKey, String counter, long fromDay, long toDay) {
        String prefix = memberKey + SEP + counter + SEP;
        return sumBuckets(prefix, prefix.length(), fromDay, toDay);
    }

    /**
     * Sums the island's daily buckets over an inclusive day range across every member and
     * the island scope.
     *
     * @param counter counter name
     * @param fromDay first epoch day, inclusive
     * @param toDay last epoch day, inclusive
     * @return the summed amount; days already pruned contribute nothing
     */
    public long sumWindowTotal(String counter, long fromDay, long toDay) {
        String infix = SEP + counter + SEP;
        long sum = 0;
        for (Map.Entry<String, Long> e : getDaily().entrySet()) {
            int at = e.getKey().indexOf(infix);
            if (at >= 0 && inRange(e.getKey(), at + infix.length(), fromDay, toDay)) {
                sum += e.getValue();
            }
        }
        return sum;
    }

    private long sumBuckets(String prefix, int dayStart, long fromDay, long toDay) {
        long sum = 0;
        for (Map.Entry<String, Long> e : getDaily().entrySet()) {
            if (e.getKey().startsWith(prefix) && inRange(e.getKey(), dayStart, fromDay, toDay)) {
                sum += e.getValue();
            }
        }
        return sum;
    }

    private boolean inRange(String key, int dayStart, long fromDay, long toDay) {
        long day = parseDay(key, dayStart);
        return day >= fromDay && day <= toDay;
    }

    private long parseDay(String key, int dayStart) {
        try {
            return Long.parseLong(key.substring(dayStart));
        } catch (NumberFormatException e) {
            return Long.MIN_VALUE;
        }
    }

    /**
     * Removes every daily bucket older than the given day. Lifetime totals are untouched:
     * pruning only limits how far back a window can reach.
     *
     * @param oldestKeptDay the oldest epoch day to keep
     */
    public void prune(long oldestKeptDay) {
        getDaily().keySet()
                .removeIf(key -> parseDay(key, key.lastIndexOf(SEP) + 1) < oldestKeptDay);
    }

    /**
     * @return every member key that has a lifetime total, island scope included
     */
    @NonNull
    public Set<String> getMemberKeys() {
        Set<String> keys = new HashSet<>();
        for (String key : getLifetime().keySet()) {
            int at = key.indexOf(SEP);
            if (at > 0) {
                keys.add(key.substring(0, at));
            }
        }
        return keys;
    }
}
