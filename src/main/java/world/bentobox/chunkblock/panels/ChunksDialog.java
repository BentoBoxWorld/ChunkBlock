package world.bentobox.chunkblock.panels;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.body.PlainMessageDialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import world.bentobox.bentobox.api.dialogs.Dialogs;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.util.Util;
import world.bentobox.chunkblock.ChunkBlock;
import world.bentobox.chunkblock.chunks.ChunkManager;
import world.bentobox.chunkblock.chunks.ChunkMap;
import world.bentobox.chunkblock.chunks.ChunkMap.Cell;

/**
 * The territory map of {@code /ch chunks} drawn as a dialog: one button per chunk, laid
 * out in a grid. When the island's territory can exceed the 13-wide viewport, a control
 * row is prepended: pan arrows that slide the viewport half a screen per click, a jump
 * button to the island center, a jump button to the player's own position, and an
 * indicator in the middle that points at whichever of those two is off-screen.
 *
 * @author tastybento
 */
public class ChunksDialog {

    /**
     * Widest map that still fits the dialog. A grid this wide is {@value #MAX_RADIUS} * 2
     * + 1 buttons across, which is as much as the dialog screen holds before the outer
     * columns are squeezed off.
     */
    static final int MAX_RADIUS = 6;

    /** How many chunks one pan-arrow click slides the viewport: half a screen. */
    static final int PAN_STEP = MAX_RADIUS;

    /** Button size in dialog units. Roughly square once the client adds its own padding. */
    private static final int BUTTON_WIDTH = 26;

    /** Width of the close button along the bottom */
    private static final int CLOSE_BUTTON_WIDTH = 100;

    /** How long a chunk button's click callback stays live after the dialog is shown */
    private static final Duration CALLBACK_LIFETIME = Duration.ofMinutes(5);

    private static final String REFERENCE = "chunkblock.chunks.dialog.";
    private static final String PLACEHOLDER_COST = "[cost]";
    private static final String PLACEHOLDER_MAX = "[max]";

    /** Direction glyphs indexed by sector (0 = east, rotating counter-clockwise). */
    private static final String[] ARROWS = { "▶", "↗", "▲", "↖", "◀", "↙", "▼", "↘" };

    private final ChunkBlock addon;
    private final User user;
    private final Island island;
    private final int radius;
    private final int viewDx;
    private final int viewDz;
    private final boolean scrollable;

    ChunksDialog(ChunkBlock addon, User user, Island island) {
        this(addon, user, island, 0, 0);
    }

    /**
     * @param viewDx requested viewport center, chunks east of the island center
     * @param viewDz requested viewport center, chunks south of the island center; both are
     *            clamped so the viewport never scrolls past the edge of the claimable map
     */
    ChunksDialog(ChunkBlock addon, User user, Island island, int viewDx, int viewDz) {
        this.addon = addon;
        this.user = user;
        this.island = island;

        ChunkManager cm = addon.getChunkManager();
        int maxRing = cm.maxRingRadius(island);
        this.scrollable = maxRing > MAX_RADIUS;

        if (scrollable) {
            this.radius = MAX_RADIUS;
            // The viewport center stops radius short of the edge so the last screen ends
            // exactly on the outermost ring instead of scrolling into the void
            int limit = Math.max(0, maxRing - radius);
            this.viewDx = Math.clamp(viewDx, -limit, limit);
            this.viewDz = Math.clamp(viewDz, -limit, limit);
        } else {
            this.radius = Math.min(MAX_RADIUS, cm.currentRing(island) + 1);
            this.viewDx = 0;
            this.viewDz = 0;
        }
    }

    /**
     * Shows the territory map to a player.
     *
     * @param addon the addon
     * @param user the player to show it to
     * @param island the island whose territory is mapped
     * @return true if the dialog was shown; false if this server cannot show dialogs, in
     *         which case the caller should fall back to the chat map
     */
    public static boolean show(@NonNull ChunkBlock addon, @NonNull User user, @NonNull Island island) {
        return show(addon, user, island, 0, 0, null);
    }

    /**
     * @param viewDx viewport center chunk offset east of the island center
     * @param viewDz viewport center chunk offset south of the island center
     * @param selection the chunk description to show above the map, or null for none
     */
    private static boolean show(ChunkBlock addon, User user, Island island, int viewDx, int viewDz,
            @Nullable Component selection) {
        if (!Dialogs.isSupported() || !user.isPlayer()) {
            return false;
        }
        try {
            new ChunksDialog(addon, user, island, viewDx, viewDz).open(selection);
            return true;
        } catch (Exception | LinkageError e) {
            addon.logError("Could not show the chunks dialog: " + e.getMessage());
            return false;
        }
    }

    private void open(@Nullable Component selection) {
        ChunkManager cm = addon.getChunkManager();
        int unlocked = cm.getUnlockedChunkCount(island);
        int max = cm.getMaxChunks(island);
        List<PlainMessageDialogBody> body = new ArrayList<>();
        if (selection != null) {
            body.add(DialogBody.plainMessage(selection));
        }
        body.add(DialogBody.plainMessage(text("chunkblock.chunks.info", "[unlocked]", String.valueOf(unlocked),
                PLACEHOLDER_MAX, String.valueOf(max), "[credit]", String.valueOf(Math.max(0, cm.getCredit(island))), PLACEHOLDER_COST,
                String.valueOf(cm.getChunkCost()))));
        body.add(DialogBody.plainMessage(text("chunkblock.chunks.rings", "[rings]",
                String.valueOf(cm.completedRings(island)), PLACEHOLDER_MAX, String.valueOf(cm.maxRingRadius(island)))));
        body.add(DialogBody.plainMessage(
                text("chunkblock.chunks.map.legend", PLACEHOLDER_COST, String.valueOf(cm.getChunkCost()))));

        DialogBase base = DialogBase
                .builder(text("chunkblock.chunks.map.title", "[unlocked]", String.valueOf(unlocked), PLACEHOLDER_MAX,
                        String.valueOf(max)))
                .canCloseWithEscape(true).afterAction(DialogBase.DialogAfterAction.CLOSE).body(body).build();

        int columns = 2 * radius + 1;
        List<ActionButton> buttons = new ArrayList<>();
        if (scrollable) {
            buttons.addAll(controlRow(columns));
        }
        buttons.addAll(cells().stream().map(this::button).toList());

        DialogType type = DialogType.multiAction(buttons).columns(columns)
                .exitAction(ActionButton.create(text(REFERENCE + "close"), null, CLOSE_BUTTON_WIDTH, null)).build();

        user.getPlayer().showDialog(Dialog.create(factory -> factory.empty().base(base).type(type)));
    }

    // ------------------------------------------------------------------
    // Control row: [◎] ─ ─ ─ [◀] [▲] [•] [▼] [▶] ─ ─ ─ [◇]
    // ------------------------------------------------------------------

    private List<ActionButton> controlRow(int columns) {
        int mid = columns / 2;
        List<ActionButton> row = new ArrayList<>(columns);
        for (int i = 0; i < columns; i++) {
            if (i == 0) {
                row.add(islandJumpButton());
            } else if (i == columns - 1) {
                row.add(playerJumpButton());
            } else if (i == mid - 2) {
                row.add(panButton("◀", -PAN_STEP, 0, "pan-west", canPan(-1, 0)));
            } else if (i == mid - 1) {
                row.add(panButton("▲", 0, -PAN_STEP, "pan-north", canPan(0, -1)));
            } else if (i == mid) {
                row.add(indicatorButton());
            } else if (i == mid + 1) {
                row.add(panButton("▼", 0, PAN_STEP, "pan-south", canPan(0, 1)));
            } else if (i == mid + 2) {
                row.add(panButton("▶", PAN_STEP, 0, "pan-east", canPan(1, 0)));
            } else {
                row.add(spacerButton());
            }
        }
        return row;
    }

    /**
     * Whether panning one step toward (signX, signZ) would reveal anything: the viewport
     * must not already touch that edge of the claimable map.
     */
    private boolean canPan(int signX, int signZ) {
        int maxRing = addon.getChunkManager().maxRingRadius(island);
        if (signX != 0) {
            return signX < 0 ? viewDx - radius > -maxRing : viewDx + radius < maxRing;
        }
        return signZ < 0 ? viewDz - radius > -maxRing : viewDz + radius < maxRing;
    }

    private ActionButton panButton(String glyph, int stepX, int stepZ, String tooltipKey, boolean enabled) {
        if (!enabled) {
            return spacerButton();
        }
        return ActionButton.builder(Component.text(glyph, NamedTextColor.WHITE))
                .tooltip(text(REFERENCE + tooltipKey)).width(BUTTON_WIDTH)
                .action(DialogAction.customClick((view, audience) -> moveViewport(viewDx + stepX, viewDz + stepZ),
                        ClickCallback.Options.builder().uses(1).lifetime(CALLBACK_LIFETIME).build()))
                .build();
    }

    private ActionButton islandJumpButton() {
        boolean active = viewDx == 0 && viewDz == 0;
        return jumpButton(active ? "◉" : "◎", active, NamedTextColor.GOLD, REFERENCE + "view-island", 0, 0);
    }

    private ActionButton playerJumpButton() {
        int[] player = playerChunkOffset();
        if (player == null) {
            return spacerButton();
        }
        // Compare against where the jump would actually land, which is the clamped spot
        ChunksDialog target = new ChunksDialog(addon, user, island, player[0], player[1]);
        boolean active = viewDx == target.viewDx && viewDz == target.viewDz;
        return jumpButton(active ? "◆" : "◇", active, NamedTextColor.AQUA, REFERENCE + "view-player", player[0],
                player[1]);
    }

    private ActionButton jumpButton(String glyph, boolean active, NamedTextColor color, String tooltipKey,
            int targetDx, int targetDz) {
        return ActionButton.builder(Component.text(glyph, active ? NamedTextColor.WHITE : color))
                .tooltip(text(tooltipKey)).width(BUTTON_WIDTH)
                .action(DialogAction.customClick((view, audience) -> moveViewport(targetDx, targetDz),
                        ClickCallback.Options.builder().uses(1).lifetime(CALLBACK_LIFETIME).build()))
                .build();
    }

    private ActionButton spacerButton() {
        return ActionButton.builder(Component.text("─", NamedTextColor.DARK_GRAY)).width(BUTTON_WIDTH).build();
    }

    /**
     * The middle of the control row: an arrow pointing at the island center when it has
     * been panned off-screen (clicking jumps home), otherwise at the player when they are
     * off-screen (clicking jumps to them), otherwise a plain dot.
     */
    private ActionButton indicatorButton() {
        if (offScreen(0, 0)) {
            return indicatorArrow(-viewDx, -viewDz, NamedTextColor.GOLD, REFERENCE + "arrow-to-island", 0, 0);
        }
        int[] player = playerChunkOffset();
        if (player != null && offScreen(player[0], player[1])) {
            return indicatorArrow(player[0] - viewDx, player[1] - viewDz, NamedTextColor.AQUA,
                    REFERENCE + "arrow-to-player", player[0], player[1]);
        }
        return ActionButton.builder(Component.text("•", NamedTextColor.DARK_GRAY)).width(BUTTON_WIDTH).build();
    }

    private boolean offScreen(int dx, int dz) {
        return Math.abs(dx - viewDx) > radius || Math.abs(dz - viewDz) > radius;
    }

    private ActionButton indicatorArrow(int relDx, int relDz, NamedTextColor color, String tooltipKey,
            int targetDx, int targetDz) {
        return ActionButton.builder(Component.text(directionGlyph(relDx, relDz), color))
                .tooltip(text(tooltipKey)).width(BUTTON_WIDTH)
                .action(DialogAction.customClick((view, audience) -> moveViewport(targetDx, targetDz),
                        ClickCallback.Options.builder().uses(1).lifetime(CALLBACK_LIFETIME).build()))
                .build();
    }

    /**
     * Returns the arrow glyph for the direction from the viewport center to the target.
     * Divides the plane into eight 45-degree sectors starting from east.
     */
    static String directionGlyph(int relDx, int relDz) {
        double angle = Math.atan2(-relDz, relDx);
        int sector = (int) Math.round(angle / (Math.PI / 4));
        return ARROWS[((sector % 8) + 8) % 8];
    }

    /**
     * @return the chunk the player is standing on as an offset from the island center, or
     *         null when the player is not in the island's world
     */
    private int @Nullable [] playerChunkOffset() {
        if (island.getWorld() == null || !Util.sameWorld(island.getWorld(), user.getLocation().getWorld())) {
            return null;
        }
        return new int[] { (user.getLocation().getBlockX() >> 4) - (island.getCenter().getBlockX() >> 4),
                (user.getLocation().getBlockZ() >> 4) - (island.getCenter().getBlockZ() >> 4) };
    }

    // ------------------------------------------------------------------
    // Map buttons
    // ------------------------------------------------------------------

    /**
     * The map, row by row from north to south — the same reading order the buttons are laid
     * out in, so the grid comes out with north at the top.
     */
    List<Cell> cells() {
        return ChunkMap.cells(addon, island, user.getLocation(), radius, viewDx, viewDz);
    }

    private ActionButton button(Cell cell) {
        Component tooltip = tooltip(cell);
        return ActionButton.builder(ChunkMap.glyph(cell)).tooltip(tooltip).width(BUTTON_WIDTH)
                .action(DialogAction.customClick((view, audience) -> reopen(tooltip),
                        ClickCallback.Options.builder().uses(1).lifetime(CALLBACK_LIFETIME).build()))
                .build();
    }

    /**
     * Puts the map back up with the clicked chunk named at the top, keeping the panned
     * position. Clicking any button closes the dialog, so a map that stays put has to be
     * shown again.
     */
    private void reopen(Component selection) {
        Bukkit.getScheduler().runTask(addon.getPlugin(), () -> show(addon, user, island, viewDx, viewDz, selection));
    }

    /**
     * Reopens the map with the viewport centered on the given offset (clamped to the map).
     */
    private void moveViewport(int newDx, int newDz) {
        Bukkit.getScheduler().runTask(addon.getPlugin(), () -> show(addon, user, island, newDx, newDz, null));
    }

    /**
     * What the chunk under the mouse is: its offset from the center chunk and, for a chunk
     * that could be claimed next, what it would cost.
     */
    private Component tooltip(Cell cell) {
        ChunkManager cm = addon.getChunkManager();
        long cost = cm.getChunkCost();
        Component tip = switch (cell.kind()) {
        case CENTER -> text(REFERENCE + "tooltip.center");
        case OWNED -> text(REFERENCE + "tooltip.owned", "[x]", offset(cell.dx()), "[z]", offset(cell.dz()));
        case CLAIMABLE -> {
            long credit = cm.getCredit(island);
            yield credit >= cost
                    ? text(REFERENCE + "tooltip.claimable", "[x]", offset(cell.dx()), "[z]", offset(cell.dz()),
                            PLACEHOLDER_COST, String.valueOf(cost))
                    : text(REFERENCE + "tooltip.no-credit", "[x]", offset(cell.dx()), "[z]", offset(cell.dz()),
                            PLACEHOLDER_COST, String.valueOf(cost), "[needed]", String.valueOf(cost - credit));
        }
        case LOCKED -> text(REFERENCE + "tooltip.locked", "[x]", offset(cell.dx()), "[z]", offset(cell.dz()));
        };
        if (cell.here()) {
            tip = tip.append(Component.newline()).append(text(REFERENCE + "tooltip.you-are-here"));
        }
        return tip;
    }

    /** Chunk offsets read better signed: the center is 0,0 and everything else hangs off it */
    private static String offset(int value) {
        return value > 0 ? "+" + value : String.valueOf(value);
    }

    boolean isScrollable() {
        return scrollable;
    }

    int getViewDx() {
        return viewDx;
    }

    int getViewDz() {
        return viewDz;
    }

    /**
     * Translates a locale key straight to a component. Going through the user rather than
     * parsing the translated string here keeps every message on BentoBox's own path,
     * whether the locale file is written in MiniMessage or in old color codes.
     */
    private Component text(String reference, String... variables) {
        return user.getTranslationAsComponent(reference, variables);
    }
}
