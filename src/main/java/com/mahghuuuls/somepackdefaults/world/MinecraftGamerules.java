package com.mahghuuuls.somepackdefaults.world;

import net.minecraft.world.GameRules;

/**
 * A world's {@code GameRules}, or a fresh instance for the vanilla set. All dimensions of a save
 * share the overworld's instance, so one instance is the whole save's gamerules.
 */
final class MinecraftGamerules implements GameruleTarget {

    private final GameRules rules;

    MinecraftGamerules(GameRules rules) {
        this.rules = rules;
    }

    /** The gamerules vanilla 1.12.2 creates for every world, with their kinds. */
    static MinecraftGamerules vanilla() {
        return new MinecraftGamerules(new GameRules());
    }

    @Override
    public Kind kindOf(String name) {
        if (!rules.hasRule(name)) {
            return null;
        }
        if (rules.areSameType(name, GameRules.ValueType.BOOLEAN_VALUE)) {
            return Kind.TRUE_OR_FALSE;
        }
        if (rules.areSameType(name, GameRules.ValueType.NUMERICAL_VALUE)) {
            return Kind.WHOLE_NUMBER;
        }
        // Text, functions, and mod-added rules read back from level.dat without their mod's declared type.
        return Kind.TEXT;
    }

    @Override
    public void set(String name, String value) {
        if (rules.hasRule(name)) {
            rules.setOrCreateGameRule(name, value);
        }
    }
}
