package com.mahghuuuls.somepackdefaults.world;

import com.mahghuuuls.somepackdefaults.config.Entry;
import com.mahghuuuls.somepackdefaults.config.EntryFiles;
import com.mahghuuuls.somepackdefaults.config.EntrySet;
import com.mahghuuuls.somepackdefaults.config.Mode;
import com.mahghuuuls.somepackdefaults.config.SettingCategory;
import com.mahghuuuls.somepackdefaults.report.ForgeStartBlocker;
import com.mahghuuuls.somepackdefaults.report.StartBlocker;
import com.mahghuuuls.somepackdefaults.report.StartReport;
import com.mahghuuuls.somepackdefaults.rules.EntryChecker;
import com.mahghuuuls.somepackdefaults.rules.GameruleLookup;
import com.mahghuuuls.somepackdefaults.rules.Problem;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerAboutToStartEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * The gamerule side of every start: a dedicated-server start and every singleplayer world open.
 * Gamerules from other mods exist only once a world has loaded and those mods have added them, so
 * the check and the apply run at the first server tick, the first point after every mod's
 * world-load and server-start handlers where a stop is still clean and before any player can act.
 * The tick listener removes itself in that same call.
 */
public final class WorldPhase {

    private final StartBlocker blocker;
    private final Logger logger;

    private EntryFiles files;
    private MinecraftServer server;
    private StartReport report = new StartReport();
    /** This start's entries, or null when the files could not be read. */
    private EntrySet entries;
    private Problem readProblem;

    public WorldPhase(Logger logger) {
        this(new ForgeStartBlocker(logger), logger);
    }

    WorldPhase(StartBlocker blocker, Logger logger) {
        this.blocker = blocker;
        this.logger = logger;
    }

    public StartReport report() {
        return report;
    }

    /** The gamerules every vanilla world has, for the check made before any world loads. */
    public static GameruleLookup vanillaGamerules() {
        return MinecraftGamerules.vanilla();
    }

    /**
     * On the client, creates missing configuration files at game launch. On a dedicated server,
     * reads the entries for this start; their check before any world loads is made together with
     * the server lines, so one stop reports every problem.
     */
    public void onPreInit(FMLPreInitializationEvent event) {
        files = EntryFiles.inConfigFolder(event.getModConfigurationDirectory().toPath());
        if (!event.getSide().isServer()) {
            try {
                files.createMissingFiles();
            } catch (IOException e) {
                logger.error(StartReport.PREFIX + "could not create the configuration files in {}", files.folder(), e);
            }
            return;
        }
        readEntries();
    }

    /** Arms the first-tick step for this start. A singleplayer world open reads the files again, so edits apply at the next open. */
    public void onServerAboutToStart(FMLServerAboutToStartEvent event) {
        server = event.getServer();
        if (!server.isDedicatedServer()) {
            report = new StartReport();
            readEntries();
        }
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        MinecraftForge.EVENT_BUS.unregister(this);
        logger.info(StartReport.PREFIX + "first server tick reached; the tick listener is removed");
        firstTick(entries, new MinecraftGamerules(server.getWorld(0).getGameRules()));
    }

    /** Checks every gamerule entry against the loaded world, then sets the Reapply gamerules, or stops. */
    void firstTick(EntrySet set, GameruleTarget world) {
        if (set == null) {
            Problem problem = readProblem != null ? readProblem
                    : new Problem("config/somepackdefaults", 0, null, "was not read for this start", null);
            stopAfterLoad(Collections.singletonList(problem));
            return;
        }
        List<Problem> problems = EntryChecker.checkGamerules(set, world);
        if (!problems.isEmpty()) {
            stopAfterLoad(problems);
            return;
        }
        for (Entry entry : set.entries(SettingCategory.GAMERULE, Mode.DEFAULT)) {
            report.notApplied(entry, "Default gamerules are not supported in this build yet");
        }
        for (Entry entry : set.entries(SettingCategory.GAMERULE, Mode.REAPPLY)) {
            world.set(entry.name(), entry.value());
            report.applied(entry);
        }
        report.log(logger, SettingCategory.GAMERULE);
    }

    private void readEntries() {
        try {
            entries = files.read();
            readProblem = null;
        } catch (IOException e) {
            entries = null;
            readProblem = new Problem(files.folder().toString(), 0, null, "could not be read (" + e + ")", null);
        }
    }

    private void stopAfterLoad(List<Problem> problems) {
        report.problems(SettingCategory.GAMERULE, problems);
        report.log(logger, SettingCategory.GAMERULE);
        blocker.stopAfterWorldLoad(problems);
    }
}
