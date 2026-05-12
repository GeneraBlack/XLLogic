package de.xllogic.runtime;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record ComputerRuntimeSnapshot(boolean running,
                                      boolean success,
                                      String summary,
                                      List<String> outputLines,
                                      List<ComputerOutputEntry> outputEntries,
                                      List<ComputerPlanStepSnapshot> planStepSnapshots,
                                      ComputerPlanJobSnapshot planJobSnapshot) {
    private static final int MAX_SUMMARY_LENGTH = 512;
    private static final int MAX_OUTPUT_LINES = 64;
    private static final int MAX_OUTPUT_LINE_LENGTH = 512;
    private static final int MAX_PLAN_STEPS = 256;
    private static final StreamCodec<ByteBuf, List<String>> OUTPUT_LINES_CODEC =
        ByteBufCodecs.stringUtf8(MAX_OUTPUT_LINE_LENGTH).apply(ByteBufCodecs.list(MAX_OUTPUT_LINES));
    private static final StreamCodec<ByteBuf, List<ComputerOutputEntry>> OUTPUT_ENTRIES_CODEC =
        ComputerOutputEntry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_OUTPUT_LINES));
    private static final StreamCodec<ByteBuf, List<ComputerPlanStepSnapshot>> PLAN_STEPS_CODEC =
        ComputerPlanStepSnapshot.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_PLAN_STEPS));
        private static final String PLAN_JOB_STATUS_BLOCKED = "blocked";
    private static final String IDLE_SUMMARY = "No program has been executed on this computer yet.";
    private static final String RUNNING_SUMMARY = "Execution running...";
    private static final String NO_OUTPUT_TEXT = "No output.";
    private static final String NO_EXECUTION_TEXT = "No program has been executed on this computer yet.";

    public static final StreamCodec<ByteBuf, ComputerRuntimeSnapshot> STREAM_CODEC = StreamCodec.of(
        ComputerRuntimeSnapshot::encode,
        ComputerRuntimeSnapshot::decode
    );

    private static final ComputerRuntimeSnapshot IDLE = new ComputerRuntimeSnapshot(
            false,
            true,
            IDLE_SUMMARY,
            List.of(ComputerOutputEntry.info(NO_EXECUTION_TEXT).formattedLine()),
            List.of(ComputerOutputEntry.info(NO_EXECUTION_TEXT)),
            List.of(),
            ComputerPlanJobSnapshot.empty());

    public ComputerRuntimeSnapshot {
        summary = sanitizeSummary(summary, running);
        outputEntries = sanitizeOutputEntries(outputEntries, outputLines, summary);
        outputLines = sanitizeOutputLines(outputLines, outputEntries, summary);
        planStepSnapshots = sanitizePlanSteps(planStepSnapshots);
        planJobSnapshot = sanitizePlanJobSnapshot(planJobSnapshot);
    }

    public static ComputerRuntimeSnapshot idle() {
        return IDLE;
    }

    public static ComputerRuntimeSnapshot running(final ComputerRuntimeSnapshot previousState) {
        final ComputerRuntimeSnapshot safeState = previousState == null ? IDLE : previousState;
        return new ComputerRuntimeSnapshot(true, true, RUNNING_SUMMARY, safeState.outputLines(), safeState.outputEntries(), List.of(), ComputerPlanJobSnapshot.empty());
    }

    public static ComputerRuntimeSnapshot running(final List<String> previousOutputLines) {
        return new ComputerRuntimeSnapshot(true, true, RUNNING_SUMMARY, previousOutputLines, List.of(), List.of(), ComputerPlanJobSnapshot.empty());
    }

    public static ComputerRuntimeSnapshot guardrailRejected(final ComputerRuntimeSnapshot previousState, final String summary) {
        final ComputerRuntimeSnapshot safeState = previousState == null ? IDLE : previousState;
        final String safeSummary = sanitizeSummary(summary, false);
        final ArrayList<ComputerOutputEntry> entries = new ArrayList<>(Math.min(MAX_OUTPUT_LINES, safeState.outputEntries().size() + 1));
        final int preservedCapacity = Math.max(0, MAX_OUTPUT_LINES - 1);
        final int preservedStart = Math.max(0, safeState.outputEntries().size() - preservedCapacity);
        for (int index = preservedStart; index < safeState.outputEntries().size(); index++) {
            final ComputerOutputEntry entry = safeState.outputEntries().get(index);
            if (entry != null) {
                entries.add(entry);
            }
        }
        entries.add(ComputerOutputEntry.error(safeSummary));
        return new ComputerRuntimeSnapshot(false, false, safeSummary, List.of(), entries, safeState.planStepSnapshots(), safeState.planJobSnapshot());
    }

    public static ComputerRuntimeSnapshot fromExecutionResult(final PythonExecutionResult result) {
        final List<ComputerOutputEntry> entries = new ArrayList<>();
        entries.add(result.success() ? ComputerOutputEntry.ok("Script finished.") : ComputerOutputEntry.error("Script failed."));

        if (!result.summary().isBlank()) {
            entries.add(result.success() ? ComputerOutputEntry.info(result.summary()) : ComputerOutputEntry.error(result.summary()));
        }
        entries.addAll(result.outputEntries());
        if (result.outputEntries().isEmpty() && result.success()) {
            entries.add(ComputerOutputEntry.info(NO_OUTPUT_TEXT));
        }

        return new ComputerRuntimeSnapshot(false, result.success(), summarize(result), List.of(), entries, result.planStepSnapshots(), result.planJobSnapshot());
    }

    public boolean neverExecuted() {
        return !this.running && IDLE_SUMMARY.equals(this.summary);
    }

    public boolean stopped() {
        return !this.running && !this.success && this.summary.startsWith("Execution stopped");
    }

    public String planReservationMode() {
        if (this.planJobSnapshot.hasReservationMode()) {
            return this.planJobSnapshot.reservationMode();
        }
        return detectPlanReservationMode(this.planStepSnapshots);
    }

    public String planJobStatus() {
        if (this.planJobSnapshot.hasStatus()) {
            return this.planJobSnapshot.status();
        }
        return detectPlanJobStatus(this.planStepSnapshots);
    }

    private static String sanitizeSummary(final String summary, final boolean running) {
        final String fallback = running ? RUNNING_SUMMARY : IDLE_SUMMARY;
        if (summary == null || summary.isBlank()) {
            return fallback;
        }
        return limit(summary, MAX_SUMMARY_LENGTH);
    }

    private static List<String> sanitizeOutputLines(final List<String> outputLines, final List<ComputerOutputEntry> outputEntries, final String summary) {
        if (hasValues(outputLines)) {
            return sanitizeLegacyOutputLines(outputLines);
        }

        if (!hasValues(outputEntries)) {
            return fallbackOutputLines(summary);
        }

        return sanitizeEntryOutputLines(outputEntries);
    }

    private static List<String> sanitizeLegacyOutputLines(final List<String> outputLines) {
        final List<String> sanitized = new ArrayList<>(Math.min(outputLines.size(), MAX_OUTPUT_LINES));
        for (int index = 0; index < outputLines.size() && sanitized.size() < MAX_OUTPUT_LINES; index++) {
            final String line = outputLines.get(index) == null ? "" : outputLines.get(index);
            sanitized.add(limit(line, MAX_OUTPUT_LINE_LENGTH));
        }
        return List.copyOf(sanitized);
    }

    private static List<String> sanitizeEntryOutputLines(final List<ComputerOutputEntry> outputEntries) {
        final List<String> sanitized = new ArrayList<>(Math.min(outputEntries.size(), MAX_OUTPUT_LINES));
        for (int index = 0; index < outputEntries.size() && sanitized.size() < MAX_OUTPUT_LINES; index++) {
            final ComputerOutputEntry entry = outputEntries.get(index);
            if (entry != null) {
                sanitized.add(limit(entry.formattedLine(), MAX_OUTPUT_LINE_LENGTH));
            }
        }
        return List.copyOf(sanitized);
    }

    private static List<String> fallbackOutputLines(final String summary) {
        final ComputerOutputEntry fallback = IDLE_SUMMARY.equals(summary)
                ? ComputerOutputEntry.info(NO_EXECUTION_TEXT)
                : ComputerOutputEntry.info(NO_OUTPUT_TEXT);
        return List.of(fallback.formattedLine());
    }

    private static List<ComputerOutputEntry> sanitizeOutputEntries(final List<ComputerOutputEntry> outputEntries, final List<String> outputLines, final String summary) {
        if (outputEntries != null && !outputEntries.isEmpty()) {
            final List<ComputerOutputEntry> sanitized = new ArrayList<>(Math.min(outputEntries.size(), MAX_OUTPUT_LINES));
            for (int index = 0; index < outputEntries.size() && sanitized.size() < MAX_OUTPUT_LINES; index++) {
                final ComputerOutputEntry entry = outputEntries.get(index);
                if (entry != null) {
                    sanitized.add(entry);
                }
            }
            return List.copyOf(sanitized);
        }

        if (outputLines != null && !outputLines.isEmpty()) {
            final List<ComputerOutputEntry> sanitized = new ArrayList<>(Math.min(outputLines.size(), MAX_OUTPUT_LINES));
            for (int index = 0; index < outputLines.size() && sanitized.size() < MAX_OUTPUT_LINES; index++) {
                sanitized.add(ComputerOutputEntry.fromLegacyLine(outputLines.get(index)));
            }
            return List.copyOf(sanitized);
        }

        return IDLE_SUMMARY.equals(summary)
                ? List.of(ComputerOutputEntry.info(NO_EXECUTION_TEXT))
                : List.of(ComputerOutputEntry.info(NO_OUTPUT_TEXT));
    }

    private static List<ComputerPlanStepSnapshot> sanitizePlanSteps(final List<ComputerPlanStepSnapshot> planStepSnapshots) {
        if (planStepSnapshots == null || planStepSnapshots.isEmpty()) {
            return List.of();
        }

        final List<ComputerPlanStepSnapshot> sanitized = new ArrayList<>(Math.min(planStepSnapshots.size(), MAX_PLAN_STEPS));
        for (int index = 0; index < planStepSnapshots.size() && sanitized.size() < MAX_PLAN_STEPS; index++) {
            final ComputerPlanStepSnapshot stepSnapshot = planStepSnapshots.get(index);
            if (stepSnapshot != null) {
                sanitized.add(stepSnapshot);
            }
        }
        return List.copyOf(sanitized);
    }

    private static ComputerPlanJobSnapshot sanitizePlanJobSnapshot(final ComputerPlanJobSnapshot planJobSnapshot) {
        return planJobSnapshot == null ? ComputerPlanJobSnapshot.empty() : planJobSnapshot;
    }

    private static String summarize(final PythonExecutionResult result) {
        final String baseSummary = sanitizeSummary(result.summary(), false);
        final ComputerPlanJobSnapshot planJobSnapshot = sanitizePlanJobSnapshot(result.planJobSnapshot());
        if (result.planStepSnapshots().isEmpty() && !planJobSnapshot.hasStatus()) {
            return baseSummary;
        }

        final String reservationMode = planJobSnapshot.hasReservationMode()
                ? planJobSnapshot.reservationMode()
                : detectPlanReservationMode(result.planStepSnapshots());
        final String jobStatus = planJobSnapshot.hasStatus()
                ? planJobSnapshot.status()
                : detectPlanJobStatus(result.planStepSnapshots());

        if (result.planStepSnapshots().isEmpty()) {
            final StringBuilder summary = new StringBuilder();
            appendJobSummaryPrefix(summary, planJobSnapshot, jobStatus, reservationMode);
            summary.append(baseSummary);
            return limit(summary.toString(), MAX_SUMMARY_LENGTH);
        }

        int completed = 0;
        int failed = 0;
        int partial = 0;
        for (final ComputerPlanStepSnapshot stepSnapshot : result.planStepSnapshots()) {
            if (stepSnapshot.successful()) {
                completed++;
            } else if (stepSnapshot.failed()) {
                failed++;
            } else if (stepSnapshot.partial()) {
                partial++;
            }
        }

        final StringBuilder summary = new StringBuilder();
        appendJobSummaryPrefix(summary, planJobSnapshot, jobStatus, reservationMode);
        summary.append(baseSummary)
                .append(" | plan steps: ")
                .append(completed)
                .append("/")
                .append(result.planStepSnapshots().size())
                .append(" completed");
        if (partial > 0) {
            summary.append(", partial: ").append(partial);
        }
        if (failed > 0) {
            summary.append(", failed: ").append(failed);
        }
        return limit(summary.toString(), MAX_SUMMARY_LENGTH);
    }

    private static String detectPlanReservationMode(final List<ComputerPlanStepSnapshot> planStepSnapshots) {
        String detectedMode = "";
        for (final ComputerPlanStepSnapshot stepSnapshot : planStepSnapshots) {
            if (stepSnapshot != null && stepSnapshot.hasReservationMode()) {
                final String stepMode = stepSnapshot.reservationMode();
                if (detectedMode.isBlank()) {
                    detectedMode = stepMode;
                } else if (!detectedMode.equals(stepMode)) {
                    return "mixed";
                }
            }
        }
        return detectedMode;
    }

    private static String detectPlanJobStatus(final List<ComputerPlanStepSnapshot> planStepSnapshots) {
        if (planStepSnapshots == null || planStepSnapshots.isEmpty()) {
            return "";
        }

        ComputerPlanStepSnapshot decisiveSnapshot = null;
        for (final ComputerPlanStepSnapshot stepSnapshot : planStepSnapshots) {
            if (stepSnapshot == null || "skipped".equals(stepSnapshot.status())) {
                continue;
            }
            decisiveSnapshot = stepSnapshot;
        }

        if (decisiveSnapshot == null) {
            return "";
        }
        if (decisiveSnapshot.failed()) {
            return "failed";
        }
        if (PLAN_JOB_STATUS_BLOCKED.equals(decisiveSnapshot.status())) {
            return PLAN_JOB_STATUS_BLOCKED;
        }
        if (decisiveSnapshot.partial()) {
            return "resumable";
        }
        if (decisiveSnapshot.successful()) {
            return "completed";
        }
        return "";
    }

    private static void appendJobSummaryPrefix(final StringBuilder summary,
                                               final ComputerPlanJobSnapshot planJobSnapshot,
                                               final String jobStatus,
                                               final String reservationMode) {
        if (!jobStatus.isBlank()) {
            summary.append("job: ").append(jobStatus).append(" | ");
        }
        if (!reservationMode.isBlank()) {
            summary.append("reservation: ").append(reservationMode).append(" | ");
        }
        if (planJobSnapshot.hasActionHint()) {
            summary.append("action: ").append(planJobSnapshot.actionHint()).append(" | ");
        }
        if (planJobSnapshot.hasErrorClass()) {
            summary.append("error: ").append(planJobSnapshot.errorClass()).append(" | ");
        }
        if (planJobSnapshot.reservableCycles() > 0 || planJobSnapshot.reservableSteps() > 0) {
            summary.append("ready: ")
                    .append(planJobSnapshot.reservableCycles())
                    .append(" cycles/")
                    .append(planJobSnapshot.reservableSteps())
                    .append(" steps | ");
        }
        if (PLAN_JOB_STATUS_BLOCKED.equals(jobStatus) || "failed".equals(jobStatus)) {
            summary.append("blocked_at: ")
                    .append(planJobSnapshot.blockedCycleIndex() + 1)
                    .append("/")
                    .append(planJobSnapshot.blockedStepIndex() + 1)
                    .append(" | ");
        }
        if (planJobSnapshot.hasTrackedIntermediates()) {
            summary.append("tracked: ")
                    .append(planJobSnapshot.trackedIntermediates().size())
                    .append(" routes/")
                    .append(planJobSnapshot.trackedIntermediateTotal())
                    .append(" items | ");
        }
    }

    private static String limit(final String value, final int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static boolean hasValues(final List<?> values) {
        return values != null && !values.isEmpty();
    }

    private static void encode(final ByteBuf buffer, final ComputerRuntimeSnapshot snapshot) {
        ByteBufCodecs.BOOL.encode(buffer, snapshot.running());
        ByteBufCodecs.BOOL.encode(buffer, snapshot.success());
        ByteBufCodecs.stringUtf8(MAX_SUMMARY_LENGTH).encode(buffer, snapshot.summary());
        OUTPUT_LINES_CODEC.encode(buffer, snapshot.outputLines());
        OUTPUT_ENTRIES_CODEC.encode(buffer, snapshot.outputEntries());
        PLAN_STEPS_CODEC.encode(buffer, snapshot.planStepSnapshots());
        ComputerPlanJobSnapshot.STREAM_CODEC.encode(buffer, snapshot.planJobSnapshot());
    }

    private static ComputerRuntimeSnapshot decode(final ByteBuf buffer) {
        return new ComputerRuntimeSnapshot(
                ByteBufCodecs.BOOL.decode(buffer),
                ByteBufCodecs.BOOL.decode(buffer),
                ByteBufCodecs.stringUtf8(MAX_SUMMARY_LENGTH).decode(buffer),
                OUTPUT_LINES_CODEC.decode(buffer),
                OUTPUT_ENTRIES_CODEC.decode(buffer),
                PLAN_STEPS_CODEC.decode(buffer),
                ComputerPlanJobSnapshot.STREAM_CODEC.decode(buffer)
        );
    }
}
