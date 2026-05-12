package de.xllogic.client.screen;

import de.xllogic.common.network.payload.RecoveryDraftResumeStatus;

final class ComputerSessionLossPolicy {
    static final int TARGET_UNAVAILABLE_GRACE_TICKS = 200;

    private ComputerSessionLossPolicy() {
    }

    enum PersistentUnavailableAction {
        WAIT,
        AUTO_CLOSE,
        OPEN_RECOVERY_DRAFT
    }

    static PersistentUnavailableAction resolvePersistentUnavailableAction(final int unavailableTicks, final boolean recoveryResumeEligible) {
        if (unavailableTicks < TARGET_UNAVAILABLE_GRACE_TICKS) {
            return PersistentUnavailableAction.WAIT;
        }
        return recoveryResumeEligible ? PersistentUnavailableAction.OPEN_RECOVERY_DRAFT : PersistentUnavailableAction.AUTO_CLOSE;
    }

    static String unavailableStatusLine(final String sessionMessage, final int unavailableTicks, final boolean recoveryResumeEligible) {
        return baseUnavailableMessage(sessionMessage) + " " + timeoutLabel(unavailableTicks, recoveryResumeEligible);
    }

    static String unavailableHeaderLine(final int unavailableTicks, final boolean recoveryResumeEligible) {
        return recoveryResumeEligible
                ? "Target unavailable  |  recovery draft in " + remainingSecondsText(unavailableTicks) + "s  |  Ctrl+PgUp/PgDn output"
                : "Target unavailable  |  auto-close in " + remainingSecondsText(unavailableTicks) + "s  |  Ctrl+PgUp/PgDn output";
    }

    static String unavailableTransitionMessage(final String sessionMessage, final boolean recoveryResumeEligible) {
        return baseUnavailableMessage(sessionMessage) + " " + (recoveryResumeEligible
                ? "A local recovery draft opens automatically if the target stays unavailable for " + graceSecondsText() + "s."
                : "This screen closes automatically if the target stays unavailable for " + graceSecondsText() + "s.");
    }

    static String persistentLossMessage(final boolean recoveryResumeEligible) {
        return recoveryResumeEligible
                ? "Target stayed unavailable. Switched to a local recovery draft and will resume automatically when the computer is back."
                : "Target stayed unavailable. Closed the bound computer screen.";
    }

    static String recoveryDraftStatusLine(final String resumeMessage) {
        if (resumeMessage == null || resumeMessage.isBlank()) {
            return "Recovery draft: waiting to resume on the original computer";
        }
        return resumeMessage;
    }

    static String recoveryDraftHeaderLine(final RecoveryDraftResumeStatus status) {
        if (status == RecoveryDraftResumeStatus.DIVERGED) {
            return "Conflict compare  |  Alt+Up/Down hunk  |  Ctrl+Right server  |  Ctrl+Enter publish";
        }
        return status == RecoveryDraftResumeStatus.BLOCKED_BY_OTHER_EDITOR
                ? "Recovery draft  |  resume blocked, retrying  |  F5 local run"
                : "Recovery draft  |  auto-resume when target returns  |  F5 local run";
    }

    static int remainingTicks(final int unavailableTicks) {
        return Math.max(0, TARGET_UNAVAILABLE_GRACE_TICKS - unavailableTicks);
    }

    private static String timeoutLabel(final int unavailableTicks, final boolean recoveryResumeEligible) {
        return recoveryResumeEligible
                ? "Recovery draft in " + remainingSecondsText(unavailableTicks) + "s."
                : "Auto-close in " + remainingSecondsText(unavailableTicks) + "s.";
    }

    private static String baseUnavailableMessage(final String sessionMessage) {
        if (sessionMessage == null || sessionMessage.isBlank()) {
            return "Computer session target is unavailable.";
        }
        return sessionMessage;
    }

    private static String remainingSecondsText(final int unavailableTicks) {
        return Integer.toString((remainingTicks(unavailableTicks) + 19) / 20);
    }

    private static String graceSecondsText() {
        return Integer.toString((TARGET_UNAVAILABLE_GRACE_TICKS + 19) / 20);
    }
}