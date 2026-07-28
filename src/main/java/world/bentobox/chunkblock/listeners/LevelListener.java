package world.bentobox.chunkblock.listeners;

import java.util.List;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.util.Vector;

import world.bentobox.bentobox.api.events.island.IslandCreatedEvent;
import world.bentobox.bentobox.api.events.island.IslandResettedEvent;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.chunkblock.ChunkBlock;
import world.bentobox.chunkblock.chunks.ChunkManager;
import world.bentobox.chunkblock.dataobjects.OneBlockIslands;
import world.bentobox.chunkblock.events.ChunkRelockEvent;
import world.bentobox.chunkblock.events.ChunkUnlockEvent;
import world.bentobox.level.events.IslandLevelCalculatedEvent;

/**
 * Watches island level changes from the Level addon. Levels are chunk currency here:
 * gaining levels earns credit the owner can spend at the border (announced when new
 * credit becomes available); losing levels below what has been spent re-locks the most
 * recently claimed chunks, in reverse claim order.
 *
 * @author tastybento
 */
public class LevelListener implements Listener {

    private final ChunkBlock addon;

    public LevelListener(ChunkBlock addon) {
        this.addon = addon;
    }

    /**
     * Fires after every island level calculation, before results are saved. Never
     * cancelled here — we only read the level.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIslandLevelCalculated(IslandLevelCalculatedEvent e) {
        Island island = e.getIsland();
        if (island != null && addon.inWorld(island.getWorld())) {
            applyLevel(island, e.getLevel());
        }
    }

    /**
     * A brand-new island starts with just the center chunk and no spending history.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onIslandCreated(IslandCreatedEvent e) {
        if (addon.inWorld(e.getIsland().getWorld())) {
            resetIsland(e.getIsland());
        }
    }

    /**
     * A reset island starts over with just the center chunk.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onIslandResetted(IslandResettedEvent e) {
        if (addon.inWorld(e.getIsland().getWorld())) {
            resetIsland(e.getIsland());
        }
    }

    private void resetIsland(Island island) {
        OneBlockIslands data = addon.getOneBlocksIsland(island);
        data.resetUnlockedChunks();
        data.setLastKnownLevel(0);
    }

    /**
     * Handles a level change: re-locks over-spent chunks and announces newly available
     * chunk credit.
     *
     * @param island the island
     * @param level the island's new level
     */
    public void applyLevel(Island island, long level) {
        ChunkManager cm = addon.getChunkManager();
        OneBlockIslands data = addon.getOneBlocksIsland(island);
        long oldLevel = data.getLastKnownLevel();
        data.setLastKnownLevel(level);
        addon.getBlockListener().saveIsland(island);
        // Level dropped below what has been spent → the most recent claims are lost
        if (addon.getSettings().isRelockOnLevelLoss() && cm.getSpentLevels(island) > Math.max(0, level)) {
            relock(island, level);
            return;
        }
        // Announce newly affordable chunks — the fun "go spend it!" moment
        long cost = cm.getChunkCost();
        long oldClaimable = Math.max(0, cm.getCredit(island, oldLevel)) / cost;
        long newClaimable = Math.max(0, cm.getCredit(island, level)) / cost;
        if (newClaimable > oldClaimable && cm.getUnlockedChunkCount(island) < cm.getMaxChunks(island)) {
            island.getMemberSet().forEach(uuid -> {
                User user = User.getInstance(uuid);
                if (user.isOnline() && addon.inWorld(user.getWorld())) {
                    user.sendMessage("chunkblock.chunks.credit", "[count]", String.valueOf(newClaimable));
                    user.getPlayer().playSound(user.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1F, 1.2F);
                }
            });
        }
    }

    private void relock(Island island, long level) {
        ChunkManager cm = addon.getChunkManager();
        int countBefore = cm.getUnlockedChunkCount(island);
        List<Vector> lost = cm.relockToBudget(island, level);
        if (lost.isEmpty()) {
            return;
        }
        // Most recently claimed chunk first: indices count down from the end of the list
        for (int i = 0; i < lost.size(); i++) {
            Bukkit.getPluginManager().callEvent(new ChunkRelockEvent(island, lost.get(i), countBefore - 1 - i));
        }
        island.getMemberSet().forEach(uuid -> {
            User user = User.getInstance(uuid);
            if (user.isOnline() && addon.inWorld(user.getWorld())) {
                user.sendMessage("chunkblock.chunks.relocked", "[count]", String.valueOf(lost.size()));
                user.getPlayer().playSound(user.getLocation(), Sound.BLOCK_CONDUIT_DEACTIVATE, 1F, 0.7F);
            }
        });
        if (addon.getSettings().isEjectPlayersOnRelock()) {
            ejectPlayers(island);
        }
    }

    /**
     * Fires the unlock event and celebrates a freshly claimed chunk. Called by the claim
     * listener after a successful claim.
     *
     * @param island the island
     * @param chunkX claimed world chunk x
     * @param chunkZ claimed world chunk z
     */
    public void celebrateClaim(Island island, int chunkX, int chunkZ) {
        ChunkManager cm = addon.getChunkManager();
        int count = cm.getUnlockedChunkCount(island);
        Vector offset = new Vector(chunkX - (island.getCenter().getBlockX() >> 4), 0,
                chunkZ - (island.getCenter().getBlockZ() >> 4));
        Bukkit.getPluginManager().callEvent(new ChunkUnlockEvent(island, offset, count - 1));
        long creditLeft = Math.max(0, cm.getCredit(island));
        island.getMemberSet().forEach(uuid -> {
            User user = User.getInstance(uuid);
            if (user.isOnline() && addon.inWorld(user.getWorld())) {
                user.sendMessage("chunkblock.chunks.claimed", "[number]", String.valueOf(count),
                        "[credit]", String.valueOf(creditLeft));
                if (count >= cm.getMaxChunks(island)) {
                    user.sendMessage("chunkblock.chunks.max-reached", "[number]", String.valueOf(count));
                }
                user.getPlayer().playSound(user.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1F, 1F);
            }
        });
        if (addon.getBorderDisplay() != null) {
            addon.getBorderDisplay().celebrate(island, List.of(offset));
        }
    }

    /**
     * Moves every non-exempt player standing in now-locked territory of this island to the
     * nearest unlocked safe spot. Never blind-teleports; fly state is preserved by the
     * backtrack.
     */
    private void ejectPlayers(Island island) {
        for (Player player : Objects.requireNonNull(island.getWorld()).getPlayers()) {
            if (island.inIslandSpace(player.getLocation())
                    && !addon.getChunkManager().isExempt(player)
                    && addon.getChunkManager().isLocked(island, player.getLocation())) {
                User.getInstance(player).sendMessage("chunkblock.chunks.ejected");
                addon.getChunkGuardListener().backtrack(player);
            }
        }
    }
}
