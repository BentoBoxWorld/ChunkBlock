package world.bentobox.chunkblock.listeners;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World.Environment;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.util.Util;
import world.bentobox.chunkblock.ChunkBlock;
import world.bentobox.chunkblock.chunks.ChunkManager;

/**
 * Keeps players out of locked chunks — the forbidden zone. Ports the containment
 * techniques proven in the Border addon (cancel-plus-teleport-back movement gate,
 * backtrack for players who end up outside, teleport cause blacklist, mount watch,
 * one-tick-late join processing, item bounce-back) to chunk geometry.
 * <p>
 * Lock checks are 2D: a locked chunk is locked for every y, from below the void to above
 * the build limit, so there is no fly-over or dig-under corridor by construction.
 *
 * @author tastybento
 */
public class ChunkGuardListener implements Listener {

    private static final Vector XZ = new Vector(1, 0, 1);
    /** How long (ticks) dropped items are tracked for bounce-back */
    private static final int ITEM_TRACK_TICKS = 400;

    private final ChunkBlock addon;
    /** Players currently being teleported by this listener; suppresses re-entrant gating */
    private final Set<UUID> inTeleport = new HashSet<>();
    /** Players we just ejected; their next fall damage is cancelled so ejection never kills */
    private final Set<UUID> recentlyEjected = new HashSet<>();
    /** Watch tasks for mounted players */
    private final Map<UUID, BukkitTask> mountedPlayers = new HashMap<>();

    public ChunkGuardListener(ChunkBlock addon) {
        this.addon = addon;
    }

    /**
     * Returns the island whose grid cell contains the location, if any.
     */
    private Optional<Island> islandAt(Location location) {
        return addon.getIslands().getIslandAt(location);
    }

    /**
     * Checks whether the location is inside a locked chunk of some island.
     */
    private boolean isLocked(Location location) {
        return islandAt(location).map(i -> addon.getChunkManager().isLocked(i, location)).orElse(false);
    }

    /**
     * True when the chunk gate does not apply to this player here.
     */
    private boolean isExempt(Player player) {
        return addon.getChunkManager().isExempt(player) || inTeleport.contains(player.getUniqueId());
    }

    // ------------------------------------------------------------------
    // Movement gate
    // ------------------------------------------------------------------

    /**
     * The core gate: cancels movement into a locked chunk and teleports the player back.
     * Cancel alone is not enough at speed (sprint-jumping, elytra, riptide can tunnel
     * through a cancelled move event), hence the teleport.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent e) {
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null || !addon.inWorld(to.getWorld())
                // Head-only movement cannot change chunk
                || from.toVector().multiply(XZ).equals(to.toVector().multiply(XZ))
                // Only evaluate when the chunk changes
                || (from.getBlockX() >> 4 == to.getBlockX() >> 4 && from.getBlockZ() >> 4 == to.getBlockZ() >> 4)) {
            return;
        }
        Player player = e.getPlayer();
        if (isExempt(player) || !isLocked(to)) {
            return;
        }
        e.setCancelled(true);
        User.getInstance(player).notify("chunkblock.chunks.entry-denied");
        if (!isLocked(from)) {
            teleportBack(player, from);
        } else {
            // Already outside somehow (piston push, re-lock underfoot, stale login spot)
            backtrack(player);
        }
    }

    /**
     * Teleports the player the short hop back to where they came from, guarding against
     * the resulting teleport re-triggering the gate. Keeps gliders gliding safely.
     */
    private void teleportBack(Player player, Location target) {
        boolean gliding = player.isGliding();
        inTeleport.add(player.getUniqueId());
        Util.teleportAsync(player, target).thenRun(() -> {
            inTeleport.remove(player.getUniqueId());
            if (gliding && !player.isOnGround()) {
                // Don't let an intercepted glider drop like a stone
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 100, 0, true, false));
            }
        });
    }

    /**
     * Moves a player who is inside locked territory to the nearest unlocked safe spot.
     * Never blind-teleports: prefers the closest unlocked position, creates a landing
     * block if the spot is unsafe, preserves fly state, and falls back to the island home.
     * Public because the re-lock sweep uses it too.
     *
     * @param player the player to move inside
     */
    public void backtrack(Player player) {
        Location loc = player.getLocation();
        Optional<Island> optionalIsland = islandAt(loc);
        if (optionalIsland.isEmpty()) {
            optionalIsland = Optional.ofNullable(addon.getIslands().getIsland(loc.getWorld(), User.getInstance(player)));
        }
        if (optionalIsland.isEmpty()) {
            // Not on any island grid and no island of their own: home teleport is all we can do
            addon.getIslands().homeTeleportAsync(Objects.requireNonNull(loc.getWorld()), player);
            return;
        }
        Island island = optionalIsland.get();
        Location target = addon.getChunkManager().nearestUnlockedSpot(island, loc);
        boolean allowFlight = player.getAllowFlight();
        boolean flying = player.isFlying();
        inTeleport.add(player.getUniqueId());
        recentlyEjected.add(player.getUniqueId());
        if (!addon.getIslands().isSafeLocation(target) && !flying) {
            // Create a landing block so the player is not dropped into the void
            target.getBlock().getRelative(BlockFace.DOWN).setType(safeLandingMaterial(target));
        }
        Util.teleportAsync(player, target).thenRun(() -> {
            inTeleport.remove(player.getUniqueId());
            // Preserve fly across the teleport so fliers don't fall to their death
            if (allowFlight) {
                player.setAllowFlight(true);
                player.setFlying(flying);
            }
            // Clear the fall-damage grace once the player is settled
            Bukkit.getScheduler().runTaskLater(addon.getPlugin(),
                    () -> recentlyEjected.remove(player.getUniqueId()), 200L);
        });
    }

    private Material safeLandingMaterial(Location target) {
        Environment env = target.getWorld() == null ? Environment.NORMAL : target.getWorld().getEnvironment();
        return switch (env) {
        case NETHER -> Material.NETHERRACK;
        case THE_END -> Material.END_STONE;
        default -> Material.STONE;
        };
    }

    /**
     * An ejection must never kill: cancel fall damage for players we just moved.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent e) {
        if (e.getCause() == DamageCause.FALL && e.getEntity() instanceof Player p
                && addon.inWorld(p.getWorld()) && recentlyEjected.remove(p.getUniqueId())) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------
    // Teleports
    // ------------------------------------------------------------------

    /**
     * Blocks ender pearls and chorus fruit into locked chunks (with a pearl refund — the
     * throw was an honest mistake) and backtracks any other teleport that lands a player
     * in locked territory, one tick later when the island state is settled.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent e) {
        Location to = e.getTo();
        Player player = e.getPlayer();
        if (to == null || !addon.inWorld(to.getWorld()) || isExempt(player)) {
            return;
        }
        TeleportCause cause = e.getCause();
        if ((cause == TeleportCause.ENDER_PEARL || cause == TeleportCause.CONSUMABLE_EFFECT) && isLocked(to)) {
            e.setCancelled(true);
            User.getInstance(player).notify("chunkblock.chunks.entry-denied");
            if (cause == TeleportCause.ENDER_PEARL) {
                player.getInventory().addItem(new ItemStack(Material.ENDER_PEARL));
            }
            return;
        }
        // Other causes (plugin homes, commands, respawn anchors...) are allowed to fire,
        // but a stale target inside locked territory gets the backtrack treatment
        Bukkit.getScheduler().runTask(addon.getPlugin(), () -> {
            if (player.isOnline() && addon.inWorld(player.getWorld()) && !isExempt(player)
                    && isLocked(player.getLocation())) {
                backtrack(player);
            }
        });
    }

    // ------------------------------------------------------------------
    // Join / respawn / quit
    // ------------------------------------------------------------------

    /**
     * Covers "the chunk re-locked while I was offline". Runs one tick after join because
     * player state is not usable on the join tick itself.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        Bukkit.getScheduler().runTask(addon.getPlugin(), () -> {
            if (player.isOnline() && addon.inWorld(player.getWorld()) && !isExempt(player)
                    && isLocked(player.getLocation())) {
                backtrack(player);
            }
        });
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerRespawn(PlayerRespawnEvent e) {
        Player player = e.getPlayer();
        Bukkit.getScheduler().runTask(addon.getPlugin(), () -> {
            if (player.isOnline() && addon.inWorld(player.getWorld()) && !isExempt(player)
                    && isLocked(player.getLocation())) {
                backtrack(player);
            }
        });
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerQuit(PlayerQuitEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        inTeleport.remove(uuid);
        recentlyEjected.remove(uuid);
        BukkitTask task = mountedPlayers.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    // ------------------------------------------------------------------
    // Mounts and vehicles
    // ------------------------------------------------------------------

    /**
     * Horses, pigs, striders and boats don't reliably fire gated player-move events, so a
     * mounted player gets a once-a-second watch that ejects them if the mount strays into
     * a locked chunk.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityMount(EntityMountEvent e) {
        if (!(e.getEntity() instanceof Player player) || !addon.inWorld(player.getWorld())) {
            return;
        }
        Entity mount = e.getMount();
        mountedPlayers.put(player.getUniqueId(), Bukkit.getScheduler().runTaskTimer(addon.getPlugin(), () -> {
            Location loc = mount.getLocation();
            if (!addon.inWorld(loc.getWorld()) || isExempt(player) || !isLocked(loc)) {
                return;
            }
            if (!mount.eject()) {
                // Stubborn custom entities: force the dismount
                Bukkit.getPluginManager().callEvent(new EntityDismountEvent(player, mount));
            }
            backtrack(player);
        }, 1L, 20L));
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDismount(EntityDismountEvent e) {
        if (e.getEntity() instanceof Player player) {
            BukkitTask task = mountedPlayers.remove(player.getUniqueId());
            if (task != null) {
                task.cancel();
            }
        }
    }

    /**
     * Gates vehicles at the chunk edge so boats and minecarts (ridden or drifting) don't
     * carry anything across. Ridden vehicles with an exempt driver pass freely.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onVehicleMove(VehicleMoveEvent e) {
        Location from = e.getFrom();
        Location to = e.getTo();
        if (!addon.inWorld(to.getWorld())
                || (from.getBlockX() >> 4 == to.getBlockX() >> 4 && from.getBlockZ() >> 4 == to.getBlockZ() >> 4)
                || !isLocked(to)) {
            return;
        }
        if (e.getVehicle().getPassengers().stream()
                .anyMatch(p -> p instanceof Player player && isExempt(player))) {
            return;
        }
        // Bounce the vehicle back; teleporting ejects passengers, who are then gated as players
        e.getVehicle().setVelocity(e.getVehicle().getVelocity().multiply(-1));
        Util.teleportAsync(e.getVehicle(), from);
    }

    // ------------------------------------------------------------------
    // Item bounce-back
    // ------------------------------------------------------------------

    /**
     * The Border-proven answer to "my stuff flew into the forbidden zone": dropped items
     * are tracked briefly and their velocity reversed the moment they cross into a locked
     * chunk.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onItemDrop(PlayerDropItemEvent e) {
        if (addon.getSettings().isBounceBackItems() && addon.inWorld(e.getPlayer().getWorld())) {
            islandAt(e.getPlayer().getLocation()).ifPresent(is -> trackItem(e.getItemDrop(), is));
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent e) {
        if (!addon.getSettings().isBounceBackItems() || !addon.inWorld(e.getPlayer().getWorld())) {
            return;
        }
        Location loc = e.getPlayer().getLocation();
        islandAt(loc).ifPresent(is -> {
            e.getDrops().forEach(item -> trackItem(e.getPlayer().getWorld().dropItemNaturally(loc, item), is));
            e.getDrops().clear();
        });
    }

    private void trackItem(Item item, Island island) {
        new BukkitRunnable() {
            int ticksActive = 0;

            @Override
            public void run() {
                if (!item.isValid() || ticksActive > ITEM_TRACK_TICKS) {
                    this.cancel();
                    return;
                }
                if (addon.getChunkManager().isLocked(island, item.getLocation())) {
                    item.setVelocity(item.getVelocity().multiply(-0.5));
                    this.cancel();
                }
                ticksActive++;
            }
        }.runTaskTimer(addon.getPlugin(), 1L, 1L);
    }
}
