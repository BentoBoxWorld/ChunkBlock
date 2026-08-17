package world.bentobox.chunkblock.listeners;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.util.Vector;
import org.eclipse.jdt.annotation.Nullable;

import world.bentobox.bentobox.api.events.island.IslandCreatedEvent;
import world.bentobox.bentobox.api.events.island.IslandResettedEvent;
import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.chunkblock.ChunkBlock;
import world.bentobox.chunkblock.activity.CounterType;
import world.bentobox.chunkblock.chunks.ChunkManager;
import world.bentobox.chunkblock.dataobjects.OneBlockIslands;
import world.bentobox.chunkblock.events.ChunkRelockEvent;
import world.bentobox.chunkblock.events.ChunkUnlockEvent;
import world.bentobox.chunkblock.events.RingCompleteEvent;
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
        data.setHighestRingRewarded(0);
        data.getEarnedTrophies().clear();
        data.setActiveTitle("");
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
        // The delta is only known here, so level gains are recorded directly rather than
        // through ActivityListener. Island scope: Level cannot attribute levels to a member.
        if (level > oldLevel && addon.getActivityManager() != null) {
            addon.getActivityManager().recordActivity(island, null, CounterType.LEVELS_EARNED, level - oldLevel);
        }
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
     * @param claimer the player who spent the credit, or null if no player did
     */
    public void celebrateClaim(Island island, int chunkX, int chunkZ, @Nullable UUID claimer) {
        ChunkManager cm = addon.getChunkManager();
        int count = cm.getUnlockedChunkCount(island);
        Vector offset = new Vector(chunkX - (island.getCenter().getBlockX() >> 4), 0,
                chunkZ - (island.getCenter().getBlockZ() >> 4));
        Bukkit.getPluginManager().callEvent(new ChunkUnlockEvent(island, offset, count - 1, claimer));
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
        checkRingMilestones(island);
    }

    /**
     * Pays out any rings the island has completed but not yet been rewarded for. Normally
     * that is a single ring — the chunk just claimed closed it — but a ring completed
     * while an inner one still had a hole in it is caught up here once the hole is filled.
     *
     * @param island the island
     */
    private void checkRingMilestones(Island island) {
        OneBlockIslands data = addon.getOneBlocksIsland(island);
        int completed = addon.getChunkManager().completedRings(island);
        if (completed <= data.getHighestRingRewarded()) {
            return;
        }
        for (int ring = data.getHighestRingRewarded() + 1; ring <= completed; ring++) {
            rewardRing(island, ring);
        }
        data.setHighestRingRewarded(completed);
        addon.getBlockListener().saveIsland(island);
    }

    /**
     * Fires {@link RingCompleteEvent} for one newly completed ring and, unless a plugin
     * cancels it, announces the milestone and runs the configured reward commands.
     */
    private void rewardRing(Island island, int ring) {
        int chunks = addon.getChunkManager().getUnlockedChunkCount(island);
        RingCompleteEvent event = new RingCompleteEvent(island, ring, chunks);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        String ringText = String.valueOf(ring);
        String chunkText = String.valueOf(chunks);
        island.getMemberSet().forEach(uuid -> {
            User user = User.getInstance(uuid);
            if (user.isOnline() && addon.inWorld(user.getWorld())) {
                user.sendMessage("chunkblock.chunks.ring-complete", "[ring]", ringText, "[chunks]", chunkText);
                user.getPlayer().playSound(user.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1F, 1F);
            }
        });
        if (addon.getSettings().isRingBroadcast()) {
            String ownerName = playerName(island.getOwner());
            Bukkit.getOnlinePlayers().forEach(player -> User.getInstance(player).sendMessage(
                    "chunkblock.chunks.ring-broadcast", TextVariables.NAME, ownerName, "[ring]", ringText,
                    "[chunks]", chunkText));
        }
        celebrateRing(island, ring);
        if (addon.getActivityManager() != null) {
            addon.getActivityManager().recordActivity(island, null,
                    world.bentobox.chunkblock.activity.CounterType.RINGS_COMPLETED, 1);
        }
        List<String> ownerCommands = addon.getSettings().getRingCommands();
        if (!ownerCommands.isEmpty()) {
            runCommands(ownerCommands, ringText, chunkText, "[owner]", playerName(island.getOwner()));
        }
        List<String> memberCommands = addon.getSettings().getRingPlayerCommands();
        if (!memberCommands.isEmpty()) {
            for (UUID uuid : island.getMemberSet()) {
                runCommands(memberCommands, ringText, chunkText, "[player]", playerName(uuid));
            }
        }
    }

    /**
     * @return the player's name, or an empty string for an unowned island or a name the
     *         players manager does not know
     */
    private String playerName(UUID uuid) {
        return uuid == null ? "" : addon.getPlayers().getName(uuid);
    }

    /**
     * Runs reward commands from the console, substituting the ring placeholders. Commands
     * with an empty name substitution are skipped rather than run against a blank argument.
     */
    private void runCommands(List<String> commands, String ring, String chunks, String nameKey, String name) {
        if (commands.isEmpty() || name == null || name.isEmpty()) {
            return;
        }
        for (String command : commands) {
            String toRun = command.replace("[ring]", ring).replace("[chunks]", chunks).replace(nameKey, name);
            if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), toRun)) {
                addon.logError("Ring reward command failed: " + toRun);
            }
        }
    }

    /**
     * Sparkles the whole completed ring, not just the chunk that closed it.
     */
    private void celebrateRing(Island island, int ring) {
        if (addon.getBorderDisplay() == null) {
            return;
        }
        List<Vector> offsets = new ArrayList<>();
        for (int d = -ring; d <= ring; d++) {
            offsets.add(new Vector(d, 0, -ring));
            offsets.add(new Vector(d, 0, ring));
            if (d != -ring && d != ring) {
                offsets.add(new Vector(-ring, 0, d));
                offsets.add(new Vector(ring, 0, d));
            }
        }
        addon.getBorderDisplay().celebrate(island, offsets);
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
