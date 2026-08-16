package world.bentobox.chunkblock.chunks;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.util.Util;
import world.bentobox.chunkblock.ChunkBlock;
import world.bentobox.chunkblock.chunks.ChunkManager.ClaimResult;

/**
 * The island's territory as a grid of squares, shared by the two things that draw it: the
 * dialog map of {@code /ch chunks}, which makes a button of every chunk, and the chat map
 * it falls back to.
 * <p>
 * Glyphs are handed out as {@link Component Components} rather than colored text. A
 * component can be put in a dialog button as it is and serialized to MiniMessage for the
 * chat map, whereas a string of color codes has to be spliced into translated text, which
 * is where formatting quietly breaks.
 *
 * @author tastybento
 */
public final class ChunkMap {

    /** What a chunk is to an island, which decides its glyph and what the map says about it */
    public enum Kind {
        /** The chunk holding the magic block, which can never lock */
        CENTER,
        /** Already claimed */
        OWNED,
        /** Locked, but adjacent to the island and inside the protection range */
        CLAIMABLE,
        /** Locked and not claimable yet */
        LOCKED
    }

    /**
     * One square of the map.
     *
     * @param dx chunk offset east of the center chunk
     * @param dz chunk offset south of the center chunk
     * @param kind what this chunk is to the island
     * @param here true if the viewer is standing in this chunk
     */
    public record Cell(int dx, int dz, Kind kind, boolean here) {
    }

    private ChunkMap() {
        // Utility class
    }

    /**
     * Maps the territory around an island, row by row from north to south and west to east
     * within a row — the order both maps draw in.
     *
     * @param addon the addon
     * @param island the island whose territory is mapped
     * @param viewer where the player is standing, or null if they are nowhere on the map
     * @param radius how many chunks out from the center the map reaches
     * @return the cells of a square map (2 * radius + 1) chunks across
     */
    public static List<Cell> cells(@NonNull ChunkBlock addon, @NonNull Island island, @Nullable Location viewer,
            int radius) {
        ChunkManager cm = addon.getChunkManager();
        int centerChunkX = island.getCenter().getBlockX() >> 4;
        int centerChunkZ = island.getCenter().getBlockZ() >> 4;
        // A player who is not in this world stands on no chunk of the map
        boolean sameWorld = viewer != null && Util.sameWorld(island.getWorld(), viewer.getWorld());
        int playerDx = sameWorld ? (viewer.getBlockX() >> 4) - centerChunkX : Integer.MIN_VALUE;
        int playerDz = sameWorld ? (viewer.getBlockZ() >> 4) - centerChunkZ : Integer.MIN_VALUE;
        List<Cell> cells = new ArrayList<>();
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                Kind kind;
                if (dx == 0 && dz == 0) {
                    kind = Kind.CENTER;
                } else if (addon.getOneBlocksIsland(island).isChunkUnlocked(dx, dz)) {
                    kind = Kind.OWNED;
                } else if (cm.checkGeometry(island, centerChunkX + dx, centerChunkZ + dz) == ClaimResult.OK) {
                    kind = Kind.CLAIMABLE;
                } else {
                    kind = Kind.LOCKED;
                }
                cells.add(new Cell(dx, dz, kind, dx == playerDx && dz == playerDz));
            }
        }
        return cells;
    }

    /**
     * The glyph standing for a chunk. The chunk the player is on keeps its own outline but
     * takes the marker color, so the map still says what that chunk is.
     *
     * @param cell the chunk
     * @return the glyph, colored
     */
    @NonNull
    public static Component glyph(@NonNull Cell cell) {
        String mark = switch (cell.kind()) {
        case CENTER -> cell.here() ? "◉" : "◎";
        case OWNED -> cell.here() ? "◆" : "■";
        case CLAIMABLE -> cell.here() ? "◆" : "▣";
        case LOCKED -> cell.here() ? "◇" : "□";
        };
        return Component.text(mark, cell.here() ? NamedTextColor.AQUA : color(cell.kind()));
    }

    /**
     * The glyph as MiniMessage text, for the chat map: a whole row of these goes into the
     * {@code [row]} variable of a translation, so the row has to be text by the time it
     * gets there — MiniMessage text, matching the locale files, never color codes.
     *
     * @param cell the chunk
     * @return the glyph as MiniMessage
     */
    @NonNull
    public static String glyphText(@NonNull Cell cell) {
        return Util.getMiniMessage().serialize(glyph(cell));
    }

    private static NamedTextColor color(Kind kind) {
        return switch (kind) {
        case CENTER -> NamedTextColor.GOLD;
        case OWNED -> NamedTextColor.GREEN;
        case CLAIMABLE -> NamedTextColor.YELLOW;
        case LOCKED -> NamedTextColor.GRAY;
        };
    }
}
