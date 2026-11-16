package com.mahghuuuls.somepackdefaults.command;

import com.mahghuuuls.somepackdefaults.report.StartReport;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InspectCommandTest {

    @Test
    void onlyOperatorsAndTheConsoleMayInspect() {
        InspectCommand command = new InspectCommand(() -> Collections.<StartReport>emptyList());
        assertEquals("somepackdefaults", command.getName());
        assertEquals(2, command.getRequiredPermissionLevel());
    }
}
