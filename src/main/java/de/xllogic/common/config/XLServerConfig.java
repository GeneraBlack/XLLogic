package de.xllogic.common.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class XLServerConfig {
    private static final long DEFAULT_SERVER_STATEMENT_BUDGET = 0L;
    private static final long MIN_SERVER_STATEMENT_BUDGET = 0L;
    private static final long MAX_SERVER_STATEMENT_BUDGET = 10_000_000L;
    private static final int DEFAULT_EXECUTION_COOLDOWN_TICKS = 10;
    private static final int MIN_EXECUTION_COOLDOWN_TICKS = 0;
    private static final int MAX_EXECUTION_COOLDOWN_TICKS = 2_400;
    private static final int DEFAULT_MAX_EXECUTABLE_SCRIPT_LENGTH = 16_384;
    private static final int MIN_MAX_EXECUTABLE_SCRIPT_LENGTH = 256;
    private static final int MAX_MAX_EXECUTABLE_SCRIPT_LENGTH = 16_384;
    private static final int DEFAULT_MAX_CPU_TIME_MILLIS = 2_000;
    private static final int MIN_MAX_CPU_TIME_MILLIS = 0;
    private static final int MAX_MAX_CPU_TIME_MILLIS = 60 * 60 * 1000;
    private static final int DEFAULT_CPU_TIME_CHECK_INTERVAL_MILLIS = 25;
    private static final int MIN_CPU_TIME_CHECK_INTERVAL_MILLIS = 1;
    private static final int MAX_CPU_TIME_CHECK_INTERVAL_MILLIS = 60 * 60 * 1000;
    private static final long DEFAULT_MAX_STDOUT_BYTES = 262_144L;
    private static final long MIN_MAX_STDOUT_BYTES = 0L;
    private static final long MAX_MAX_STDOUT_BYTES = 16L * 1024L * 1024L;
    private static final long DEFAULT_MAX_STDERR_BYTES = 262_144L;
    private static final long MIN_MAX_STDERR_BYTES = 0L;
    private static final long MAX_MAX_STDERR_BYTES = 16L * 1024L * 1024L;
    private static final int DEFAULT_EDITOR_LEASE_TIMEOUT_TICKS = 100;
    private static final int MIN_EDITOR_LEASE_TIMEOUT_TICKS = 20;
    private static final int MAX_EDITOR_LEASE_TIMEOUT_TICKS = 2_400;
    private static final int DEFAULT_PERSISTENT_RESUME_INTERVAL_TICKS = 20;
    private static final int MIN_PERSISTENT_RESUME_INTERVAL_TICKS = 1;
    private static final int MAX_PERSISTENT_RESUME_INTERVAL_TICKS = 2_400;
    private static final int DEFAULT_HOST_CALL_DIAGNOSTICS_THRESHOLD_MILLIS = 25;
    private static final int MIN_HOST_CALL_DIAGNOSTICS_THRESHOLD_MILLIS = 0;
    private static final int MAX_HOST_CALL_DIAGNOSTICS_THRESHOLD_MILLIS = 60_000;
    private static final int DEFAULT_HOST_CALL_DIAGNOSTICS_TOP_ENTRIES = 6;
    private static final int MIN_HOST_CALL_DIAGNOSTICS_TOP_ENTRIES = 1;
    private static final int MAX_HOST_CALL_DIAGNOSTICS_TOP_ENTRIES = 16;
    private static final String PROPERTY_HOST_CALL_DIAGNOSTICS_THRESHOLD_MILLIS = "xllogic.hostCallDiagnosticsThresholdMillis";
    private static final String PROPERTY_HOST_CALL_DIAGNOSTICS_TOP_ENTRIES = "xllogic.hostCallDiagnosticsTopEntries";

    public static final ModConfigSpec SPEC;
    public static final XLServerConfig INSTANCE;

    private static volatile Integer testMaxCpuTimeMillisOverride;
    private static volatile Integer testCpuTimeCheckIntervalMillisOverride;
    private static volatile Long testMaxStdoutBytesOverride;
    private static volatile Long testMaxStderrBytesOverride;
    private static volatile Integer testPersistentResumeIntervalTicksOverride;

    private final ModConfigSpec.LongValue serverStatementBudget;
    private final ModConfigSpec.IntValue executionCooldownTicks;
    private final ModConfigSpec.IntValue maxExecutableScriptLength;
    private final ModConfigSpec.IntValue maxCpuTimeMillis;
    private final ModConfigSpec.IntValue cpuTimeCheckIntervalMillis;
    private final ModConfigSpec.LongValue maxStdoutBytes;
    private final ModConfigSpec.LongValue maxStderrBytes;
    private final ModConfigSpec.IntValue editorLeaseTimeoutTicks;
    private final ModConfigSpec.IntValue persistentResumeIntervalTicks;
    private final ModConfigSpec.IntValue hostCallDiagnosticsThresholdMillis;
    private final ModConfigSpec.IntValue hostCallDiagnosticsTopEntries;

    private XLServerConfig(final ModConfigSpec.Builder builder) {
        builder.push("runtime");
        this.serverStatementBudget = builder
            .comment("Optional statement budget for uninterrupted one-shot server execution paths. Cooperative tick runtimes should yield with 'yield from sleep_ticks(...)'. Set to 0 to disable.")
                .defineInRange("serverStatementBudget", DEFAULT_SERVER_STATEMENT_BUDGET, MIN_SERVER_STATEMENT_BUDGET, MAX_SERVER_STATEMENT_BUDGET);
        this.executionCooldownTicks = builder
                .comment("Minimum cooldown in ticks between two server-side executions on the same computer. Set to 0 to disable.")
                .defineInRange("executionCooldownTicks", DEFAULT_EXECUTION_COOLDOWN_TICKS, MIN_EXECUTION_COOLDOWN_TICKS, MAX_EXECUTION_COOLDOWN_TICKS);
        this.maxExecutableScriptLength = builder
                .comment("Maximum script length in characters that the server will execute for a bound computer.")
                .defineInRange("maxExecutableScriptLength", DEFAULT_MAX_EXECUTABLE_SCRIPT_LENGTH, MIN_MAX_EXECUTABLE_SCRIPT_LENGTH, MAX_MAX_EXECUTABLE_SCRIPT_LENGTH);
        this.maxCpuTimeMillis = builder
            .comment("Target watchdog duration in milliseconds for one uninterrupted Python execution slice before it is cancelled. The runtime applies a small built-in grace window to avoid overly eager cancellations. Set to 0 to disable.")
            .defineInRange("maxCpuTimeMillis", DEFAULT_MAX_CPU_TIME_MILLIS, MIN_MAX_CPU_TIME_MILLIS, MAX_MAX_CPU_TIME_MILLIS);
        this.cpuTimeCheckIntervalMillis = builder
            .comment("Polling interval in milliseconds for the server-side Python runtime watchdog. Higher values reduce watchdog overhead but react less aggressively.")
            .defineInRange("cpuTimeCheckIntervalMillis", DEFAULT_CPU_TIME_CHECK_INTERVAL_MILLIS, MIN_CPU_TIME_CHECK_INTERVAL_MILLIS, MAX_CPU_TIME_CHECK_INTERVAL_MILLIS);
        this.maxStdoutBytes = builder
            .comment("Maximum bytes Python code may write to stdout during a single execution. Set to 0 to disable.")
            .defineInRange("maxStdoutBytes", DEFAULT_MAX_STDOUT_BYTES, MIN_MAX_STDOUT_BYTES, MAX_MAX_STDOUT_BYTES);
        this.maxStderrBytes = builder
            .comment("Maximum bytes Python code may write to stderr during a single execution. Set to 0 to disable.")
            .defineInRange("maxStderrBytes", DEFAULT_MAX_STDERR_BYTES, MIN_MAX_STDERR_BYTES, MAX_MAX_STDERR_BYTES);
        this.persistentResumeIntervalTicks = builder
            .comment("Minimum number of server ticks between two cooperative Python resume slices for the same running computer.")
            .defineInRange("persistentResumeIntervalTicks", DEFAULT_PERSISTENT_RESUME_INTERVAL_TICKS, MIN_PERSISTENT_RESUME_INTERVAL_TICKS, MAX_PERSISTENT_RESUME_INTERVAL_TICKS);
        this.hostCallDiagnosticsThresholdMillis = builder
            .comment("Adds a Python host-call diagnostics table when bridged device/world calls accumulate at least this many milliseconds of roundtrip time. Set to 0 to always include diagnostics.")
            .defineInRange("hostCallDiagnosticsThresholdMillis", DEFAULT_HOST_CALL_DIAGNOSTICS_THRESHOLD_MILLIS, MIN_HOST_CALL_DIAGNOSTICS_THRESHOLD_MILLIS, MAX_HOST_CALL_DIAGNOSTICS_THRESHOLD_MILLIS);
        this.hostCallDiagnosticsTopEntries = builder
            .comment("Maximum number of bridged Python host methods listed in the diagnostics table.")
            .defineInRange("hostCallDiagnosticsTopEntries", DEFAULT_HOST_CALL_DIAGNOSTICS_TOP_ENTRIES, MIN_HOST_CALL_DIAGNOSTICS_TOP_ENTRIES, MAX_HOST_CALL_DIAGNOSTICS_TOP_ENTRIES);
        this.editorLeaseTimeoutTicks = builder
            .comment("How long a computer editor lock stays valid without heartbeats before another player may take over editing.")
            .defineInRange("editorLeaseTimeoutTicks", DEFAULT_EDITOR_LEASE_TIMEOUT_TICKS, MIN_EDITOR_LEASE_TIMEOUT_TICKS, MAX_EDITOR_LEASE_TIMEOUT_TICKS);
        builder.pop();
    }

    public long serverStatementBudget() {
        return readLong(this.serverStatementBudget, null);
    }

    public int executionCooldownTicks() {
        return readInt(this.executionCooldownTicks, null);
    }

    public int maxExecutableScriptLength() {
        return readInt(this.maxExecutableScriptLength, null);
    }

    public int maxCpuTimeMillis() {
        return readInt(this.maxCpuTimeMillis, testMaxCpuTimeMillisOverride);
    }

    public int cpuTimeCheckIntervalMillis() {
        return readInt(this.cpuTimeCheckIntervalMillis, testCpuTimeCheckIntervalMillisOverride);
    }

    public long maxStdoutBytes() {
        return readLong(this.maxStdoutBytes, testMaxStdoutBytesOverride);
    }

    public long maxStderrBytes() {
        return readLong(this.maxStderrBytes, testMaxStderrBytesOverride);
    }

    public int persistentResumeIntervalTicks() {
        return readInt(this.persistentResumeIntervalTicks, testPersistentResumeIntervalTicksOverride);
    }

    public int hostCallDiagnosticsThresholdMillis() {
        final Integer propertyOverride = readIntegerSystemProperty(PROPERTY_HOST_CALL_DIAGNOSTICS_THRESHOLD_MILLIS);
        if (propertyOverride != null) {
            return clampInt(propertyOverride, MIN_HOST_CALL_DIAGNOSTICS_THRESHOLD_MILLIS, MAX_HOST_CALL_DIAGNOSTICS_THRESHOLD_MILLIS);
        }
        return readInt(this.hostCallDiagnosticsThresholdMillis, null);
    }

    public int hostCallDiagnosticsTopEntries() {
        final Integer propertyOverride = readIntegerSystemProperty(PROPERTY_HOST_CALL_DIAGNOSTICS_TOP_ENTRIES);
        if (propertyOverride != null) {
            return clampInt(propertyOverride, MIN_HOST_CALL_DIAGNOSTICS_TOP_ENTRIES, MAX_HOST_CALL_DIAGNOSTICS_TOP_ENTRIES);
        }
        return readInt(this.hostCallDiagnosticsTopEntries, null);
    }

    public int editorLeaseTimeoutTicks() {
        return readInt(this.editorLeaseTimeoutTicks, null);
    }

    private static int readInt(final ModConfigSpec.IntValue value, final Integer overrideValue) {
        if (overrideValue != null) {
            return overrideValue;
        }
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    private static long readLong(final ModConfigSpec.LongValue value, final Long overrideValue) {
        if (overrideValue != null) {
            return overrideValue;
        }
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    private static Integer readIntegerSystemProperty(final String propertyName) {
        final String propertyValue = System.getProperty(propertyName);
        if (propertyValue == null || propertyValue.isBlank()) {
            return null;
        }

        try {
            return Integer.parseInt(propertyValue.trim());
        } catch (final NumberFormatException ignored) {
            return null;
        }
    }

    private static int clampInt(final int value, final int minValue, final int maxValue) {
        return Math.max(minValue, Math.min(maxValue, value));
    }

    static {
        final ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        INSTANCE = new XLServerConfig(builder);
        SPEC = builder.build();
    }
}