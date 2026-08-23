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
 * row is prepended with two view-mode buttons (island center / player position) and a
 * directional arrow pointing toward the off-screen target.
 *
 * @author tastybento
 */
public class ChunksDialog {

    /**
     * Whether the viewport is centered on the island center or on the player's position.
     */
    enum ViewMode {
        ISLAND_CENTER, PLAYER_CENTER
    }

    /**
     * Widest map that still fits the dialog. A grid this wide is {@value #MAX_RADIUS} * 2
     * + 1 buttons across, which is as much as the dialog screen holds before the outer
     * columns are squeezed off.
     */
    static final int MAX_RADIUS = 6;

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
    private final ViewMode viewMode;
    private final int viewDx;
    private final int viewDz;
    private final boolean scrollable;

    ChunksDialog(ChunkBlock addon, User user, Island island) {
        this(addon, user, island, ViewMode.ISLAND_CENTER);
    }

    ChunksDialog(ChunkBlock addon, User user, Island island, ViewMode viewMode) {
        this.addon = addon;
        this.user = user;
        this.island = island;
        this.viewMode = viewMode;

        ChunkManager cm = addon.getChunkManager();
        this.scrollable = cm.maxRingRadius(island) > MAX_RADIUS;

        if (scrollable) {
            this.radius = MAX_RADIUS;
            if (viewMode == ViewMode.PLAYER_CENTER && isPlayerOnIsland()) {
                int centerChunkX = island.getCenter().getBlockX() >> 4;
                int centerChunkZ = island.getCenter().getBlockZ() >> 4;
                this.viewDx = (user.getLocation().getBlockX() >> 4) - centerChunkX;
                this.viewDz = (user.getLocation().getBlockZ() >> 4) - centerChunkZ;
            } else {
                this.viewDx = 0;
                this.viewDz = 0;
            }
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
        return show(addon, user, island, ViewMode.ISLAND_CENTER, null);
    }

    /**
     * @param viewMode which point the viewport is centered on
     * @param selection the chunk description to show above the map, or null for none
     */
    private static boolean show(ChunkBlock addon, User user, Island island, ViewMode viewMode,
            @Nullable Component selection) {
        if (!Dialogs.isSupported() || !user.isPlayer()) {
            return false;
        }
        try {
            new ChunksDialog(addon, user, island, viewMode).open(selection);
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
    // Control row (view-mode toggle + directional arrow)
    // ------------------------------------------------------------------

    private List<ActionButton> controlRow(int columns) {
        List<ActionButton> row = new ArrayList<>(columns);
        for (int i = 0; i < columns; i++) {
            if (i == 0) {
                row.add(modeButton(ViewMode.ISLAND_CENTER, "◎", "◉", NamedTextColor.GOLD,
                        REFERENCE + "view-island"));
            } else if (i == columns - 1) {
                row.add(modeButton(ViewMode.PLAYER_CENTER, "◇", "◆", NamedTextColor.AQUA,
                        REFERENCE + "view-player"));
            } else if (i == columns / 2) {
                row.add(directionArrowButton());
            } else {
                row.add(spacerButton());
            }
        }
        return row;
    }

    private ActionButton modeButton(ViewMode mode, String inactiveGlyph, String activeGlyph,
            NamedTextColor color, String tooltipKey) {
        boolean active = viewMode == mode;
        String glyph = active ? activeGlyph : inactiveGlyph;
        NamedTextColor buttonColor = active ? NamedTextColor.WHITE : color;
        return ActionButton.builder(Component.text(glyph, buttonColor))
                .tooltip(text(tooltipKey)).width(BUTTON_WIDTH)
                .action(DialogAction.customClick((view, audience) -> switchMode(mode),
                        ClickCallback.Options.builder().uses(1).lifetime(CALLBACK_LIFETIME).build()))
                .build();
    }

    private ActionButton spacerButton() {
        return ActionButton.builder(Component.text("─", NamedTextColor.DARK_GRAY)).width(BUTTON_WIDTH).build();
    }

    private ActionButton directionArrowButton() {
        int targetDx;
        int targetDz;
        NamedTextColor arrowColor;
        String tooltipKey;
        ViewMode targetMode;

        if (viewMode == ViewMode.ISLAND_CENTER) {
            if (!isPlayerOnIsland()) {
                return spacerButton();
            }
            int centerChunkX = island.getCenter().getBlockX() >> 4;
            int centerChunkZ = island.getCenter().getBlockZ() >> 4;
            targetDx = (user.getLocation().getBlockX() >> 4) - centerChunkX;
            targetDz = (user.getLocation().getBlockZ() >> 4) - centerChunkZ;
            arrowColor = NamedTextColor.AQUA;
            tooltipKey = REFERENCE + "arrow-to-player";
            targetMode = ViewMode.PLAYER_CENTER;
        } else {
            targetDx = 0;
            targetDz = 0;
            arrowColor = NamedTextColor.GOLD;
            tooltipKey = REFERENCE + "arrow-to-island";
            targetMode = ViewMode.ISLAND_CENTER;
        }

        int relDx = targetDx - viewDx;
        int relDz = targetDz - viewDz;
        if (Math.abs(relDx) <= radius && Math.abs(relDz) <= radius) {
            return ActionButton.builder(Component.text("•", NamedTextColor.DARK_GRAY)).width(BUTTON_WIDTH).build();
        }

        String arrow = directionGlyph(relDx, relDz);
        return ActionButton.builder(Component.text(arrow, arrowColor))
                .tooltip(text(tooltipKey)).width(BUTTON_WIDTH)
                .action(DialogAction.customClick((view, audience) -> switchMode(targetMode),
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
     * Puts the map back up with the clicked chunk named at the top. Clicking any button
     * closes the dialog, so a map that stays put has to be shown again.
     */
    private void reopen(Component selection) {
        Bukkit.getScheduler().runTask(addon.getPlugin(), () -> show(addon, user, island, viewMode, selection));
    }

    private void switchMode(ViewMode mode) {
        Bukkit.getScheduler().runTask(addon.getPlugin(), () -> show(addon, user, island, mode, null));
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
                            "[cost]", String.valueOf(cost))
                    : text(REFERENCE + "tooltip.no-credit", "[x]", offset(cell.dx()), "[z]", offset(cell.dz()),
                            "[cost]", String.valueOf(cost), "[needed]", String.valueOf(cost - credit));
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

    private boolean isPlayerOnIsland() {
        return island.getWorld() != null && Util.sameWorld(island.getWorld(), user.getLocation().getWorld());
    }

    boolean isScrollable() {
        return scrollable;
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
