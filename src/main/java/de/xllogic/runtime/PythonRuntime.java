package de.xllogic.runtime;

public interface PythonRuntime {
    String displayName();

    boolean available();

    default PythonExecutionSession startSession(final String source, final PythonExecutionContext context, final PythonExecutionLimits limits) {
        return PythonExecutionSession.immediate(() -> this.execute(source, context, limits));
    }

    default PythonExecutionResult execute(final String source) {
        return this.execute(source, PythonExecutionContext.empty());
    }

    default PythonExecutionResult execute(final String source, final PythonExecutionContext context) {
        return this.execute(source, context, PythonExecutionLimits.unbounded());
    }

    PythonExecutionResult execute(String source, PythonExecutionContext context, PythonExecutionLimits limits);
}
