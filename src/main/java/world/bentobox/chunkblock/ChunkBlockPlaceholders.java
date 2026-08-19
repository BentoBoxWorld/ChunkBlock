package world.bentobox.chunkblock;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.bukkit.Material;

import world.bentobox.chunkblock.dataobjects.OneBlockIslands;
import world.bentobox.chunkblock.panels.PhasesPanel;
import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.hooks.LangUtilsHook;
import world.bentobox.bentobox.util.Util;

public class ChunkBlockPlaceholders {

    private static final TreeMap<Double, String> SCALE;
    private static final String INFINITE = "chunkblock.placeholders.infinite";
    private static final String UNKNOWN_PHASE = "chunkblock.placeholders.my-island-phase-default";
    static {
        SCALE = new TreeMap<>();
        SCALE.put(0D, "&c╍╍╍╍╍╍╍╍");
        SCALE.put(12.5, "&a╍&c╍╍╍╍╍╍╍");
        SCALE.put(25.0, "&a╍╍&c╍╍╍╍╍╍");
        SCALE.put(37.5, "&a╍╍╍&c╍╍╍╍╍");
        SCALE.put(50D, "&a╍╍╍╍&c╍╍╍╍");
        SCALE.put(62.5, "&a╍╍╍╍╍&c╍╍╍");
        SCALE.put(75.0, "&a╍╍╍╍╍╍&c╍╍");
        SCALE.put(87.5, "&a╍╍╍╍╍╍╍&c╍");
        SCALE.put(100D, "&a╍╍╍╍╍╍╍╍");
    }

    private final ChunkBlock addon;

    public ChunkBlockPlaceholders(ChunkBlock addon,
            world.bentobox.bentobox.managers.PlaceholdersManager placeholdersManager) {
        this.addon = addon;
        placeholdersManager.registerPlaceholder(addon, "visited_island_phase", this::getPhaseByLocation);
        placeholdersManager.registerPlaceholder(addon, "visited_island_count", this::getCountByLocation);
        placeholdersManager.registerPlaceholder(addon, "my_island_phase", this::getPhase);
        placeholdersManager.registerPlaceholder(addon, "my_island_count", this::getCount);
        placeholdersManager.registerPlaceholder(addon, "visited_island_next_phase", this::getNextPhaseByLocation);
        placeholdersManager.registerPlaceholder(addon, "my_island_next_phase", this::getNextPhase);
        placeholdersManager.registerPlaceholder(addon, "my_island_blocks_for_phase", this::getPhaseBlocks);
        placeholdersManager.registerPlaceholder(addon, "my_island_blocks_to_next_phase", this::getNextPhaseBlocks);
        placeholdersManager.registerPlaceholder(addon, "visited_island_blocks_to_next_phase",
                this::getNextPhaseBlocksByLocation);
        placeholdersManager.registerPlaceholder(addon, "my_island_percent_done", this::getPercentDone);
        placeholdersManager.registerPlaceholder(addon, "visited_island_percent_done", this::getPercentDoneByLocation);
        placeholdersManager.registerPlaceholder(addon, "my_island_done_scale", this::getDoneScale);
        placeholdersManager.registerPlaceholder(addon, "visited_island_done_scale", this::getDoneScaleByLocation);
        // Since 1.10
        placeholdersManager.registerPlaceholder(addon, "visited_island_lifetime_count", this::getLifetimeByLocation);
        placeholdersManager.registerPlaceholder(addon, "my_island_lifetime_count", this::getLifetime);

        placeholdersManager.registerPlaceholder(addon, "visited_island_phase_block_list",
                this::getPhaseBlocksNamesByLocation);
        placeholdersManager.registerPlaceholder(addon, "my_island_phase_block_list", this::getPhaseBlocksNames);

        // Chunk territory placeholders
        placeholdersManager.registerPlaceholder(addon, "island_chunks", this::getIslandChunks);
        placeholdersManager.registerPlaceholder(addon, "island_max_chunks", this::getIslandMaxChunks);
        placeholdersManager.registerPlaceholder(addon, "island_next_chunk_level", this::getIslandNextChunkLevel);
        placeholdersManager.registerPlaceholder(addon, "island_chunk_credit", this::getIslandChunkCredit);
        placeholdersManager.registerPlaceholder(addon, "island_ring", this::getIslandRing);
        placeholdersManager.registerPlaceholder(addon, "island_rings_complete", this::getIslandRingsComplete);
        placeholdersManager.registerPlaceholder(addon, "island_next_ring_remaining", this::getNextRingRemaining);
        placeholdersManager.registerPlaceholder(addon, "island_title", this::getIslandTitle);
        placeholdersManager.registerPlaceholder(addon, "island_trophies", this::getIslandTrophies);
    }

    /**
     * @param user user
     * @return the user's island's active trophy title as configured (MiniMessage text),
     *         or an empty string for no title
     */
    public String getIslandTitle(User user) {
        if (user == null || user.getUniqueId() == null || addon.getTrophyManager() == null) {
            return "";
        }
        return getUsersIsland(user).map(i -> addon.getTrophyManager().getActiveTitleText(i)).orElse("");
    }

    /**
     * @param user user
     * @return how many trophies the user's island has earned
     */
    public String getIslandTrophies(User user) {
        if (user == null || user.getUniqueId() == null || addon.getTrophyManager() == null) {
            return "";
        }
        return getUsersIsland(user)
                .map(i -> String.valueOf(addon.getOneBlocksIsland(i).getEarnedTrophies().size()))
                .orElse("");
    }

    /**
     * @param user user
     * @return number of chunks the user's island has unlocked
     */
    public String getIslandChunks(User user) {
        if (user == null || user.getUniqueId() == null) {
            return "";
        }
        return getUsersIsland(user)
                .map(i -> String.valueOf(addon.getChunkManager().getUnlockedChunkCount(i))).orElse("");
    }

    /**
     * @param user user
     * @return maximum number of chunks the user's island can unlock
     */
    public String getIslandMaxChunks(User user) {
        if (user == null || user.getUniqueId() == null) {
            return "";
        }
        return getUsersIsland(user).map(i -> String.valueOf(addon.getChunkManager().getMaxChunks(i))).orElse("");
    }

    /**
     * @param user user
     * @return total island level needed to afford the user's island's next chunk
     */
    public String getIslandNextChunkLevel(User user) {
        if (user == null || user.getUniqueId() == null) {
            return "";
        }
        return getUsersIsland(user).map(i -> String.valueOf(
                addon.getChunkManager().getSpentLevels(i) + addon.getChunkManager().getChunkCost()))
                .orElse("");
    }

    /**
     * @param user user
     * @return level credit the user's island has available to spend on chunks
     */
    public String getIslandChunkCredit(User user) {
        if (user == null || user.getUniqueId() == null) {
            return "";
        }
        return getUsersIsland(user)
                .map(i -> String.valueOf(Math.max(0, addon.getChunkManager().getCredit(i)))).orElse("");
    }

    /**
     * @param user user
     * @return ring number of the user's island's outermost unlocked chunk
     */
    public String getIslandRing(User user) {
        if (user == null || user.getUniqueId() == null) {
            return "";
        }
        return getUsersIsland(user).map(i -> String.valueOf(addon.getChunkManager().currentRing(i))).orElse("");
    }

    /**
     * @param user user
     * @return how many whole rings the user's island has closed around its center
     */
    public String getIslandRingsComplete(User user) {
        if (user == null || user.getUniqueId() == null) {
            return "";
        }
        return getUsersIsland(user).map(i -> String.valueOf(addon.getChunkManager().completedRings(i))).orElse("");
    }

    /**
     * @param user user
     * @return how many chunks remain to complete the user's island's next ring, or "0" if
     *         all rings are done
     */
    public String getNextRingRemaining(User user) {
        if (user == null || user.getUniqueId() == null) {
            return "";
        }
        return getUsersIsland(user).map(i -> {
            int next = addon.getChunkManager().completedRings(i) + 1;
            if (next > addon.getChunkManager().maxRingRadius(i)) {
                return "0";
            }
            return String.valueOf(addon.getChunkManager().chunksRemainingInRing(i, next));
        }).orElse("");
    }

    /**
     * Get the user's owned island. Returns the island owned by the user, not a team
     * island they may be visiting as a member. If the user owns more than one island,
     * one is picked.
     * @param user user
     * @return island owned by the user, or empty if they own none
     */
    private Optional<Island> getUsersIsland(User user) {
        // Get the active island for the user
        Island i = addon.getIslands().getIsland(addon.getOverWorld(), user);
        if (i != null && i.getOwner() != null && user.getUniqueId().equals(i.getOwner())) {
            // User is the owner of their primary island
            return Optional.of(i);
        }

        // Find an island the user actually owns (not just a team island they are visiting)
        return addon.getIslands().getOwnedIslands(addon.getOverWorld(), user).stream().findFirst();
    }

    public String getPhaseBlocksNames(User user) {
        if (user == null || user.getUniqueId() == null)
            return "";
        return getUsersIsland(user).map(i -> getPhaseBlocksForIsland(user, i)).orElse("");
    }

    private String getPhaseBlocksForIsland(User user, Island i) {
        String phaseName = addon.getOneBlocksIsland(i).getPhaseName();
        Set<Material> set = addon.getOneBlockManager().getPhase(phaseName).map(phase -> phase.getBlocks().keySet())
                .orElse(null);
        if (set == null) {
            return "";
        }

        String result = set.stream().map(m -> getMaterialName(user, m))
                .map(string -> user.getTranslation(PhasesPanel.REFERENCE + "blocks", TextVariables.NAME,
                        string))
                .collect(Collectors.joining());
        // Removing the last newline character or comma if it exists
        result = result.trim();
        if (result.endsWith("\n") || result.endsWith(",")) {
            result = result.substring(0, result.length() - 1);
        }

        return result;

    }

    private String getMaterialName(User user, Material m) {
        return addon.getPlugin().getHooks().getHook("LangUtils").map(hook -> LangUtilsHook.getMaterialName(m, user))
                .orElse(Util.prettifyText(m.name()));
    }

    public String getPhaseBlocksNamesByLocation(User user) {
        if (user == null || user.getUniqueId() == null || !addon.inWorld(user.getWorld()))
            return "";
        return addon.getIslands().getIslandAt(user.getLocation()).map(i -> getPhaseBlocksForIsland(user, i)).orElse("");
    }

    /**
     * Get phase by location of user
     * 
     * @param user - user
     * @return Phase name
     */
    public String getPhaseByLocation(User user) {
        if (user == null || user.getUniqueId() == null)
            return "";
        return addon.getIslands().getProtectedIslandAt(Objects.requireNonNull(user.getLocation()))
                .map(addon::getOneBlocksIsland).map(OneBlockIslands::getPhaseName).orElse("");
    }

    /**
     * Get block count by user location
     * 
     * @param user - user
     * @return String of count
     */
    public String getCountByLocation(User user) {
        if (user == null || user.getUniqueId() == null || !addon.inWorld(user.getWorld()))
            return "";
        return addon.getIslands().getProtectedIslandAt(Objects.requireNonNull(user.getLocation()))
                .map(addon::getOneBlocksIsland).map(OneBlockIslands::getBlockNumber).map(String::valueOf).orElse("");
    }

    /**
     * Get user's island phase
     * 
     * @param user - island owner or team member
     * @return phase name
     */
    public String getPhase(User user) {
        if (user == null || user.getUniqueId() == null)
            return "";
        return getUsersIsland(user).map(i -> addon.getOneBlocksIsland(i).getPhaseName())
                .orElse(user.getTranslation(UNKNOWN_PHASE));
    }

    /**
     * Get island block count
     * 
     * @param user island owner or team member
     * @return string of block count
     */
    public String getCount(User user) {
        if (user == null || user.getUniqueId() == null)
            return "";
        return getUsersIsland(user).map(i -> String.valueOf(addon.getOneBlocksIsland(i).getBlockNumber())).orElse("0");
    }

    /**
     * Get the next phase based on user's location
     * 
     * @param user - user
     * @return next phase
     */
    public String getNextPhaseByLocation(User user) {
        if (user == null || user.getUniqueId() == null || !addon.inWorld(user.getWorld()))
            return "";
        return addon.getIslands().getProtectedIslandAt(Objects.requireNonNull(user.getLocation()))
                .map(addon::getOneBlocksIsland).map(addon.getOneBlockManager()::getNextPhase).orElse("");
    }

    /**
     * Get next island phase
     * 
     * @param user island owner or team member
     * @return next island phase
     */
    public String getNextPhase(User user) {
        if (user == null || user.getUniqueId() == null)
            return "";
        return getUsersIsland(user).map(i -> addon.getOneBlockManager().getNextPhase(addon.getOneBlocksIsland(i)))
                .orElse("");
    }

    /**
     * Get how many blocks until next phase based on user's location
     * 
     * @param user user
     * @return string number of blocks
     */
    public String getNextPhaseBlocksByLocation(User user) {
        if (user == null || user.getUniqueId() == null || !addon.inWorld(user.getWorld()))
            return "";
        return addon.getIslands().getProtectedIslandAt(Objects.requireNonNull(user.getLocation()))
                .map(addon::getOneBlocksIsland).map(addon.getOneBlockManager()::getNextPhaseBlocks)
                .map(num -> num < 0 ? user.getTranslation(INFINITE) : String.valueOf(num)).orElse("");
    }

    /**
     * Get how many blocks until the next island phase
     * 
     * @param user owner or team member
     * @return string number of blocks
     */
    public String getNextPhaseBlocks(User user) {
        if (user == null || user.getUniqueId() == null)
            return "";
        return getUsersIsland(user).map(i -> {
            int num = addon.getOneBlockManager().getNextPhaseBlocks(addon.getOneBlocksIsland(i));
            return num < 0 ? user.getTranslation(INFINITE) : String.valueOf(num);
        }).orElse("");
    }

    /**
     * Get how many blocks for this phase
     * 
     * @param user owner or team member
     * @return string number of blocks
     */
    public String getPhaseBlocks(User user) {
        if (user == null || user.getUniqueId() == null)
            return "";
        return getUsersIsland(user).map(i -> {
            int num = addon.getOneBlockManager().getPhaseBlocks(addon.getOneBlocksIsland(i));
            return num < 0 ? user.getTranslation(INFINITE) : String.valueOf(num);
        }).orElse("");
    }

    /**
     * Get percentage done of current phase by user's location
     * 
     * @param user - user
     * @return string percentage
     */
    public String getPercentDoneByLocation(User user) {
        if (user == null || user.getUniqueId() == null || !addon.inWorld(user.getWorld()))
            return "";
        return addon.getIslands().getProtectedIslandAt(Objects.requireNonNull(user.getLocation()))
                .map(addon::getOneBlocksIsland).map(addon.getOneBlockManager()::getPercentageDone)
                .map(num -> Math.round(num) + "%").orElse("");
    }

    /**
     * Get percentage done of user's island phase
     * 
     * @param user owner or team member
     * @return string percentage
     */
    public String getPercentDone(User user) {
        if (user == null || user.getUniqueId() == null)
            return "";
        return getUsersIsland(user).map(i -> {
            double num = addon.getOneBlockManager().getPercentageDone(addon.getOneBlocksIsland(i));
            return Math.round(num) + "%";
        }).orElse("0%");
    }

    /**
     * Get percentage done of phase as colored scale based on user's location
     * 
     * @param user user
     * @return colored scale
     */
    public String getDoneScaleByLocation(User user) {
        if (user == null || user.getUniqueId() == null || !addon.inWorld(user.getWorld()))
            return "";
        return addon.getIslands().getProtectedIslandAt(Objects.requireNonNull(user.getLocation()))
                .map(addon::getOneBlocksIsland).map(addon.getOneBlockManager()::getPercentageDone)
                .map(num -> SCALE.floorEntry(num).getValue())
                .map(s -> s.replace("╍", addon.getSettings().getPercentCompleteSymbol())).orElse("");
    }

    /**
     * Get percentage done of phase as colored scale
     * 
     * @param user owner or team member
     * @return colored scale
     */
    public String getDoneScale(User user) {
        if (user == null || user.getUniqueId() == null)
            return "";
        return getUsersIsland(user).map(i -> {
            double num = addon.getOneBlockManager().getPercentageDone(addon.getOneBlocksIsland(i));
            return SCALE.floorEntry(num).getValue().replace("╍", addon.getSettings().getPercentCompleteSymbol());
        }).orElse("");
    }

    /**
     * Get island Lifetime count
     * 
     * @param user island owner or team member
     * @return string of Lifetime count
     */
    public String getLifetime(User user) {
        if (user == null || user.getUniqueId() == null)
            return "";

        return getUsersIsland(user).map(i -> String.valueOf(this.addon.getOneBlocksIsland(i).getLifetime()))
                .orElse("");
    }

    /**
     * Get Lifetime count by user location
     * 
     * @param user - user
     * @return String of Lifetime
     */
    public String getLifetimeByLocation(User user) {
        if (user == null || user.getUniqueId() == null || !addon.inWorld(user.getWorld()))
            return "";

        return this.addon.getIslands().getProtectedIslandAt(Objects.requireNonNull(user.getLocation()))
                .map(this.addon::getOneBlocksIsland).map(OneBlockIslands::getLifetime).map(String::valueOf).orElse("");
    }
}
