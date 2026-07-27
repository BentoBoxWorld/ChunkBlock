package world.bentobox.chunkblock.commands.island;

import java.util.List;

import world.bentobox.chunkblock.ChunkBlock;
import world.bentobox.chunkblock.panels.PhasesPanel;
import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;

public class IslandPhasesCommand extends CompositeCommand {

    private ChunkBlock addon;

    public IslandPhasesCommand(CompositeCommand islandCommand, String label, String[] aliases)
    {
        super(islandCommand, label, aliases);
    }

    @Override
    public void setup() {
        setDescription("chunkblock.commands.phases.description");
        setOnlyPlayer(true);
        // Permission
        setPermission("phases");
        addon = getAddon();
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        PhasesPanel.openPanel(this.addon, this.getWorld(), user);
        return true;
    }
}
