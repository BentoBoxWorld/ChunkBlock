package world.bentobox.chunkblock;

import java.io.IOException;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.WorldCreator;
import org.bukkit.entity.SpawnCategory;
import org.bukkit.generator.ChunkGenerator;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import world.bentobox.chunkblock.activity.ActivityManager;
import world.bentobox.chunkblock.chunks.BorderDisplay;
import world.bentobox.chunkblock.chunks.ChunkManager;
import world.bentobox.chunkblock.listeners.ActivityListener;
import world.bentobox.chunkblock.trophies.TrophyManager;
import world.bentobox.chunkblock.commands.admin.AdminCommand;
import world.bentobox.chunkblock.commands.island.PlayerCommand;
import world.bentobox.chunkblock.dataobjects.OneBlockIslands;
import world.bentobox.chunkblock.generators.ChunkGeneratorWorld;
import world.bentobox.chunkblock.listeners.BlockListener;
import world.bentobox.chunkblock.listeners.BlockProtect;
import world.bentobox.chunkblock.listeners.ChunkClaimListener;
import world.bentobox.chunkblock.listeners.ChunkGuardListener;
import world.bentobox.chunkblock.listeners.LockedChunkProtect;
import world.bentobox.chunkblock.listeners.BossBarListener;
import world.bentobox.chunkblock.listeners.HoloListener;
import world.bentobox.chunkblock.listeners.InfoListener;
import world.bentobox.chunkblock.listeners.CraftEngineListener;
import world.bentobox.chunkblock.listeners.ItemsAdderListener;
import world.bentobox.chunkblock.listeners.JoinLeaveListener;
import world.bentobox.chunkblock.listeners.LevelListener;
import world.bentobox.chunkblock.listeners.NexoListener;
import world.bentobox.chunkblock.listeners.NoBlockHandler;
import world.bentobox.chunkblock.listeners.StartSafetyListener;
import world.bentobox.chunkblock.oneblocks.OneBlockCustomBlockCreator;
import world.bentobox.chunkblock.oneblocks.OneBlocksManager;
import world.bentobox.chunkblock.oneblocks.customblock.CraftEngineCustomBlock;
import world.bentobox.chunkblock.oneblocks.customblock.ItemsAdderCustomBlock;
import world.bentobox.chunkblock.oneblocks.customblock.NexoCustomBlock;
import world.bentobox.chunkblock.requests.IslandStatsHandler;
import world.bentobox.chunkblock.requests.MemberActivityHandler;
import world.bentobox.chunkblock.requests.UnlockedChunksHandler;
import world.bentobox.chunkblock.requests.LocationStatsHandler;
import world.bentobox.bentobox.api.addons.GameModeAddon;
import world.bentobox.bentobox.api.configuration.Config;
import world.bentobox.bentobox.api.configuration.WorldSettings;
import world.bentobox.bentobox.api.flags.Flag;
import world.bentobox.bentobox.api.flags.Flag.Mode;
import world.bentobox.bentobox.api.flags.Flag.Type;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.managers.RanksManager;

/**
 * Main OneBlock class - provides an island minigame in the sky
 *
 * @author tastybento
 */
public class ChunkBlock extends GameModeAddon {

    /** Suffix for the nether world */
    private static final String NETHER = "_nether";
    /** Suffix for the end world */
    private static final String THE_END = "_the_end";
    /** Whether ItemsAdder is present on the server */
    private boolean hasItemsAdder = false;
    /** Whether Nexo is present on the server */
    private boolean hasNexo = false;
    /** Whether CraftEngine is present on the server */
    private boolean hasCraftEngine = false;

    /** The addon settings */
    private Settings settings;
    /** The custom chunk generator for OneBlock worlds */
    private ChunkGeneratorWorld chunkGenerator;
    /** The configuration object for settings */
    private final Config<Settings> configObject = new Config<>(this, Settings.class);
    /** The listener for block-related events */
    private BlockListener blockListener;
    /** The listener that keeps players out of locked chunks */
    private ChunkGuardListener chunkGuardListener;
    /** The listener that turns level changes into chunk credit and re-locks */
    private LevelListener levelListener;
    /** The locked-chunk border visuals */
    private BorderDisplay borderDisplay;
    /** The manager for OneBlock phases and blocks */
    private OneBlocksManager oneBlockManager;
    /** The manager for chunk locking and the unlock spiral */
    private ChunkManager chunkManager;
    /** The per-member activity counters (the ledger/leaderboard/trophy substrate) */
    private ActivityManager activityManager;
    /** The config-defined island trophies and titles */
    private TrophyManager trophyManager;
    /** The placeholder manager for ChunkBlock */
    private ChunkBlockPlaceholders phManager;
    /** The listener for hologram-related events */
    private HoloListener holoListener;

    /**
     * Flag to enable or disable start safety for players.
     */
    public final Flag CHUNKBLOCK_START_SAFETY = new Flag.Builder("CHUNKBLOCK_START_SAFETY", Material.BAMBOO_BLOCK)
            .mode(Mode.BASIC)
            .type(Type.WORLD_SETTING)
            .listener(new StartSafetyListener(this))
            .defaultSetting(false)
            .build();
    /** The listener for the boss bar */
    private final BossBarListener bossBar = new BossBarListener(this);
    /**
     * Flag to enable or disable the OneBlock boss bar.
     */
    public final Flag CHUNKBLOCK_BOSSBAR = new Flag.Builder("CHUNKBLOCK_BOSSBAR", Material.DRAGON_HEAD)
            .mode(Mode.BASIC)
            .type(Type.SETTING)
            .listener(bossBar)
            .defaultSetting(true)
            .build();
    /**
     * Flag to enable or disable the OneBlock action bar.
     */
    public final Flag CHUNKBLOCK_ACTIONBAR = new Flag.Builder("CHUNKBLOCK_ACTIONBAR", Material.IRON_BARS)
            .mode(Mode.BASIC)
            .type(Type.SETTING)
            .listener(bossBar)
            .defaultSetting(true)
            .build();    
    /**
     * Flag to set who can break the magic block.
     */
    public final Flag CHUNKBLOCK_MAGIC_BLOCK = new Flag.Builder("CHUNKBLOCK_MAGIC_BLOCK", Material.GRASS_BLOCK)
            .mode(Mode.BASIC)
            .type(Type.PROTECTION)
            .defaultRank(RanksManager.COOP_RANK)
            .build();
    /**
     * Flag to set who can spend the island's level credit on new chunks. Defaults to the
     * owner alone, because a claim is irreversible until the levels are earned back.
     */
    public final Flag CHUNKBLOCK_CLAIM_CHUNKS = new Flag.Builder("CHUNKBLOCK_CLAIM_CHUNKS", Material.OAK_FENCE_GATE)
            .mode(Mode.BASIC)
            .type(Type.PROTECTION)
            .defaultRank(RanksManager.OWNER_RANK)
            .build();

    @Override
    public void onLoad() {
        // Check if ItemsAdder exists, if yes register listener
        if (Bukkit.getPluginManager().getPlugin("ItemsAdder") != null) {
            registerListener(new ItemsAdderListener(this));
            OneBlockCustomBlockCreator.register(ItemsAdderCustomBlock::fromId);
            OneBlockCustomBlockCreator.register("itemsadder", ItemsAdderCustomBlock::fromMap);
            hasItemsAdder = true;
        }
        // Check if Nexo exists, if yes register listener
        if (Bukkit.getPluginManager().getPlugin("Nexo") != null) {
            registerListener(new NexoListener(this));
            OneBlockCustomBlockCreator.register(NexoCustomBlock::fromId);
            OneBlockCustomBlockCreator.register("nexo", NexoCustomBlock::fromMap);
            hasNexo = true;
        }
        // Check if CraftEngine exists, if yes register listener
        if (Bukkit.getPluginManager().getPlugin("CraftEngine") != null) {
            registerListener(new CraftEngineListener(this));
            OneBlockCustomBlockCreator.register(CraftEngineCustomBlock::fromId);
            OneBlockCustomBlockCreator.register("craftengine", CraftEngineCustomBlock::fromMap);
            hasCraftEngine = true;
        }
        // Save the default config from config.yml
        saveDefaultConfig();
        // Load settings from config.yml. This will check if there are any issues with
        // it too.
        if (loadSettings()) {
            // Chunk generator
            chunkGenerator = settings.isUseOwnGenerator() ? null : new ChunkGeneratorWorld(this);
            // Register commands
            playerCommand = new PlayerCommand(this);
            adminCommand = new AdminCommand(this);
            // Register flag with BentoBox
            // Register protection flag with BentoBox
            registerFlagOrWarn(CHUNKBLOCK_START_SAFETY);
            // Bossbar
            if (getSettings().isBossBar()) {
                registerFlagOrWarn(this.CHUNKBLOCK_BOSSBAR);
            }
            // Actionbar
            if (getSettings().isActionBar()) {
                registerFlagOrWarn(this.CHUNKBLOCK_ACTIONBAR);
            }
            // Magic Block protection
            registerFlagOrWarn(this.CHUNKBLOCK_MAGIC_BLOCK);
            // Who may spend level credit on chunks
            registerFlagOrWarn(this.CHUNKBLOCK_CLAIM_CHUNKS);
        }
    }

    /**
     * Registers a flag and complains if it is refused. A flag whose ID is already taken by
     * another addon is dropped silently by the flags manager, and this addon then runs
     * against whichever definition won — so the only symptom would be settings that
     * quietly do nothing. Every ID here is prefixed to avoid that, and this says so out
     * loud if one ever collides anyway.
     *
     * @param flag the flag to register
     */
    private void registerFlagOrWarn(Flag flag) {
        if (!registerFlag(flag)) {
            logError("Flag " + flag.getID() + " is already registered by another addon, so ChunkBlock's own "
                    + "definition was dropped. Its island settings will behave as that addon defines them.");
        }
    }

    /**
     * Loads the settings from the config file.
     * @return true if settings were loaded successfully, false otherwise.
     */
    private boolean loadSettings() {
        // Load settings again to get worlds
        settings = configObject.loadConfigObject();
        if (settings == null) {
            // Disable
            logError("ChunkBlock settings could not load! Addon disabled.");
            setState(State.DISABLED);
            return false;
        } else {
            // Save the settings
            configObject.saveConfigObject(settings);
        }
        return true;
    }

    @Override
    public void onEnable() {
        // ChunkBlock cannot run without the Level addon: island levels are the currency
        // spent to claim chunks. addon.yml declares the hard dependency, but check here
        // too so a missing Level shuts the addon down cleanly instead of leaving it
        // half-alive.
        if (getAddonByName("Level").isEmpty()) {
            logError("ChunkBlock requires the Level addon - island levels are the currency used to claim chunks.");
            logError("Install Level from https://github.com/BentoBoxWorld/Level or remove ChunkBlock. Disabling.");
            // Take down the flags registered in onLoad so their listeners do not linger
            getPlugin().getFlagsManager().unregister(this);
            setState(State.DISABLED);
            return;
        }
        // Initialize the OneBlock manager
        oneBlockManager = new OneBlocksManager(this);
        // Initialize the chunk lock manager
        chunkManager = new ChunkManager(this);
        // Initialize the activity counters and the trophies that read them
        activityManager = new ActivityManager(this);
        trophyManager = new TrophyManager(this);
        trophyManager.loadTrophies();
        // Load phase data
        if (loadData()) {
            // Failed to load - don't register anything
            return;
        }
        // Initialize and register listeners
        blockListener = new BlockListener(this);
        registerListener(blockListener);
        chunkGuardListener = new ChunkGuardListener(this);
        registerListener(chunkGuardListener);
        registerListener(new LockedChunkProtect(this));
        levelListener = new LevelListener(this);
        registerListener(levelListener);
        registerListener(new ChunkClaimListener(this));
        borderDisplay = new BorderDisplay(this);
        registerListener(borderDisplay);
        borderDisplay.start();
        registerListener(new NoBlockHandler(this));
        registerListener(new BlockProtect(this));
        registerListener(new JoinLeaveListener(this));
        registerListener(new InfoListener(this));
        registerListener(new ActivityListener(this));
        // Note: bossBar is registered as a listener by the FlagsManager when the
        // CHUNKBLOCK_BOSSBAR or CHUNKBLOCK_ACTIONBAR flag is registered in onLoad, so it
        // must not be registered here too or events would be handled twice
        // Register placeholders
        phManager = new ChunkBlockPlaceholders(this, getPlugin().getPlaceholdersManager());

        // Register request handlers
        registerRequestHandler(new IslandStatsHandler(this));
        registerRequestHandler(new LocationStatsHandler(this));
        registerRequestHandler(new UnlockedChunksHandler(this));
        registerRequestHandler(new MemberActivityHandler(this));

        // Register Holograms
        holoListener = new HoloListener(this);
        registerListener(holoListener);
    }

    /**
     * Load phase data from oneblock.yml.
     * @return true if there was an error, false otherwise.
     */
    public boolean loadData() {
        if (oneBlockManager == null) {
            // oneBlockManager is not yet initialized (addon not fully enabled)
            return false;
        }
        try {
            oneBlockManager.loadPhases();
        } catch (IOException e) {
            // Disable the addon if phase data cannot be loaded
            logError("ChunkBlock settings could not load (oneblock.yml error)! Addon disabled.");
            logError(e.getMessage());
            setState(State.DISABLED);
            return true;
        }
        return false;
    }

    @Override
    public void onDisable() {
        // Save cache. This must be a direct write, not a queued one: the server disables this
        // Pladdon before BentoBox, so anything queued here depends on BentoBox draining it later.
        if (blockListener != null) {
            blockListener.saveCacheNow();
        }
        if (activityManager != null) {
            activityManager.saveCacheNow();
        }

        // Stop border rendering and restore client-side blocks
        if (borderDisplay != null) {
            borderDisplay.stop();
        }

        // Clear holograms
        if (holoListener != null) {
            holoListener.onDisable();
        }
    }

    @Override
    public void onReload() {
        // save cache
        blockListener.saveCache();
        if (activityManager != null) {
            activityManager.saveCache();
        }
        // Reload settings and phase data
        if (loadSettings()) {
            log("Reloaded ChunkBlock settings");
            loadData();
        }
        if (trophyManager != null) {
            trophyManager.loadTrophies();
        }
    }

    /**
     * @return the settings
     */
    public Settings getSettings() {
        return settings;
    }

    /**
     * @return the chunk lock manager
     */
    public ChunkManager getChunkManager() {
        return chunkManager;
    }

    /**
     * @return the activity counter manager, or null before the addon is enabled
     */
    public ActivityManager getActivityManager() {
        return activityManager;
    }

    /**
     * @return the trophy and title manager, or null before the addon is enabled
     */
    public TrophyManager getTrophyManager() {
        return trophyManager;
    }

    /**
     * @return the chunk guard listener (containment and backtracking)
     */
    public ChunkGuardListener getChunkGuardListener() {
        return chunkGuardListener;
    }

    /**
     * @return the level listener (chunk credit, claim celebrations and re-locks)
     */
    public LevelListener getLevelListener() {
        return levelListener;
    }

    /**
     * @return the locked-chunk border display, or null before the addon is enabled
     */
    public BorderDisplay getBorderDisplay() {
        return borderDisplay;
    }

    /**
     * Reads the island's level from the Level addon.
     *
     * @param island the island
     * @return the island level, or 0 if the Level addon or island owner is missing
     */
    public long getIslandLevel(@NonNull Island island) {
        return getPlugin().getAddonsManager().getAddonByName("Level")
                .filter(world.bentobox.level.Level.class::isInstance)
                .map(world.bentobox.level.Level.class::cast)
                .filter(l -> island.getOwner() != null)
                .map(l -> l.getManager().getIslandLevel(island.getWorld(), island.getOwner()))
                .orElse(0L);
    }

    @Override
    public void createWorlds() {
        String worldName = settings.getWorldName().toLowerCase();
        if (getServer().getWorld(worldName) == null) {
            log("Creating ChunkBlock world ...");
        }

        // Create the world if it does not exist
        islandWorld = getWorld(worldName, World.Environment.NORMAL, chunkGenerator);
        // Make the nether if it does not exist
        if (settings.isNetherGenerate()) {
            if (getServer().getWorld(worldName + NETHER) == null) {
                log("Creating ChunkBlock's Nether...");
            }
            netherWorld = settings.isNetherIslands() ? getWorld(worldName, World.Environment.NETHER, chunkGenerator)
                    : getWorld(worldName, World.Environment.NETHER, null);
        }
        // Make the end if it does not exist
        if (settings.isEndGenerate()) {
            if (getServer().getWorld(worldName + THE_END) == null) {
                log("Creating ChunkBlock's End World...");
            }
            endWorld = settings.isEndIslands() ? getWorld(worldName, World.Environment.THE_END, chunkGenerator)
                    : getWorld(worldName, World.Environment.THE_END, null);
        }
    }

    /**
     * Gets a world or generates a new world if it does not exist
     *
     * @param worldName2      - the overworld name
     * @param env             - the environment
     * @param chunkGenerator2 - the chunk generator. If <tt>null</tt> then the
     *                        generator will not be specified
     * @return world loaded or generated
     */
    private World getWorld(String worldName2, Environment env, ChunkGeneratorWorld chunkGenerator2) {
        // Set world name
        worldName2 = env.equals(World.Environment.NETHER) ? worldName2 + NETHER : worldName2;
        worldName2 = env.equals(World.Environment.THE_END) ? worldName2 + THE_END : worldName2;
        WorldCreator wc = WorldCreator.name(worldName2).environment(env);
        // Use custom generator if configured, otherwise default
        World w = settings.isUseOwnGenerator() ? wc.createWorld() : wc.generator(chunkGenerator2).createWorld();
        // Set spawn rates
        if (w != null) {
            setSpawnRates(w);
        }
        return w;

    }

    /**
     * Sets the spawn rates for a given world based on the addon's settings.
     * @param w The world to set spawn rates for.
     */
    private void setSpawnRates(World w) {
        if (getSettings().getSpawnLimitMonsters() > 0) {
            w.setSpawnLimit(SpawnCategory.MONSTER, getSettings().getSpawnLimitMonsters());
        }
        if (getSettings().getSpawnLimitAmbient() > 0) {
            w.setSpawnLimit(SpawnCategory.AMBIENT, getSettings().getSpawnLimitAmbient());
        }
        if (getSettings().getSpawnLimitAnimals() > 0) {
            w.setSpawnLimit(SpawnCategory.ANIMAL, getSettings().getSpawnLimitAnimals());
        }
        if (getSettings().getSpawnLimitWaterAnimals() > 0) {
            w.setSpawnLimit(SpawnCategory.WATER_ANIMAL, getSettings().getSpawnLimitWaterAnimals());
        }
        if (getSettings().getTicksPerAnimalSpawns() > 0) {
            w.setTicksPerSpawns(SpawnCategory.ANIMAL, getSettings().getTicksPerAnimalSpawns());
        }
        if (getSettings().getTicksPerMonsterSpawns() > 0) {
            w.setTicksPerSpawns(SpawnCategory.MONSTER, getSettings().getTicksPerMonsterSpawns());
        }

    }

    @Override
    public WorldSettings getWorldSettings() {
        return getSettings();
    }

    @Override
    public @Nullable ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return chunkGenerator;
    }

    @Override
    public void saveWorldSettings() {
        if (settings != null) {
            configObject.saveConfigObject(settings);
        }
    }

    @Override
    public void saveDefaultConfig() {
        super.saveDefaultConfig();
        // Save default phases panel
        this.saveResource("panels/phases_panel.yml", false);
        // Save default trophy definitions
        this.saveResource("trophies.yml", false);
    }

    /**
     * (non-Javadoc)
     *
     * @see world.bentobox.bentobox.api.addons.Addon#allLoaded()
     */
    @Override
    public void allLoaded() {
        // save settings. This will occur after all addons have loaded
        this.saveWorldSettings();
    }

    /**
     * @param i - island
     * @return one block island data
     */
    @NonNull
    public OneBlockIslands getOneBlocksIsland(@NonNull Island i) {
        return blockListener.getIsland(Objects.requireNonNull(i));
    }

    /**
     * @return The OneBlock manager.
     */
    public OneBlocksManager getOneBlockManager() {
        return oneBlockManager;
    }

    /**
     * @return the blockListener
     */
    public BlockListener getBlockListener() {
        return blockListener;
    }

    /**
     * Get the placeholder manager
     *
     * @return the phManager
     */
    public ChunkBlockPlaceholders getPlaceholdersManager() {
        return phManager;
    }

    /**
     * @return the holoListener
     */
    public HoloListener getHoloListener() {
        return holoListener;
    }

    /**
     * @return true if ItemsAdder is on the server
     */
    public boolean hasItemsAdder() {
        return hasItemsAdder;
    }

    /**
     * @return true if Nexo is on the server
     */
    public boolean hasNexo() {
        return hasNexo;
    }

    /**
     * @return true if CraftEngine is on the server
     */
    public boolean hasCraftEngine() {
        return hasCraftEngine;
    }

    /**
     * Set the addon's world. Used only for testing.
     * @param world world
     */
    public void setIslandWorld(World world) {
        this.islandWorld = world;

    }

    /**
     * Sets the addon's settings. Used only for testing.
     * @param settings The settings to set.
     */
    public void setSettings(Settings settings) {
        this.settings = settings;
    }

    /**
     * @return the bossBar
     */
    public BossBarListener getBossBar() {
        return bossBar;
    }

}
