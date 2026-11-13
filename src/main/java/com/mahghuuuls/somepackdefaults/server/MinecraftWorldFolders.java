package com.mahghuuuls.somepackdefaults.server;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.io.File;

/**
 * World folders as the dedicated server sees them. The server object exists at pre-initialization
 * (FML loads mods from inside its start), and its save format already points at the world root,
 * which {@code --universe} can move away from the working folder. Its folder name is set only by
 * {@code --world} at this point; vanilla fills it from {@code level-name} later, and only when empty.
 */
final class MinecraftWorldFolders implements WorldFolders {

    @Override
    public boolean hasLevelDat(String levelName) {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        File levelDat = server != null
                ? server.getActiveAnvilConverter().getFile(levelName, "level.dat")
                : new File(levelName, "level.dat");
        return levelDat.isFile();
    }

    @Override
    public String startOptionWorldName() {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        return server != null ? server.getFolderName() : null;
    }
}
