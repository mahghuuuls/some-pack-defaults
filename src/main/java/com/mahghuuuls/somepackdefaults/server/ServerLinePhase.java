package com.mahghuuuls.somepackdefaults.server;

import com.mahghuuuls.somepackdefaults.config.Entry;
import com.mahghuuuls.somepackdefaults.config.EntryFiles;
import com.mahghuuuls.somepackdefaults.config.EntrySet;
import com.mahghuuuls.somepackdefaults.config.Mode;
import com.mahghuuuls.somepackdefaults.config.SettingCategory;
import com.mahghuuuls.somepackdefaults.report.ForgeStartBlocker;
import com.mahghuuuls.somepackdefaults.report.StartBlocker;
import com.mahghuuuls.somepackdefaults.report.StartReport;
import com.mahghuuuls.somepackdefaults.rules.EntryChecker;
import com.mahghuuuls.somepackdefaults.rules.Problem;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

/**
 * The server-line side of a dedicated-server start. It acts only on a dedicated server, at
 * pre-initialization, before vanilla reads {@code server.properties}, so every line it writes takes
 * effect on this same start. It checks every server-line entry first and writes nothing when any is
 * invalid. The instance lives for the whole start and keeps the file it wrote, so a later check can
 * undo the write.
 */
public final class ServerLinePhase {

    private final Path serverProperties;
    private final StartReport report;
    private final StartBlocker blocker;
    private final Logger logger;

    private ServerPropertiesFile written;

    public ServerLinePhase(Logger logger) {
        // Vanilla reads server.properties from the server's working folder.
        this(Paths.get("server.properties"), new StartReport(), new ForgeStartBlocker(logger), logger);
    }

    ServerLinePhase(Path serverProperties, StartReport report, StartBlocker blocker, Logger logger) {
        this.serverProperties = serverProperties;
        this.report = report;
        this.blocker = blocker;
        this.logger = logger;
    }

    public StartReport report() {
        return report;
    }

    public void onPreInit(FMLPreInitializationEvent event) {
        if (!event.getSide().isServer()) {
            return;
        }
        apply(EntryFiles.inConfigFolder(event.getModConfigurationDirectory().toPath()));
    }

    /** Checks the server-line entries and writes the lines, or stops the server. */
    void apply(EntryFiles files) {
        EntrySet set;
        try {
            set = files.read();
        } catch (IOException e) {
            stop(Collections.singletonList(new Problem(files.folder().toString(), 0, null, "could not be read (" + e + ")", null)));
            return;
        }
        List<Problem> problems = EntryChecker.checkServerLines(set);
        if (!problems.isEmpty()) {
            stop(problems);
            return;
        }
        try {
            written = ServerPropertiesFile.open(serverProperties);
        } catch (IOException e) {
            stop(Collections.singletonList(new Problem(serverProperties.toString(), 0, null, "could not be read: " + e.getMessage(), null)));
            return;
        }
        for (Entry entry : set.entries(SettingCategory.SERVER_LINE, Mode.DEFAULT)) {
            report.notApplied(entry, "Default server lines are not supported in this build yet");
        }
        for (Entry entry : set.entries(SettingCategory.SERVER_LINE, Mode.REAPPLY)) {
            written.set(entry.name(), entry.value());
            report.applied(entry);
        }
        try {
            written.save();
        } catch (IOException e) {
            restoreAfterFailedWrite();
            stop(Collections.singletonList(new Problem(serverProperties.toString(), 0, null, "could not be written (" + e + ")", null)));
            return;
        }
        report.log(logger, SettingCategory.SERVER_LINE);
    }

    private void restoreAfterFailedWrite() {
        try {
            written.restore();
        } catch (IOException e) {
            logger.error(StartReport.PREFIX + "could not restore {} after a failed write", serverProperties, e);
        }
    }

    private void stop(List<Problem> problems) {
        report.problems(SettingCategory.SERVER_LINE, problems);
        report.log(logger, SettingCategory.SERVER_LINE);
        blocker.stopBeforeWorldLoad(problems);
    }
}
