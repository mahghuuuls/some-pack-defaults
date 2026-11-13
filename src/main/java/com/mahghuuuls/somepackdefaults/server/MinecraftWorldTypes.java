package com.mahghuuuls.somepackdefaults.server;

import com.mahghuuuls.somepackdefaults.rules.WorldTypeLookup;
import net.minecraft.world.WorldType;

import java.util.ArrayList;
import java.util.List;

/** The game's world-type registry, matched by name exactly as the server matches {@code level-type}. */
final class MinecraftWorldTypes implements WorldTypeLookup {

    @Override
    public boolean isKnown(String name) {
        return WorldType.byName(name) != null;
    }

    /** Every registered name. Uses only methods present on the dedicated server: canBeCreated() is client-only. */
    @Override
    public List<String> knownNames() {
        List<String> names = new ArrayList<String>();
        for (WorldType type : WorldType.WORLD_TYPES) {
            if (type != null) {
                names.add(type.getName());
            }
        }
        return names;
    }
}
