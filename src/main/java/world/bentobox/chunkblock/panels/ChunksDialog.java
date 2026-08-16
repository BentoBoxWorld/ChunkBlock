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
import world.bentobox.bentobox.api.dialogs.Dialogs;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.chunkblock.ChunkBlock;
import world.bentobox.chunkblock.chunks.ChunkManager;
import world.bentobox.chunkblock.chunks.ChunkMap;
import world.bentobox.chunkblock.chunks.ChunkMap.Cell;

/**
 * The territory map of {@code /ch chunks} drawn as a dialog: one button per chunk, laid
 * out in a grid. Chat renders a glyph grid differently on every client — font, chat width
 * and scale all pull it out of shape — whereas dialog buttons are fixed-size boxes that
 * look the same everywhere, and can carry a tooltip explaining the chunk under the mouse.
 * <p>
 * The map is read-only: chunks are still claimed by hitting the border, so clicking a
 * chunk only reports what it is and reopens the map.
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

    /** Button size in dialog units. Roughly square once the client adds its own padding. */
    private static final int BUTTON_WIDTH = 26;

    /** Width of the close button along the bottom */
    private static final int CLOSE_BUTTON_WIDTH = 100;

    /** How long a chunk button's click callback stays live after the dialog is shown */
    private static final Duration CALLBACK_LIFETIME = Duration.ofMinutes(5);

    private static final String REFERENCE = "chunkblock.chunks.dialog.";

    private final ChunkBlock addon;
    private final User user;
    private final Island island;
    private final int radius;

    ChunksDialog(ChunkBlock addon, User user, Island island) {
        this.addon = addon;
        this.user = user;
        this.island = island;
        this.radius = Math.min(MAX_RADIUS, addon.getChunkManager().currentRing(island) + 1);
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
        return show(addon, user, island, null);
    }

    /**
     * @param selection the chunk description to show above the map, or null for none
     */
    private static boolean show(ChunkBlock addon, User user, Island island, @Nullable Component selection) {
        if (!Dialogs.isSupported() || !user.isPlayer() || user.getPlayer() == null) {
            return false;
        }
        try {
            new ChunksDialog(addon, user, island).open(selection);
            return true;
        } catch (Exception | LinkageError e) {
            // A server that reports dialog support but cannot build one is no reason to
            // leave the player with nothing — the caller falls back to the chat map
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
                "[max]", String.valueOf(max), "[credit]", String.valueOf(Math.max(0, cm.getCredit(island))), "[cost]",
                String.valueOf(cm.getChunkCost()))));
        body.add(DialogBody.plainMessage(text("chunkblock.chunks.rings", "[rings]",
                String.valueOf(cm.completedRings(island)), "[max]", String.valueOf(cm.maxRingRadius(island)))));
        body.add(DialogBody.plainMessage(
                text("chunkblock.chunks.map.legend", "[cost]", String.valueOf(cm.getChunkCost()))));

        DialogBase base = DialogBase
                .builder(text("chunkblock.chunks.map.title", "[unlocked]", String.valueOf(unlocked), "[max]",
                        String.valueOf(max)))
                .canCloseWithEscape(true).afterAction(DialogBase.DialogAfterAction.CLOSE).body(body).build();

        List<ActionButton> buttons = cells().stream().map(this::button).toList();
        DialogType type = DialogType.multiAction(buttons).columns(2 * radius + 1)
                .exitAction(ActionButton.create(text(REFERENCE + "close"), null, CLOSE_BUTTON_WIDTH, null)).build();

        user.getPlayer().showDialog(Dialog.create(factory -> factory.empty().base(base).type(type)));
    }

    /**
     * The map, row by row from north to south — the same reading order the buttons are laid
     * out in, so the grid comes out with north at the top.
     */
    List<Cell> cells() {
        return ChunkMap.cells(addon, island, user.getLocation(), radius);
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
        // Dialog callbacks may arrive off the main thread, and everything the map reads is
        // island data
        Bukkit.getScheduler().runTask(addon.getPlugin(), () -> show(addon, user, island, selection));
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

    /**
     * Translates a locale key straight to a component. Going through the user rather than
     * parsing the translated string here keeps every message on BentoBox's own path,
     * whether the locale file is written in MiniMessage or in old color codes.
     */
    private Component text(String reference, String... variables) {
        return user.getTranslationAsComponent(reference, variables);
    }
}
