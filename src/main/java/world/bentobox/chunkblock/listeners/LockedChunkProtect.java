package world.bentobox.chunkblock.listeners;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.StructureGrowEvent;

import world.bentobox.bentobox.api.user.User;
import world.bentobox.chunkblock.ChunkBlock;

/**
 * Keeps locked chunks pristine: no block changes reach across the boundary from either
 * side, no machines or physics leak into the forbidden zone, and (optionally) nothing
 * spawns there. Ports the BentoBox core protection listener patterns (piston allMatch,
 * horizontal-only liquid gate, tree pruning, explosion block stripping) from island
 * protection ranges to chunk locks.
 *
 * @author tastybento
 */
public class LockedChunkProtect implements Listener {

    private final ChunkBlock addon;

    public LockedChunkProtect(ChunkBlock addon) {
        this.addon = addon;
    }

    /**
     * Checks whether the location is inside a locked chunk of some island.
     */
    private boolean isLocked(Location location) {
        if (!addon.inWorld(location.getWorld())) {
            return false;
        }
        return addon.getIslands().getIslandAt(location)
                .map(i -> addon.getChunkManager().isLocked(i, location)).orElse(false);
    }

    /**
     * Cancels a player-driven block change in a locked chunk, unless the player is exempt.
     * @return true if the action was denied
     */
    private boolean denyPlayerAction(Player player, Location target) {
        if (addon.getChunkManager().isExempt(player) || !isLocked(target)) {
            return false;
        }
        User.getInstance(player).notify("chunkblock.chunks.locked");
        return true;
    }

    // ---------------- player reach-across ----------------

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        if (addon.inWorld(e.getBlock().getWorld()) && denyPlayerAction(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        if (addon.inWorld(e.getBlock().getWorld()) && denyPlayerAction(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (addon.inWorld(e.getBlock().getWorld()) && denyPlayerAction(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (addon.inWorld(e.getBlock().getWorld()) && denyPlayerAction(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() != null && addon.inWorld(e.getClickedBlock().getWorld())
                && denyPlayerAction(e.getPlayer(), e.getClickedBlock().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent e) {
        if (e.getPlayer() != null && addon.inWorld(e.getEntity().getWorld())
                && denyPlayerAction(e.getPlayer(), e.getEntity().getLocation())) {
            e.setCancelled(true);
        }
    }

    // ---------------- machines and physics ----------------

    /**
     * Pistons may not push blocks into, out of, or within locked chunks.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (!addon.inWorld(e.getBlock().getWorld())) {
            return;
        }
        boolean allowed = e.getBlocks().stream().allMatch(b -> !isLocked(b.getLocation())
                && !isLocked(b.getRelative(e.getDirection()).getLocation()))
                && !isLocked(e.getBlock().getRelative(e.getDirection()).getLocation());
        if (!allowed) {
            e.setCancelled(true);
        }
    }

    /**
     * Sticky pistons may not pull blocks out of locked chunks (or drag them in).
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (!addon.inWorld(e.getBlock().getWorld())) {
            return;
        }
        boolean allowed = e.getBlocks().stream().allMatch(b -> !isLocked(b.getLocation())
                && !isLocked(b.getRelative(e.getDirection()).getLocation()));
        if (!allowed) {
            e.setCancelled(true);
        }
    }

    /**
     * Water and lava may not flow into locked chunks. Vertical flows are skipped — they
     * cannot cross a chunk border and they are by far the most common flow event.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onLiquidFlow(BlockFromToEvent e) {
        Block from = e.getBlock();
        Block to = e.getToBlock();
        if (from.getY() != to.getY()
                || (from.getX() >> 4 == to.getX() >> 4 && from.getZ() >> 4 == to.getZ() >> 4)
                || !addon.inWorld(from.getWorld())) {
            return;
        }
        if (isLocked(to.getLocation())) {
            e.setCancelled(true);
        }
    }

    /**
     * Dispensers may not fire liquids (or powder snow) across the boundary.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent e) {
        if (!addon.inWorld(e.getBlock().getWorld())
                || !(e.getBlock().getBlockData() instanceof Directional directional)
                || !e.getItem().getType().name().endsWith("_BUCKET")) {
            return;
        }
        Block target = e.getBlock().getRelative(directional.getFacing());
        if (isLocked(target.getLocation())) {
            e.setCancelled(true);
        }
    }

    /**
     * Trees grow — just pruned at the boundary, like BentoBox does at the island edge.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent e) {
        if (addon.inWorld(e.getWorld())) {
            e.getBlocks().removeIf(b -> isLocked(b.getLocation()));
        }
    }

    /**
     * Fire, mushrooms, chorus and friends may not spread into locked chunks.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent e) {
        if (addon.inWorld(e.getBlock().getWorld()) && isLocked(e.getBlock().getLocation())) {
            e.setCancelled(true);
        }
    }

    /**
     * Explosions happen, but blocks in locked chunks are stripped from the damage list.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        if (addon.inWorld(e.getEntity().getWorld())) {
            e.blockList().removeIf(b -> isLocked(b.getLocation()));
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (addon.inWorld(e.getBlock().getWorld())) {
            e.blockList().removeIf(b -> isLocked(b.getLocation()));
        }
    }

    /**
     * Keeps the forbidden zone sterile and cheap: no natural spawns in locked chunks.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent e) {
        if (addon.getSettings().isDenyMobSpawnsInLocked()
                && (e.getSpawnReason() == SpawnReason.NATURAL || e.getSpawnReason() == SpawnReason.SPAWNER)
                && isLocked(e.getLocation())) {
            e.setCancelled(true);
        }
    }
}
