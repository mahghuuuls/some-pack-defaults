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
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerAboutToStartEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.time.Instant;
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
    /** Told when a start's first tick succeeded; this class does not know what the listener does with it. */
    private final Runnable afterSuccessfulFirstTick;

    private EntryFiles files;
    private MinecraftServer server;
    private StartReport report = new StartReport();
    /** This start's entries, or null when the files could not be read. */
    private EntrySet entries;
    private Problem readProblem;
    /** True once vanilla ran first-time setup for this start's overworld. */
    private boolean createdThisStart;

    public WorldPhase(Logger logger, Runnable afterSuccessfulFirstTick) {
        this(new ForgeStartBlocker(logger), logger, afterSuccessfulFirstTick);
    }

    WorldPhase(StartBlocker blocker, Logger logger, Runnable afterSuccessfulFirstTick) {
        this.blocker = blocker;
        this.logger = logger;
        this.afterSuccessfulFirstTick = afterSuccessfulFirstTick;
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
        createdThisStart = false;
        if (!server.isDedicatedServer()) {
            report = new StartReport();
            readEntries();
            reportServerLinesInSingleplayer();
        }
        MinecraftForge.EVENT_BUS.register(this);
    }

    /**
     * Vanilla's first-time setup of an overworld: it runs only for a world that was never
     * initialized, so a copied or restored world never gets here. Received first and also when
     * another mod cancels the event, because only the fact that it fired matters.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void onNewOverworld(WorldEvent.CreateSpawnPosition event) {
        if (event.getWorld().provider.getDimension() == 0) {
            overworldCreated();
        }
    }

    void overworldCreated() {
        createdThisStart = true;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        MinecraftForge.EVENT_BUS.unregister(this);
        logger.info(StartReport.PREFIX + "first server tick reached; the tick listener is removed");
        final WorldServer overworld = server.getWorld(0);
        final NewWorldMark mark = NewWorldMark.in(overworld.getSaveHandler().getWorldDirectory().toPath());
        firstTick(entries, new MinecraftGamerules(overworld.getGameRules()), mark,
                () -> MinecraftForge.EVENT_BUS.register(new MarkRemovalOnSave(mark, overworld, logger)));
    }

    /** As in the game, with the mark removed at once, as if the world were saved right after. */
    void firstTick(EntrySet set, GameruleTarget world, NewWorldMark mark) {
        firstTick(set, world, mark, () -> MarkRemovalOnSave.remove(mark, logger));
    }

    /**
     * Checks every gamerule entry against the loaded world, then sets the Default gamerules when
     * the world is new and the Reapply gamerules always, and reports the start as successful; or
     * stops, keeping a new world new through the mark. A mark that led to the Defaults is removed
     * by {@code removeMarkAfterSave}, at the world's next save, when the Defaults are on disk.
     */
    void firstTick(EntrySet set, GameruleTarget world, NewWorldMark mark, Runnable removeMarkAfterSave) {
        boolean newWorld = isNew(mark);
        if (set == null) {
            Problem problem = readProblem != null ? readProblem
                    : new Problem("config/somepackdefaults", 0, null, "was not read for this start", null);
            stopAfterLoad(Collections.singletonList(problem), newWorld, mark);
            return;
        }
        List<Problem> problems = EntryChecker.checkGamerules(set, world);
        if (!problems.isEmpty()) {
            stopAfterLoad(problems, newWorld, mark);
            return;
        }
        for (Entry entry : set.entries(SettingCategory.GAMERULE, Mode.DEFAULT)) {
            if (newWorld) {
                world.set(entry.name(), entry.value());
                report.applied(entry);
            } else {
                report.skipped(entry, "the world already exists");
            }
        }
        for (Entry entry : set.entries(SettingCategory.GAMERULE, Mode.REAPPLY)) {
            world.set(entry.name(), entry.value());
            report.applied(entry);
        }
        if (newWorld && mark.exists()) {
            removeMarkAfterSave.run();
        }
        afterSuccessfulFirstTick.run();
        report.log(logger, SettingCategory.GAMERULE);
    }

    /** The one place that decides whether this world is new: created on this open, or marked by a stopped first open. */
    private boolean isNew(NewWorldMark mark) {
        if (createdThisStart) {
            logger.info(StartReport.PREFIX + "new world: created on this start; Default gamerules apply");
            return true;
        }
        if (mark.exists()) {
            logger.info(StartReport.PREFIX + "new world: its first open was stopped ({} exists); Default gamerules apply", mark.path());
            return true;
        }
        logger.info(StartReport.PREFIX + "existing world: Default gamerules are skipped");
        return false;
    }

    /**
     * Singleplayer has no {@code server.properties} to write, so no server line is used; the two
     * server files are still checked, and their errors are reported without stopping the world.
     */
    private void reportServerLinesInSingleplayer() {
        reportServerLinesInSingleplayer(entries);
    }

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

    private void stopAfterLoad(List<Problem> problems, boolean newWorld, NewWorldMark mark) {
        if (newWorld) {
            try {
                mark.write(Instant.now());
                logger.info(StartReport.PREFIX + "wrote the new-world mark {}; this world stays new until its Default gamerules are applied", mark.path());
            } catch (IOException e) {
                logger.error(StartReport.PREFIX + "could not write the new-world mark {}; the next open counts this world as existing", mark.path(), e);
            }
        }
        report.problems(SettingCategory.GAMERULE, problems);
        report.log(logger, SettingCategory.GAMERULE);
        blocker.stopAfterWorldLoad(problems);
    }
}
