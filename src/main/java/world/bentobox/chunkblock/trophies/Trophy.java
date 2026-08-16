package world.bentobox.chunkblock.trophies;

import java.util.List;

import org.bukkit.Material;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import world.bentobox.chunkblock.activity.CounterType;

/**
 * One config-defined trophy: a name, an icon, a condition, and what earning it grants.
 * Trophies are earned once and stay earned — re-locking and re-claiming never re-awards —
 * and are cleared only by an island create or reset.
 *
 * @param id the trophy's config key, unique among trophies
 * @param name display name, MiniMessage text
 * @param description one-line description, MiniMessage text
 * @param icon icon material for panels and dialogs
 * @param title the island title this trophy makes available, MiniMessage text, or null
 *        if the trophy carries no title
 * @param criteria when the trophy is earned
 * @param commands console commands run once on award; placeholders [owner] and [trophy]
 * @param playerCommands console commands run once per island member on award;
 *        placeholders [player] and [trophy]
 * @author tastybento
 */
public record Trophy(@NonNull String id, @NonNull String name, @NonNull String description,
        @NonNull Material icon, @Nullable String title, @NonNull Criteria criteria,
        @NonNull List<String> commands, @NonNull List<String> playerCommands) {

    /** What kind of condition earns the trophy */
    public enum CriteriaType {
        /** Earned when the island has completed the given ring */
        RING,
        /** Earned when an activity counter reaches a threshold */
        COUNTER
    }

    /** Whose counter a COUNTER criteria reads */
    public enum Scope {
        /** The island's total across all members */
        ISLAND,
        /** Any single member's own count */
        MEMBER
    }

    /**
     * A trophy's earning condition.
     *
     * @param type the condition kind
     * @param ring for RING: the ring that must be complete, &gt;= 1
     * @param counter for COUNTER: the counter read
     * @param scope for COUNTER: island total or any single member
     * @param threshold for COUNTER: the amount that must be reached, &gt;= 1
     * @param windowDays for COUNTER: how many days back to count, 0 for lifetime
     */
    public record Criteria(@NonNull CriteriaType type, int ring, @Nullable CounterType counter,
            @NonNull Scope scope, long threshold, int windowDays) {
    }
}
