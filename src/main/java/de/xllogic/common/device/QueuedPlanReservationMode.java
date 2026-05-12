package de.xllogic.common.device;

import java.util.Locale;

public enum QueuedPlanReservationMode {
    FULL_QUEUE("full_queue"),
    ACTIVE_CYCLE("active_cycle");

    private final String serializedName;

    QueuedPlanReservationMode(final String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return this.serializedName;
    }

    public boolean reservesFullQueue() {
        return this == FULL_QUEUE;
    }

    public static QueuedPlanReservationMode fromSerializedName(final String rawName) {
        if (rawName != null && !rawName.isBlank()) {
            final String normalized = rawName.trim().toLowerCase(Locale.ROOT);
            for (final QueuedPlanReservationMode mode : values()) {
                if (mode.serializedName.equals(normalized)) {
                    return mode;
                }
            }
        }
        return FULL_QUEUE;
    }
}