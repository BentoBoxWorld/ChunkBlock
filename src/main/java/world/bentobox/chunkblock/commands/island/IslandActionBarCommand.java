package world.bentobox.chunkblock.commands.island;

import java.util.List;

import world.bentobox.chunkblock.ChunkBlock;
import world.bentobox.chunkblock.listeners.BossBarListener;
import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.metadata.MetaDataValue;
import world.bentobox.bentobox.api.user.User;

public class IslandActionBarCommand extends CompositeCommand {

    private ChunkBlock addon;

    public IslandActionBarCommand(CompositeCommand islandCommand, String label, String[] aliases)
    {
        super(islandCommand, label, aliases);
    }

    @Override
    public void setup() {
        setDescription("chunkblock.commands.island.actionbar.description");
        setOnlyPlayer(true);
        // Permission
        setPermission("island.actionbar");
        addon = getAddon();
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        getIslands().getIslandAt(user.getLocation()).ifPresent(i -> {
            if (!i.isAllowed(addon.CHUNKBLOCK_ACTIONBAR)) {
                user.sendMessage("chunkblock.actionbar.not-active");
            }
        });
        // Toggle state
        boolean newState = !user.getMetaData(BossBarListener.ACTIONBAR_METADATA).map(MetaDataValue::asBoolean).orElse(true);
        user.putMetaData(BossBarListener.ACTIONBAR_METADATA, new MetaDataValue(newState));
        if (newState) {
             user.sendMessage("chunkblock.commands.island.actionbar.status_on");
        } else {
            user.sendMessage("chunkblock.commands.island.actionbar.status_off");
        }
        return true;
    }
}
