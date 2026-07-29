package world.bentobox.chunkblock.commands.island;

import java.util.List;

import world.bentobox.chunkblock.ChunkBlock;
import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;

public class IslandBossBarCommand extends CompositeCommand {

    private ChunkBlock addon;

    public IslandBossBarCommand(CompositeCommand islandCommand, String label, String[] aliases)
    {
        super(islandCommand, label, aliases);
    }

    @Override
    public void setup() {
        setDescription("chunkblock.commands.island.bossbar.description");
        setOnlyPlayer(true);
        // Permission
        setPermission("island.bossbar");
        addon = getAddon();
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        addon.getBossBar().toggleUser(user);
        getIslands().getIslandAt(user.getLocation()).ifPresent(i -> {
            if (!i.isAllowed(addon.CHUNKBLOCK_BOSSBAR)) {
                user.sendMessage("chunkblock.bossbar.not-active");
            }
        });
        return true;
    }
}
