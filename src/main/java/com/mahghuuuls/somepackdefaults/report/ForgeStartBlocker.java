package com.mahghuuuls.somepackdefaults.report;

import com.mahghuuuls.somepackdefaults.rules.Problem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.StartupQuery;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import org.apache.logging.log4j.Logger;

import java.util.List;

/**
 * Stops starts in the real game. Before a world loads on a dedicated server the process ends
 * through Forge's exit call: an exception thrown from a mod's lifecycle event would turn into a
 * crash report, and nothing has been written yet that needs a normal shutdown.
 */
public final class ForgeStartBlocker implements StartBlocker {

    /** How many problems the disconnect screen shows; the log always has all of them. */
    private static final int MAX_SHOWN = 5;

    private final Logger logger;
    /** The message held for the singleplayer host's login, when a stop is waiting for it. */
    private String pendingMessage;
    /** The integrated server that stop belongs to, so it never reaches a later world. */
    private MinecraftServer pendingServer;

    public ForgeStartBlocker(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void stopBeforeWorldLoad(List<Problem> problems) {
        logger.error(StartReport.PREFIX + "stopping the server: {} configuration error(s), listed above. Fix them and start again.", problems.size());
        FMLCommonHandler.instance().exitJava(1, false);
    }

    /**
     * On a dedicated server, uses Forge's start-up query: the message goes to the log, and the
     * abort ends the start; the server's run loop catches it, saves the unchanged world, and stops.
     * In singleplayer that query cannot be answered once the world has loaded (only the client's
     * world-loading wait answers it), so the stop waits for the player's login and disconnects the
     * player with the message; leaving the world stops the integrated server, which saves the
     * unchanged world, and the client shows the message with a button back to the menu.
     */
    @Override
    public void stopAfterWorldLoad(List<Problem> problems) {
        logger.error(StartReport.PREFIX + "stopping this world: {} configuration error(s), listed above. Fix them and open it again.", problems.size());
        StringBuilder text = new StringBuilder("Some Pack Defaults stopped this world because of ")
                .append(problems.size()).append(problems.size() == 1 ? " configuration error" : " configuration errors")
                .append(" in config/somepackdefaults:");
        // The disconnect screen does not scroll, so it shows the first few; the log has all of them.
        int shown = Math.min(problems.size(), MAX_SHOWN);
        for (int i = 0; i < shown; i++) {
            text.append("\n\n").append(problems.get(i).message());
        }
        if (problems.size() > shown) {
            text.append("\n\n...and ").append(problems.size() - shown).append(" more in the game log.");
        }
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server != null && !server.isDedicatedServer()) {
            pendingMessage = text.toString();
            pendingServer = server;
            MinecraftForge.EVENT_BUS.register(this);
            return;
        }
        StartupQuery.notify(text.toString());
        StartupQuery.abort();
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (pendingMessage == null || !(event.player instanceof EntityPlayerMP)) {
            return;
        }
        String message = pendingMessage;
        MinecraftServer stoppedServer = pendingServer;
        pendingMessage = null;
        pendingServer = null;
        MinecraftForge.EVENT_BUS.unregister(this);
        if (((EntityPlayerMP) event.player).getServer() != stoppedServer) {
            // The world that was stopped ended before its player joined; this is another world.
            return;
        }
        logger.info(StartReport.PREFIX + "disconnecting {} with the configuration errors", event.player.getName());
        ((EntityPlayerMP) event.player).connection.disconnect(new TextComponentString(message));
    }
}
