package de.xllogic.runtime;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record ComputerPlanJobSnapshot(
        String deviceApiName,
        int totalCycles,
        int remainingCycles,
        int cycleIndex,
        int stepIndex,
        int totalSteps,
        int remainingCrafts,
        String status,
        String reservationMode,
        String actionHint,
        String errorClass,
        String message,
        String inputRoute,
        String outputRoute,
        int reservableCycles,
        int reservableSteps,
        int blockedCycleIndex,
        int blockedStepIndex,
        List<ComputerPlanTrackedRouteSnapshot> trackedIntermediates) {
    private static final int MAX_STRING_LENGTH = 128;
    private static final int MAX_MESSAGE_LENGTH = 256;
    private static final int MAX_TRACKED_INTERMEDIATES = 32;
    private static final String TAG_RESERVATION_MODE = "ReservationMode";
    private static final String TAG_ACTION_HINT = "ActionHint";
    private static final String TAG_ERROR_CLASS = "ErrorClass";
    private static final String TAG_MESSAGE = "Message";
    private static final String TAG_INPUT_ROUTE = "InputRoute";
    private static final String TAG_OUTPUT_ROUTE = "OutputRoute";
    private static final String TAG_TRACKED_INTERMEDIATES = "TrackedIntermediates";
    private static final StreamCodec<ByteBuf, List<ComputerPlanTrackedRouteSnapshot>> TRACKED_INTERMEDIATES_CODEC =
            ComputerPlanTrackedRouteSnapshot.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_TRACKED_INTERMEDIATES));
    private static final ComputerPlanJobSnapshot EMPTY = new ComputerPlanJobSnapshot(
            "",
            0,
            0,
            0,
            0,
            0,
            0,
            "",
            "",
            "",
            "",
            "",
            "",
            "",
                0,
                0,
                0,
                0,
            List.of());

            public static final StreamCodec<ByteBuf, ComputerPlanJobSnapshot> STREAM_CODEC = StreamCodec.of(
                ComputerPlanJobSnapshot::encode,
                ComputerPlanJobSnapshot::decode
            );

    public ComputerPlanJobSnapshot {
        deviceApiName = limit(deviceApiName, MAX_STRING_LENGTH);
        totalCycles = Math.max(0, totalCycles);
        remainingCycles = Math.max(0, remainingCycles);
        cycleIndex = Math.max(0, cycleIndex);
        stepIndex = Math.max(0, stepIndex);
        totalSteps = Math.max(0, totalSteps);
        remainingCrafts = Math.max(0, remainingCrafts);
        status = limit(status, MAX_STRING_LENGTH);
        reservationMode = limit(reservationMode, MAX_STRING_LENGTH);
        actionHint = limit(actionHint, MAX_STRING_LENGTH);
        errorClass = limit(errorClass, MAX_STRING_LENGTH);
        message = limit(message, MAX_MESSAGE_LENGTH);
        inputRoute = limit(inputRoute, MAX_STRING_LENGTH);
        outputRoute = limit(outputRoute, MAX_STRING_LENGTH);
        reservableCycles = Math.max(0, reservableCycles);
        reservableSteps = Math.max(0, reservableSteps);
        blockedCycleIndex = Math.max(0, blockedCycleIndex);
        blockedStepIndex = Math.max(0, blockedStepIndex);
        trackedIntermediates = sanitizeTrackedIntermediates(trackedIntermediates);
    }

    public static ComputerPlanJobSnapshot empty() {
        return EMPTY;
    }

    public CompoundTag toTag() {
        final CompoundTag tag = new CompoundTag();
        tag.putString("DeviceApiName", this.deviceApiName);
        tag.putInt("TotalCycles", this.totalCycles);
        tag.putInt("RemainingCycles", this.remainingCycles);
        tag.putInt("CycleIndex", this.cycleIndex);
        tag.putInt("StepIndex", this.stepIndex);
        tag.putInt("TotalSteps", this.totalSteps);
        tag.putInt("RemainingCrafts", this.remainingCrafts);
        tag.putString("Status", this.status);
        tag.putString(TAG_RESERVATION_MODE, this.reservationMode);
        tag.putString(TAG_ACTION_HINT, this.actionHint);
        tag.putString(TAG_ERROR_CLASS, this.errorClass);
        tag.putString(TAG_MESSAGE, this.message);
        tag.putString(TAG_INPUT_ROUTE, this.inputRoute);
        tag.putString(TAG_OUTPUT_ROUTE, this.outputRoute);
        tag.putInt("ReservableCycles", this.reservableCycles);
        tag.putInt("ReservableSteps", this.reservableSteps);
        tag.putInt("BlockedCycleIndex", this.blockedCycleIndex);
        tag.putInt("BlockedStepIndex", this.blockedStepIndex);
        final ListTag trackedIntermediatesTag = new ListTag();
        for (final ComputerPlanTrackedRouteSnapshot trackedIntermediate : this.trackedIntermediates) {
            trackedIntermediatesTag.add(trackedIntermediate.toTag());
        }
        tag.put(TAG_TRACKED_INTERMEDIATES, trackedIntermediatesTag);
        return tag;
    }

    public static ComputerPlanJobSnapshot fromTag(final CompoundTag tag) {
        final List<ComputerPlanTrackedRouteSnapshot> trackedIntermediates;
        if (tag.contains(TAG_TRACKED_INTERMEDIATES, Tag.TAG_LIST)) {
            final ListTag trackedIntermediatesTag = tag.getList(TAG_TRACKED_INTERMEDIATES, Tag.TAG_COMPOUND);
            final ArrayList<ComputerPlanTrackedRouteSnapshot> snapshots = new ArrayList<>(Math.min(trackedIntermediatesTag.size(), MAX_TRACKED_INTERMEDIATES));
            for (int index = 0; index < trackedIntermediatesTag.size() && snapshots.size() < MAX_TRACKED_INTERMEDIATES; index++) {
                snapshots.add(ComputerPlanTrackedRouteSnapshot.fromTag(trackedIntermediatesTag.getCompound(index)));
            }
            trackedIntermediates = List.copyOf(snapshots);
        } else {
            trackedIntermediates = List.of();
        }

        return new ComputerPlanJobSnapshot(
                tag.getString("DeviceApiName"),
                tag.getInt("TotalCycles"),
                tag.getInt("RemainingCycles"),
                tag.getInt("CycleIndex"),
                tag.getInt("StepIndex"),
                tag.getInt("TotalSteps"),
                tag.getInt("RemainingCrafts"),
                tag.getString("Status"),
                tag.contains(TAG_RESERVATION_MODE) ? tag.getString(TAG_RESERVATION_MODE) : "",
                tag.contains(TAG_ACTION_HINT) ? tag.getString(TAG_ACTION_HINT) : "",
                tag.contains(TAG_ERROR_CLASS) ? tag.getString(TAG_ERROR_CLASS) : "",
                tag.contains(TAG_MESSAGE) ? tag.getString(TAG_MESSAGE) : "",
                tag.contains(TAG_INPUT_ROUTE) ? tag.getString(TAG_INPUT_ROUTE) : "",
                tag.contains(TAG_OUTPUT_ROUTE) ? tag.getString(TAG_OUTPUT_ROUTE) : "",
                tag.getInt("ReservableCycles"),
                tag.getInt("ReservableSteps"),
                tag.getInt("BlockedCycleIndex"),
                tag.getInt("BlockedStepIndex"),
                trackedIntermediates
        );
    }

    public boolean hasStatus() {
        return !this.status.isBlank();
    }

    public boolean hasReservationMode() {
        return !this.reservationMode.isBlank();
    }

    public boolean hasActionHint() {
        return !this.actionHint.isBlank();
    }

    public boolean hasErrorClass() {
        return !this.errorClass.isBlank();
    }

    public boolean hasMessage() {
        return !this.message.isBlank();
    }

    public boolean hasTrackedIntermediates() {
        return !this.trackedIntermediates.isEmpty();
    }

    public int trackedIntermediateTotal() {
        int total = 0;
        for (final ComputerPlanTrackedRouteSnapshot trackedIntermediate : this.trackedIntermediates) {
            total += trackedIntermediate.expectedCount();
        }
        return total;
    }

    private static List<ComputerPlanTrackedRouteSnapshot> sanitizeTrackedIntermediates(final List<ComputerPlanTrackedRouteSnapshot> trackedIntermediates) {
        if (trackedIntermediates == null || trackedIntermediates.isEmpty()) {
            return List.of();
        }

        final ArrayList<ComputerPlanTrackedRouteSnapshot> sanitized = new ArrayList<>(Math.min(trackedIntermediates.size(), MAX_TRACKED_INTERMEDIATES));
        for (int index = 0; index < trackedIntermediates.size() && sanitized.size() < MAX_TRACKED_INTERMEDIATES; index++) {
            final ComputerPlanTrackedRouteSnapshot trackedIntermediate = trackedIntermediates.get(index);
            if (trackedIntermediate != null && trackedIntermediate.expectedCount() > 0) {
                sanitized.add(trackedIntermediate);
            }
        }
        return List.copyOf(sanitized);
    }

    private static String limit(final String value, final int maxLength) {
        final String safeValue = value == null ? "" : value;
        return safeValue.length() <= maxLength ? safeValue : safeValue.substring(0, maxLength);
    }

    private static void encode(final ByteBuf buffer, final ComputerPlanJobSnapshot snapshot) {
        ByteBufCodecs.stringUtf8(MAX_STRING_LENGTH).encode(buffer, snapshot.deviceApiName());
        ByteBufCodecs.VAR_INT.encode(buffer, snapshot.totalCycles());
        ByteBufCodecs.VAR_INT.encode(buffer, snapshot.remainingCycles());
        ByteBufCodecs.VAR_INT.encode(buffer, snapshot.cycleIndex());
        ByteBufCodecs.VAR_INT.encode(buffer, snapshot.stepIndex());
        ByteBufCodecs.VAR_INT.encode(buffer, snapshot.totalSteps());
        ByteBufCodecs.VAR_INT.encode(buffer, snapshot.remainingCrafts());
        ByteBufCodecs.stringUtf8(MAX_STRING_LENGTH).encode(buffer, snapshot.status());
        ByteBufCodecs.stringUtf8(MAX_STRING_LENGTH).encode(buffer, snapshot.reservationMode());
        ByteBufCodecs.stringUtf8(MAX_STRING_LENGTH).encode(buffer, snapshot.actionHint());
        ByteBufCodecs.stringUtf8(MAX_STRING_LENGTH).encode(buffer, snapshot.errorClass());
        ByteBufCodecs.stringUtf8(MAX_MESSAGE_LENGTH).encode(buffer, snapshot.message());
        ByteBufCodecs.stringUtf8(MAX_STRING_LENGTH).encode(buffer, snapshot.inputRoute());
        ByteBufCodecs.stringUtf8(MAX_STRING_LENGTH).encode(buffer, snapshot.outputRoute());
        ByteBufCodecs.VAR_INT.encode(buffer, snapshot.reservableCycles());
        ByteBufCodecs.VAR_INT.encode(buffer, snapshot.reservableSteps());
        ByteBufCodecs.VAR_INT.encode(buffer, snapshot.blockedCycleIndex());
        ByteBufCodecs.VAR_INT.encode(buffer, snapshot.blockedStepIndex());
        TRACKED_INTERMEDIATES_CODEC.encode(buffer, snapshot.trackedIntermediates());
    }

    private static ComputerPlanJobSnapshot decode(final ByteBuf buffer) {
        return new ComputerPlanJobSnapshot(
                ByteBufCodecs.stringUtf8(MAX_STRING_LENGTH).decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer),
                ByteBufCodecs.stringUtf8(MAX_STRING_LENGTH).decode(buffer),
                ByteBufCodecs.stringUtf8(MAX_STRING_LENGTH).decode(buffer),
                ByteBufCodecs.stringUtf8(MAX_STRING_LENGTH).decode(buffer),
                ByteBufCodecs.stringUtf8(MAX_STRING_LENGTH).decode(buffer),
                ByteBufCodecs.stringUtf8(MAX_MESSAGE_LENGTH).decode(buffer),
                ByteBufCodecs.stringUtf8(MAX_STRING_LENGTH).decode(buffer),
                ByteBufCodecs.stringUtf8(MAX_STRING_LENGTH).decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer),
                TRACKED_INTERMEDIATES_CODEC.decode(buffer)
        );
    }
}