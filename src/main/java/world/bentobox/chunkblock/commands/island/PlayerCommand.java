package world.bentobox.chunkblock.commands.island;

import world.bentobox.chunkblock.ChunkBlock;
import world.bentobox.chunkblock.Settings;
import world.bentobox.bentobox.api.commands.island.DefaultPlayerCommand;

public class PlayerCommand extends DefaultPlayerCommand {

    public PlayerCommand(ChunkBlock addon) {
        super(addon);
    }

    /* (non-Javadoc)
     * @see world.bentobox.bentobox.api.commands.island.DefaultPlayerCommand#setup()
     */
    @Override
    public void setup() {
        super.setup();

        Settings settings = this.<ChunkBlock>getAddon().getSettings();

        // Count
        new IslandCountCommand(this,
                settings.getCountCommand().split(" ")[0],
                settings.getCountCommand().split(" "));
        // Phase list
        new IslandPhasesCommand(this,
                settings.getPhasesCommand().split(" ")[0],
                settings.getPhasesCommand().split(" "));
        // Set Count
        new IslandSetCountCommand(this,
                settings.getSetCountCommand().split(" ")[0],
                settings.getSetCountCommand().split(" "));
        // Force block respawn
        new IslandRespawnBlockCommand(this,
                settings.getRespawnBlockCommand().split(" ")[0],
                settings.getRespawnBlockCommand().split(" "));
       
        // Action bar
        if (settings.isActionBar()) {
            new IslandActionBarCommand(this, settings.getActionBarCommand().split(" ")[0],
                    settings.getActionBarCommand().split(" "));
        }
        
        // Boss bar
        if (settings.isBossBar()) {
            new IslandBossBarCommand(this, settings.getBossBarCommand().split(" ")[0],
                    settings.getBossBarCommand().split(" "));
        }
    }
}
