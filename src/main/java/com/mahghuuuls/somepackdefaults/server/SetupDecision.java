package com.mahghuuuls.somepackdefaults.server;

import com.mahghuuuls.somepackdefaults.config.Entry;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * What counts as initial server setup (ARC-002), in one place: a dedicated server is at initial
 * server setup only when it has no setup record and no world under any name the world can have on
 * this start. A world counts only when its folder holds {@code level.dat}, so an empty
 * folder left by a stopped first start, or made by a hosting panel, is not a world.
 */
final class SetupDecision {

    /** The world name vanilla uses when {@code server.properties} has no {@code level-name}. */
    static final String VANILLA_LEVEL_NAME = "world";

    /** The outcome, with the reason Default server lines are skipped when there is one. */
    enum State {
        INITIAL(null),
        EXISTING_SETUP("the setup already exists"),
        EXISTING_WORLD("the world already exists");

        private final String skipReason;

        State(String skipReason) {
            this.skipReason = skipReason;
        }

        String skipReason() {
            return skipReason;
        }
    }

    private SetupDecision() {
    }

    static State decide(boolean recordExists, boolean worldExists) {
        if (recordExists) {
            return State.EXISTING_SETUP;
        }
        return worldExists ? State.EXISTING_WORLD : State.INITIAL;
    }

    /**
     * Every name the world can have on this start, in the order they are checked: the value in
     * {@code server.properties} (or vanilla's {@code world} when it has none), the pack's Default
     * entry, the pack's Reapply entry, and the {@code --world} start option's name when one was
     * given. Checking all of them keeps a pack, or a start option, that names another world from
     * making an old server look new.
     */
    static Set<String> worldNames(String fileValue, Entry defaultLevelName, Entry reapplyLevelName, String startOptionName) {
        Set<String> names = new LinkedHashSet<String>();
        names.add(fileValue == null ? VANILLA_LEVEL_NAME : fileValue);
        if (defaultLevelName != null) {
            names.add(defaultLevelName.value());
        }
        if (reapplyLevelName != null) {
            names.add(reapplyLevelName.value());
        }
        if (startOptionName != null) {
            names.add(startOptionName);
        }
        return Collections.unmodifiableSet(names);
    }
}
