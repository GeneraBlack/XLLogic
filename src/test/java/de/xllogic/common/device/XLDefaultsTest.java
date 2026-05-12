package de.xllogic.common.device;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class XLDefaultsTest {
    @Test
    void migratesPreviousCooperativeStarterVariantToCurrentStarter() {
        final String previousStarter = XLDefaults.PRE_BEGINNER_STARTER_SCRIPT;

        assertEquals(XLDefaults.STARTER_SCRIPT, XLDefaults.migrateBundledStarterScript(previousStarter));
    }

    @Test
    void starterScriptMentionsNamedScreenTargets() {
        assertTrue(XLDefaults.STARTER_SCRIPT.contains("left_panel = get_device(\"left_panel\")"),
                "expected starter script to mention named screen targets");
        assertTrue(XLDefaults.STARTER_SCRIPT.contains("right_panel.table(\"Devices\""),
                "expected starter script to include a second named screen example");
    }
}