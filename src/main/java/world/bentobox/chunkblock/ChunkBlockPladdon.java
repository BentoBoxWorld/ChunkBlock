package world.bentobox.chunkblock;

import world.bentobox.bentobox.api.addons.Addon;
import world.bentobox.bentobox.api.addons.Pladdon;

public class ChunkBlockPladdon extends Pladdon {

    private Addon addon;

    @Override
    public Addon getAddon() {
        if (addon == null) {
            addon = new ChunkBlock();
        }

        return addon;
    }
}
