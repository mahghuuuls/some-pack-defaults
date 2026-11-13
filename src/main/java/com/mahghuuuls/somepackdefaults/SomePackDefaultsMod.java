package com.mahghuuuls.somepackdefaults;

import com.mahghuuuls.somepackdefaults.server.ServerLinePhase;
import com.mahghuuuls.somepackdefaults.world.WorldPhase;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerAboutToStartEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Entry point. The mod runs on the logical server only and registers nothing that clients
 * must have, so clients without it can join a server that runs it.
 */
@Mod(modid = Tags.MOD_ID, name = Tags.MOD_NAME, version = Tags.VERSION, acceptableRemoteVersions = "*")
public class SomePackDefaultsMod {

    private static final Logger LOGGER = LogManager.getLogger(Tags.MOD_ID);

    private final ServerLinePhase serverLines = new ServerLinePhase(LOGGER, WorldPhase.vanillaGamerules());
    private final WorldPhase world = new WorldPhase(LOGGER);

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        world.onPreInit(event);
        serverLines.onPreInit(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        serverLines.onPostInit(event);
    }

    @Mod.EventHandler
    public void serverAboutToStart(FMLServerAboutToStartEvent event) {
        world.onServerAboutToStart(event);
    }
}
