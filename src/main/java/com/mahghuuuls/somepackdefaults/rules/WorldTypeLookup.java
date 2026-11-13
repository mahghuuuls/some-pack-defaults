package com.mahghuuuls.somepackdefaults.rules;

import java.util.List;

/** The world types the running game knows, including those other mods registered. */
public interface WorldTypeLookup {

    /** True when the server would find this name, the same way it reads {@code level-type}. */
    boolean isKnown(String name);

    /** The names the game knows, for messages. */
    List<String> knownNames();
}
