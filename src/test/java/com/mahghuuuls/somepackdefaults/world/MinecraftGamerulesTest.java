package com.mahghuuuls.somepackdefaults.world;

import com.mahghuuuls.somepackdefaults.rules.GameruleLookup.Kind;
import net.minecraft.world.GameRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The adapter over Minecraft's own GameRules class, with its real vanilla rules. */
class MinecraftGamerulesTest {

    @Test
    void vanillaKinds() {
        MinecraftGamerules vanilla = MinecraftGamerules.vanilla();
        assertEquals(Kind.TRUE_OR_FALSE, vanilla.kindOf("keepInventory"));
        assertEquals(Kind.WHOLE_NUMBER, vanilla.kindOf("randomTickSpeed"));
        assertEquals(Kind.TEXT, vanilla.kindOf("gameLoopFunction"));
        assertNull(vanilla.kindOf("keepInventroy"));
    }

    @Test
    void aRuleReadBackFromASaveWithoutItsModIsText() {
        GameRules rules = new GameRules();
        rules.setOrCreateGameRule("modFeature", "on");
        assertEquals(Kind.TEXT, new MinecraftGamerules(rules).kindOf("modFeature"));
    }

    @Test
    void setChangesAnExistingRuleAndNeverCreatesOne() {
        GameRules rules = new GameRules();
        MinecraftGamerules world = new MinecraftGamerules(rules);
        world.set("keepInventory", "true");
        assertEquals("true", rules.getString("keepInventory"));
        world.set("keepInventroy", "true");
        assertFalse(rules.hasRule("keepInventroy"));
    }
}
