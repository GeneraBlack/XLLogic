package de.xllogic.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.xllogic.common.network.payload.RecoveryDraftResumeStatus;
import org.junit.jupiter.api.Test;

class ComputerSessionLossPolicyTest {
    @Test
    void waitsBeforeGraceTimeoutExpires() {
        assertEquals(
                ComputerSessionLossPolicy.PersistentUnavailableAction.WAIT,
                ComputerSessionLossPolicy.resolvePersistentUnavailableAction(ComputerSessionLossPolicy.TARGET_UNAVAILABLE_GRACE_TICKS - 1, true));
    }

    @Test
    void opensRecoveryDraftForFormerEditorAfterGraceTimeout() {
        assertEquals(
                ComputerSessionLossPolicy.PersistentUnavailableAction.OPEN_RECOVERY_DRAFT,
                ComputerSessionLossPolicy.resolvePersistentUnavailableAction(ComputerSessionLossPolicy.TARGET_UNAVAILABLE_GRACE_TICKS, true));
    }

    @Test
    void autoClosesReadOnlyViewerAfterGraceTimeout() {
        assertEquals(
                ComputerSessionLossPolicy.PersistentUnavailableAction.AUTO_CLOSE,
                ComputerSessionLossPolicy.resolvePersistentUnavailableAction(ComputerSessionLossPolicy.TARGET_UNAVAILABLE_GRACE_TICKS, false));
    }

    @Test
    void messagesDescribeCountdownAndRecoveryPolicy() {
        assertTrue(ComputerSessionLossPolicy.unavailableHeaderLine(0, true).contains("recovery draft in 10s"));
        assertTrue(ComputerSessionLossPolicy.unavailableHeaderLine(0, false).contains("auto-close in 10s"));
        assertTrue(ComputerSessionLossPolicy.unavailableTransitionMessage("Target lost.", true).contains("local recovery draft"));
        assertTrue(ComputerSessionLossPolicy.persistentLossMessage(false).contains("Closed the bound computer screen"));
        assertTrue(ComputerSessionLossPolicy.recoveryDraftHeaderLine(RecoveryDraftResumeStatus.TARGET_UNAVAILABLE).contains("auto-resume"));
        assertTrue(ComputerSessionLossPolicy.recoveryDraftHeaderLine(RecoveryDraftResumeStatus.BLOCKED_BY_OTHER_EDITOR).contains("resume blocked"));
        assertTrue(ComputerSessionLossPolicy.recoveryDraftHeaderLine(RecoveryDraftResumeStatus.DIVERGED).contains("Ctrl+Right server"));
    }
}