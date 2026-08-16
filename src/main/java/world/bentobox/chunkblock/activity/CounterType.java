package world.bentobox.chunkblock.activity;

/**
 * The activity counters recorded per (island, member). These are data, not reward:
 * ledgers, leaderboards and trophies all read from them, but nothing here pays anything
 * out.
 * <p>
 * Counters attributed to a member are only recorded while that player is actually on the
 * island's team, so recruiting a veteran cannot import old progress. Counters an event
 * cannot attribute to a member (an island level change, a re-lock) are recorded at island
 * scope instead.
 *
 * @author tastybento
 */
public enum CounterType {
    /** Magic blocks broken at the island's center. Island scope when broken by a minion. */
    MAGIC_BLOCKS,
    /**
     * Island levels gained. Always island scope: the Level addon reports the island's
     * level as a whole and cannot attribute a change to a member.
     */
    LEVELS_EARNED,
    /**
     * Chunks claimed that the island had never claimed before — distinct chunks, so
     * re-locking a chunk and claiming it back never counts twice.
     */
    CHUNKS_CLAIMED,
    /** Claims of chunks the island had claimed before: territory recovered after a re-lock. */
    CHUNKS_RECLAIMED,
    /** Chunks lost to level loss. Island scope — nobody performs a re-lock. */
    CHUNKS_RELOCKED,
    /** Rings completed for the first time. Island scope, one per ring per island. */
    RINGS_COMPLETED
}
