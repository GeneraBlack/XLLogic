package de.xllogic.runtime;

public record PythonExecutionLimits(long maxStatements, String limitExceededSummary) {
    private static final PythonExecutionLimits UNBOUNDED = new PythonExecutionLimits(0L, "");

    public PythonExecutionLimits {
        maxStatements = Math.max(0L, maxStatements);
        limitExceededSummary = limitExceededSummary == null ? "" : limitExceededSummary;
    }

    public static PythonExecutionLimits unbounded() {
        return UNBOUNDED;
    }

    public static PythonExecutionLimits statementBudget(final long maxStatements, final String limitExceededSummary) {
        return new PythonExecutionLimits(maxStatements, limitExceededSummary);
    }

    public boolean limited() {
        return this.maxStatements > 0L;
    }
}