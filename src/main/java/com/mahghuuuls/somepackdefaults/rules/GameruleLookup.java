package com.mahghuuuls.somepackdefaults.rules;

/** The gamerules a world has, with the kind of value each takes. */
public interface GameruleLookup {

    /** The kind of value a gamerule takes. */
    enum Kind {
        TRUE_OR_FALSE("true or false"),
        WHOLE_NUMBER("a whole number"),
        TEXT("any text");

        private final String allowed;

        Kind(String allowed) {
            this.allowed = allowed;
        }

        String allowed() {
            return allowed;
        }
    }

    /** The kind of the named gamerule, or null when the world has no gamerule with this name. */
    Kind kindOf(String name);
}
