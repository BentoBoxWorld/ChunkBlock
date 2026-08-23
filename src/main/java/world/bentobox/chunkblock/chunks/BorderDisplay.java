package world.bentobox.chunkblock.chunks;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.chunkblock.ChunkBlock;

/**
 * Client-side visuals for the locked-chunk frontier: a dust-particle curtain on the faces
 * between unlocked and locked chunks near each player, an optional client-side barrier
 * block layer (the world is never modified), and the green celebration sweep when chunks
 * unlock. Rendering is proximity-limited like the Border addon's ShowBarrier, driven by a
 * short repeating task so the curtain is visible even to players standing still.
 *
 * @author tastybento
 */
public class BorderDisplay implements Listener {

    /** How close (blocks) a player must be to a locked face for it to render */
    private static final int BARRIER_RADIUS = 5;
    /** Ticks between redraws; dust particles live about a second */
    private static final long REDRAW_PERIOD = 12L;
    /** Extra curtain height beyond the world limits so fliers above the roof see the wall */
    private static final int OUT_OF_WORLD_DEPTH = 16;
    /** Dust color when the curtain is beyond the world height limits */
    private static final Color OUT_OF_WORLD_COLOR = Color.ORANGE;
    /** Dust color for a chunk a player has lined up to claim but not yet confirmed */
    private static final Color PREVIEW_COLOR = Color.YELLOW;
    /** Heights above the viewer's feet at which the preview outline is drawn */
    private static final int[] PREVIEW_HEIGHTS = { 0, 3, 6 };

    private final ChunkBlock addon;
    /** Client-side barrier blocks sent per player, with the original data for restore */
    private final Map<UUID, Set<BarrierBlock>> barrierBlocks = new HashMap<>();
    /** Chunks outlined for a player pending claim confirmation */
    private final Map<UUID, Preview> previews = new HashMap<>();
    private BukkitTask task;

    private record BarrierBlock(Location location, BlockData oldData) {
    }

    /** A pending claim outline: world chunk coordinates and when to stop drawing it */
    private record Preview(int chunkX, int chunkZ, long expiry) {
    }

    public BorderDisplay(ChunkBlock addon) {
        this.addon = addon;
    }

    /**
     * Starts the redraw task. Call once on enable.
     */
    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(addon.getPlugin(), this::redrawAll, REDRAW_PERIOD, REDRAW_PERIOD);
    }

    /**
     * Stops the redraw task and restores all client-side blocks. Call on disable.
     */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        barrierBlocks.keySet().stream().map(Bukkit::getPlayer).filter(java.util.Objects::nonNull)
                .toList().forEach(this::hideBorder);
        barrierBlocks.clear();
        previews.clear();
    }

    private void redrawAll() {
        for (World world : Bukkit.getWorlds()) {
            if (addon.inWorld(world)) {
                for (Player player : world.getPlayers()) {
                    showBorder(player);
                    drawPreview(player);
                }
            }
        }
    }

    /**
     * Draws the locked-chunk curtain near one player.
     */
    public void showBorder(Player player) {
        if (!addon.getSettings().isBorderShowParticles() && !addon.getSettings().isBorderBarrierBlocks()) {
            return;
        }
        if (addon.getChunkManager().isExempt(player)) {
            // Staff inspecting bypass locks see no walls
            hideBorder(player);
            return;
        }
        Optional<Island> optionalIsland = addon.getIslands().getIslandAt(player.getLocation());
        if (optionalIsland.isEmpty()) {
            return;
        }
        Island island = optionalIsland.get();
        ChunkManager cm = addon.getChunkManager();
        Location loc = player.getLocation();
        int pcx = loc.getBlockX() >> 4;
        int pcz = loc.getBlockZ() >> 4;
        // Check the 3x3 chunk neighborhood; locked chunks with an unlocked neighbor
        // present a face that may be near enough to draw
        for (int dcx = -1; dcx <= 1; dcx++) {
            for (int dcz = -1; dcz <= 1; dcz++) {
                int cx = pcx + dcx;
                int cz = pcz + dcz;
                drawLockedChunkFaces(player, island, cm, cx, cz);
            }
        }
    }

    /**
     * Draws all four faces of a locked chunk that border unlocked territory.
     */
    private void drawLockedChunkFaces(Player player, Island island, ChunkManager cm, int cx, int cz) {
        if (cm.isUnlocked(island, cx, cz)) {
            return;
        }
        if (cm.isUnlocked(island, cx - 1, cz)) {
            drawWallX(player, cx << 4, cz << 4, 0);
        }
        if (cm.isUnlocked(island, cx + 1, cz)) {
            drawWallX(player, (cx << 4) + 16, cz << 4, -1);
        }
        if (cm.isUnlocked(island, cx, cz - 1)) {
            drawWallZ(player, cx << 4, cz << 4, 0);
        }
        if (cm.isUnlocked(island, cx, cz + 1)) {
            drawWallZ(player, cx << 4, (cz << 4) + 16, -1);
        }
    }

    /**
     * Draws a wall on the plane x = wallX spanning the chunk starting at block minZ.
     */
    private void drawWallX(Player player, int wallX, int minZ, int lockedSideOffset) {
        Location loc = player.getLocation();
        if (Math.abs(loc.getX() - wallX) > BARRIER_RADIUS) {
            return;
        }
        int zFrom = Math.max(minZ, loc.getBlockZ() - BARRIER_RADIUS);
        int zTo = Math.min(minZ + 15, loc.getBlockZ() + BARRIER_RADIUS);
        for (int z = zFrom; z <= zTo; z++) {
            drawColumn(player, wallX, z, true, wallX + lockedSideOffset, z);
        }
    }

    /**
     * Draws a wall on the plane z = wallZ spanning the chunk starting at block minX.
     */
    private void drawWallZ(Player player, int minX, int wallZ, int lockedSideOffset) {
        Location loc = player.getLocation();
        if (Math.abs(loc.getZ() - wallZ) > BARRIER_RADIUS) {
            return;
        }
        int xFrom = Math.max(minX, loc.getBlockX() - BARRIER_RADIUS);
        int xTo = Math.min(minX + 15, loc.getBlockX() + BARRIER_RADIUS);
        for (int x = xFrom; x <= xTo; x++) {
            drawColumn(player, x, wallZ, false, x, wallZ + lockedSideOffset);
        }
    }

    /**
     * Draws the vertical slice of a wall column around the player's y. The curtain extends
     * beyond world min/max heights (with a color switch) so it is visible to players in
     * the void or above the roof.
     */
    private void drawColumn(Player player, int x, int z, boolean alongZ, int barrierX, int barrierZ) {
        World world = player.getWorld();
        int yFrom = Math.max(world.getMinHeight() - OUT_OF_WORLD_DEPTH, player.getLocation().getBlockY() - BARRIER_RADIUS);
        int yTo = Math.min(world.getMaxHeight() + 2 * OUT_OF_WORLD_DEPTH, player.getLocation().getBlockY() + BARRIER_RADIUS);
        for (int y = yFrom; y <= yTo; y++) {
            drawColumnSegment(player, x, y, z, alongZ, barrierX, barrierZ, world);
        }
    }

    private void drawColumnSegment(Player player, int x, int y, int z, boolean alongZ, int barrierX, int barrierZ, World world) {
        if (addon.getSettings().isBorderShowParticles()) {
            boolean outOfWorld = y < world.getMinHeight() || y > world.getMaxHeight();
            Color color = outOfWorld ? OUT_OF_WORLD_COLOR : addon.getSettings().getBorderParticleColor();
            double px = alongZ ? x : x + 0.5D;
            double pz = alongZ ? z + 0.5D : z;
            player.spawnParticle(Particle.DUST, px, y + 0.5D, pz, 1, 0, 0, 0, 0,
                    new Particle.DustOptions(color, 1.0F));
        }
        if (addon.getSettings().isBorderBarrierBlocks() && y >= world.getMinHeight() && y < world.getMaxHeight()) {
            sendBarrier(player, new Location(world, barrierX, y, barrierZ));
        }
    }

    private void sendBarrier(Player player, Location location) {
        if (!location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            return;
        }
        Block block = location.getBlock();
        if (block.isEmpty() || block.isLiquid()) {
            Set<BarrierBlock> cache = barrierBlocks.computeIfAbsent(player.getUniqueId(), u -> new HashSet<>());
            if (cache.stream().noneMatch(b -> b.location().equals(location))) {
                cache.add(new BarrierBlock(location, block.getBlockData()));
                player.sendBlockChange(location, Material.BARRIER.createBlockData());
            }
        }
    }

    /**
     * Restores every client-side barrier block sent to the player and clears the cache.
     */
    public void hideBorder(Player player) {
        Set<BarrierBlock> cache = barrierBlocks.remove(player.getUniqueId());
        if (cache != null && player.isOnline()) {
            cache.stream().filter(b -> player.getWorld().equals(b.location().getWorld()))
                    .forEach(b -> player.sendBlockChange(b.location(), b.oldData()));
        }
    }

    /**
     * Outlines a chunk in yellow for one player until the given time, marking it as lined
     * up for claiming but not yet paid for. Only that player sees it, and the outline
     * disappears by itself when the confirmation window closes.
     *
     * @param player the player about to spend credit
     * @param chunkX world chunk x coordinate of the previewed chunk
     * @param chunkZ world chunk z coordinate of the previewed chunk
     * @param expiry when to stop drawing, in {@link System#currentTimeMillis()} terms
     */
    public void showPreview(Player player, int chunkX, int chunkZ, long expiry) {
        previews.put(player.getUniqueId(), new Preview(chunkX, chunkZ, expiry));
        drawPreview(player);
    }

    /**
     * Stops outlining whatever chunk this player had lined up.
     *
     * @param uuid the player's UUID
     */
    public void clearPreview(UUID uuid) {
        previews.remove(uuid);
    }

    /**
     * Draws the pending claim outline for one player, if they have one that has not run out
     * of time. The box is drawn around the player's own height so it reads as a wall even
     * when the terrain beyond the border is far above or below them.
     */
    private void drawPreview(Player player) {
        Preview preview = previews.get(player.getUniqueId());
        if (preview == null) {
            return;
        }
        if (System.currentTimeMillis() > preview.expiry()) {
            previews.remove(player.getUniqueId());
            return;
        }
        // The outline traces the chunk's boundary planes, so the corners meet exactly where
        // the four walls of the locked-chunk curtain would
        double minX = preview.chunkX() << 4;
        double minZ = preview.chunkZ() << 4;
        double baseY = player.getLocation().getBlockY() + 0.5D;
        Particle.DustOptions dust = new Particle.DustOptions(PREVIEW_COLOR, 1.5F);
        for (int height : PREVIEW_HEIGHTS) {
            double y = baseY + height;
            for (int i = 0; i <= 16; i += 2) {
                player.spawnParticle(Particle.DUST, minX + i, y, minZ, 1, 0, 0, 0, 0, dust);
                player.spawnParticle(Particle.DUST, minX + i, y, minZ + 16, 1, 0, 0, 0, 0, dust);
                player.spawnParticle(Particle.DUST, minX, y, minZ + i, 1, 0, 0, 0, 0, dust);
                player.spawnParticle(Particle.DUST, minX + 16, y, minZ + i, 1, 0, 0, 0, 0, dust);
            }
        }
    }

    /**
     * One-shot green particle celebration along freshly unlocked chunks, visible to
     * everyone in the world near the island.
     *
     * @param island the island that unlocked
     * @param gained the relative chunk offsets that were unlocked
     */
    public void celebrate(Island island, List<Vector> gained) {
        World world = island.getWorld();
        if (world == null) {
            return;
        }
        int centerChunkX = island.getCenter().getBlockX() >> 4;
        int centerChunkZ = island.getCenter().getBlockZ() >> 4;
        for (Player player : world.getPlayers()) {
            if (!island.inIslandSpace(player.getLocation())) {
                continue;
            }
            int y = player.getLocation().getBlockY() + 1;
            for (Vector offset : gained) {
                int minX = (centerChunkX + offset.getBlockX()) << 4;
                int minZ = (centerChunkZ + offset.getBlockZ()) << 4;
                // Sweep the chunk perimeter
                for (int i = 0; i <= 16; i += 2) {
                    player.spawnParticle(Particle.HAPPY_VILLAGER, minX + i + 0.5, y, minZ + 0.5, 1, 0, 0.5, 0, 0);
                    player.spawnParticle(Particle.HAPPY_VILLAGER, minX + i + 0.5, y, minZ + 16.5, 1, 0, 0.5, 0, 0);
                    player.spawnParticle(Particle.HAPPY_VILLAGER, minX + 0.5, y, minZ + i + 0.5, 1, 0, 0.5, 0, 0);
                    player.spawnParticle(Particle.HAPPY_VILLAGER, minX + 16.5, y, minZ + i + 0.5, 1, 0, 0.5, 0, 0);
                }
            }
        }
    }

    // Barrier caches go stale whenever the client rebuilds its world view
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        barrierBlocks.remove(e.getPlayer().getUniqueId());
        previews.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        hideBorder(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent e) {
        barrierBlocks.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent e) {
        barrierBlocks.remove(e.getPlayer().getUniqueId());
        previews.remove(e.getPlayer().getUniqueId());
    }
}
