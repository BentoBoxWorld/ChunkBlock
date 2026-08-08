package world.bentobox.chunkblock.listeners;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;

import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.managers.RanksManager;
import world.bentobox.chunkblock.ChunkBlock;
import world.bentobox.chunkblock.chunks.ChunkManager;
import world.bentobox.chunkblock.chunks.ChunkManager.ClaimResult;

/**
 * Lets an island spend level credit by hitting the border: when a player punches (or
 * right-clicks) toward the locked chunk blocking them, that chunk is claimed and opens up.
 * Expansion goes in any direction, up to the island's protection range. Who is allowed to
 * spend is the CHUNKBLOCK_CLAIM_CHUNKS island setting, owner-only unless an island opens it
 * to lower ranks.
 * <p>
 * Levels are hard-won, so by default a claim takes two deliberate gestures: the first hit
 * outlines the target chunk and quotes the price, and only a second hit made while sneaking
 * spends the credit. Servers that prefer the old one-hit claim can switch confirmation off
 * in the config.
 *
 * @author tastybento
 */
public class ChunkClaimListener implements Listener {

    /** How far (blocks) a border hit can reach into the locked neighbor */
    private static final double REACH = 5.0;
    /** Ray step size in blocks */
    private static final double STEP = 0.25;
    /** Minimum time between failure nags per player, so mining swings can't spam */
    private static final long FEEDBACK_COOLDOWN_MS = 2000;
    /**
     * How long a preview must have been showing before a sneaking hit can confirm it. One
     * physical swing can fire both LEFT_CLICK_AIR and LEFT_CLICK_BLOCK on the same tick, and
     * without this gap that single swing would preview and pay in one go.
     */
    private static final long CONFIRM_ARM_MS = 250;

    private final ChunkBlock addon;
    private final Map<UUID, Long> lastFeedback = new HashMap<>();
    /** Chunks each player has lined up but not yet paid for */
    private final Map<UUID, PendingClaim> pending = new HashMap<>();

    /**
     * A chunk a player has been quoted a price for, waiting on their confirming hit.
     *
     * @param islandId the island the chunk would join, so a preview cannot be confirmed
     *        on someone else's island
     * @param chunkX world chunk x coordinate
     * @param chunkZ world chunk z coordinate
     * @param shownAt when the preview was raised
     */
    private record PendingClaim(String islandId, int chunkX, int chunkZ, long shownAt) {
    }

    /** Time source, overridable so tests need not sleep */
    private LongSupplier clock = System::currentTimeMillis;

    public ChunkClaimListener(ChunkBlock addon) {
        this.addon = addon;
    }

    /**
     * Replaces the time source used for confirmation windows and feedback throttling.
     *
     * @param clock supplier of the current time in milliseconds
     */
    void setClock(LongSupplier clock) {
        this.clock = clock;
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
        User user = User.getInstance(player);
        // Who may spend the island's credit is an island setting, owner-only by default
        if (!island.isAllowed(user, addon.CHUNKBLOCK_CLAIM_CHUNKS)) {
            denyClaim(user, island);
            return;
        }
        ChunkManager cm = addon.getChunkManager();
        // The player must be standing in their own territory, aiming at a locked chunk
        if (cm.isLocked(island, player.getLocation())) {
            return;
        }
        // A click on a block inside unlocked territory is ordinary interaction (mining a
        // generator, pressing a button...), never a claim gesture — regardless of where
        // the aim line would end up beyond it.
        Block clicked = e.getClickedBlock();
        if (clicked != null && cm.isUnlocked(island, clicked.getX() >> 4, clicked.getZ() >> 4)) {
            return;
        }
        int[] target;
        if (clicked != null) {
            // The clicked block is itself in a locked chunk: that chunk is the target
            target = new int[] { clicked.getX() >> 4, clicked.getZ() >> 4 };
        } else {
            target = findTargetLockedChunk(player, island);
        }
        if (target == null) {
            return;
        }
        attemptClaim(user, island, target[0], target[1]);
    }

    /**
     * Tells a teammate whose rank is too low that expansion is not theirs to spend on.
     * Visitors and passers-by are told nothing: they get the ordinary locked-chunk message
     * from the guard listener instead, and have no business hearing about the island's
     * credit.
     */
    private void denyClaim(User user, Island island) {
        if (island.getRank(user) > RanksManager.VISITOR_RANK && feedbackReady(user.getUniqueId())) {
            user.notify(addon.CHUNKBLOCK_CLAIM_CHUNKS.getHintReference());
            user.getPlayer().playSound(user.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1F, 0.6F);
        }
    }

    /**
     * Drops a player's pending preview when they log out, so it cannot be confirmed by
     * whoever next holds that session.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        pending.remove(uuid);
        lastFeedback.remove(uuid);
        if (addon.getBorderDisplay() != null) {
            addon.getBorderDisplay().clearPreview(uuid);
        }
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
            Location point = eye.clone().add(direction.getX() * d, direction.getY() * d, direction.getZ() * d);
            int chunkX = point.getBlockX() >> 4;
            int chunkZ = point.getBlockZ() >> 4;
            if (!cm.isUnlocked(island, chunkX, chunkZ)) {
                return new int[] { chunkX, chunkZ };
            }
            Block block = point.getBlock();
            if (block != null && !block.isPassable()) {
                // The aim line is blocked by the player's own blocks before the border
                return null;
            }
        }
        return null;
    }

    /**
     * Runs the claim and gives the player the appropriate feedback. With confirmation
     * enabled a claimable chunk is only previewed the first time round; the credit is spent
     * on the confirming hit.
     *
     * @param user the player spending the credit, already checked against the claim flag
     * @param island the island
     * @param chunkX target world chunk x
     * @param chunkZ target world chunk z
     */
    public void attemptClaim(User user, Island island, int chunkX, int chunkZ) {
        ChunkManager cm = addon.getChunkManager();
        // Price the chunk before spending anything: only a claim that would actually go
        // through is worth asking the player to confirm
        if (addon.getSettings().isRequireClaimConfirmation()
                && cm.checkGeometry(island, chunkX, chunkZ) == ClaimResult.OK
                && cm.getCredit(island) >= cm.getChunkCost() && !confirming(user, island, chunkX, chunkZ)) {
            preview(user, island, chunkX, chunkZ);
            return;
        }
        ClaimResult result = cm.claim(island, chunkX, chunkZ);
        switch (result) {
        case OK -> {
            clearPending(user.getUniqueId());
            addon.getLevelListener().celebrateClaim(island, chunkX, chunkZ);
        }
        case NO_CREDIT -> {
            if (feedbackReady(user.getUniqueId())) {
                long needed = cm.getChunkCost() - cm.getCredit(island);
                user.notify("chunkblock.chunks.no-credit", "[needed]", String.valueOf(needed));
                user.getPlayer().playSound(user.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1F, 0.6F);
            }
        }
        case BEYOND_LIMIT -> {
            if (feedbackReady(user.getUniqueId())) {
                user.notify("chunkblock.chunks.beyond-limit");
            }
        }
        case NOT_ADJACENT, ALREADY_UNLOCKED -> {
            // Aiming at a diagonal corner or a chunk already owned: no claim, no nag
        }
        }
    }

    /**
     * Decides whether this hit is the confirming one: the player must be sneaking and
     * aiming at the very chunk they were quoted for, on the same island, inside the
     * confirmation window and no longer in the same swing that raised the preview.
     */
    private boolean confirming(User user, Island island, int chunkX, int chunkZ) {
        PendingClaim quote = pending.get(user.getUniqueId());
        if (quote == null || !user.getPlayer().isSneaking()
                || !Objects.equals(quote.islandId(), island.getUniqueId()) || quote.chunkX() != chunkX
                || quote.chunkZ() != chunkZ) {
            return false;
        }
        long age = clock.getAsLong() - quote.shownAt();
        if (age >= timeoutMillis()) {
            // The window closed; this hit re-prices the chunk instead of paying for it
            pending.remove(user.getUniqueId());
            return false;
        }
        return age >= CONFIRM_ARM_MS;
    }

    /**
     * Quotes the price of a chunk and outlines it, arming the confirming hit.
     */
    private void preview(User user, Island island, int chunkX, int chunkZ) {
        ChunkManager cm = addon.getChunkManager();
        long now = clock.getAsLong();
        PendingClaim previous = pending.get(user.getUniqueId());
        pending.put(user.getUniqueId(), new PendingClaim(island.getUniqueId(), chunkX, chunkZ, now));
        if (addon.getBorderDisplay() != null) {
            addon.getBorderDisplay().showPreview(user.getPlayer(), chunkX, chunkZ, now + timeoutMillis());
        }
        // Swinging at the same chunk repeatedly keeps the outline alive but must not
        // re-announce the price on every blow
        boolean sameChunk = previous != null && previous.chunkX() == chunkX && previous.chunkZ() == chunkZ;
        if (sameChunk && !feedbackReady(user.getUniqueId())) {
            return;
        }
        // Aiming somewhere new always earns a fresh quote, but it still resets the throttle
        // so the follow-up swings at that chunk stay quiet
        lastFeedback.put(user.getUniqueId(), now);
        long cost = cm.getChunkCost();
        user.notify("chunkblock.chunks.claim-confirm", "[cost]", String.valueOf(cost), "[after]",
                String.valueOf(cm.getCredit(island) - cost), "[seconds]",
                String.valueOf(addon.getSettings().getClaimConfirmationTimeout()));
        user.getPlayer().playSound(user.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1F, 1.4F);
    }

    private void clearPending(UUID uuid) {
        pending.remove(uuid);
        if (addon.getBorderDisplay() != null) {
            addon.getBorderDisplay().clearPreview(uuid);
        }
    }

    private long timeoutMillis() {
        return addon.getSettings().getClaimConfirmationTimeout() * 1000L;
    }

    /**
     * Rate-limits failure feedback: repeated swings while mining should not turn every
     * failed claim probe into a chat message and a sound.
     */
    private boolean feedbackReady(UUID uuid) {
        long now = clock.getAsLong();
        Long last = lastFeedback.get(uuid);
        if (last != null && now - last < FEEDBACK_COOLDOWN_MS) {
            return false;
        }
        lastFeedback.put(uuid, now);
        return true;
    }
}
