package com.mahghuuuls.somepackdefaults.server;

import com.mahghuuuls.somepackdefaults.config.Entry;
import com.mahghuuuls.somepackdefaults.config.Mode;
import com.mahghuuuls.somepackdefaults.config.SettingCategory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The initial-setup rule over its two facts, and the world names it checks. */
class SetupDecisionTest {

    @Test
    void onlyNoRecordAndNoWorldIsInitialSetup() {
        assertEquals(SetupDecision.State.INITIAL, SetupDecision.decide(false, false));
        assertEquals(SetupDecision.State.EXISTING_WORLD, SetupDecision.decide(false, true));
        assertEquals(SetupDecision.State.EXISTING_SETUP, SetupDecision.decide(true, false));
        assertEquals(SetupDecision.State.EXISTING_SETUP, SetupDecision.decide(true, true));
    }

    @Test
    void eachExistingStateHasItsSkipReason() {
        assertNull(SetupDecision.State.INITIAL.skipReason());
        assertEquals("the setup already exists", SetupDecision.State.EXISTING_SETUP.skipReason());
        assertEquals("the world already exists", SetupDecision.State.EXISTING_WORLD.skipReason());
    }

    private static Entry levelName(Mode mode, String value) {
        return new Entry(SettingCategory.SERVER_LINE, mode, "level-name", value, 1);
    }

    @Test
    void withoutAFileValueVanillasWorldIsChecked() {
        assertEquals(Arrays.asList("world"), new ArrayList<String>(SetupDecision.worldNames(null, null, null, null)));
    }

    @Test
    void theFileValueTheDefaultTheReapplyAndTheStartOptionNamesAreAllChecked() {
        assertEquals(Arrays.asList("old", "packworld", "reworld", "optionworld"), new ArrayList<String>(SetupDecision.worldNames("old",
                levelName(Mode.DEFAULT, "packworld"), levelName(Mode.REAPPLY, "reworld"), "optionworld")));
    }

    @Test
    void aNameIsCheckedOnce() {
        assertEquals(Arrays.asList("world", "packworld"), new ArrayList<String>(SetupDecision.worldNames(null,
                levelName(Mode.DEFAULT, "world"), levelName(Mode.REAPPLY, "packworld"), "world")));
    }
}
