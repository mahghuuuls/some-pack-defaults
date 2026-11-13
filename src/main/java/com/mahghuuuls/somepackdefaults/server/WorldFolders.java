package com.mahghuuuls.somepackdefaults.server;

/** The server's world root, and the world name its start options chose, when they chose one. */
interface WorldFolders {

    /** True when {@code <world root>/<levelName>/level.dat} is a file. */
    boolean hasLevelDat(String levelName);

    /**
     * The world name given by the server's {@code --world} start option, or null when none was
     * given. When it is given, vanilla loads that world and ignores {@code level-name}.
     */
    String startOptionWorldName();
}
