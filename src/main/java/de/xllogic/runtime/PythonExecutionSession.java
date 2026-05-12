package de.xllogic.runtime;

import java.util.function.Supplier;

public interface PythonExecutionSession extends AutoCloseable {
    void advanceTick();

    boolean finished();

    ComputerRuntimeSnapshot snapshot();

    @Override
    default void close() {
    }

    static PythonExecutionSession immediate(final Supplier<PythonExecutionResult> executionSupplier) {
        return new PythonExecutionSession() {
            private ComputerRuntimeSnapshot snapshot = ComputerRuntimeSnapshot.running(ComputerRuntimeSnapshot.idle());
            private boolean finished;

            @Override
            public void advanceTick() {
                if (this.finished) {
                    return;
                }

                final PythonExecutionResult result = executionSupplier.get();
                this.snapshot = ComputerRuntimeSnapshot.fromExecutionResult(result);
                this.finished = true;
            }

            @Override
            public boolean finished() {
                return this.finished;
            }

            @Override
            public ComputerRuntimeSnapshot snapshot() {
                return this.snapshot;
            }
        };
    }
}