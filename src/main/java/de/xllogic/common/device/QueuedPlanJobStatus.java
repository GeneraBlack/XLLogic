package de.xllogic.common.device;

import java.util.Locale;

public enum QueuedPlanJobStatus {
    IDLE("idle"),
    RESUMABLE("resumable"),
    BLOCKED("blocked"),
    FAILED("failed"),
    COMPLETED("completed");

    private final String serializedName;

    QueuedPlanJobStatus(final String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return this.serializedName;
    }

    public boolean allowsExecution() {
        return this == RESUMABLE;
    }

    public boolean requiresResume() {
        return this == BLOCKED || this == FAILED;
    }

    public boolean canAbort() {
        return this == RESUMABLE || this == BLOCKED || this == FAILED;
    }

    public static QueuedPlanJobStatus fromSerializedName(final String rawName) {
        if (rawName != null && !rawName.isBlank()) {
            final String normalized = rawName.trim().toLowerCase(Locale.ROOT);
            for (final QueuedPlanJobStatus status : values()) {
                if (status.serializedName.equals(normalized)) {
                    return status;
                }
            }
        }
        return IDLE;
    }
}