package de.xllogic.runtime;

import de.xllogic.XLLogicMod;
import java.util.List;

public final class RuntimeFactory {
    private static final String UNAVAILABLE_SUMMARY = "GraalPy is not available on the current classpath.";

    private RuntimeFactory() {
    }

    public static PythonRuntime createPythonRuntime() {
        try {
            return new GraalPythonRuntime();
        } catch (final RuntimeException | LinkageError exception) {
            XLLogicMod.LOGGER.warn("Falling back to unavailable GraalPy runtime.", exception);
            return UnavailablePythonRuntime.INSTANCE;
        }
    }

    private static final class UnavailablePythonRuntime implements PythonRuntime {
        private static final UnavailablePythonRuntime INSTANCE = new UnavailablePythonRuntime();

        @Override
        public String displayName() {
            return "GraalPy";
        }

        @Override
        public boolean available() {
            return false;
        }

        @Override
        public PythonExecutionResult execute(final String source, final PythonExecutionContext context, final PythonExecutionLimits limits) {
            return PythonExecutionResult.failure(List.of(), List.of(), UNAVAILABLE_SUMMARY);
        }
    }
}
