package de.xllogic.runtime;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record ComputerPlanStepSnapshot(
        String deviceApiName,
        int cycleIndex,
        int stepIndex,
        int windowX,
        int windowY,
        String inputRoute,
        String outputRoute,
        String recipeId,
        String resultItem,
        int requestedCrafts,
        int completedCrafts,
        String reservationMode,
        String errorClass,
        String status,
        String message) {
    private static final int MAX_STRING_LENGTH = 128;
    private static final String TAG_RESERVATION_MODE = "ReservationMode";
    private static final String TAG_ERROR_CLASS = "ErrorClass";

        public static final StreamCodec<ByteBuf, ComputerPlanStepSnapshot> STREAM_CODEC = StreamCodec.of(
            ComputerPlanStepSnapshot::encode,
            ComputerPlanStepSnapshot::decode
        );

    public ComputerPlanStepSnapshot {
        deviceApiName = limit(deviceApiName);
        cycleIndex = Math.max(0, cycleIndex);
        stepIndex = Math.max(0, stepIndex);
        windowX = Math.max(0, windowX);
        windowY = Math.max(0, windowY);
        inputRoute = limit(inputRoute);
        outputRoute = limit(outputRoute);
        recipeId = limit(recipeId);
        resultItem = limit(resultItem);
        requestedCrafts = Math.max(0, requestedCrafts);
        completedCrafts = Math.max(0, completedCrafts);
        reservationMode = limit(reservationMode);
        errorClass = limit(errorClass);
        status = limit(status);
        message = limit(message);
    }

    public CompoundTag toTag() {
        final CompoundTag tag = new CompoundTag();
        tag.putString("DeviceApiName", this.deviceApiName);
        tag.putInt("CycleIndex", this.cycleIndex);
        tag.putInt("StepIndex", this.stepIndex);
        tag.putInt("WindowX", this.windowX);
        tag.putInt("WindowY", this.windowY);
        tag.putString("InputRoute", this.inputRoute);
        tag.putString("OutputRoute", this.outputRoute);
        tag.putString("RecipeId", this.recipeId);
        tag.putString("ResultItem", this.resultItem);
        tag.putInt("RequestedCrafts", this.requestedCrafts);
        tag.putInt("CompletedCrafts", this.completedCrafts);
        tag.putString(TAG_RESERVATION_MODE, this.reservationMode);
        tag.putString(TAG_ERROR_CLASS, this.errorClass);
        tag.putString("Status", this.status);
        tag.putString("Message", this.message);
        return tag;
    }

    public static ComputerPlanStepSnapshot fromTag(final CompoundTag tag) {
        return new ComputerPlanStepSnapshot(
                tag.getString("DeviceApiName"),
                tag.getInt("CycleIndex"),
                tag.getInt("StepIndex"),
                tag.getInt("WindowX"),
                tag.getInt("WindowY"),
                tag.getString("InputRoute"),
                tag.getString("OutputRoute"),
                tag.getString("RecipeId"),
                tag.getString("ResultItem"),
                tag.getInt("RequestedCrafts"),
                tag.getInt("CompletedCrafts"),
                tag.contains(TAG_RESERVATION_MODE) ? tag.getString(TAG_RESERVATION_MODE) : "",
                tag.contains(TAG_ERROR_CLASS) ? tag.getString(TAG_ERROR_CLASS) : "",
                tag.getString("Status"),
                tag.getString("Message")
        );
    }

    public boolean hasReservationMode() {
        return !this.reservationMode.isBlank();
    }

    public boolean hasErrorClass() {
        return !this.errorClass.isBlank();
    }

    public boolean successful() {
        return "completed".equals(this.status);
    }

    public boolean failed() {
        return "failed".equals(this.status);
    }

    public boolean partial() {
        return "partial".equals(this.status);
    }

    public ComputerOutputEntry outputEntry() {
        return ComputerOutputEntry.planCard(this.outputTone(), this.outputTitle(), this.outputText(), this.outputFields());
    }

    public String outputLine() {
        return this.outputEntry().formattedLine();
    }

    private String outputText() {
        final StringBuilder builder = new StringBuilder()
                .append(this.status)
                .append(" ")
                .append(this.completedCrafts)
                .append("/")
                .append(this.requestedCrafts);
        if (this.hasReservationMode()) {
            builder.append(" | reservation: ").append(this.reservationMode);
        }
        if (this.hasErrorClass()) {
            builder.append(" | error: ").append(this.errorClass);
        }
        if (!this.message.isBlank()) {
            builder.append(" | ").append(this.message);
        }
        return builder.toString();
    }

    private String outputTitle() {
        return this.deviceApiName + " cycle " + (this.cycleIndex + 1) + " step " + (this.stepIndex + 1);
    }

    private java.util.List<ComputerOutputEntry.OutputField> outputFields() {
        final java.util.ArrayList<ComputerOutputEntry.OutputField> fields = new java.util.ArrayList<>();
        fields.add(new ComputerOutputEntry.OutputField("Window", this.windowX + "," + this.windowY));
        fields.add(new ComputerOutputEntry.OutputField("Crafts", this.completedCrafts + "/" + this.requestedCrafts));
        if (this.hasReservationMode()) {
            fields.add(new ComputerOutputEntry.OutputField("Reservation", this.reservationMode));
        }
        if (this.hasErrorClass()) {
            fields.add(new ComputerOutputEntry.OutputField("Error", this.errorClass));
        }
        if (!this.recipeId.isBlank()) {
            fields.add(new ComputerOutputEntry.OutputField("Recipe", this.recipeId));
        }
        if (!this.resultItem.isBlank()) {
            fields.add(new ComputerOutputEntry.OutputField("Result", this.resultItem));
        }
        if (!this.inputRoute.isBlank() || !this.outputRoute.isBlank()) {
            fields.add(new ComputerOutputEntry.OutputField("Route", this.inputRoute + " -> " + this.outputRoute));
        }
        return java.util.List.copyOf(fields);
    }

    private static String limit(final String value) {
        final String safeValue = value == null ? "" : value;
        return safeValue.length() <= MAX_STRING_LENGTH ? safeValue : safeValue.substring(0, MAX_STRING_LENGTH);
    }

    private String outputTone() {
        if (this.failed()) {
            return "error";
        }
        if (this.successful()) {
            return "ok";
        }
        return "info";
    }

    private static void encode(final ByteBuf buffer, final ComputerPlanStepSnapshot snapshot) {
        ByteBufCodecs.stringUtf8(MAX_STRING_LENGTH).encode(buffer, snapshot.deviceApiName());
        ByteBufCodecs.VAR_INT.encode(buffer, snapshot.cycleIndex());
        ByteBufCodecs.VAR_INT.encode(buffer, snapshot.stepIndex());
        ByteBufCodecs.VAR_INT.encode(buffer, snapshot.windowX());
        ByteBufCodecs.VAR_INT.encode(buffer, snapshot.windowY());
        ByteBufCodecs.stringUtf8(MAX_STRING_LENGTH).encode(buffer, snapshot.inputRoute());
        ByteBufCodecs.stringUtf8(MAX_STRING_LENGTH).encode(buffer, snapshot.outputRoute());
        ByteBufCodecs.stringUtf8(MAX_STRING_LENGTH).encode(buffer, snapshot.recipeId());
        ByteBufCodecs.stringUtf8(MAX_STRING_LENGTH).encode(buffer, snapshot.resultItem());
        ByteBufCodecs.VAR_INT.encode(buffer, snapshot.requestedCrafts());
        ByteBufCodecs.VAR_INT.encode(buffer, snapshot.completedCrafts());
        ByteBufCodecs.stringUtf8(MAX_STRING_LENGTH).encode(buffer, snapshot.reservationMode());
        ByteBufCodecs.stringUtf8(MAX_STRING_LENGTH).encode(buffer, snapshot.errorClass());
        ByteBufCodecs.stringUtf8(MAX_STRING_LENGTH).encode(buffer, snapshot.status());
        ByteBufCodecs.stringUtf8(MAX_STRING_LENGTH).encode(buffer, snapshot.message());
    }

    private static ComputerPlanStepSnapshot decode(final ByteBuf buffer) {
        return new ComputerPlanStepSnapshot(
                ByteBufCodecs.stringUtf8(MAX_STRING_LENGTH).decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer),
                ByteBufCodecs.stringUtf8(MAX_STRING_LENGTH).decode(buffer),
                ByteBufCodecs.stringUtf8(MAX_STRING_LENGTH).decode(buffer),
                ByteBufCodecs.stringUtf8(MAX_STRING_LENGTH).decode(buffer),
                ByteBufCodecs.stringUtf8(MAX_STRING_LENGTH).decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer),
                ByteBufCodecs.stringUtf8(MAX_STRING_LENGTH).decode(buffer),
                ByteBufCodecs.stringUtf8(MAX_STRING_LENGTH).decode(buffer),
                ByteBufCodecs.stringUtf8(MAX_STRING_LENGTH).decode(buffer),
                ByteBufCodecs.stringUtf8(MAX_STRING_LENGTH).decode(buffer)
        );
    }
}