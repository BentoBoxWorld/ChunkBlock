package world.bentobox.chunkblock.listeners;

import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;

import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.chunkblock.ChunkBlock;
import world.bentobox.chunkblock.chunks.ChunkManager;
import world.bentobox.chunkblock.chunks.ChunkManager.ClaimResult;

/**
 * Lets the island owner spend level credit by hitting the border: when they punch (or
 * right-click) toward the locked chunk blocking them, that chunk is claimed and opens up.
 * Expansion is the owner's choice, in any direction, up to the island's protection range.
 *
 * @author tastybento
 */
public class ChunkClaimListener implements Listener {

    /** How far (blocks) a border hit can reach into the locked neighbor */
    private static final double REACH = 5.0;
    /** Ray step size in blocks */
    private static final double STEP = 0.25;

    private final ChunkBlock addon;

    public ChunkClaimListener(ChunkBlock addon) {
        this.addon = addon;
    }

    /**
     * A left or right click aimed through the border claims the locked chunk on the other
     * side. Runs regardless of event cancellation because the protection listeners rightly
     * cancel interactions that touch locked chunks — claiming is the one exception.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
    public void onBorderHit(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND
                || (e.getAction() != Action.LEFT_CLICK_AIR && e.getAction() != Action.LEFT_CLICK_BLOCK
                        && e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Player player = e.getPlayer();
        if (!addon.inWorld(player.getWorld())) {
            return;
        }
        Optional<Island> optionalIsland = addon.getIslands().getIslandAt(player.getLocation());
        if (optionalIsland.isEmpty()) {
            return;
        }
        Island island = optionalIsland.get();
        // Claiming is the owner's call
        if (!player.getUniqueId().equals(island.getOwner())) {
            return;
        }
        ChunkManager cm = addon.getChunkManager();
        // The player must be standing in their own territory, aiming at a locked chunk
        if (cm.isLocked(island, player.getLocation())) {
            return;
        }
        int[] target = findTargetLockedChunk(player, island);
        if (target == null) {
            return;
        }
        attemptClaim(User.getInstance(player), island, target[0], target[1]);
    }

    /**
     * Walks a short ray along the player's line of sight and returns the first locked
     * chunk it enters, or null if the player is not aiming through the border.
     *
     * @return {chunkX, chunkZ} world chunk coordinates, or null
     */
    private int[] findTargetLockedChunk(Player player, Island island) {
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection();
        // A straight-down or straight-up look can't cross a chunk border usefully
        if (direction.getX() == 0 && direction.getZ() == 0) {
            return null;
        }
        ChunkManager cm = addon.getChunkManager();
        for (double d = STEP; d <= REACH; d += STEP) {
            Location point = eye.clone().add(direction.getX() * d, 0, direction.getZ() * d);
            int chunkX = point.getBlockX() >> 4;
            int chunkZ = point.getBlockZ() >> 4;
            if (!cm.isUnlocked(island, chunkX, chunkZ)) {
                return new int[] { chunkX, chunkZ };
            }
        }
        return null;
    }

    /**
     * Runs the claim and gives the player the appropriate feedback.
     *
     * @param user the island owner
     * @param island the island
     * @param chunkX target world chunk x
     * @param chunkZ target world chunk z
     */
    public void attemptClaim(User user, Island island, int chunkX, int chunkZ) {
        ChunkManager cm = addon.getChunkManager();
        ClaimResult result = cm.claim(island, chunkX, chunkZ);
        switch (result) {
        case OK -> addon.getLevelListener().celebrateClaim(island, chunkX, chunkZ);
        case NO_CREDIT -> {
            long needed = cm.getChunkCost() - cm.getCredit(island);
            user.notify("chunkblock.chunks.no-credit", "[needed]", String.valueOf(needed));
            user.getPlayer().playSound(user.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1F, 0.6F);
        }
        case BEYOND_LIMIT -> user.notify("chunkblock.chunks.beyond-limit");
        case NOT_ADJACENT, ALREADY_UNLOCKED -> {
            // Aiming at a diagonal corner or a chunk already owned: no claim, no nag
        }
        }
    }
}
