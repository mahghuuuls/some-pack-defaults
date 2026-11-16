package com.mahghuuuls.somepackdefaults.config;

/**
 * Tells server lines from gamerules by name, so one file can hold both. The line list belongs to
 * {@code rules}; this interface lets the files tag each entry without depending on it.
 */
public interface NameKinds {

    /** True for a vanilla server line, host lines included; every other name is a gamerule. */
    boolean isServerLine(String name);
}
