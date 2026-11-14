package com.mahghuuuls.somepackdefaults.world;

import com.mahghuuuls.somepackdefaults.report.StartReport;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

/**
 * Removes a new-world mark at the first save of its world after the Default gamerules were set.
 * The gamerules are durable only once {@code level.dat} is saved, and vanilla posts the save event
 * after writing it; removing the mark earlier would lose the Defaults if the process died first.
 * Listens once and only for the overworld it was made for.
 */
public final class MarkRemovalOnSave {

    private final NewWorldMark mark;
    private final World overworld;
    private final Logger logger;

    MarkRemovalOnSave(NewWorldMark mark, World overworld, Logger logger) {
        this.mark = mark;
        this.overworld = overworld;
        this.logger = logger;
    }

    @SubscribeEvent
    public void onWorldSave(WorldEvent.Save event) {
        if (event.getWorld() != overworld) {
            return;
        }
        MinecraftForge.EVENT_BUS.unregister(this);
        remove(mark, logger);
    }

    static void remove(NewWorldMark mark, Logger logger) {
        try {
            mark.remove();
            logger.info(StartReport.PREFIX + "removed the new-world mark {}; the world was saved with its Default gamerules", mark.path());
        } catch (IOException e) {
            logger.error(StartReport.PREFIX + "could not remove the new-world mark {}; the next open applies Default gamerules again", mark.path(), e);
        }
    }
}
