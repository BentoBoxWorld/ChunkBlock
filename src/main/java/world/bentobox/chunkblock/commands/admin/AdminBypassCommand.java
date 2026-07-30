package world.bentobox.chunkblock.commands.admin;

import java.util.List;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.chunkblock.ChunkBlock;

/**
 * /chadmin bypass — lets staff with the bypass permission toggle chunk lock enforcement
 * for themselves, so they can test the game as players see it and inspect cleanly.
 *
 * @author tastybento
 */
public class AdminBypassCommand extends CompositeCommand {

    private ChunkBlock addon;

    public AdminBypassCommand(CompositeCommand adminCommand) {
        super(adminCommand, "bypass");
    }

    @Override
    public void setup() {
        setDescription("chunkblock.commands.admin.bypass.description");
        setOnlyPlayer(true);
        setPermission("mod.bypasschunks");
        addon = getAddon();
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        boolean bypassing = addon.getChunkManager().toggleBypass(user.getUniqueId());
        user.sendMessage(bypassing ? "chunkblock.commands.admin.bypass.on"
                : "chunkblock.commands.admin.bypass.off");
        return true;
    }
}
