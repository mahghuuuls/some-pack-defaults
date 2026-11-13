package com.mahghuuuls.somepackdefaults.world;

import com.mahghuuuls.somepackdefaults.rules.GameruleLookup;

/** The gamerules of one loaded world: what exists, and setting a value that exists. */
interface GameruleTarget extends GameruleLookup {

    /** Sets an existing gamerule. Never called for a name {@link #kindOf} does not know. */
    void set(String name, String value);
}
