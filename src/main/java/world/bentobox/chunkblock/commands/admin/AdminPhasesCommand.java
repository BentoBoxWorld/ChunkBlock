package world.bentobox.chunkblock.commands.admin;

import java.util.List;

import world.bentobox.chunkblock.ChunkBlock;
import world.bentobox.chunkblock.panels.AdminPhasesPanel;
import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;

/**
 * Command to open the phase order editor
 *
 * @author tastybento
 */
public class AdminPhasesCommand extends CompositeCommand {

    private ChunkBlock addon;

    public AdminPhasesCommand(CompositeCommand parent) {
        super(parent, "phases");
    }

    @Override
    public void setup() {
        setDescription("chunkblock.commands.admin.phases.description");
        // Permission
        setPermission("admin.phases");
        setOnlyPlayer(true);
        addon = getAddon();
    }

    @Override
    public boolean canExecute(User user, String label, List<String> args) {
        if (!args.isEmpty()) {
            showHelp(this, user);
            return false;
        }
        if (addon.getOneBlockManager().getPhaseIndex().isEmpty()) {
            // Phases were loaded without an index, so there is nothing to reorder
            user.sendMessage("chunkblock.commands.admin.phases.no-index");
            return false;
        }
        return true;
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        AdminPhasesPanel.openPanel(addon, user);
        return true;
    }
}
