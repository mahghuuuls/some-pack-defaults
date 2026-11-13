package com.mahghuuuls.somepackdefaults.report;

import com.mahghuuuls.somepackdefaults.rules.Problem;
import net.minecraftforge.fml.common.FMLCommonHandler;
import org.apache.logging.log4j.Logger;

import java.util.List;

/**
 * Stops starts in the real game. Before a world loads on a dedicated server the process ends
 * through Forge's exit call: an exception thrown from a mod's lifecycle event would turn into a
 * crash report, and nothing has been written yet that needs a normal shutdown.
 */
public final class ForgeStartBlocker implements StartBlocker {

    private final Logger logger;

    public ForgeStartBlocker(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void stopBeforeWorldLoad(List<Problem> problems) {
        logger.error(StartReport.PREFIX + "stopping the server: {} configuration error(s), listed above. Fix them and start again.", problems.size());
        FMLCommonHandler.instance().exitJava(1, false);
    }
}
