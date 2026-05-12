package de.xllogic.runtime;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ComputerRuntimeSnapshotTest {
    private static final String CRAFTING_IO_API_NAME = "crafting_io";
    private static final String BUFFER_ROUTE = "buffer";
    private static final String RESERVATION_MODE_FULL_QUEUE = "full_queue";
    private static final String ERROR_INTERMEDIATE_MISSING = "intermediate_missing";
    private static final String ERROR_OUTPUT_FULL = "output_full";
    private static final String STATUS_BLOCKED = "blocked";
    private static final String STATUS_FAILED = "failed";
    private static final String STICK_ITEM_ID = "minecraft:stick";

    @Test
    void executionSummaryIncludesPlanJobStatusAndReservationMode() {
        final ComputerPlanStepSnapshot stepSnapshot = new ComputerPlanStepSnapshot(
            CRAFTING_IO_API_NAME,
                0,
                0,
                3,
                4,
                "source",
                "sink",
                "minecraft:oak_planks",
                STICK_ITEM_ID,
                4,
                4,
                "active_cycle",
                "",
                "completed",
                "Plan step crafted successfully.");

        final ComputerRuntimeSnapshot runtimeSnapshot = ComputerRuntimeSnapshot.fromExecutionResult(
                PythonExecutionResult.success(List.of(), List.of(), "Script executed successfully.", List.of(stepSnapshot)));

            assertEquals("completed", runtimeSnapshot.planJobStatus());
        assertEquals("active_cycle", runtimeSnapshot.planReservationMode());
            assertTrue(runtimeSnapshot.summary().contains("job: completed"));
        assertTrue(runtimeSnapshot.summary().contains("reservation: active_cycle"));
    }

            @Test
            void planJobStatusPrefersLastNonSkippedStep() {
            final ComputerPlanStepSnapshot blockedStep = new ComputerPlanStepSnapshot(
                CRAFTING_IO_API_NAME,
                0,
                1,
                3,
                4,
                BUFFER_ROUTE,
                "sink",
                STICK_ITEM_ID,
                STICK_ITEM_ID,
                4,
                0,
                RESERVATION_MODE_FULL_QUEUE,
                ERROR_OUTPUT_FULL,
                STATUS_BLOCKED,
                "Plan step is waiting for free capacity in its output route.");
            final ComputerPlanStepSnapshot skippedStep = new ComputerPlanStepSnapshot(
                CRAFTING_IO_API_NAME,
                0,
                2,
                4,
                4,
                BUFFER_ROUTE,
                "sink",
                STICK_ITEM_ID,
                STICK_ITEM_ID,
                4,
                0,
                RESERVATION_MODE_FULL_QUEUE,
                "upstream_blocked",
                "skipped",
                "Skipped because a previous step could not complete.");

            final ComputerRuntimeSnapshot runtimeSnapshot = ComputerRuntimeSnapshot.fromExecutionResult(
                PythonExecutionResult.success(List.of(), List.of(), "Script executed successfully.", List.of(blockedStep, skippedStep)));

            assertEquals(STATUS_BLOCKED, runtimeSnapshot.planJobStatus());
            assertTrue(runtimeSnapshot.summary().contains("job: blocked"));
            }

    @Test
    void planCardOutputIncludesReservationModeField() {
        final ComputerPlanStepSnapshot stepSnapshot = new ComputerPlanStepSnapshot(
            CRAFTING_IO_API_NAME,
                1,
                1,
                5,
                6,
            BUFFER_ROUTE,
                "sink",
                STICK_ITEM_ID,
                STICK_ITEM_ID,
                4,
                0,
            RESERVATION_MODE_FULL_QUEUE,
            ERROR_OUTPUT_FULL,
            STATUS_BLOCKED,
                "cycle 2 step 2 is waiting for enough reserved capacity.");

        final ComputerOutputEntry outputEntry = stepSnapshot.outputEntry();

        assertTrue(outputEntry.text().contains("reservation: full_queue"));
            assertTrue(outputEntry.text().contains("error: output_full"));
        assertTrue(outputEntry.fields().contains(new ComputerOutputEntry.OutputField("Reservation", RESERVATION_MODE_FULL_QUEUE)));
            assertTrue(outputEntry.fields().contains(new ComputerOutputEntry.OutputField("Error", ERROR_OUTPUT_FULL)));
    }

    @Test
    void executionSummaryUsesExplicitPlanJobSnapshotWithoutStepSnapshots() {
        final ComputerPlanJobSnapshot planJobSnapshot = new ComputerPlanJobSnapshot(
                CRAFTING_IO_API_NAME,
                2,
                1,
                1,
                1,
                2,
                2,
                STATUS_FAILED,
                RESERVATION_MODE_FULL_QUEUE,
                "restore_intermediate",
                ERROR_INTERMEDIATE_MISSING,
                "Tracked intermediate items are missing from the buffer route.",
                BUFFER_ROUTE,
                "sink",
                1,
                2,
                1,
                1,
                List.of(new ComputerPlanTrackedRouteSnapshot(BUFFER_ROUTE, "minecraft:oak_planks", 4)));

        final ComputerRuntimeSnapshot runtimeSnapshot = ComputerRuntimeSnapshot.fromExecutionResult(
                PythonExecutionResult.failure(List.of(), List.of(), "Crafting job stopped.", List.of(), List.of(), planJobSnapshot));

        assertEquals(STATUS_FAILED, runtimeSnapshot.planJobStatus());
        assertEquals(RESERVATION_MODE_FULL_QUEUE, runtimeSnapshot.planReservationMode());
        assertEquals(ERROR_INTERMEDIATE_MISSING, runtimeSnapshot.planJobSnapshot().errorClass());
        assertTrue(runtimeSnapshot.summary().contains("job: failed"));
        assertTrue(runtimeSnapshot.summary().contains("action: restore_intermediate"));
        assertTrue(runtimeSnapshot.summary().contains("error: intermediate_missing"));
        assertTrue(runtimeSnapshot.summary().contains("ready: 1 cycles/2 steps"));
        assertTrue(runtimeSnapshot.summary().contains("blocked_at: 2/2"));
        assertTrue(runtimeSnapshot.summary().contains("tracked: 1 routes/4 items"));
    }
}