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
 * The unlock trigger: turns island level changes from the Level addon into chunk unlock
 * and re-lock flows. This is the only place the unlocked chunk count is ever changed
 * (besides island creation/reset, handled here too).
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
     * A brand-new island starts with just the center chunk.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onIslandCreated(IslandCreatedEvent e) {
        if (addon.inWorld(e.getIsland().getWorld())) {
            addon.getOneBlocksIsland(e.getIsland()).setUnlockedChunkCount(1);
        }
    }

    /**
     * A reset island starts over with just the center chunk.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onIslandResetted(IslandResettedEvent e) {
        if (addon.inWorld(e.getIsland().getWorld())) {
            addon.getOneBlocksIsland(e.getIsland()).setUnlockedChunkCount(1);
        }
    }

    /**
     * Recomputes the unlocked chunk count for a level and runs the unlock or re-lock flow
     * if it changed.
     *
     * @param island the island
     * @param level the island's level
     */
    public void applyLevel(Island island, long level) {
        ChunkManager cm = addon.getChunkManager();
        OneBlockIslands data = addon.getOneBlocksIsland(island);
        int oldCount = Math.min(data.getUnlockedChunkCount(), cm.getMaxChunks(island));
        int newCount = cm.computeUnlockedCount(island, level);
        if (!addon.getSettings().isRelockOnLevelLoss()) {
            // Ratchet mode: territory never shrinks
            newCount = Math.max(oldCount, newCount);
        }
        if (newCount == oldCount) {
            return;
        }
        data.setUnlockedChunkCount(newCount);
        addon.getBlockListener().saveIsland(island);
        if (newCount > oldCount) {
            onUnlock(island, oldCount, newCount);
        } else {
            onRelock(island, newCount, oldCount);
        }
    }

    private void onUnlock(Island island, int oldCount, int newCount) {
        ChunkManager cm = addon.getChunkManager();
        List<Vector> gained = cm.chunksBetween(oldCount, newCount);
        for (int i = 0; i < gained.size(); i++) {
            Bukkit.getPluginManager().callEvent(new ChunkUnlockEvent(island, gained.get(i), oldCount + i));
        }
        int count = gained.size();
        island.getMemberSet().forEach(uuid -> {
            User user = User.getInstance(uuid);
            if (user.isOnline() && addon.inWorld(user.getWorld())) {
                if (count == 1) {
                    user.sendMessage("chunkblock.chunks.unlocked", "[number]", String.valueOf(newCount));
                } else {
                    user.sendMessage("chunkblock.chunks.unlocked-multiple", "[count]", String.valueOf(count),
                            "[number]", String.valueOf(newCount));
                }
                if (newCount >= cm.getMaxChunks(island)) {
                    user.sendMessage("chunkblock.chunks.max-reached", "[number]", String.valueOf(newCount));
                } else {
                    user.sendMessage("chunkblock.chunks.next-unlock", "[level]",
                            String.valueOf(cm.levelForChunkNumber(newCount + 1)));
                }
                user.getPlayer().playSound(user.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1F, 1F);
            }
        });
        // Celebration particles along the freshly unlocked chunks
        if (addon.getBorderDisplay() != null) {
            addon.getBorderDisplay().celebrate(island, gained);
        }
    }

    private void onRelock(Island island, int newCount, int oldCount) {
        List<Vector> lost = addon.getChunkManager().chunksBetween(newCount, oldCount);
        // Highest index re-locks first: reverse order for the events
        for (int i = lost.size() - 1; i >= 0; i--) {
            Bukkit.getPluginManager().callEvent(new ChunkRelockEvent(island, lost.get(i), newCount + i));
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
