package de.xllogic.runtime;

import java.util.List;

public record PythonExecutionResult(boolean success,
                                    List<String> stdout,
                                    List<String> stderr,
                                    String summary,
                                    List<ComputerOutputEntry> outputEntries,
                                    List<ComputerPlanStepSnapshot> planStepSnapshots,
                                    ComputerPlanJobSnapshot planJobSnapshot) {
    public PythonExecutionResult {
        stdout = List.copyOf(stdout);
        stderr = List.copyOf(stderr);
        outputEntries = outputEntries == null ? List.of() : List.copyOf(outputEntries);
        planStepSnapshots = planStepSnapshots == null ? List.of() : List.copyOf(planStepSnapshots);
        planJobSnapshot = planJobSnapshot == null ? ComputerPlanJobSnapshot.empty() : planJobSnapshot;
    }

    public static PythonExecutionResult success(final List<String> stdout, final List<String> stderr) {
        return success(stdout, stderr, "Execution completed.", List.of(), List.of(), ComputerPlanJobSnapshot.empty());
    }

    public static PythonExecutionResult success(final List<String> stdout, final List<String> stderr, final String summary) {
        return success(stdout, stderr, summary, List.of(), List.of(), ComputerPlanJobSnapshot.empty());
    }

    public static PythonExecutionResult failure(final List<String> stdout, final List<String> stderr, final String summary) {
        return failure(stdout, stderr, summary, List.of(), List.of(), ComputerPlanJobSnapshot.empty());
    }

    public static PythonExecutionResult success(final List<String> stdout, final List<String> stderr, final String summary, final List<ComputerPlanStepSnapshot> planStepSnapshots) {
        return success(stdout, stderr, summary, List.of(), planStepSnapshots, ComputerPlanJobSnapshot.empty());
    }

    public static PythonExecutionResult failure(final List<String> stdout, final List<String> stderr, final String summary, final List<ComputerPlanStepSnapshot> planStepSnapshots) {
        return failure(stdout, stderr, summary, List.of(), planStepSnapshots, ComputerPlanJobSnapshot.empty());
    }

    public static PythonExecutionResult success(final List<String> stdout, final List<String> stderr, final String summary, final List<ComputerOutputEntry> outputEntries, final List<ComputerPlanStepSnapshot> planStepSnapshots) {
        return success(stdout, stderr, summary, outputEntries, planStepSnapshots, ComputerPlanJobSnapshot.empty());
    }

    public static PythonExecutionResult failure(final List<String> stdout, final List<String> stderr, final String summary, final List<ComputerOutputEntry> outputEntries, final List<ComputerPlanStepSnapshot> planStepSnapshots) {
        return failure(stdout, stderr, summary, outputEntries, planStepSnapshots, ComputerPlanJobSnapshot.empty());
    }

    public static PythonExecutionResult success(final List<String> stdout,
                                                final List<String> stderr,
                                                final String summary,
                                                final List<ComputerOutputEntry> outputEntries,
                                                final List<ComputerPlanStepSnapshot> planStepSnapshots,
                                                final ComputerPlanJobSnapshot planJobSnapshot) {
        return new PythonExecutionResult(true, stdout, stderr, summary, outputEntries, planStepSnapshots, planJobSnapshot);
    }

    public static PythonExecutionResult failure(final List<String> stdout,
                                                final List<String> stderr,
                                                final String summary,
                                                final List<ComputerOutputEntry> outputEntries,
                                                final List<ComputerPlanStepSnapshot> planStepSnapshots,
                                                final ComputerPlanJobSnapshot planJobSnapshot) {
        return new PythonExecutionResult(false, stdout, stderr, summary, outputEntries, planStepSnapshots, planJobSnapshot);
    }
}
