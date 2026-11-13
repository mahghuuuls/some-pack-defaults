package com.mahghuuuls.somepackdefaults;

import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Entry point. The mod runs on the logical server only and registers nothing that clients
 * must have, so clients without it can join a server that runs it.
 */
@Mod(modid = Tags.MOD_ID, name = Tags.MOD_NAME, version = Tags.VERSION, acceptableRemoteVersions = "*")
public class SomePackDefaultsMod {

    public static final Logger LOGGER = LogManager.getLogger(Tags.MOD_NAME);

}
