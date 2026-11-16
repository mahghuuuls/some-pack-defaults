package com.mahghuuuls.somepackdefaults.world;

import com.mahghuuuls.somepackdefaults.config.Entry;
import com.mahghuuuls.somepackdefaults.config.EntryFiles;
import com.mahghuuuls.somepackdefaults.config.EntrySet;
import com.mahghuuuls.somepackdefaults.config.SettingCategory;
import com.mahghuuuls.somepackdefaults.report.ForgeStartBlocker;
import com.mahghuuuls.somepackdefaults.report.StartBlocker;
import com.mahghuuuls.somepackdefaults.report.StartReport;
import com.mahghuuuls.somepackdefaults.rules.EntryChecker;
import com.mahghuuuls.somepackdefaults.rules.EntryResolution;
import com.mahghuuuls.somepackdefaults.rules.GameruleLookup;
import com.mahghuuuls.somepackdefaults.rules.Problem;
import com.mahghuuuls.somepackdefaults.rules.ServerLineTable;
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
 * the check and the set run at the first server tick, the first point after every mod's
 * world-load and server-start handlers where a stop is still clean and before any player can act.
 * New and existing worlds are handled the same: every effective gamerule is set at every start.
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
     * Creates the missing files at game or server launch, before anything reads them: the root
     * class calls this before the server-line phase, so a player file made from the pack's
     * suggestions counts on the first start. A failure is logged and the start goes on with the
     * files that exist. On a dedicated server, also reads the entries for this start; their check
     * before any world loads is made together with the server lines, so one stop reports every
     * problem.
     */
    public void onPreInit(FMLPreInitializationEvent event) {
        files = EntryFiles.inConfigFolder(event.getModConfigurationDirectory().toPath(), ServerLineTable.NAME_KINDS);
        createMissingFiles();
        if (event.getSide().isServer()) {
            readEntries();
        }
    }

    void createMissingFiles() {
        try {
            for (String line : StartReport.creationLines(files.createMissingFiles())) {
                logger.info(StartReport.PREFIX + line);
            }
        } catch (IOException e) {
            logger.error(StartReport.PREFIX + "could not create the missing files in {}; the files that exist are used", files.folder(), e);
        }
    }

    /** Arms the first-tick step for this start. A singleplayer world open reads the files again, so edits apply at the next open. */
    public void onServerAboutToStart(FMLServerAboutToStartEvent event) {
        server = event.getServer();
        if (!server.isDedicatedServer()) {
            report = new StartReport();
            readEntries();
            reportServerLinesInSingleplayer(entries);
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

    /**
     * Checks the lines of both files and every gamerule entry against the loaded world, then sets
     * every effective gamerule and reports the player entries the pack file overrides; or stops,
     * with no gamerule set.
     */
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
        EntryResolution resolution = EntryResolution.of(set);
        for (Entry entry : resolution.effective(SettingCategory.GAMERULE)) {
            world.set(entry.name(), entry.value());
            report.applied(entry);
        }
        for (Entry entry : resolution.overridden(SettingCategory.GAMERULE)) {
            report.overridden(entry);
        }
        report.log(logger, SettingCategory.GAMERULE);
    }

    /**
     * Singleplayer has no {@code server.properties} to write, so no server line is used; the server
     * lines are still checked, and their errors are reported without stopping the world.
     */
    void reportServerLinesInSingleplayer(EntrySet set) {
        if (set == null) {
            return;
        }
        for (Entry entry : set.entries(SettingCategory.SERVER_LINE)) {
            report.notApplied(entry, "not used in singleplayer");
        }
        report.problems(SettingCategory.SERVER_LINE, EntryChecker.checkServerLines(set));
        report.log(logger, SettingCategory.SERVER_LINE);
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
