package world.bentobox.chunkblock.trophies;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.chunkblock.ChunkBlock;
import world.bentobox.chunkblock.activity.CounterType;
import world.bentobox.chunkblock.dataobjects.OneBlockIslands;
import world.bentobox.chunkblock.events.TrophyAwardEvent;
import world.bentobox.chunkblock.trophies.Trophy.Criteria;
import world.bentobox.chunkblock.trophies.Trophy.CriteriaType;
import world.bentobox.chunkblock.trophies.Trophy.Scope;

/**
 * The reward layer over the activity counters: config-defined trophies an island earns
 * once and keeps, and the titles they carry. Conditions read the counters and the ring
 * state; nothing here is hardcoded to a particular milestone.
 * <p>
 * Earned trophies persist on the island and only an island create or reset clears them,
 * so re-locking a ring on level loss and claiming it back never re-awards — the same
 * lesson as {@code highestRingRewarded}.
 *
 * @author tastybento
 */
public class TrophyManager {

    private static final String TROPHIES_FILE = "trophies.yml";
    /** Prefix for config-validation complaints */
    private static final String TROPHY = "Trophy '";

    private final ChunkBlock addon;
    /** Trophies by id, in config order */
    private Map<String, Trophy> trophies = new LinkedHashMap<>();
    /** Islands currently mid-check, so an award's own side effects cannot re-enter */
    private final Set<String> checking = new HashSet<>();

    public TrophyManager(ChunkBlock addon) {
        this.addon = addon;
    }

    /**
     * Loads trophy definitions from trophies.yml in the addon's data folder. Invalid
     * trophies are logged and skipped; the rest still load.
     */
    public void loadTrophies() {
        File file = new File(addon.getDataFolder(), TROPHIES_FILE);
        if (!file.exists()) {
            addon.saveResource(TROPHIES_FILE, false);
        }
        loadTrophies(YamlConfiguration.loadConfiguration(file).getConfigurationSection("trophies"));
    }

    /**
     * Loads trophy definitions from a configuration section. Exposed for testing.
     *
     * @param section the "trophies" section, each key one trophy; null loads nothing
     */
    public void loadTrophies(@Nullable ConfigurationSection section) {
        Map<String, Trophy> loaded = new LinkedHashMap<>();
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection ts = section.getConfigurationSection(id);
                if (ts == null) {
                    addon.logError(TROPHY + id + "' is not a section - skipping.");
                    continue;
                }
                Trophy trophy = parseTrophy(id, ts);
                if (trophy != null) {
                    loaded.put(id, trophy);
                }
            }
        }
        trophies = loaded;
    }

    @Nullable
    private Trophy parseTrophy(String id, ConfigurationSection ts) {
        Material icon = Material.matchMaterial(ts.getString("icon", "GOLD_INGOT"));
        if (icon == null) {
            addon.logError(TROPHY + id + "': unknown icon material '" + ts.getString("icon")
                    + "' - skipping.");
            return null;
        }
        Criteria criteria = parseCriteria(id, ts.getConfigurationSection("criteria"));
        if (criteria == null) {
            return null;
        }
        return new Trophy(id, ts.getString("name", id), ts.getString("description", ""), icon,
                ts.getString("title"), criteria, ts.getStringList("rewards.commands"),
                ts.getStringList("rewards.player-commands"));
    }

    @Nullable
    private Criteria parseCriteria(String id, @Nullable ConfigurationSection cs) {
        if (cs == null) {
            addon.logError(TROPHY + id + "': no criteria section - skipping.");
            return null;
        }
        CriteriaType type = matchEnum(CriteriaType.class, cs.getString("type"));
        if (type == null) {
            addon.logError(TROPHY + id + "': criteria type must be RING or COUNTER - skipping.");
            return null;
        }
        if (type == CriteriaType.RING) {
            int ring = cs.getInt("ring", 0);
            if (ring < 1) {
                addon.logError(TROPHY + id + "': ring must be 1 or more - skipping.");
                return null;
            }
            return new Criteria(type, ring, null, Scope.ISLAND, 0, 0);
        }
        CounterType counter = matchEnum(CounterType.class, cs.getString("counter"));
        if (counter == null) {
            addon.logError(TROPHY + id + "': unknown counter '" + cs.getString("counter")
                    + "' - skipping.");
            return null;
        }
        long threshold = cs.getLong("threshold", 0);
        if (threshold < 1) {
            addon.logError(TROPHY + id + "': threshold must be 1 or more - skipping.");
            return null;
        }
        Scope scope = matchEnum(Scope.class, cs.getString("scope", "ISLAND"));
        if (scope == null) {
            addon.logError(TROPHY + id + "': scope must be ISLAND or MEMBER - skipping.");
            return null;
        }
        return new Criteria(type, 0, counter, scope, threshold, Math.max(0, cs.getInt("window-days", 0)));
    }

    @Nullable
    private <T extends Enum<T>> T matchEnum(Class<T> clazz, @Nullable String name) {
        if (name == null) {
            return null;
        }
        try {
            return Enum.valueOf(clazz, name.toUpperCase(Locale.ENGLISH).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * @return all loaded trophies in config order
     */
    @NonNull
    public Collection<Trophy> getTrophies() {
        return Collections.unmodifiableCollection(trophies.values());
    }

    /**
     * @param id trophy id
     * @return the trophy, or empty if no such trophy is defined
     */
    @NonNull
    public Optional<Trophy> getTrophy(@Nullable String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(trophies.get(id));
    }

    /**
     * Evaluates every trophy this island has not yet earned and awards the ones whose
     * condition is now met. Called whenever activity is recorded; cheap unless something
     * is actually awarded.
     *
     * @param island the island to check
     */
    public void check(@NonNull Island island) {
        if (trophies.isEmpty() || !checking.add(island.getUniqueId())) {
            // Re-entered from an award's own side effects — the outer check finishes the job
            return;
        }
        try {
            OneBlockIslands data = addon.getOneBlocksIsland(island);
            for (Trophy trophy : trophies.values()) {
                if (!data.getEarnedTrophies().contains(trophy.id()) && isMet(island, trophy)) {
                    award(island, data, trophy);
                }
            }
        } finally {
            checking.remove(island.getUniqueId());
        }
    }

    private boolean isMet(Island island, Trophy trophy) {
        Criteria c = trophy.criteria();
        if (c.type() == CriteriaType.RING) {
            return addon.getChunkManager().completedRings(island) >= c.ring();
        }
        // Parsing guarantees every COUNTER criteria has a counter
        CounterType counter = Objects.requireNonNull(c.counter());
        if (c.scope() == Scope.ISLAND) {
            return addon.getActivityManager().getCount(island, null, counter, c.windowDays()) >= c
                    .threshold();
        }
        return addon.getActivityManager().getContributors(island).stream().anyMatch(
                uuid -> addon.getActivityManager().getCount(island, uuid, counter, c.windowDays()) >= c
                        .threshold());
    }

    /**
     * Awards a trophy. The trophy is persisted as earned before the event goes out, so it
     * can never be awarded twice; cancelling the event suppresses only the addon's own
     * handling — messages, sound, title and reward commands.
     */
    private void award(Island island, OneBlockIslands data, Trophy trophy) {
        data.getEarnedTrophies().add(trophy.id());
        // First titled trophy becomes the island's title until someone picks another
        if (trophy.title() != null && data.getActiveTitle().isEmpty()) {
            data.setActiveTitle(trophy.id());
        }
        addon.getBlockListener().saveIsland(island);
        TrophyAwardEvent event = new TrophyAwardEvent(island, trophy);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        island.getMemberSet().forEach(uuid -> {
            User user = User.getInstance(uuid);
            if (user.isOnline() && addon.inWorld(user.getWorld())) {
                user.sendMessage("chunkblock.trophies.awarded", "[name]", trophy.name());
                if (trophy.title() != null) {
                    user.sendMessage("chunkblock.trophies.title-available", "[title]", trophy.title());
                }
                user.getPlayer().playSound(user.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1F, 1.2F);
            }
        });
        runCommands(island, trophy);
    }

    /**
     * Runs the trophy's console reward commands. The config warns against paying out
     * island levels here: levels buy chunks, so a level-granting trophy makes each
     * milestone buy the next one.
     */
    private void runCommands(Island island, Trophy trophy) {
        String ownerName = island.getOwner() == null ? "" : addon.getPlayers().getName(island.getOwner());
        for (String command : trophy.commands()) {
            if (!ownerName.isEmpty()) {
                dispatch(command.replace("[owner]", ownerName).replace("[trophy]", trophy.id()));
            }
        }
        if (!trophy.playerCommands().isEmpty()) {
            for (UUID uuid : island.getMemberSet()) {
                String name = addon.getPlayers().getName(uuid);
                if (name != null && !name.isEmpty()) {
                    for (String command : trophy.playerCommands()) {
                        dispatch(command.replace("[player]", name).replace("[trophy]", trophy.id()));
                    }
                }
            }
        }
    }

    private void dispatch(String command) {
        if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)) {
            addon.logError("Trophy reward command failed: " + command);
        }
    }

    /**
     * @param island the island
     * @return the trophies this island has earned, in config order
     */
    @NonNull
    public List<Trophy> getEarned(@NonNull Island island) {
        Set<String> earned = addon.getOneBlocksIsland(island).getEarnedTrophies();
        List<Trophy> result = new ArrayList<>();
        for (Trophy trophy : trophies.values()) {
            if (earned.contains(trophy.id())) {
                result.add(trophy);
            }
        }
        return result;
    }

    /**
     * @param island the island
     * @return the island's active title text (MiniMessage), or an empty string if the
     *         island has no title or its trophy is no longer defined
     */
    @NonNull
    public String getActiveTitleText(@NonNull Island island) {
        return getTrophy(addon.getOneBlocksIsland(island).getActiveTitle())
                .map(trophy -> trophy.title() == null ? "" : trophy.title()).orElse("");
    }

    /**
     * Sets the island's active title to the one carried by an earned trophy, or clears it.
     *
     * @param island the island
     * @param trophyId an earned trophy's id whose title to show, or null to clear
     * @return true if the title was set or cleared, false if the trophy is unknown,
     *         unearned or carries no title
     */
    public boolean setActiveTitle(@NonNull Island island, @Nullable String trophyId) {
        OneBlockIslands data = addon.getOneBlocksIsland(island);
        if (trophyId == null) {
            data.setActiveTitle("");
            addon.getBlockListener().saveIsland(island);
            return true;
        }
        Optional<Trophy> trophy = getTrophy(trophyId);
        if (trophy.isEmpty() || trophy.get().title() == null
                || !data.getEarnedTrophies().contains(trophyId)) {
            return false;
        }
        data.setActiveTitle(trophyId);
        addon.getBlockListener().saveIsland(island);
        return true;
    }
}
