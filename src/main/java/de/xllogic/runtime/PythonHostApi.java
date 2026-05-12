package de.xllogic.runtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.xllogic.common.blockentity.CraftingCPUBlockEntity;
import de.xllogic.common.blockentity.CraftingIOBlockEntity;
import de.xllogic.common.blockentity.ComputerBlockEntity;
import de.xllogic.common.blockentity.ComputerBlockEntity.BridgeMessage;
import de.xllogic.common.blockentity.MaterialIOBlockEntity;
import de.xllogic.common.blockentity.RedstoneIOBlockEntity;
import de.xllogic.common.blockentity.ScreenBlockEntity;
import de.xllogic.common.blockentity.XLApiBlockEntity;
import de.xllogic.common.config.XLServerConfig;
import de.xllogic.common.device.MaterialIOMode;
import de.xllogic.common.device.QueuedPlanJobStatus;
import de.xllogic.common.device.QueuedPlanReservationMode;
import de.xllogic.common.device.RedstoneIOMode;
import de.xllogic.common.network.NamedNetworkEndpointBlockEntity;
import de.xllogic.common.network.XLNetworkResolver;
import de.xllogic.common.network.XLNetworkResolver.BridgeRemoteComputerSnapshot;
import de.xllogic.common.util.XLItemFluidAccess;
import de.xllogic.runtime.debug.XLRuntimeDebugger;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

public final class PythonHostApi {
    private static final DateTimeFormatter REAL_TIME_FORMATTER = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final WorldBridge UNAVAILABLE_WORLD = new WorldBridge(null);
    private static final String ENDPOINT_PREFIX = "Endpoint: ";
    private static final String TYPE_SCREEN = "screen";
    private static final String TYPE_REDSTONE_IO = "redstone_io";
    private static final String TYPE_CLOCK = "clock";
    private static final String TYPE_LIGHT_SENSOR = "light_sensor";
    private static final String TYPE_RAIN_SENSOR = "rain_sensor";
    private static final String TYPE_MATERIAL_IO = "material_io";
    private static final String TYPE_CRAFTING_IO = "crafting_io";
    private static final String TYPE_CRAFTING_CPU = "crafting_cpu";
    private static final String TYPE_XLAPI_BLOCK = "xlapi_block";
    private static final String BRIDGE_CHANNEL_RESPONSE = "xlapi_response";
    private static final String BRIDGE_COMMAND_STATUS = "status";
    private static final String BRIDGE_COMMAND_PING = "ping";
    private static final String BRIDGE_COMMAND_DEVICES = "devices";
    private static final String BRIDGE_COMMAND_RUNTIME = "runtime";
    private static final String BRIDGE_KIND_RESPONSE = "response";
    private static final String BRIDGE_POLICY_READ_ONLY = "read_only";
    private static final String BRIDGE_POLICY_READ_WRITE = "read_write";
    private static final String JSON_BRIDGE_NAME = "bridge_name";
    private static final String JSON_BRIDGE_GROUP = "bridge_group";
    private static final String JSON_POSITION = "position";
    private static final String JSON_SOURCE_ID = "source_id";
    private static final String JSON_SOURCE_POSITION = "source_position";
    private static final String JSON_CREATED_GAME_TIME = "created_game_time";
    private static final String JSON_RUNTIME_STATUS = "runtime_status";
    private static final String JSON_REMOTE_POLICY = "remote_policy";
    private static final String JSON_REMOTE_WRITABLE = "remote_writable";
    private static final String REMOTE_POLICY_LOCAL = "local";
    private static final String ACTION_MODE_CHANGES = "mode changes";
    private static final String ACTION_PLAN_CHANGES = "plan changes";
    private static final String ACTION_QUEUED_PLAN_CHANGES = "queued plan changes";
    private static final String JSON_OUTPUT_LIMIT = "output_limit";
    private static final String JSON_PLAN_LIMIT = "plan_limit";
    private static final int BRIDGE_RUNTIME_DEFAULT_OUTPUT_LIMIT = 8;
    private static final int BRIDGE_RUNTIME_MAX_OUTPUT_LIMIT = 16;
    private static final int BRIDGE_RUNTIME_DEFAULT_PLAN_LIMIT = 6;
    private static final int BRIDGE_RUNTIME_MAX_PLAN_LIMIT = 12;
    private static final String UNKNOWN = "unknown";
    private static final String PLAN_STATUS_BLOCKED = "blocked";
    private static final String PLAN_STATUS_COMPLETED = "completed";
    private static final String PLAN_STATUS_FAILED = "failed";
    private static final String PLAN_STATUS_PARTIAL = "partial";
    private static final String PLAN_STATUS_SKIPPED = "skipped";
    private static final String PLAN_ERROR_CPU_UNAVAILABLE = "cpu_unavailable";
    private static final String PLAN_ERROR_ROUTE_MISSING = "route_missing";
    private static final String PLAN_ERROR_RECIPE_INVALID = "recipe_invalid";
    private static final String PLAN_ERROR_MATERIAL_MISSING = "material_missing";
    private static final String PLAN_ERROR_BUFFER_FULL = "buffer_full";
    private static final String PLAN_ERROR_OUTPUT_FULL = "output_full";
    private static final String PLAN_ERROR_INTERMEDIATE_CONTAMINATED = "intermediate_contaminated";
    private static final String PLAN_ERROR_INTERMEDIATE_MISSING = "intermediate_missing";
    private static final String PLAN_ERROR_UPSTREAM_FAILED = "upstream_failed";
    private static final String PLAN_ERROR_UPSTREAM_BLOCKED = "upstream_blocked";
    private static final String PLAN_ERROR_INTERNAL = "internal_error";
    private static final int MAX_RECORDED_OUTPUT_ENTRIES = 256;
    private static final int MAX_RECORDED_PLAN_STEPS = 256;
    private static final Map<Class<?>, Map<String, List<ExportedMethod>>> EXPORTED_METHOD_CACHE = new ConcurrentHashMap<>();
    private static final String HOST_DIAGNOSTIC_CHANNEL = "diagnostic";
    private static final String HOST_DIAGNOSTIC_SUMMARY_TITLE = "Python host bridge";
    private static final String HOST_DIAGNOSTIC_SUMMARY_TEXT = "Server-thread marshalled device/world calls during this execution.";
    private static final String HOST_DIAGNOSTIC_TABLE_TITLE = "Slowest host calls";
    private static final String HOST_DIAGNOSTIC_TABLE_TEXT = "Top bridged Python host methods by total roundtrip time.";
    private static final String PY_INDENT_2 = "        ";
    private static final String PY_INDENT_3 = "            ";

    private final ServerLevel level;
    private final String computerName;
    private final String computerPosition;
    private final BlockPos computerBlockPos;
    private final Map<String, PythonPeripheralBinding> bindingsByApiName;
    private final String bootstrapBindSource;
    private final WorldBridge worldBridge;
    private final Object exportedWorldBridge;
    private final Map<String, Object> exportedDeviceBridgesByApiName;
    private final Object exportedRootBridge;
    private final List<ComputerOutputEntry> outputEntries = new ArrayList<>();
    private final List<ComputerPlanStepSnapshot> planStepSnapshots = new ArrayList<>();
    private final Map<String, HostCallMetric> hostCallMetrics = new LinkedHashMap<>();
    private ComputerPlanJobSnapshot planJobSnapshot = ComputerPlanJobSnapshot.empty();
    private PythonExecutionTranscript executionTranscript;
    private boolean hostCallDiagnosticsPrepared;

    private PythonHostApi(final ServerLevel level, final String computerName, final BlockPos computerPos, final List<PythonPeripheralBinding> peripherals) {
        this.level = level;
        this.computerName = computerName;
        this.computerBlockPos = computerPos == null ? BlockPos.ZERO : computerPos.immutable();
        this.computerPosition = computerPos.toShortString();

        final Map<String, PythonPeripheralBinding> bindings = new LinkedHashMap<>();
        for (final PythonPeripheralBinding peripheral : peripherals) {
            bindings.put(peripheral.apiName(), peripheral);
        }
        this.bindingsByApiName = Map.copyOf(bindings);
        this.bootstrapBindSource = this.buildBootstrapBindSource(bindings);
        this.worldBridge = level == null ? UNAVAILABLE_WORLD : new WorldBridge(level);
        this.exportedWorldBridge = new ExportedProxy(this.worldBridge);
        this.exportedDeviceBridgesByApiName = this.createExportedDeviceBridges(bindings);
        this.exportedRootBridge = new ExportedProxy(new BootstrapBridge());
    }

    public static PythonHostApi unavailable(final String computerName, final BlockPos computerPos, final List<PythonPeripheralBinding> peripherals) {
        return new PythonHostApi(null, computerName, computerPos, peripherals);
    }

    public static PythonHostApi server(final ServerLevel level, final String computerName, final BlockPos computerPos, final List<PythonPeripheralBinding> peripherals) {
        return new PythonHostApi(level, computerName, computerPos, peripherals);
    }

    @HostAccess.Export
    public boolean available() {
        return this.level != null;
    }

    @HostAccess.Export
    public String computerName() {
        return this.computerName;
    }

    @HostAccess.Export
    public String computerPosition() {
        return this.computerPosition;
    }

    @HostAccess.Export
    public int endpointCount() {
        return this.bindingsByApiName.size();
    }

    @HostAccess.Export
    public String networkSummary() {
        int bridgedCount = 0;
        for (final PythonPeripheralBinding binding : this.bindingsByApiName.values()) {
            if (binding.isBridged()) {
                bridgedCount++;
            }
        }

        final int localCount = this.bindingsByApiName.size() - bridgedCount;
        if (bridgedCount <= 0) {
            return "Computer " + this.computerName + " @ " + this.computerPosition + " with " + localCount + " local endpoints.";
        }
        return "Computer " + this.computerName + " @ " + this.computerPosition + " with " + localCount + " local and " + bridgedCount + " bridged endpoints.";
    }

    @HostAccess.Export
    public List<String> deviceNames() {
        return List.copyOf(this.bindingsByApiName.keySet());
    }

    @HostAccess.Export
    public DeviceBridge getDevice(final String apiName) {
        if (apiName == null || apiName.isBlank()) {
            return null;
        }

        final PythonPeripheralBinding binding = this.bindingsByApiName.get(apiName);
        return binding == null ? null : new DeviceBridge(binding);
    }

    @HostAccess.Export
    public WorldBridge world() {
        return this.worldBridge;
    }

    @HostAccess.Export
    public boolean emitOutput(final String tone, final String channel, final String kind, final String title, final String text, final String payloadJson) {
        final ComputerOutputEntry outputEntry = ComputerOutputEntry.structured(tone, channel, kind, title, text, payloadJson);
        recordLimited(this.outputEntries, outputEntry, MAX_RECORDED_OUTPUT_ENTRIES);
        if (this.executionTranscript != null) {
            this.executionTranscript.recordStructuredOutput(outputEntry);
        }
        return true;
    }

    void beginExecution(final PythonExecutionTranscript executionTranscript) {
        this.outputEntries.clear();
        this.planStepSnapshots.clear();
        this.hostCallMetrics.clear();
        this.planJobSnapshot = ComputerPlanJobSnapshot.empty();
        this.executionTranscript = executionTranscript;
        this.hostCallDiagnosticsPrepared = false;
    }

    void finishExecution() {
        this.executionTranscript = null;
    }

    synchronized void prepareExecutionDiagnostics() {
        if (this.hostCallDiagnosticsPrepared || this.executionTranscript == null || this.hostCallMetrics.isEmpty()) {
            return;
        }

        this.hostCallDiagnosticsPrepared = true;

        long totalCallCount = 0L;
        long totalFailureCount = 0L;
        long totalRoundTripNanos = 0L;
        long totalServerNanos = 0L;
        long maxRoundTripNanos = 0L;
        for (final HostCallMetric metric : this.hostCallMetrics.values()) {
            totalCallCount += metric.callCount();
            totalFailureCount += metric.failureCount();
            totalRoundTripNanos += metric.totalRoundTripNanos();
            totalServerNanos += metric.totalServerNanos();
            maxRoundTripNanos = Math.max(maxRoundTripNanos, metric.maxRoundTripNanos());
        }

        if (totalCallCount <= 0L) {
            return;
        }

        final long thresholdNanos = TimeUnit.MILLISECONDS.toNanos(XLServerConfig.INSTANCE.hostCallDiagnosticsThresholdMillis());
        if (thresholdNanos > 0L && totalRoundTripNanos < thresholdNanos && maxRoundTripNanos < thresholdNanos) {
            return;
        }

        final long totalWaitNanos = Math.max(0L, totalRoundTripNanos - totalServerNanos);
        this.recordDiagnosticOutput(ComputerOutputEntry.keyValue(
                "info",
                HOST_DIAGNOSTIC_CHANNEL,
                HOST_DIAGNOSTIC_SUMMARY_TITLE,
                HOST_DIAGNOSTIC_SUMMARY_TEXT,
                List.of(
                        new ComputerOutputEntry.OutputField("Calls", Long.toString(totalCallCount)),
                        new ComputerOutputEntry.OutputField("Methods", Integer.toString(this.hostCallMetrics.size())),
                        new ComputerOutputEntry.OutputField("Roundtrip total ms", formatDurationMillis(totalRoundTripNanos)),
                        new ComputerOutputEntry.OutputField("Roundtrip max ms", formatDurationMillis(maxRoundTripNanos)),
                        new ComputerOutputEntry.OutputField("Wait total ms", formatDurationMillis(totalWaitNanos)),
                        new ComputerOutputEntry.OutputField("Failures", Long.toString(totalFailureCount))
                )));

        final ArrayList<Map.Entry<String, HostCallMetric>> rankedMetrics = new ArrayList<>(this.hostCallMetrics.entrySet());
        rankedMetrics.sort((left, right) -> {
            final int byTotal = Long.compare(right.getValue().totalRoundTripNanos(), left.getValue().totalRoundTripNanos());
            if (byTotal != 0) {
                return byTotal;
            }

            final int byMax = Long.compare(right.getValue().maxRoundTripNanos(), left.getValue().maxRoundTripNanos());
            if (byMax != 0) {
                return byMax;
            }
            return left.getKey().compareTo(right.getKey());
        });

        final ArrayList<List<String>> rows = new ArrayList<>();
        final int maxRows = XLServerConfig.INSTANCE.hostCallDiagnosticsTopEntries();
        for (int index = 0; index < rankedMetrics.size() && index < maxRows; index++) {
            final Map.Entry<String, HostCallMetric> entry = rankedMetrics.get(index);
            final HostCallMetric metric = entry.getValue();
            rows.add(List.of(
                    entry.getKey(),
                    Long.toString(metric.callCount()),
                    formatDurationMillis(metric.totalRoundTripNanos()),
                    formatDurationMillis(metric.averageRoundTripNanos()),
                    formatDurationMillis(metric.maxRoundTripNanos()),
                    formatDurationMillis(metric.averageWaitNanos()),
                    Long.toString(metric.failureCount())));
        }

        if (!rows.isEmpty()) {
            this.recordDiagnosticOutput(ComputerOutputEntry.table(
                    "info",
                    HOST_DIAGNOSTIC_CHANNEL,
                    HOST_DIAGNOSTIC_TABLE_TITLE,
                    HOST_DIAGNOSTIC_TABLE_TEXT,
                    List.of("Method", "Calls", "Total ms", "Avg ms", "Max ms", "Avg wait ms", "Failed"),
                    rows));
        }
    }

    public List<ComputerOutputEntry> outputEntries() {
        return List.copyOf(this.outputEntries);
    }

    public List<ComputerPlanStepSnapshot> planStepSnapshots() {
        return List.copyOf(this.planStepSnapshots);
    }

    public ComputerPlanJobSnapshot planJobSnapshot() {
        return this.planJobSnapshot;
    }

    Object exportedBridge() {
        return this.exportedRootBridge;
    }

    Object exportedWorldBridge() {
        return this.exportedWorldBridge;
    }

    Object exportedDeviceBridge(final String apiName) {
        if (apiName == null || apiName.isBlank()) {
            return null;
        }
        return this.exportedDeviceBridgesByApiName.get(apiName);
    }

    String bootstrapBindSource() {
        return this.bootstrapBindSource;
    }

    private String buildBootstrapBindSource(final Map<String, PythonPeripheralBinding> bindings) {
        final StringBuilder builder = new StringBuilder();
        final List<String> apiNames = new ArrayList<>(bindings.size());
        builder.append("_bootstrap = {\n");
        builder.append("    'computer': {\n");
        appendPythonLiteralEntry(builder, PY_INDENT_2, "name", pythonLiteral(this.computerName));
        appendPythonLiteralEntry(builder, PY_INDENT_2, JSON_POSITION, pythonLiteral(this.computerPosition));
        appendPythonLiteralEntry(builder, PY_INDENT_2, "endpoint_count", Integer.toString(bindings.size()));
        builder.append("    },\n");
        builder.append("    'endpoints': [\n");
        for (final PythonPeripheralBinding binding : bindings.values()) {
            apiNames.add(binding.apiName());
            builder.append("        {\n");
            appendPythonLiteralEntry(builder, PY_INDENT_3, "api_name", pythonLiteral(binding.apiName()));
            appendPythonLiteralEntry(builder, PY_INDENT_3, "name", pythonLiteral(binding.displayName()));
            appendPythonLiteralEntry(builder, PY_INDENT_3, "type", pythonLiteral(binding.type()));
            appendPythonLiteralEntry(builder, PY_INDENT_3, JSON_POSITION, pythonLiteral(binding.position()));
            appendPythonLiteralEntry(builder, PY_INDENT_3, "distance", Integer.toString(binding.distance()));
            appendPythonLiteralEntry(builder, PY_INDENT_3, "scope", pythonLiteral(binding.networkScope()));
            appendPythonLiteralEntry(builder, PY_INDENT_3, "remote", pythonLiteral(binding.isBridged()));
            appendPythonLiteralEntry(builder, PY_INDENT_3, JSON_BRIDGE_NAME, pythonLiteral(binding.bridgeEndpointName()));
            appendPythonLiteralEntry(builder, PY_INDENT_3, JSON_BRIDGE_GROUP, Integer.toString(binding.bridgeUplinkGroup()));
            appendPythonLiteralEntry(builder, PY_INDENT_3, JSON_REMOTE_POLICY, pythonLiteral(remotePolicyId(binding)));
            appendPythonLiteralEntry(builder, PY_INDENT_3, JSON_REMOTE_WRITABLE, pythonLiteral(bridgeRemoteWritable(binding)));
            builder.append("        },\n");
        }
        builder.append("    ],\n");
        builder.append("}\n");
        builder.append("computer = _bootstrap['computer']\n");
        builder.append("endpoints = _bootstrap['endpoints']\n");
        builder.append("peripherals = {\n");
        for (int index = 0; index < apiNames.size(); index++) {
            builder.append("    ").append(pythonLiteral(apiNames.get(index))).append(": endpoints[").append(index).append("],\n");
        }
        builder.append("}\n");
        appendPythonStringList(builder, "endpoint_names", apiNames);
        builder.append("device_names = list(endpoint_names)\n");
        return builder.toString();
    }

    private static void appendPythonStringList(final StringBuilder builder, final String variableName, final List<String> values) {
        builder.append(variableName).append(" = [");
        if (values.isEmpty()) {
            builder.append("]\n");
            return;
        }

        builder.append("\n");
        for (final String value : values) {
            builder.append("    ").append(pythonLiteral(value)).append(",\n");
        }
        builder.append("]\n");
    }

    private static void appendPythonLiteralEntry(final StringBuilder builder,
                                                 final String indentation,
                                                 final String key,
                                                 final String literalValue) {
        builder.append(indentation)
                .append(pythonLiteral(key))
                .append(": ")
                .append(literalValue)
                .append(",\n");
    }

    private static String pythonLiteral(final String value) {
        if (value == null) {
            return "None";
        }

        final String escaped = value
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
        return '\'' + escaped + '\'';
    }

    private static String pythonLiteral(final boolean value) {
        return value ? "True" : "False";
    }

    private Map<String, Object> createExportedDeviceBridges(final Map<String, PythonPeripheralBinding> bindings) {
        final Map<String, Object> exportedDevices = new LinkedHashMap<>(bindings.size());
        for (final Map.Entry<String, PythonPeripheralBinding> entry : bindings.entrySet()) {
            exportedDevices.put(entry.getKey(), new ExportedProxy(new DeviceBridge(entry.getValue())));
        }
        return Map.copyOf(exportedDevices);
    }

    private Object exportValue(final Object value) {
        if (value instanceof DeviceBridge || value instanceof WorldBridge) {
            return new ExportedProxy(value);
        }
        return value;
    }

    private boolean requiresServerThread(final Object delegate) {
        return delegate instanceof DeviceBridge || delegate instanceof WorldBridge;
    }

    private synchronized void recordHostCallMetric(final String metricKey, final long roundTripNanos, final long serverNanos, final boolean failed) {
        if (metricKey == null || metricKey.isBlank()) {
            return;
        }

        this.hostCallMetrics.computeIfAbsent(metricKey, ignored -> new HostCallMetric()).addSample(roundTripNanos, serverNanos, failed);
    }

    private void recordDiagnosticOutput(final ComputerOutputEntry outputEntry) {
        recordLimited(this.outputEntries, outputEntry, MAX_RECORDED_OUTPUT_ENTRIES);
        if (this.executionTranscript != null) {
            this.executionTranscript.recordStructuredOutput(outputEntry);
        }
    }

    private static String formatDurationMillis(final long durationNanos) {
        return String.format(Locale.ROOT, "%.2f", durationNanos / 1_000_000.0d);
    }

    private static String hostCallMetricKey(final Object delegate, final String methodName, final int argumentCount) {
        if (delegate instanceof DeviceBridge deviceBridge) {
            return deviceBridge.binding.apiName() + "." + methodName + "/" + argumentCount;
        }
        if (delegate instanceof WorldBridge) {
            return "world." + methodName + "/" + argumentCount;
        }
        return delegate.getClass().getSimpleName() + "." + methodName + "/" + argumentCount;
    }

    private <T> T callOnLevelThread(final Callable<T> action, final String metricKey) {
        final long roundTripStartedAt = System.nanoTime();
        long serverStartedAt = roundTripStartedAt;
        long serverFinishedAt = roundTripStartedAt;
        boolean failed = false;

        try {
            if (this.level == null || this.level.getServer() == null || this.level.getServer().isSameThread()) {
                serverStartedAt = System.nanoTime();
                try {
                    return callUnchecked(action);
                } catch (final RuntimeException exception) {
                    failed = true;
                    throw exception;
                } finally {
                    serverFinishedAt = System.nanoTime();
                }
            }

            final long[] serverTiming = new long[2];
            final CompletableFuture<T> future = new CompletableFuture<>();
            this.level.getServer().execute(() -> {
                serverTiming[0] = System.nanoTime();
                try {
                    future.complete(action.call());
                } catch (final Throwable throwable) {
                    future.completeExceptionally(throwable);
                } finally {
                    serverTiming[1] = System.nanoTime();
                }
            });

            try {
                return future.get();
            } catch (final InterruptedException exception) {
                Thread.currentThread().interrupt();
                failed = true;
                throw new IllegalStateException("Interrupted while waiting for a server-thread Python host call.", exception);
            } catch (final ExecutionException exception) {
                failed = true;
                throw rethrowUnchecked(exception.getCause());
            } finally {
                if (serverTiming[0] != 0L) {
                    serverStartedAt = serverTiming[0];
                }
                if (serverTiming[1] != 0L) {
                    serverFinishedAt = serverTiming[1];
                }
            }
        } finally {
            final long roundTripDurationNanos = Math.max(0L, System.nanoTime() - roundTripStartedAt);
            final long serverDurationNanos = serverFinishedAt >= serverStartedAt ? serverFinishedAt - serverStartedAt : 0L;
            final long waitDurationNanos = Math.max(0L, roundTripDurationNanos - serverDurationNanos);
            this.recordHostCallMetric(metricKey, roundTripDurationNanos, serverDurationNanos, failed);
            XLRuntimeDebugger.recordDuration("python.host.roundtrip." + metricKey, roundTripDurationNanos);
            XLRuntimeDebugger.recordDuration("python.host.wait." + metricKey, waitDurationNanos);
            XLRuntimeDebugger.recordDuration("python.host.server." + metricKey, serverDurationNanos);
        }
    }

    private static <T> T callUnchecked(final Callable<T> action) {
        try {
            return action.call();
        } catch (final RuntimeException exception) {
            throw exception;
        } catch (final Exception exception) {
            throw new IllegalStateException("Python host call failed.", exception);
        }
    }

    private static RuntimeException rethrowUnchecked(final Throwable throwable) {
        if (throwable instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        if (throwable instanceof Error error) {
            throw error;
        }
        return new IllegalStateException("Python host call failed.", throwable);
    }

    private static Map<String, List<ExportedMethod>> exportedMethods(final Class<?> type) {
        return EXPORTED_METHOD_CACHE.computeIfAbsent(type, PythonHostApi::scanExportedMethods);
    }

    private static Map<String, List<ExportedMethod>> scanExportedMethods(final Class<?> type) {
        final Map<String, List<ExportedMethod>> exportedMethods = new LinkedHashMap<>();
        for (final Method method : type.getMethods()) {
            if (!method.isAnnotationPresent(HostAccess.Export.class)) {
                continue;
            }

            final String methodName = method.getName();
            exportedMethods.computeIfAbsent(methodName, ignored -> new ArrayList<>()).add(new ExportedMethod(method));
        }

        final Map<String, List<ExportedMethod>> immutableMethods = new LinkedHashMap<>(exportedMethods.size());
        for (final Map.Entry<String, List<ExportedMethod>> entry : exportedMethods.entrySet()) {
            immutableMethods.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(immutableMethods);
    }

    private static Object convertArgument(final Value value, final Class<?> targetType) {
        if (value == null || value.isNull()) {
            if (targetType.isPrimitive()) {
                throw new IllegalArgumentException("Primitive Python host arguments cannot be null.");
            }
            return null;
        }
        if (targetType == String.class) {
            return value.asString();
        }
        if (targetType == int.class || targetType == Integer.class) {
            return value.asInt();
        }
        if (targetType == long.class || targetType == Long.class) {
            return value.asLong();
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            return value.asBoolean();
        }
        if (targetType == double.class || targetType == Double.class) {
            return value.asDouble();
        }
        return value.as(targetType);
    }

    private record ExportedMethod(Method method) {
        private int parameterCount() {
            return this.method.getParameterCount();
        }

        private Object invokeRaw(final Object delegate, final Value[] arguments) {
            final Class<?>[] parameterTypes = this.method.getParameterTypes();
            if (arguments.length != parameterTypes.length) {
                throw new IllegalArgumentException("Expected " + parameterTypes.length + " Python host arguments for '" + this.method.getName() + "' but got " + arguments.length + ".");
            }

            final Object[] convertedArguments = new Object[parameterTypes.length];
            for (int index = 0; index < parameterTypes.length; index++) {
                convertedArguments[index] = convertArgument(arguments[index], parameterTypes[index]);
            }

            try {
                return this.method.invoke(delegate, convertedArguments);
            } catch (final InvocationTargetException exception) {
                throw rethrowUnchecked(exception.getCause());
            } catch (final IllegalAccessException exception) {
                throw new IllegalStateException("Failed to invoke exported Python host method '" + this.method.getName() + "'.", exception);
            }
        }
    }

    private final class BootstrapBridge {
        @HostAccess.Export
        public boolean available() {
            return PythonHostApi.this.available();
        }

        @HostAccess.Export
        public String computerName() {
            return PythonHostApi.this.computerName();
        }

        @HostAccess.Export
        public String computerPosition() {
            return PythonHostApi.this.computerPosition();
        }

        @HostAccess.Export
        public int endpointCount() {
            return PythonHostApi.this.endpointCount();
        }

        @HostAccess.Export
        public String networkSummary() {
            return PythonHostApi.this.networkSummary();
        }

        @HostAccess.Export
        public Object getDevice(final String apiName) {
            if (apiName == null || apiName.isBlank()) {
                return null;
            }
            return PythonHostApi.this.exportedDeviceBridgesByApiName.get(apiName);
        }

        @HostAccess.Export
        public Object world() {
            return PythonHostApi.this.exportedWorldBridge;
        }

        @HostAccess.Export
        public String bootstrapExtensionSource(final String name) {
            return GraalPythonRuntime.bootstrapExtensionSource(name);
        }

        @HostAccess.Export
        public String bootstrapBindSource() {
            return PythonHostApi.this.bootstrapBindSource;
        }

        @HostAccess.Export
        public boolean emitOutput(final String tone, final String channel, final String kind, final String title, final String text, final String payloadJson) {
            return PythonHostApi.this.emitOutput(tone, channel, kind, title, text, payloadJson);
        }
    }

    private final class ExportedProxy implements ProxyObject {
        private final Object delegate;
        private final Map<String, List<ExportedMethod>> exportedMethods;

        private ExportedProxy(final Object delegate) {
            this.delegate = delegate;
            this.exportedMethods = exportedMethods(delegate.getClass());
        }

        @Override
        public Object getMember(final String key) {
            final List<ExportedMethod> matchingMethods = this.exportedMethods.get(key);
            if (matchingMethods == null || matchingMethods.isEmpty()) {
                return null;
            }

            return (ProxyExecutable) arguments -> this.invoke(key, matchingMethods, arguments);
        }

        @Override
        public Object getMemberKeys() {
            return this.exportedMethods.keySet().toArray(String[]::new);
        }

        @Override
        public boolean hasMember(final String key) {
            return this.exportedMethods.containsKey(key);
        }

        @Override
        public void putMember(final String key, final Value value) {
            throw new UnsupportedOperationException("Python host proxies are read-only.");
        }

        private Object invoke(final String methodName, final List<ExportedMethod> exportedMethods, final Value[] arguments) {
            final ExportedMethod resolvedMethod = this.resolveMethod(methodName, exportedMethods, arguments);
            final Callable<Object> invocation = () -> PythonHostApi.this.exportValue(resolvedMethod.invokeRaw(this.delegate, arguments));
            if (PythonHostApi.this.requiresServerThread(this.delegate)) {
                return PythonHostApi.this.callOnLevelThread(invocation, hostCallMetricKey(this.delegate, methodName, resolvedMethod.parameterCount()));
            }
            return callUnchecked(invocation);
        }

        private ExportedMethod resolveMethod(final String methodName, final List<ExportedMethod> exportedMethods, final Value[] arguments) {
            ExportedMethod compatibleMethod = null;
            for (final ExportedMethod exportedMethod : exportedMethods) {
                if (exportedMethod.parameterCount() != arguments.length) {
                    continue;
                }

                if (compatibleMethod != null) {
                    throw new IllegalStateException("Ambiguous overloaded Python host method '" + methodName + "' with " + arguments.length + " arguments on " + this.delegate.getClass().getName() + ".");
                }
                compatibleMethod = exportedMethod;
            }

            if (compatibleMethod != null) {
                return compatibleMethod;
            }

            throw new IllegalArgumentException("No compatible Python host method '" + methodName + "' with " + arguments.length + " arguments on " + this.delegate.getClass().getName() + ".");
        }
    }

    private static final class HostCallMetric {
        private long callCount;
        private long failureCount;
        private long totalRoundTripNanos;
        private long totalServerNanos;
        private long maxRoundTripNanos;

        private void addSample(final long roundTripNanos, final long serverNanos, final boolean failed) {
            final long safeRoundTripNanos = Math.max(0L, roundTripNanos);
            final long safeServerNanos = Math.max(0L, Math.min(safeRoundTripNanos, serverNanos));
            this.callCount++;
            if (failed) {
                this.failureCount++;
            }
            this.totalRoundTripNanos += safeRoundTripNanos;
            this.totalServerNanos += safeServerNanos;
            this.maxRoundTripNanos = Math.max(this.maxRoundTripNanos, safeRoundTripNanos);
        }

        private long callCount() {
            return this.callCount;
        }

        private long failureCount() {
            return this.failureCount;
        }

        private long totalRoundTripNanos() {
            return this.totalRoundTripNanos;
        }

        private long totalServerNanos() {
            return this.totalServerNanos;
        }

        private long maxRoundTripNanos() {
            return this.maxRoundTripNanos;
        }

        private long averageRoundTripNanos() {
            return this.callCount <= 0L ? 0L : this.totalRoundTripNanos / this.callCount;
        }

        private long averageWaitNanos() {
            return this.callCount <= 0L ? 0L : Math.max(0L, this.totalRoundTripNanos - this.totalServerNanos) / this.callCount;
        }
    }

    private static <T> void recordLimited(final List<T> target, final T entry, final int maxEntries) {
        target.add(entry);
        while (target.size() > maxEntries) {
            target.remove(0);
        }
    }

    private static Direction resolveSide(final String rawSide) {
        final Direction direction = rawSide == null ? null : Direction.byName(rawSide.toLowerCase(Locale.ROOT));
        if (direction == null) {
            throw new IllegalArgumentException("Unknown side '" + rawSide + "'. Expected one of down, up, north, south, west, east.");
        }
        return direction;
    }

    private static Direction resolveSide(final String rawSide, final NamedNetworkEndpointBlockEntity endpoint) {
        if (endpoint != null) {
            final Direction namedSide = endpoint.resolveNamedSide(rawSide);
            if (namedSide != null) {
                return namedSide;
            }
        }
        return resolveSide(rawSide);
    }

    private static RedstoneIOMode resolveRedstoneMode(final String rawMode) {
        if (rawMode == null || rawMode.isBlank()) {
            throw new IllegalArgumentException("Redstone mode must be 'input' or 'output'.");
        }

        try {
            return RedstoneIOMode.valueOf(rawMode.trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported redstone mode '" + rawMode + "'. Expected 'input' or 'output'.", exception);
        }
    }

    private static MaterialIOMode resolveMaterialMode(final String rawMode) {
        if (rawMode == null || rawMode.isBlank()) {
            throw new IllegalArgumentException("Material I/O mode must be 'items_only', 'fluids_only' or 'hybrid'.");
        }

        try {
            return MaterialIOMode.valueOf(rawMode.trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported material I/O mode '" + rawMode + "'.", exception);
        }
    }

    public final class DeviceBridge {
        private final PythonPeripheralBinding binding;

        private DeviceBridge(final PythonPeripheralBinding binding) {
            this.binding = binding;
        }

        @HostAccess.Export
        public String apiName() {
            return this.binding.apiName();
        }

        @HostAccess.Export
        public String name() {
            final NamedNetworkEndpointBlockEntity endpoint = this.resolveEndpoint();
            return endpoint == null ? this.binding.displayName() : endpoint.getEndpointName();
        }

        @HostAccess.Export
        public String type() {
            return this.binding.type();
        }

        @HostAccess.Export
        public String position() {
            return this.binding.position();
        }

        @HostAccess.Export
        public int distance() {
            return this.binding.distance();
        }

        @HostAccess.Export
        public String networkScope() {
            return this.binding.networkScope();
        }

        @HostAccess.Export
        public boolean isRemote() {
            return this.binding.isBridged();
        }

        @HostAccess.Export
        public String bridgeEndpointName() {
            return this.binding.bridgeEndpointName();
        }

        @HostAccess.Export
        public int bridgeUplinkGroup() {
            return this.binding.bridgeUplinkGroup();
        }

        @HostAccess.Export
        public String remotePolicy() {
            return remotePolicyId(this.binding);
        }

        @HostAccess.Export
        public boolean remoteWritable() {
            return bridgeRemoteWritable(this.binding);
        }

        @HostAccess.Export
        public boolean online() {
            return this.resolveEndpoint() != null;
        }

        @HostAccess.Export
        public String summary() {
            final String summary = this.name() + " [" + this.type() + "] @ " + this.position();
            if (!this.binding.isBridged()) {
                return summary;
            }
            return summary + " via " + this.binding.bridgeEndpointName() + " (group " + this.binding.bridgeUplinkGroup() + ")";
        }

        @HostAccess.Export
        public String rename(final String rawName) {
            this.requireLocalOnly("endpoint renaming");
            final NamedNetworkEndpointBlockEntity endpoint = this.requireEndpoint();
            return endpoint.setEndpointName(rawName);
        }

        @HostAccess.Export
        public String sideAliasesJson() {
            final NamedNetworkEndpointBlockEntity endpoint = this.requireEndpoint();
            final JsonObject object = new JsonObject();
            if (!endpoint.supportsSideNaming()) {
                return object.toString();
            }

            for (final Direction direction : Direction.values()) {
                final String alias = endpoint.getSideAlias(direction);
                if (!alias.isBlank()) {
                    object.addProperty(direction.getSerializedName(), alias);
                }
            }
            return object.toString();
        }

        @HostAccess.Export
        public String describeState() {
            final NamedNetworkEndpointBlockEntity endpoint = this.requireEndpoint();
            if (endpoint instanceof ScreenBlockEntity screen) {
                return screen.describeState();
            }
            if (endpoint instanceof RedstoneIOBlockEntity redstoneIo) {
                return redstoneIo.describeState();
            }
            if (endpoint instanceof MaterialIOBlockEntity materialIo) {
                return materialIo.describeState();
            }
            if (endpoint instanceof CraftingIOBlockEntity craftingIo) {
                return craftingIo.describeState();
            }
            if (endpoint instanceof CraftingCPUBlockEntity craftingCpu) {
                return craftingCpu.describeState();
            }
            if (endpoint instanceof XLApiBlockEntity xlApi) {
                return xlApi.describeState();
            }
            if (TYPE_LIGHT_SENSOR.equals(this.type())) {
                return ENDPOINT_PREFIX + this.name() + " | Light level: " + this.lightLevel();
            }
            if (TYPE_RAIN_SENSOR.equals(this.type())) {
                return ENDPOINT_PREFIX + this.name() + " | Raining: " + this.isRaining();
            }
            if (TYPE_CLOCK.equals(this.type())) {
                return ENDPOINT_PREFIX + this.name() + " | Game time: " + this.dayTime() + " | Real time: " + this.realTime();
            }
            return ENDPOINT_PREFIX + this.name() + " | type: " + this.type();
        }

        @HostAccess.Export
        public boolean emitScreenOutput(final String tone, final String channel, final String kind, final String title, final String text, final String payloadJson) {
            this.requireLocalOnly("screen output");
            final NamedNetworkEndpointBlockEntity endpoint = this.requireEndpoint();
            if (!(endpoint instanceof ScreenBlockEntity screen)) {
                throw this.unsupported("screen output");
            }
            return screen.emitTargetOutput(ComputerOutputEntry.structured(tone, channel, kind, title, text, payloadJson));
        }

        @HostAccess.Export
        public boolean clearScreenOutput() {
            this.requireLocalOnly("screen output clearing");
            final NamedNetworkEndpointBlockEntity endpoint = this.requireEndpoint();
            if (!(endpoint instanceof ScreenBlockEntity screen)) {
                throw this.unsupported("screen output clearing");
            }
            return screen.clearTargetOutput();
        }

        @HostAccess.Export
        public String getMode() {
            final NamedNetworkEndpointBlockEntity endpoint = this.requireEndpoint();
            if (endpoint instanceof RedstoneIOBlockEntity redstoneIo) {
                return redstoneIo.getMode().name().toLowerCase(Locale.ROOT);
            }
            if (endpoint instanceof MaterialIOBlockEntity materialIo) {
                return materialIo.getMode().name().toLowerCase(Locale.ROOT);
            }
            throw this.unsupported("mode access");
        }

        @HostAccess.Export
        public String setMode(final String rawMode) {
            final NamedNetworkEndpointBlockEntity endpoint = this.requireEndpoint();
            if (endpoint instanceof RedstoneIOBlockEntity redstoneIo) {
                this.requireRemoteWriteAllowed(ACTION_MODE_CHANGES);
                redstoneIo.setMode(resolveRedstoneMode(rawMode));
                return redstoneIo.getMode().name().toLowerCase(Locale.ROOT);
            }
            if (endpoint instanceof MaterialIOBlockEntity materialIo) {
                this.requireRemoteWriteAllowed(ACTION_MODE_CHANGES);
                materialIo.setMode(resolveMaterialMode(rawMode));
                return materialIo.getMode().name().toLowerCase(Locale.ROOT);
            }
            throw this.unsupported(ACTION_MODE_CHANGES);
        }

        @HostAccess.Export
        public int read(final String sideName) {
            final NamedNetworkEndpointBlockEntity endpoint = this.requireEndpoint();
            if (endpoint instanceof RedstoneIOBlockEntity redstoneIo) {
                if (redstoneIo.getMode() == RedstoneIOMode.INPUT) {
                    redstoneIo.captureInputs();
                }
                return redstoneIo.getSideLevel(resolveSide(sideName, redstoneIo));
            }
            throw this.unsupported("redstone reads");
        }

        @HostAccess.Export
        public int write(final String sideName, final int level) {
            final NamedNetworkEndpointBlockEntity endpoint = this.requireEndpoint();
            if (endpoint instanceof RedstoneIOBlockEntity redstoneIo) {
                this.requireRemoteWriteAllowed("redstone writes");
                return redstoneIo.setSideLevel(resolveSide(sideName, redstoneIo), Mth.clamp(level, 0, 15));
            }
            throw this.unsupported("redstone writes");
        }

        @HostAccess.Export
        public int channel(final String sideName) {
            final NamedNetworkEndpointBlockEntity endpoint = this.requireEndpoint();
            if (endpoint instanceof RedstoneIOBlockEntity redstoneIo) {
                return redstoneIo.getBusChannel(resolveSide(sideName, redstoneIo));
            }
            throw this.unsupported("bus channel reads");
        }

        @HostAccess.Export
        public int setChannel(final String sideName, final int channel) {
            final NamedNetworkEndpointBlockEntity endpoint = this.requireEndpoint();
            if (endpoint instanceof RedstoneIOBlockEntity redstoneIo) {
                this.requireRemoteWriteAllowed("bus channel writes");
                return redstoneIo.setBusChannel(resolveSide(sideName, redstoneIo), Math.max(0, Math.min(15, channel)));
            }
            throw this.unsupported("bus channel writes");
        }

        @HostAccess.Export
        public int lightLevel() {
            this.requireType(TYPE_LIGHT_SENSOR, "light level sampling");
            final ServerLevel serverLevel = this.requireLevel();
            return Mth.clamp(serverLevel.getMaxLocalRawBrightness(this.binding.blockPos()), 0, 15);
        }

        @HostAccess.Export
        public boolean isRaining() {
            this.requireType(TYPE_RAIN_SENSOR, "rain checks");
            final ServerLevel serverLevel = this.requireLevel();
            return serverLevel.isRaining() && serverLevel.canSeeSky(this.binding.blockPos().above());
        }

        @HostAccess.Export
        public int rainLevel() {
            return this.isRaining() ? 15 : 0;
        }

        @HostAccess.Export
        public long gameTime() {
            this.requireType(TYPE_CLOCK, "time sampling");
            return this.requireLevel().getGameTime();
        }

        @HostAccess.Export
        public long dayTime() {
            this.requireType(TYPE_CLOCK, "time sampling");
            return this.requireLevel().getDayTime() % 24000L;
        }

        @HostAccess.Export
        public String realTime() {
            this.requireType(TYPE_CLOCK, "real time sampling");
            return REAL_TIME_FORMATTER.format(Instant.now());
        }

        @HostAccess.Export
        public int gridWidth() {
            final NamedNetworkEndpointBlockEntity endpoint = this.requireEndpoint();
            if (endpoint instanceof CraftingIOBlockEntity craftingIo) {
                return craftingIo.getGridWidth();
            }
            throw this.unsupported("grid width access");
        }

        @HostAccess.Export
        public int gridHeight() {
            final NamedNetworkEndpointBlockEntity endpoint = this.requireEndpoint();
            if (endpoint instanceof CraftingIOBlockEntity craftingIo) {
                return craftingIo.getGridHeight();
            }
            throw this.unsupported("grid height access");
        }

        @HostAccess.Export
        public int setGridSize(final int size) {
            final NamedNetworkEndpointBlockEntity endpoint = this.requireEndpoint();
            if (endpoint instanceof CraftingIOBlockEntity craftingIo) {
                this.requireRemoteWriteAllowed("grid size changes");
                craftingIo.setGridSize(size);
                return craftingIo.getGridWidth();
            }
            throw this.unsupported("grid size changes");
        }

        @HostAccess.Export
        public int gridSlotCount() {
            final NamedNetworkEndpointBlockEntity endpoint = this.requireEndpoint();
            if (endpoint instanceof CraftingIOBlockEntity craftingIo) {
                return craftingIo.getGridSlotCount();
            }
            throw this.unsupported("grid slot access");
        }

        @HostAccess.Export
        public String gridItemId(final int slot) {
            final NamedNetworkEndpointBlockEntity endpoint = this.requireEndpoint();
            if (endpoint instanceof CraftingIOBlockEntity craftingIo) {
                return craftingIo.getGridItemId(slot);
            }
            throw this.unsupported("grid item lookup");
        }

        @HostAccess.Export
        public int gridItemCount(final int slot) {
            final NamedNetworkEndpointBlockEntity endpoint = this.requireEndpoint();
            if (endpoint instanceof CraftingIOBlockEntity craftingIo) {
                return craftingIo.getGridItemCount(slot);
            }
            throw this.unsupported("grid count lookup");
        }

        @HostAccess.Export
        public boolean setGridSlot(final int slot, final String itemId, final int count) {
            final CraftingIOBlockEntity craftingIo = this.requireCraftingIo();
            this.requireRemoteWriteAllowed("grid slot changes");
            craftingIo.setGridSlot(slot, itemId, count);
            return true;
        }

        @HostAccess.Export
        public boolean clearGrid() {
            final CraftingIOBlockEntity craftingIo = this.requireCraftingIo();
            this.requireRemoteWriteAllowed("grid clearing");
            craftingIo.clearGrid();
            return true;
        }

        @HostAccess.Export
        public int routeCount() {
            return this.requireCraftingIo().getRouteCount();
        }

        @HostAccess.Export
        public String routeName(final int index) {
            return this.requireCraftingIo().getRouteName(index);
        }

        @HostAccess.Export
        public String routeDevice(final int index) {
            return this.requireCraftingIo().getRouteEndpoint(index);
        }

        @HostAccess.Export
        public String routeSide(final int index) {
            return this.requireCraftingIo().getRouteSide(index).getSerializedName();
        }

        @HostAccess.Export
        public String setRoute(final String routeName, final String apiName, final String sideName) {
            this.requireRemoteWriteAllowed("route changes");
            return this.requireCraftingIo().setRoute(routeName, apiName, this.resolveReachableSideAlias(apiName, sideName));
        }

        @HostAccess.Export
        public boolean clearRoute(final String routeName) {
            this.requireRemoteWriteAllowed("route clearing");
            return this.requireCraftingIo().clearRoute(routeName);
        }

        @HostAccess.Export
        public boolean clearRoutes() {
            final CraftingIOBlockEntity craftingIo = this.requireCraftingIo();
            this.requireRemoteWriteAllowed("route clearing");
            craftingIo.clearRoutes();
            return true;
        }

        @HostAccess.Export
        public int windowX() {
            return this.requireCraftingIo().getWindowX();
        }

        @HostAccess.Export
        public int windowY() {
            return this.requireCraftingIo().getWindowY();
        }

        @HostAccess.Export
        public boolean setWindowOrigin(final int windowX, final int windowY) {
            final CraftingIOBlockEntity craftingIo = this.requireCraftingIo();
            this.requireRemoteWriteAllowed("window origin changes");
            craftingIo.setWindowOrigin(windowX, windowY);
            return true;
        }

        @HostAccess.Export
        public String linkedCpu() {
            return this.requireCraftingIo().getLinkedCpuEndpoint();
        }

        @HostAccess.Export
        public String setLinkedCpu(final String apiName) {
            final CraftingIOBlockEntity craftingIo = this.requireCraftingIo();
            this.requireRemoteWriteAllowed("linked CPU changes");
            craftingIo.setLinkedCpuEndpoint(apiName);
            return craftingIo.getLinkedCpuEndpoint();
        }

        @HostAccess.Export
        public String materialInputDevice() {
            return this.requireCraftingIo().getMaterialInputEndpoint();
        }

        @HostAccess.Export
        public String setMaterialInputDevice(final String apiName) {
            final CraftingIOBlockEntity craftingIo = this.requireCraftingIo();
            this.requireRemoteWriteAllowed("material input device changes");
            craftingIo.setMaterialInputEndpoint(apiName);
            return craftingIo.getMaterialInputEndpoint();
        }

        @HostAccess.Export
        public String materialInputSide() {
            return this.requireCraftingIo().getMaterialInputSide().getSerializedName();
        }

        @HostAccess.Export
        public String setMaterialInputSide(final String sideName) {
            final CraftingIOBlockEntity craftingIo = this.requireCraftingIo();
            this.requireRemoteWriteAllowed("material input side changes");
            craftingIo.setMaterialInputSide(this.resolveReachableSideAlias(craftingIo.getMaterialInputEndpoint(), sideName));
            return craftingIo.getMaterialInputSide().getSerializedName();
        }

        @HostAccess.Export
        public String materialOutputDevice() {
            return this.requireCraftingIo().getMaterialOutputEndpoint();
        }

        @HostAccess.Export
        public String setMaterialOutputDevice(final String apiName) {
            final CraftingIOBlockEntity craftingIo = this.requireCraftingIo();
            this.requireRemoteWriteAllowed("material output device changes");
            craftingIo.setMaterialOutputEndpoint(apiName);
            return craftingIo.getMaterialOutputEndpoint();
        }

        @HostAccess.Export
        public String materialOutputSide() {
            return this.requireCraftingIo().getMaterialOutputSide().getSerializedName();
        }

        @HostAccess.Export
        public String setMaterialOutputSide(final String sideName) {
            final CraftingIOBlockEntity craftingIo = this.requireCraftingIo();
            this.requireRemoteWriteAllowed("material output side changes");
            craftingIo.setMaterialOutputSide(this.resolveReachableSideAlias(craftingIo.getMaterialOutputEndpoint(), sideName));
            return craftingIo.getMaterialOutputSide().getSerializedName();
        }

        @HostAccess.Export
        public boolean applyRecipeWindow() {
            final CraftingIOBlockEntity craftingIo = this.requireCraftingIo();
            this.requireRemoteWriteAllowed("recipe window application");
            craftingIo.pushRecipeWindowTo(this.requireCraftingCpu(craftingIo.getLinkedCpuEndpoint(), "recipe window application", true));
            return true;
        }

        @HostAccess.Export
        public String linkedPreviewRecipeId() {
            final CraftingIOBlockEntity craftingIo = this.requireCraftingIo();
            final CraftingCPUBlockEntity craftingCpu = this.resolveCraftingCpu(craftingIo.getLinkedCpuEndpoint());
            return craftingCpu == null ? "" : craftingCpu.getPreviewRecipeIdForPattern(craftingIo.copyActiveRecipeWindow());
        }

        @HostAccess.Export
        public String linkedPreviewResultItem() {
            final CraftingIOBlockEntity craftingIo = this.requireCraftingIo();
            final CraftingCPUBlockEntity craftingCpu = this.resolveCraftingCpu(craftingIo.getLinkedCpuEndpoint());
            return craftingCpu == null ? "" : craftingCpu.getPreviewResultItemIdForPattern(craftingIo.copyActiveRecipeWindow());
        }

        @HostAccess.Export
        public int linkedPreviewResultCount() {
            final CraftingIOBlockEntity craftingIo = this.requireCraftingIo();
            final CraftingCPUBlockEntity craftingCpu = this.resolveCraftingCpu(craftingIo.getLinkedCpuEndpoint());
            return craftingCpu == null ? 0 : craftingCpu.getPreviewResultCountForPattern(craftingIo.copyActiveRecipeWindow());
        }

        @HostAccess.Export
        public int craftLinked(final int crafts) {
            final CraftingIOBlockEntity craftingIo = this.requireCraftingIo();
            this.requireRemoteWriteAllowed("linked craft execution");
            final CraftingCPUBlockEntity craftingCpu = this.requireCraftingCpu(craftingIo.getLinkedCpuEndpoint(), "linked crafting", true);
            final MaterialIOBlockEntity inputEndpoint = this.requireMaterialIo(craftingIo.getMaterialInputEndpoint(), "linked crafting input", true);
            final MaterialIOBlockEntity outputEndpoint = this.requireMaterialIo(craftingIo.getMaterialOutputEndpoint(), "linked crafting output", true);
            final IItemHandler inputHandler = this.requireItemInputHandler(inputEndpoint, craftingIo.getMaterialInputSide(), "linked crafting input");
            final IItemHandler outputHandler = this.requireItemOutputHandler(outputEndpoint, craftingIo.getMaterialOutputSide(), "linked crafting output");

            craftingIo.pushRecipeWindowTo(craftingCpu);
            return craftingCpu.craftWithHandlers(inputHandler, outputHandler, crafts);
        }

        @HostAccess.Export
        public int planStepCount() {
            return this.requireCraftingIo().getPlanStepCount();
        }

        @HostAccess.Export
        public int planStepWindowX(final int index) {
            return this.requireCraftingIo().getPlanStepWindowX(index);
        }

        @HostAccess.Export
        public int planStepWindowY(final int index) {
            return this.requireCraftingIo().getPlanStepWindowY(index);
        }

        @HostAccess.Export
        public int planStepCrafts(final int index) {
            return this.requireCraftingIo().getPlanStepCrafts(index);
        }

        @HostAccess.Export
        public String planStepInputRoute(final int index) {
            return this.requireCraftingIo().getPlanStepInputRoute(index);
        }

        @HostAccess.Export
        public String planStepOutputRoute(final int index) {
            return this.requireCraftingIo().getPlanStepOutputRoute(index);
        }

        @HostAccess.Export
        public boolean appendPlanStep(final int windowX, final int windowY, final int crafts) {
            return this.appendPlanStep(windowX, windowY, crafts, "", "");
        }

        @HostAccess.Export
        public boolean appendPlanStep(final int windowX, final int windowY, final int crafts, final String inputRouteName, final String outputRouteName) {
            final CraftingIOBlockEntity craftingIo = this.requireCraftingIo();
            this.requireRemoteWriteAllowed(ACTION_PLAN_CHANGES);
            craftingIo.appendPlanStep(windowX, windowY, crafts, inputRouteName, outputRouteName);
            return true;
        }

        @HostAccess.Export
        public boolean setPlanStep(final int index, final int windowX, final int windowY, final int crafts) {
            return this.setPlanStep(index, windowX, windowY, crafts, "", "");
        }

        @HostAccess.Export
        public boolean setPlanStep(final int index, final int windowX, final int windowY, final int crafts, final String inputRouteName, final String outputRouteName) {
            final CraftingIOBlockEntity craftingIo = this.requireCraftingIo();
            this.requireRemoteWriteAllowed(ACTION_PLAN_CHANGES);
            craftingIo.setPlanStep(index, windowX, windowY, crafts, inputRouteName, outputRouteName);
            return true;
        }

        @HostAccess.Export
        public boolean removePlanStep(final int index) {
            final CraftingIOBlockEntity craftingIo = this.requireCraftingIo();
            this.requireRemoteWriteAllowed(ACTION_PLAN_CHANGES);
            craftingIo.removePlanStep(index);
            return true;
        }

        @HostAccess.Export
        public boolean clearCraftPlan() {
            final CraftingIOBlockEntity craftingIo = this.requireCraftingIo();
            this.requireRemoteWriteAllowed(ACTION_PLAN_CHANGES);
            craftingIo.clearPlan();
            return true;
        }

        @HostAccess.Export
        public int rebuildPlanFromGrid() {
            this.requireRemoteWriteAllowed("plan rebuilding");
            return this.requireCraftingIo().rebuildPlanFromGrid();
        }

        @HostAccess.Export
        public String planStepPreviewRecipeId(final int index) {
            final CraftingIOBlockEntity craftingIo = this.requireCraftingIo();
            final CraftingCPUBlockEntity craftingCpu = this.resolveCraftingCpu(craftingIo.getLinkedCpuEndpoint());
            return craftingCpu == null ? "" : craftingCpu.getPreviewRecipeIdForPattern(craftingIo.copyPlanWindow(index));
        }

        @HostAccess.Export
        public String planStepPreviewResultItem(final int index) {
            final CraftingIOBlockEntity craftingIo = this.requireCraftingIo();
            final CraftingCPUBlockEntity craftingCpu = this.resolveCraftingCpu(craftingIo.getLinkedCpuEndpoint());
            return craftingCpu == null ? "" : craftingCpu.getPreviewResultItemIdForPattern(craftingIo.copyPlanWindow(index));
        }

        @HostAccess.Export
        public int planStepPreviewResultCount(final int index) {
            final CraftingIOBlockEntity craftingIo = this.requireCraftingIo();
            final CraftingCPUBlockEntity craftingCpu = this.resolveCraftingCpu(craftingIo.getLinkedCpuEndpoint());
            return craftingCpu == null ? 0 : craftingCpu.getPreviewResultCountForPattern(craftingIo.copyPlanWindow(index));
        }

        @HostAccess.Export
        public int craftPlan(final int cycles) {
            final CraftingIOBlockEntity craftingIo = this.requireCraftingIo();
            this.requireRemoteWriteAllowed("plan craft execution");
            if (cycles <= 0 || craftingIo.getPlanStepCount() == 0) {
                return 0;
            }

            final CraftingCPUBlockEntity craftingCpu;
            try {
                craftingCpu = this.requireCraftingCpu(craftingIo.getLinkedCpuEndpoint(), "plan crafting", true);
            } catch (final RuntimeException exception) {
                this.recordCpuUnavailableStep(craftingIo, 0, 0, craftingIo.getPlanStepCrafts(0), exception);
                throw exception;
            }

            int totalCrafts = 0;
            for (int cycleIndex = 0; cycleIndex < cycles; cycleIndex++) {
                for (int stepIndex = 0; stepIndex < craftingIo.getPlanStepCount(); stepIndex++) {
                    final PlanStepContext stepContext = this.createPlanStepContext(craftingIo, craftingCpu, stepIndex, cycleIndex);
                    final PlanStepExecutionResult executionResult = this.executePlanStep(craftingCpu, stepContext);
                    this.recordPlanStep(executionResult.snapshot());
                    totalCrafts += executionResult.snapshot().completedCrafts();
                    if (executionResult.failure() != null) {
                        this.recordSkippedSteps(craftingIo, craftingCpu, cycleIndex, stepIndex + 1, cycles, PLAN_ERROR_UPSTREAM_FAILED);
                        throw executionResult.failure();
                    }
                    if (executionResult.stopPlan()) {
                        this.recordSkippedSteps(craftingIo, craftingCpu, cycleIndex, stepIndex + 1, cycles, PLAN_ERROR_UPSTREAM_BLOCKED);
                        return totalCrafts;
                    }
                }
            }

            return totalCrafts;
        }

        @HostAccess.Export
        public int queuedPlanCycles() {
            return this.requireCraftingIo().getQueuedPlanCycles();
        }

        @HostAccess.Export
        public int setQueuedPlanCycles(final int cycles) {
            final CraftingIOBlockEntity craftingIo = this.requireCraftingIo();
            this.requireRemoteWriteAllowed(ACTION_QUEUED_PLAN_CHANGES);
            craftingIo.setQueuedPlanCycles(cycles);
            this.rememberQueuedPlanJobSnapshot(craftingIo);
            return craftingIo.getQueuedPlanCycles();
        }

        @HostAccess.Export
        public int queuePlanCycles(final int cycles) {
            final CraftingIOBlockEntity craftingIo = this.requireCraftingIo();
            this.requireRemoteWriteAllowed(ACTION_QUEUED_PLAN_CHANGES);
            final int queuedCycles = craftingIo.queuePlanCycles(cycles);
            this.rememberQueuedPlanJobSnapshot(craftingIo);
            return queuedCycles;
        }

        @HostAccess.Export
        public boolean canResumeQueuedPlan() {
            return this.requireCraftingIo().canResumeQueuedPlan();
        }

        @HostAccess.Export
        public boolean clearQueuedPlan() {
            final CraftingIOBlockEntity craftingIo = this.requireCraftingIo();
            this.requireRemoteWriteAllowed(ACTION_QUEUED_PLAN_CHANGES);
            craftingIo.clearQueuedPlan();
            this.rememberQueuedPlanJobSnapshot(craftingIo);
            return true;
        }

        @HostAccess.Export
        public boolean canAbortQueuedPlan() {
            return this.requireCraftingIo().canAbortQueuedPlan();
        }

        @HostAccess.Export
        public String resumeQueuedPlan() {
            final CraftingIOBlockEntity craftingIo = this.requireCraftingIo();
            this.requireRemoteWriteAllowed(ACTION_QUEUED_PLAN_CHANGES);
            craftingIo.resumeQueuedPlan();
            this.rememberQueuedPlanJobSnapshot(craftingIo);
            return craftingIo.getQueuedPlanJobStatus().serializedName();
        }

        @HostAccess.Export
        public boolean abortQueuedPlan() {
            final CraftingIOBlockEntity craftingIo = this.requireCraftingIo();
            this.requireRemoteWriteAllowed(ACTION_QUEUED_PLAN_CHANGES);
            final boolean aborted = craftingIo.abortQueuedPlan();
            this.rememberQueuedPlanJobSnapshot(craftingIo);
            return aborted;
        }

        @HostAccess.Export
        public String queuedPlanReservationMode() {
            return this.requireCraftingIo().getQueuedPlanReservationMode().serializedName();
        }

        @HostAccess.Export
        public String queuedPlanJobStatus() {
            return this.requireCraftingIo().getQueuedPlanJobStatus().serializedName();
        }

        @HostAccess.Export
        public String setQueuedPlanReservationMode(final String mode) {
            final CraftingIOBlockEntity craftingIo = this.requireCraftingIo();
            this.requireRemoteWriteAllowed(ACTION_QUEUED_PLAN_CHANGES);
            craftingIo.setQueuedPlanReservationMode(this.resolveQueuedPlanReservationMode(mode));
            this.rememberQueuedPlanJobSnapshot(craftingIo);
            return craftingIo.getQueuedPlanReservationMode().serializedName();
        }

        @HostAccess.Export
        public String queuedPlanStateJson() {
            final CraftingIOBlockEntity craftingIo = this.requireCraftingIo();
            this.rememberQueuedPlanJobSnapshot(craftingIo);
            final JsonObject object = this.planJobSnapshotJson(this.buildQueuedPlanJobSnapshot(craftingIo));
            object.addProperty("can_resume", craftingIo.canResumeQueuedPlan());
            object.addProperty("can_abort", craftingIo.canAbortQueuedPlan());
            return object.toString();
        }

        @HostAccess.Export
        public int queuedPlanCycleIndex() {
            return this.requireCraftingIo().getQueuedPlanCycleIndex();
        }

        @HostAccess.Export
        public int queuedPlanStepIndex() {
            return this.requireCraftingIo().getQueuedPlanStepIndex();
        }

        @HostAccess.Export
        public int queuedPlanRemainingCrafts() {
            return this.requireCraftingIo().getQueuedPlanRequestedCrafts();
        }

        @HostAccess.Export
        public int craftQueuedPlan() {
            final CraftingIOBlockEntity craftingIo = this.requireCraftingIo();
            this.requireRemoteWriteAllowed("queued plan craft execution");
            if (!craftingIo.hasQueuedPlan()) {
                this.rememberQueuedPlanJobSnapshot(craftingIo);
                return 0;
            }
            if (!craftingIo.canExecuteQueuedPlan()) {
                this.rememberQueuedPlanJobSnapshot(craftingIo);
                throw new IllegalStateException("Queued plan is "
                        + craftingIo.getQueuedPlanJobStatus().serializedName()
                        + " and must be resumed or aborted before crafting continues.");
            }

            final CraftingCPUBlockEntity craftingCpu;
            try {
                craftingCpu = this.requireCraftingCpu(craftingIo.getLinkedCpuEndpoint(), "queued plan crafting", true);
            } catch (final RuntimeException exception) {
                this.recordCpuUnavailableStep(
                        craftingIo,
                        craftingIo.getQueuedPlanStepIndex(),
                        craftingIo.getQueuedPlanCycleIndex(),
                        craftingIo.getQueuedPlanRequestedCrafts(),
                        exception);
                this.updateQueuedPlanJobState(craftingIo, PythonHostApi.this.planStepSnapshots.get(PythonHostApi.this.planStepSnapshots.size() - 1));
                this.rememberQueuedPlanJobSnapshot(craftingIo);
                throw exception;
            }

            int totalCrafts = 0;
            while (craftingIo.hasQueuedPlan()) {
                final PlanStepContext stepContext = this.createPlanStepContext(
                        craftingIo,
                        craftingCpu,
                        craftingIo.getQueuedPlanStepIndex(),
                        craftingIo.getQueuedPlanCycleIndex(),
                        craftingIo.getQueuedPlanRequestedCrafts());
                final PlanStepExecutionResult executionResult = this.executePlanStep(craftingCpu, stepContext, true);
                this.recordPlanStep(executionResult.snapshot());
                totalCrafts += executionResult.snapshot().completedCrafts();
                if (executionResult.failure() != null) {
                    this.updateQueuedPlanJobState(craftingIo, executionResult.snapshot());
                    this.rememberQueuedPlanJobSnapshot(craftingIo);
                    throw executionResult.failure();
                }

                if (executionResult.stopPlan()) {
                    this.updateQueuedPlanJobState(craftingIo, executionResult.snapshot());
                    this.rememberQueuedPlanJobSnapshot(craftingIo);
                    return totalCrafts;
                }
            }

            craftingIo.markQueuedPlanCompleted();
            this.rememberQueuedPlanJobSnapshot(craftingIo);
            return totalCrafts;
        }

        @HostAccess.Export
        public boolean isBusy() {
            final NamedNetworkEndpointBlockEntity endpoint = this.requireEndpoint();
            if (endpoint instanceof CraftingCPUBlockEntity craftingCpu) {
                return craftingCpu.isBusy();
            }
            throw this.unsupported("busy flag access");
        }

        @HostAccess.Export
        public boolean setBusy(final boolean busy) {
            final NamedNetworkEndpointBlockEntity endpoint = this.requireEndpoint();
            if (endpoint instanceof CraftingCPUBlockEntity craftingCpu) {
                this.requireRemoteWriteAllowed("busy flag changes");
                craftingCpu.setBusy(busy);
                return craftingCpu.isBusy();
            }
            throw this.unsupported("busy flag changes");
        }

        @HostAccess.Export
        public int queuedJobs() {
            final NamedNetworkEndpointBlockEntity endpoint = this.requireEndpoint();
            if (endpoint instanceof CraftingCPUBlockEntity craftingCpu) {
                return craftingCpu.getQueuedJobs();
            }
            throw this.unsupported("queue access");
        }

        @HostAccess.Export
        public int setQueuedJobs(final int queuedJobs) {
            final NamedNetworkEndpointBlockEntity endpoint = this.requireEndpoint();
            if (endpoint instanceof CraftingCPUBlockEntity craftingCpu) {
                this.requireRemoteWriteAllowed("queue changes");
                craftingCpu.setQueuedJobs(queuedJobs);
                return craftingCpu.getQueuedJobs();
            }
            throw this.unsupported("queue changes");
        }

        @HostAccess.Export
        public int uplinkGroup() {
            final NamedNetworkEndpointBlockEntity endpoint = this.requireEndpoint();
            if (endpoint instanceof XLApiBlockEntity xlApi) {
                return xlApi.getUplinkGroup();
            }
            throw this.unsupported("uplink group access");
        }

        @HostAccess.Export
        public int setUplinkGroup(final int uplinkGroup) {
            final NamedNetworkEndpointBlockEntity endpoint = this.requireEndpoint();
            if (endpoint instanceof XLApiBlockEntity xlApi) {
                this.requireLocalOnly("uplink group changes");
                xlApi.setUplinkGroup(uplinkGroup);
                return xlApi.getUplinkGroup();
            }
            throw this.unsupported("uplink group changes");
        }

        @HostAccess.Export
        public boolean relayEnabled() {
            final NamedNetworkEndpointBlockEntity endpoint = this.requireEndpoint();
            if (endpoint instanceof XLApiBlockEntity xlApi) {
                return xlApi.isRelayEnabled();
            }
            throw this.unsupported("relay access");
        }

        @HostAccess.Export
        public boolean setRelayEnabled(final boolean relayEnabled) {
            final NamedNetworkEndpointBlockEntity endpoint = this.requireEndpoint();
            if (endpoint instanceof XLApiBlockEntity xlApi) {
                this.requireLocalOnly("relay changes");
                xlApi.setRelayEnabled(relayEnabled);
                return xlApi.isRelayEnabled();
            }
            throw this.unsupported("relay changes");
        }

        @HostAccess.Export
        public int forwardedMessages() {
            return this.requireXlApiBridge().getForwardedMessages();
        }

        @HostAccess.Export
        public int remoteComputerCount() {
            return XLNetworkResolver.countBridgedComputersForBridge(this.requireLevel(), PythonHostApi.this.computerBlockPos, this.requireXlApiBridge().getBlockPos());
        }

        @HostAccess.Export
        public String remoteComputersJson() {
            final JsonArray array = new JsonArray();
            for (final BridgeRemoteComputerSnapshot snapshot : XLNetworkResolver.resolveBridgedComputersForBridge(this.requireLevel(), PythonHostApi.this.computerBlockPos, this.requireXlApiBridge().getBlockPos())) {
                array.add(snapshot.toJson());
            }
            return array.toString();
        }

        @HostAccess.Export
        public int bridgeInboxCount() {
            final XLApiBlockEntity bridge = this.requireXlApiBridge();
            return this.requireLocalComputer().countBridgeMessages(bridge.getUplinkGroup());
        }

        @HostAccess.Export
        public String peekBridgeMessagesJson(final int limit) {
            return this.bridgeMessagesJson(limit, false);
        }

        @HostAccess.Export
        public String pollBridgeMessagesJson(final int limit) {
            return this.bridgeMessagesJson(limit, true);
        }

        @HostAccess.Export
        public int sendBridgeMessage(final String targetComputerId, final String channel, final String payload) {
            final XLApiBlockEntity bridge = this.requireXlApiBridge();
            final ComputerBlockEntity localComputer = this.requireLocalComputer();
            final List<BridgeRemoteComputerSnapshot> remoteComputers = XLNetworkResolver.resolveBridgedComputersForBridge(this.requireLevel(), PythonHostApi.this.computerBlockPos, bridge.getBlockPos());
            if (remoteComputers.isEmpty()) {
                return 0;
            }

            final String targetId = targetComputerId == null ? "" : targetComputerId.trim();
            final BridgeMessage message = new BridgeMessage(
                    localComputer.computerId(),
                    PythonHostApi.this.computerPosition,
                    bridge.getEndpointName(),
                    bridge.getUplinkGroup(),
                    channel,
                    payload,
                    this.requireLevel().getGameTime()
            );

            int delivered = 0;
            for (final BridgeRemoteComputerSnapshot snapshot : remoteComputers) {
                if (!targetId.isBlank() && !targetId.equals(snapshot.computerId())) {
                    continue;
                }

                if (PythonHostApi.this.level.getBlockEntity(snapshot.computerPos()) instanceof ComputerBlockEntity remoteComputer && remoteComputer.receiveBridgeMessage(message)) {
                    delivered++;
                }
            }

            if (delivered > 0) {
                bridge.recordForwardedMessages(delivered);
            }
            return delivered;
        }

        @HostAccess.Export
        public String sendBridgeCommandJson(final String targetComputerId, final String command, final String payload) {
            final String normalizedCommand = normalizeBridgeCommand(command);
            final JsonObject result = new JsonObject();
            result.addProperty("command", normalizedCommand);
            result.addProperty("target", targetComputerId == null ? "" : targetComputerId.trim());
            result.addProperty("response_channel", BRIDGE_CHANNEL_RESPONSE);

            if (normalizedCommand.isBlank()) {
                result.addProperty("supported", false);
                result.addProperty("delivered", 0);
                result.addProperty("error", "Unsupported bridge command. Expected 'status', 'ping', 'devices' or 'runtime'.");
                return result.toString();
            }

            final XLApiBlockEntity bridge = this.requireXlApiBridge();
            final ComputerBlockEntity localComputer = this.requireLocalComputer();
            final List<BridgeRemoteComputerSnapshot> remoteComputers = XLNetworkResolver.resolveBridgedComputersForBridge(this.requireLevel(), PythonHostApi.this.computerBlockPos, bridge.getBlockPos());
            final String targetId = targetComputerId == null ? "" : targetComputerId.trim();
            final String requestId = buildBridgeRequestId(localComputer, normalizedCommand);

            int delivered = 0;
            for (final BridgeRemoteComputerSnapshot snapshot : remoteComputers) {
                if (!targetId.isBlank() && !targetId.equals(snapshot.computerId())) {
                    continue;
                }

                if (PythonHostApi.this.level.getBlockEntity(snapshot.computerPos()) instanceof ComputerBlockEntity remoteComputer) {
                    final JsonObject responsePayload = buildBridgeResponsePayload(normalizedCommand, requestId, bridge, remoteComputer, payload);
                    final BridgeMessage responseMessage = new BridgeMessage(
                            remoteComputer.computerId(),
                            remoteComputer.getBlockPos().toShortString(),
                            bridge.getEndpointName(),
                            bridge.getUplinkGroup(),
                            BRIDGE_CHANNEL_RESPONSE,
                            responsePayload.toString(),
                            this.requireLevel().getGameTime()
                    );
                    if (localComputer.receiveBridgeMessage(responseMessage)) {
                        delivered++;
                    }
                }
            }

            if (delivered > 0) {
                bridge.recordForwardedMessages(delivered);
            }

            result.addProperty("supported", true);
            result.addProperty("request_id", requestId);
            result.addProperty("delivered", delivered);
            result.addProperty(JSON_BRIDGE_NAME, bridge.getEndpointName());
            result.addProperty(JSON_BRIDGE_GROUP, bridge.getUplinkGroup());
            return result.toString();
        }

        @HostAccess.Export
        public String requestRemoteStatusJson(final String targetComputerId) {
            return this.sendBridgeCommandJson(targetComputerId, BRIDGE_COMMAND_STATUS, "");
        }

        @HostAccess.Export
        public String requestRemotePingJson(final String targetComputerId, final String payload) {
            return this.sendBridgeCommandJson(targetComputerId, BRIDGE_COMMAND_PING, payload);
        }

        @HostAccess.Export
        public String requestRemoteDevicesJson(final String targetComputerId) {
            return this.sendBridgeCommandJson(targetComputerId, BRIDGE_COMMAND_DEVICES, "");
        }

        @HostAccess.Export
        public String requestRemoteRuntimeJson(final String targetComputerId, final int outputLimit, final int planLimit) {
            return this.sendBridgeCommandJson(targetComputerId, BRIDGE_COMMAND_RUNTIME, buildBridgeRuntimeRequestPayload(outputLimit, planLimit));
        }

        @HostAccess.Export
        public String peekBridgeResponsesJson(final int limit) {
            return this.bridgeResponsesJson(limit, false);
        }

        @HostAccess.Export
        public String pollBridgeResponsesJson(final int limit) {
            return this.bridgeResponsesJson(limit, true);
        }

        @HostAccess.Export
        public boolean itemInputEnabled() {
            final NamedNetworkEndpointBlockEntity endpoint = this.requireEndpoint();
            if (endpoint instanceof MaterialIOBlockEntity materialIo) {
                return materialIo.isItemInputEnabled();
            }
            throw this.unsupported("item input access");
        }

        @HostAccess.Export
        public boolean setItemInputEnabled(final boolean enabled) {
            final NamedNetworkEndpointBlockEntity endpoint = this.requireEndpoint();
            if (endpoint instanceof MaterialIOBlockEntity materialIo) {
                this.requireRemoteWriteAllowed("item input changes");
                materialIo.setItemInputEnabled(enabled);
                return materialIo.isItemInputEnabled();
            }
            throw this.unsupported("item input changes");
        }

        @HostAccess.Export
        public boolean itemOutputEnabled() {
            final NamedNetworkEndpointBlockEntity endpoint = this.requireEndpoint();
            if (endpoint instanceof MaterialIOBlockEntity materialIo) {
                return materialIo.isItemOutputEnabled();
            }
            throw this.unsupported("item output access");
        }

        @HostAccess.Export
        public boolean setItemOutputEnabled(final boolean enabled) {
            final NamedNetworkEndpointBlockEntity endpoint = this.requireEndpoint();
            if (endpoint instanceof MaterialIOBlockEntity materialIo) {
                this.requireRemoteWriteAllowed("item output changes");
                materialIo.setItemOutputEnabled(enabled);
                return materialIo.isItemOutputEnabled();
            }
            throw this.unsupported("item output changes");
        }

        @HostAccess.Export
        public boolean fluidInputEnabled() {
            final NamedNetworkEndpointBlockEntity endpoint = this.requireEndpoint();
            if (endpoint instanceof MaterialIOBlockEntity materialIo) {
                return materialIo.isFluidInputEnabled();
            }
            throw this.unsupported("fluid input access");
        }

        @HostAccess.Export
        public boolean setFluidInputEnabled(final boolean enabled) {
            final NamedNetworkEndpointBlockEntity endpoint = this.requireEndpoint();
            if (endpoint instanceof MaterialIOBlockEntity materialIo) {
                this.requireRemoteWriteAllowed("fluid input changes");
                materialIo.setFluidInputEnabled(enabled);
                return materialIo.isFluidInputEnabled();
            }
            throw this.unsupported("fluid input changes");
        }

        @HostAccess.Export
        public boolean fluidOutputEnabled() {
            final NamedNetworkEndpointBlockEntity endpoint = this.requireEndpoint();
            if (endpoint instanceof MaterialIOBlockEntity materialIo) {
                return materialIo.isFluidOutputEnabled();
            }
            throw this.unsupported("fluid output access");
        }

        @HostAccess.Export
        public boolean setFluidOutputEnabled(final boolean enabled) {
            final NamedNetworkEndpointBlockEntity endpoint = this.requireEndpoint();
            if (endpoint instanceof MaterialIOBlockEntity materialIo) {
                this.requireRemoteWriteAllowed("fluid output changes");
                materialIo.setFluidOutputEnabled(enabled);
                return materialIo.isFluidOutputEnabled();
            }
            throw this.unsupported("fluid output changes");
        }

        @HostAccess.Export
        public int itemSlotCount(final String sideName) {
            final NamedNetworkEndpointBlockEntity endpoint = this.requireEndpoint();
            if (endpoint instanceof MaterialIOBlockEntity materialIo) {
                return materialIo.getItemSlotCount(resolveSide(sideName, materialIo));
            }
            throw this.unsupported("inventory slot access");
        }

        @HostAccess.Export
        public String itemId(final String sideName, final int slot) {
            final NamedNetworkEndpointBlockEntity endpoint = this.requireEndpoint();
            if (endpoint instanceof MaterialIOBlockEntity materialIo) {
                return materialIo.getItemId(resolveSide(sideName, materialIo), slot);
            }
            throw this.unsupported("inventory item lookup");
        }

        @HostAccess.Export
        public int itemCount(final String sideName, final int slot) {
            final NamedNetworkEndpointBlockEntity endpoint = this.requireEndpoint();
            if (endpoint instanceof MaterialIOBlockEntity materialIo) {
                return materialIo.getItemCount(resolveSide(sideName, materialIo), slot);
            }
            throw this.unsupported("inventory count lookup");
        }

        @HostAccess.Export
        public int countItem(final String sideName, final String itemId) {
            final NamedNetworkEndpointBlockEntity endpoint = this.requireEndpoint();
            if (endpoint instanceof MaterialIOBlockEntity materialIo) {
                return materialIo.countItem(resolveSide(sideName, materialIo), itemId);
            }
            throw this.unsupported("inventory counting");
        }

        @HostAccess.Export
        public int transferItem(final String sourceSideName, final String targetSideName, final int sourceSlot, final int amount) {
            final NamedNetworkEndpointBlockEntity endpoint = this.requireEndpoint();
            if (endpoint instanceof MaterialIOBlockEntity materialIo) {
                this.requireRemoteWriteAllowed("inventory transfer");
                return materialIo.transferItem(resolveSide(sourceSideName, materialIo), resolveSide(targetSideName, materialIo), sourceSlot, amount);
            }
            throw this.unsupported("inventory transfer");
        }

        @HostAccess.Export
        public int transferItemTo(final String targetApiName, final String sourceSideName, final String targetSideName, final int sourceSlot, final int amount) {
            final NamedNetworkEndpointBlockEntity endpoint = this.requireEndpoint();
            if (endpoint instanceof MaterialIOBlockEntity materialIo) {
                this.requireRemoteWriteAllowed("inventory transfer");
                final MaterialIOBlockEntity targetMaterialIo = this.requireMaterialIo(targetApiName, "inventory transfer", true);
                return materialIo.transferItemTo(targetMaterialIo,
                        resolveSide(sourceSideName, materialIo),
                        resolveSide(targetSideName, targetMaterialIo),
                        sourceSlot,
                        amount);
            }
            throw this.unsupported("inventory transfer");
        }

        @HostAccess.Export
        public int fluidTankCount(final String sideName) {
            final NamedNetworkEndpointBlockEntity endpoint = this.requireEndpoint();
            if (endpoint instanceof MaterialIOBlockEntity materialIo) {
                return materialIo.getFluidTankCount(resolveSide(sideName, materialIo));
            }
            throw this.unsupported("fluid tank access");
        }

        @HostAccess.Export
        public String fluidId(final String sideName, final int tank) {
            final NamedNetworkEndpointBlockEntity endpoint = this.requireEndpoint();
            if (endpoint instanceof MaterialIOBlockEntity materialIo) {
                return materialIo.getFluidId(resolveSide(sideName, materialIo), tank);
            }
            throw this.unsupported("fluid lookup");
        }

        @HostAccess.Export
        public int fluidAmount(final String sideName, final int tank) {
            final NamedNetworkEndpointBlockEntity endpoint = this.requireEndpoint();
            if (endpoint instanceof MaterialIOBlockEntity materialIo) {
                return materialIo.getFluidAmount(resolveSide(sideName, materialIo), tank);
            }
            throw this.unsupported("fluid amount lookup");
        }

        @HostAccess.Export
        public int transferFluid(final String sourceSideName, final String targetSideName, final int sourceTank, final int amount) {
            final NamedNetworkEndpointBlockEntity endpoint = this.requireEndpoint();
            if (endpoint instanceof MaterialIOBlockEntity materialIo) {
                this.requireRemoteWriteAllowed("fluid transfer");
                return materialIo.transferFluid(resolveSide(sourceSideName, materialIo), resolveSide(targetSideName, materialIo), sourceTank, amount);
            }
            throw this.unsupported("fluid transfer");
        }

        @HostAccess.Export
        public int transferFluidTo(final String targetApiName, final String sourceSideName, final String targetSideName, final int sourceTank, final int amount) {
            final NamedNetworkEndpointBlockEntity endpoint = this.requireEndpoint();
            if (endpoint instanceof MaterialIOBlockEntity materialIo) {
                this.requireRemoteWriteAllowed("fluid transfer");
                final MaterialIOBlockEntity targetMaterialIo = this.requireMaterialIo(targetApiName, "fluid transfer", true);
                return materialIo.transferFluidTo(targetMaterialIo,
                        resolveSide(sourceSideName, materialIo),
                        resolveSide(targetSideName, targetMaterialIo),
                        sourceTank,
                        amount);
            }
            throw this.unsupported("fluid transfer");
        }

        @HostAccess.Export
        public int recipeSlotCount() {
            final NamedNetworkEndpointBlockEntity endpoint = this.requireEndpoint();
            if (endpoint instanceof CraftingCPUBlockEntity craftingCpu) {
                return craftingCpu.getRecipeSlotCount();
            }
            throw this.unsupported("recipe slot access");
        }

        @HostAccess.Export
        public String recipeItemId(final int slot) {
            final NamedNetworkEndpointBlockEntity endpoint = this.requireEndpoint();
            if (endpoint instanceof CraftingCPUBlockEntity craftingCpu) {
                return craftingCpu.getRecipeItemId(slot);
            }
            throw this.unsupported("recipe item lookup");
        }

        @HostAccess.Export
        public int recipeItemCount(final int slot) {
            final NamedNetworkEndpointBlockEntity endpoint = this.requireEndpoint();
            if (endpoint instanceof CraftingCPUBlockEntity craftingCpu) {
                return craftingCpu.getRecipeItemCount(slot);
            }
            throw this.unsupported("recipe count lookup");
        }

        @HostAccess.Export
        public boolean setRecipeSlot(final int slot, final String itemId, final int count) {
            final NamedNetworkEndpointBlockEntity endpoint = this.requireEndpoint();
            if (endpoint instanceof CraftingCPUBlockEntity craftingCpu) {
                this.requireRemoteWriteAllowed("recipe slot changes");
                craftingCpu.setRecipeSlot(slot, itemId, count);
                return true;
            }
            throw this.unsupported("recipe slot changes");
        }

        @HostAccess.Export
        public boolean clearRecipe() {
            final NamedNetworkEndpointBlockEntity endpoint = this.requireEndpoint();
            if (endpoint instanceof CraftingCPUBlockEntity craftingCpu) {
                this.requireRemoteWriteAllowed("recipe clearing");
                craftingCpu.clearRecipe();
                return true;
            }
            throw this.unsupported("recipe clearing");
        }

        @HostAccess.Export
        public String previewRecipeId() {
            final NamedNetworkEndpointBlockEntity endpoint = this.requireEndpoint();
            if (endpoint instanceof CraftingCPUBlockEntity craftingCpu) {
                return craftingCpu.getPreviewRecipeId();
            }
            throw this.unsupported("recipe preview lookup");
        }

        @HostAccess.Export
        public String previewResultItem() {
            final NamedNetworkEndpointBlockEntity endpoint = this.requireEndpoint();
            if (endpoint instanceof CraftingCPUBlockEntity craftingCpu) {
                return craftingCpu.getPreviewResultItemId();
            }
            throw this.unsupported("recipe preview result lookup");
        }

        @HostAccess.Export
        public int previewResultCount() {
            final NamedNetworkEndpointBlockEntity endpoint = this.requireEndpoint();
            if (endpoint instanceof CraftingCPUBlockEntity craftingCpu) {
                return craftingCpu.getPreviewResultCount();
            }
            throw this.unsupported("recipe preview result count lookup");
        }

        @HostAccess.Export
        public int craft(final String inputSideName, final String outputSideName, final int crafts) {
            final NamedNetworkEndpointBlockEntity endpoint = this.requireEndpoint();
            if (endpoint instanceof CraftingCPUBlockEntity craftingCpu) {
                this.requireRemoteWriteAllowed("craft execution");
                return craftingCpu.craft(resolveSide(inputSideName, craftingCpu), resolveSide(outputSideName, craftingCpu), crafts);
            }
            throw this.unsupported("craft execution");
        }

        @HostAccess.Export
        public int craftQueued(final String inputSideName, final String outputSideName) {
            final NamedNetworkEndpointBlockEntity endpoint = this.requireEndpoint();
            if (endpoint instanceof CraftingCPUBlockEntity craftingCpu) {
                this.requireRemoteWriteAllowed("queued craft execution");
                return craftingCpu.craftQueued(resolveSide(inputSideName, craftingCpu), resolveSide(outputSideName, craftingCpu));
            }
            throw this.unsupported("queued craft execution");
        }

        private ServerLevel requireLevel() {
            if (PythonHostApi.this.level == null || !PythonHostApi.this.level.isLoaded(this.binding.blockPos())) {
                throw new IllegalStateException("World access is unavailable for endpoint '" + this.binding.apiName() + "'.");
            }
            return PythonHostApi.this.level;
        }

        private NamedNetworkEndpointBlockEntity resolveEndpoint() {
            if (PythonHostApi.this.level == null || !PythonHostApi.this.level.isLoaded(this.binding.blockPos())) {
                return null;
            }

            final BlockEntity blockEntity = PythonHostApi.this.level.getBlockEntity(this.binding.blockPos());
            return blockEntity instanceof NamedNetworkEndpointBlockEntity endpoint ? endpoint : null;
        }

        private NamedNetworkEndpointBlockEntity requireEndpoint() {
            final NamedNetworkEndpointBlockEntity endpoint = this.resolveEndpoint();
            if (endpoint == null) {
                throw new IllegalStateException("Endpoint '" + this.binding.apiName() + "' is offline or unloaded.");
            }
            return endpoint;
        }

        private CraftingIOBlockEntity requireCraftingIo() {
            final NamedNetworkEndpointBlockEntity endpoint = this.requireEndpoint();
            if (endpoint instanceof CraftingIOBlockEntity craftingIo) {
                return craftingIo;
            }
            throw this.unsupported("crafting frontend access");
        }

        private XLApiBlockEntity requireXlApiBridge() {
            final NamedNetworkEndpointBlockEntity endpoint = this.requireEndpoint();
            if (endpoint instanceof XLApiBlockEntity xlApi) {
                return xlApi;
            }
            throw this.unsupported("XLAPI bridge access");
        }

        private CraftingCPUBlockEntity resolveCraftingCpu(final String apiName) {
            if (apiName == null || apiName.isBlank()) {
                return null;
            }

            final DeviceBridge device = PythonHostApi.this.getDevice(apiName);
            if (device == null) {
                return null;
            }

            final NamedNetworkEndpointBlockEntity endpoint = device.resolveEndpoint();
            return endpoint instanceof CraftingCPUBlockEntity craftingCpu ? craftingCpu : null;
        }

        private CraftingCPUBlockEntity requireCraftingCpu(final String apiName, final String action, final boolean requiresWrite) {
            final DeviceBridge device = this.requireReachableDevice(apiName, action);
            if (requiresWrite) {
                device.requireRemoteWriteAllowed(action);
            }

            final NamedNetworkEndpointBlockEntity endpoint = device.requireEndpoint();
            if (endpoint instanceof CraftingCPUBlockEntity craftingCpu) {
                return craftingCpu;
            }
            throw new IllegalStateException("Endpoint '" + apiName + "' must be a Crafting CPU for " + action + ".");
        }

        private MaterialIOBlockEntity requireMaterialIo(final String apiName, final String action, final boolean requiresWrite) {
            final DeviceBridge device = this.requireReachableDevice(apiName, action);
            if (requiresWrite) {
                device.requireRemoteWriteAllowed(action);
            }

            final NamedNetworkEndpointBlockEntity endpoint = device.requireEndpoint();
            if (endpoint instanceof MaterialIOBlockEntity materialIo) {
                return materialIo;
            }
            throw new IllegalStateException("Endpoint '" + apiName + "' must be a Material I/O for " + action + ".");
        }

        private DeviceBridge requireReachableDevice(final String apiName, final String action) {
            if (apiName == null || apiName.isBlank()) {
                throw new IllegalStateException("No endpoint configured for " + action + ".");
            }

            final DeviceBridge device = PythonHostApi.this.getDevice(apiName);
            if (device != null) {
                return device;
            }
            throw new IllegalStateException("Endpoint '" + apiName + "' is not part of the current network for " + action + ".");
        }

        private IItemHandler requireItemInputHandler(final MaterialIOBlockEntity materialIo, final Direction side, final String action) {
            final IItemHandler handler = materialIo.getInputItemHandler(side);
            if (handler != null) {
                return handler;
            }
            throw new IllegalStateException("Material I/O endpoint '" + materialIo.getEndpointName() + "' does not expose an input inventory on side '" + side.getSerializedName() + "' for " + action + ".");
        }

        private IItemHandler requireItemOutputHandler(final MaterialIOBlockEntity materialIo, final Direction side, final String action) {
            final IItemHandler handler = materialIo.getOutputItemHandler(side);
            if (handler != null) {
                return handler;
            }
            throw new IllegalStateException("Material I/O endpoint '" + materialIo.getEndpointName() + "' does not expose an output inventory on side '" + side.getSerializedName() + "' for " + action + ".");
        }

        private MaterialIOBlockEntity requirePlanMaterialIo(final String apiName, final String action) {
            try {
                return this.requireMaterialIo(apiName, action, true);
            } catch (final RuntimeException exception) {
                throw planExecutionError(PLAN_ERROR_ROUTE_MISSING, exception.getMessage());
            }
        }

        private Direction resolveReachableSideAlias(final String apiName, final String rawSide) {
            if (apiName == null || apiName.isBlank()) {
                return resolveSide(rawSide);
            }

            final DeviceBridge device = PythonHostApi.this.getDevice(apiName);
            if (device == null) {
                return resolveSide(rawSide);
            }
            return resolveSide(rawSide, device.resolveEndpoint());
        }

        private IItemHandler requirePlanItemInputHandler(final MaterialIOBlockEntity materialIo, final Direction side, final String action) {
            try {
                return this.requireItemInputHandler(materialIo, side, action);
            } catch (final RuntimeException exception) {
                throw planExecutionError(PLAN_ERROR_ROUTE_MISSING, exception.getMessage());
            }
        }

        private IItemHandler requirePlanItemOutputHandler(final MaterialIOBlockEntity materialIo, final Direction side, final String action) {
            try {
                return this.requireItemOutputHandler(materialIo, side, action);
            } catch (final RuntimeException exception) {
                throw planExecutionError(PLAN_ERROR_ROUTE_MISSING, exception.getMessage());
            }
        }

        private PlanRouteBinding resolvePlanRoute(final CraftingIOBlockEntity craftingIo, final int stepIndex, final boolean input) {
            final String configuredRouteName = input ? craftingIo.getPlanStepInputRoute(stepIndex) : craftingIo.getPlanStepOutputRoute(stepIndex);
            if (!configuredRouteName.isBlank()) {
                final String endpointApiName = craftingIo.getRouteEndpoint(configuredRouteName);
                final Direction side = craftingIo.getRouteSide(configuredRouteName);
                if (endpointApiName.isBlank() || side == null) {
                    throw planExecutionError(PLAN_ERROR_ROUTE_MISSING, "Crafting I/O route '" + configuredRouteName + "' is not configured.");
                }

                final MaterialIOBlockEntity materialIo = this.requirePlanMaterialIo(endpointApiName, input ? "plan crafting input route" : "plan crafting output route");
                return new PlanRouteBinding(configuredRouteName, materialIo, side);
            }

            final String endpointApiName = input ? craftingIo.getMaterialInputEndpoint() : craftingIo.getMaterialOutputEndpoint();
            final Direction side = input ? craftingIo.getMaterialInputSide() : craftingIo.getMaterialOutputSide();
            final MaterialIOBlockEntity materialIo = this.requirePlanMaterialIo(endpointApiName, input ? "plan crafting input" : "plan crafting output");
            return new PlanRouteBinding(this.defaultRouteLabel(input, endpointApiName, side), materialIo, side);
        }

        private String defaultRouteLabel(final boolean input, final String endpointApiName, final Direction side) {
            final String direction = side == null ? UNKNOWN : side.getSerializedName();
            final String endpoint = endpointApiName == null || endpointApiName.isBlank() ? "unbound" : endpointApiName;
            return (input ? "default_input(" : "default_output(") + endpoint + ":" + direction + ")";
        }

        private String bridgeMessagesJson(final int limit, final boolean drain) {
            final XLApiBlockEntity bridge = this.requireXlApiBridge();
            final ComputerBlockEntity localComputer = this.requireLocalComputer();
            final List<BridgeMessage> messages = drain
                    ? localComputer.drainBridgeMessages(bridge.getUplinkGroup(), limit)
                    : localComputer.peekBridgeMessages(bridge.getUplinkGroup(), limit);
            final JsonArray array = new JsonArray();
            for (final BridgeMessage message : messages) {
                array.add(message.toJson());
            }
            return array.toString();
        }

        private String bridgeResponsesJson(final int limit, final boolean drain) {
            final XLApiBlockEntity bridge = this.requireXlApiBridge();
            final ComputerBlockEntity localComputer = this.requireLocalComputer();
            final List<BridgeMessage> responses = drain
                    ? localComputer.drainBridgeMessagesByChannel(bridge.getUplinkGroup(), limit, BRIDGE_CHANNEL_RESPONSE)
                    : localComputer.peekBridgeMessagesByChannel(bridge.getUplinkGroup(), limit, BRIDGE_CHANNEL_RESPONSE);
            final JsonArray array = new JsonArray();
            for (final BridgeMessage response : responses) {
                array.add(this.bridgeResponseJson(response));
            }
            return array.toString();
        }

        private JsonObject bridgeResponseJson(final BridgeMessage response) {
            final JsonObject object = parseBridgePayloadObject(response.payload());
            object.addProperty("channel", response.channel());
            if (!object.has(JSON_SOURCE_ID)) {
                object.addProperty(JSON_SOURCE_ID, response.sourceComputerId());
            }
            if (!object.has(JSON_SOURCE_POSITION)) {
                object.addProperty(JSON_SOURCE_POSITION, response.sourceComputerPosition());
            }
            if (!object.has(JSON_BRIDGE_NAME)) {
                object.addProperty(JSON_BRIDGE_NAME, response.sourceBridgeName());
            }
            if (!object.has(JSON_BRIDGE_GROUP)) {
                object.addProperty(JSON_BRIDGE_GROUP, response.uplinkGroup());
            }
            if (!object.has(JSON_CREATED_GAME_TIME)) {
                object.addProperty(JSON_CREATED_GAME_TIME, response.createdGameTime());
            }
            return object;
        }

        private JsonObject buildBridgeResponsePayload(final String command, final String requestId, final XLApiBlockEntity bridge, final ComputerBlockEntity remoteComputer, final String payload) {
            final JsonObject response = new JsonObject();
            response.addProperty("kind", BRIDGE_KIND_RESPONSE);
            response.addProperty("command", command);
            response.addProperty("request_id", requestId);
            response.addProperty(JSON_SOURCE_ID, remoteComputer.computerId());
            response.addProperty(JSON_SOURCE_POSITION, remoteComputer.getBlockPos().toShortString());
            response.addProperty(JSON_BRIDGE_NAME, bridge.getEndpointName());
            response.addProperty(JSON_BRIDGE_GROUP, bridge.getUplinkGroup());
            response.addProperty(JSON_CREATED_GAME_TIME, this.requireLevel().getGameTime());
            response.addProperty("success", true);
            if (BRIDGE_COMMAND_STATUS.equals(command)) {
                response.addProperty(JSON_RUNTIME_STATUS, remoteComputer.runtimeStatus());
                response.addProperty("running", remoteComputer.getRuntimeState().running());
                response.addProperty("last_success", remoteComputer.getRuntimeState().success());
                response.addProperty("summary", remoteComputer.getRuntimeState().summary());
                response.addProperty("output_count", remoteComputer.getRuntimeState().outputEntries().size());
                response.addProperty("plan_step_count", remoteComputer.getRuntimeState().planStepSnapshots().size());
                if (!remoteComputer.getRuntimeState().planJobStatus().isBlank()) {
                    response.addProperty("job_status", remoteComputer.getRuntimeState().planJobStatus());
                }
                if (remoteComputer.getRuntimeState().planJobSnapshot().hasStatus()) {
                    response.add("job", this.bridgePlanJobJson(remoteComputer.getRuntimeState().planJobSnapshot()));
                }
                response.addProperty("inbox_count", remoteComputer.countBridgeMessages(bridge.getUplinkGroup()));
            } else if (BRIDGE_COMMAND_PING.equals(command)) {
                response.addProperty("message", payload == null ? "" : payload);
                response.addProperty(JSON_RUNTIME_STATUS, remoteComputer.runtimeStatus());
            } else if (BRIDGE_COMMAND_DEVICES.equals(command)) {
                response.addProperty(JSON_RUNTIME_STATUS, remoteComputer.runtimeStatus());
                response.addProperty("network_summary", remoteComputer.describeNetworkSummary());
                response.addProperty("network_conflict", remoteComputer.hasNetworkConflict());
                response.addProperty("local_endpoint_count", remoteComputer.getConnectedEndpoints().size());
                response.addProperty("bridged_endpoint_count", remoteComputer.getBridgedEndpoints().size());
                response.addProperty("reachable_endpoint_count", remoteComputer.getReachableEndpoints().size());
                response.add(BRIDGE_COMMAND_DEVICES, this.buildBridgeDevicesArray(remoteComputer));
            } else if (BRIDGE_COMMAND_RUNTIME.equals(command)) {
                final JsonObject request = parseBridgePayloadObject(payload);
                final ComputerRuntimeSnapshot runtimeState = remoteComputer.getRuntimeState();
                final String reservationMode = runtimeState.planReservationMode();
                final String jobStatus = runtimeState.planJobStatus();
                final int outputLimit = bridgeRequestLimit(request, JSON_OUTPUT_LIMIT, BRIDGE_RUNTIME_DEFAULT_OUTPUT_LIMIT, BRIDGE_RUNTIME_MAX_OUTPUT_LIMIT);
                final int planLimit = bridgeRequestLimit(request, JSON_PLAN_LIMIT, BRIDGE_RUNTIME_DEFAULT_PLAN_LIMIT, BRIDGE_RUNTIME_MAX_PLAN_LIMIT);
                response.addProperty(JSON_RUNTIME_STATUS, remoteComputer.runtimeStatus());
                response.addProperty("running", runtimeState.running());
                response.addProperty("last_success", runtimeState.success());
                response.addProperty("summary", runtimeState.summary());
                if (!jobStatus.isBlank()) {
                    response.addProperty("job_status", jobStatus);
                }
                if (!reservationMode.isBlank()) {
                    response.addProperty("reservation_mode", reservationMode);
                }
                if (runtimeState.planJobSnapshot().hasStatus()) {
                    response.add("job", this.bridgePlanJobJson(runtimeState.planJobSnapshot()));
                }
                response.addProperty("output_count", runtimeState.outputEntries().size());
                response.addProperty("plan_step_count", runtimeState.planStepSnapshots().size());
                response.addProperty("network_conflict", remoteComputer.hasNetworkConflict());
                response.add("output_lines", this.buildBridgeOutputLines(runtimeState, outputLimit));
                response.add("plan_steps", this.buildBridgePlanSteps(runtimeState, planLimit));
                response.addProperty(JSON_OUTPUT_LIMIT, outputLimit);
                response.addProperty(JSON_PLAN_LIMIT, planLimit);
                response.addProperty("output_truncated", runtimeState.outputLines().size() > outputLimit);
                response.addProperty("plan_truncated", runtimeState.planStepSnapshots().size() > planLimit);
            }
            return response;
        }

        private JsonArray buildBridgeDevicesArray(final ComputerBlockEntity remoteComputer) {
            final JsonArray array = new JsonArray();
            final PythonExecutionContext context = PythonExecutionContext.fromSnapshots(remoteComputer.computerId(), remoteComputer.getBlockPos(), remoteComputer.getReachableEndpoints());
            for (final PythonPeripheralBinding deviceBinding : context.peripherals()) {
                array.add(this.bridgeDeviceJson(deviceBinding));
            }
            return array;
        }

        private JsonObject bridgeDeviceJson(final PythonPeripheralBinding binding) {
            final JsonObject object = new JsonObject();
            object.addProperty("api_name", binding.apiName());
            object.addProperty("name", binding.displayName());
            object.addProperty("type", binding.type());
            object.addProperty(JSON_POSITION, binding.position());
            object.addProperty("distance", binding.distance());
            object.addProperty("scope", binding.networkScope());
            object.addProperty("remote", binding.isBridged());
            object.addProperty(JSON_BRIDGE_NAME, binding.bridgeEndpointName());
            object.addProperty(JSON_BRIDGE_GROUP, binding.bridgeUplinkGroup());
            object.addProperty(JSON_REMOTE_POLICY, remotePolicyId(binding));
            object.addProperty(JSON_REMOTE_WRITABLE, bridgeRemoteWritable(binding));
            return object;
        }

        private JsonArray buildBridgeOutputLines(final ComputerRuntimeSnapshot runtimeState, final int limit) {
            final JsonArray array = new JsonArray();
            final int cappedLimit = Math.max(0, Math.min(limit, BRIDGE_RUNTIME_MAX_OUTPUT_LIMIT));
            final List<String> outputLines = runtimeState.outputLines();
            for (int index = 0; index < outputLines.size() && index < cappedLimit; index++) {
                array.add(outputLines.get(index));
            }
            return array;
        }

        private JsonArray buildBridgePlanSteps(final ComputerRuntimeSnapshot runtimeState, final int limit) {
            final JsonArray array = new JsonArray();
            final int cappedLimit = Math.max(0, Math.min(limit, BRIDGE_RUNTIME_MAX_PLAN_LIMIT));
            final List<ComputerPlanStepSnapshot> planSteps = runtimeState.planStepSnapshots();
            for (int index = 0; index < planSteps.size() && index < cappedLimit; index++) {
                array.add(this.bridgePlanStepJson(planSteps.get(index)));
            }
            return array;
        }

        private JsonObject bridgePlanStepJson(final ComputerPlanStepSnapshot stepSnapshot) {
            final JsonObject object = new JsonObject();
            object.addProperty("device_api_name", stepSnapshot.deviceApiName());
            object.addProperty("cycle_index", stepSnapshot.cycleIndex());
            object.addProperty("step_index", stepSnapshot.stepIndex());
            object.addProperty("window_x", stepSnapshot.windowX());
            object.addProperty("window_y", stepSnapshot.windowY());
            object.addProperty("input_route", stepSnapshot.inputRoute());
            object.addProperty("output_route", stepSnapshot.outputRoute());
            object.addProperty("recipe_id", stepSnapshot.recipeId());
            object.addProperty("result_item", stepSnapshot.resultItem());
            object.addProperty("requested_crafts", stepSnapshot.requestedCrafts());
            object.addProperty("completed_crafts", stepSnapshot.completedCrafts());
            object.addProperty("reservation_mode", stepSnapshot.reservationMode());
            object.addProperty("error_class", stepSnapshot.errorClass());
            object.addProperty(BRIDGE_COMMAND_STATUS, stepSnapshot.status());
            object.addProperty("message", stepSnapshot.message());
            return object;
        }

        private JsonObject bridgePlanJobJson(final ComputerPlanJobSnapshot planJobSnapshot) {
            return this.planJobSnapshotJson(planJobSnapshot);
        }

        private int bridgeRequestLimit(final JsonObject request, final String key, final int fallback, final int maxValue) {
            if (request.has(key) && request.get(key).isJsonPrimitive() && request.get(key).getAsJsonPrimitive().isNumber()) {
                return Math.max(0, Math.min(maxValue, request.get(key).getAsInt()));
            }
            return fallback;
        }

        private String buildBridgeRuntimeRequestPayload(final int outputLimit, final int planLimit) {
            final JsonObject request = new JsonObject();
            request.addProperty(JSON_OUTPUT_LIMIT, Math.max(0, Math.min(BRIDGE_RUNTIME_MAX_OUTPUT_LIMIT, outputLimit)));
            request.addProperty(JSON_PLAN_LIMIT, Math.max(0, Math.min(BRIDGE_RUNTIME_MAX_PLAN_LIMIT, planLimit)));
            return request.toString();
        }

        private JsonObject parseBridgePayloadObject(final String payload) {
            if (payload == null || payload.isBlank()) {
                return new JsonObject();
            }

            try {
                final JsonElement element = JsonParser.parseString(payload);
                if (element.isJsonObject()) {
                    return element.getAsJsonObject();
                }
            } catch (final RuntimeException ignored) {
                // Fall back to a raw payload wrapper when a mailbox entry is not valid JSON.
            }

            final JsonObject object = new JsonObject();
            object.addProperty("payload", payload);
            return object;
        }

        private String buildBridgeRequestId(final ComputerBlockEntity localComputer, final String command) {
            return localComputer.computerId() + "_" + command + "_" + UUID.randomUUID().toString().replace('-', '_');
        }

        private static String normalizeBridgeCommand(final String command) {
            if (command == null || command.isBlank()) {
                return "";
            }

            return switch (command.trim().toLowerCase(Locale.ROOT)) {
                case BRIDGE_COMMAND_STATUS -> BRIDGE_COMMAND_STATUS;
                case BRIDGE_COMMAND_PING -> BRIDGE_COMMAND_PING;
                case BRIDGE_COMMAND_DEVICES -> BRIDGE_COMMAND_DEVICES;
                case BRIDGE_COMMAND_RUNTIME -> BRIDGE_COMMAND_RUNTIME;
                default -> "";
            };
        }

        private ComputerBlockEntity requireLocalComputer() {
            if (PythonHostApi.this.level == null || !PythonHostApi.this.level.isLoaded(PythonHostApi.this.computerBlockPos)) {
                throw new IllegalStateException("Computer '" + PythonHostApi.this.computerName + "' is offline or unloaded.");
            }

            final BlockEntity blockEntity = PythonHostApi.this.level.getBlockEntity(PythonHostApi.this.computerBlockPos);
            if (blockEntity instanceof ComputerBlockEntity computer) {
                return computer;
            }
            throw new IllegalStateException("Computer '" + PythonHostApi.this.computerName + "' is offline or unloaded.");
        }

        private String describeConfiguredRoute(final CraftingIOBlockEntity craftingIo, final int stepIndex, final boolean input) {
            final String configuredRouteName = input ? craftingIo.getPlanStepInputRoute(stepIndex) : craftingIo.getPlanStepOutputRoute(stepIndex);
            if (!configuredRouteName.isBlank()) {
                return configuredRouteName;
            }

            return this.defaultRouteLabel(
                    input,
                    input ? craftingIo.getMaterialInputEndpoint() : craftingIo.getMaterialOutputEndpoint(),
                    input ? craftingIo.getMaterialInputSide() : craftingIo.getMaterialOutputSide());
        }

        private PlanStepContext createPlanStepContext(final CraftingIOBlockEntity craftingIo, final CraftingCPUBlockEntity craftingCpu,
                                                      final int stepIndex, final int cycleIndex) {
            return this.createPlanStepContext(craftingIo, craftingCpu, stepIndex, cycleIndex, craftingIo.getPlanStepCrafts(stepIndex));
        }

        private PlanStepContext createPlanStepContext(final CraftingIOBlockEntity craftingIo, final CraftingCPUBlockEntity craftingCpu,
                                                      final int stepIndex, final int cycleIndex, final int requestedCrafts) {
            final List<ItemStack> pattern = craftingIo.copyPlanWindow(stepIndex);
            final String recipeId = craftingCpu == null ? "" : craftingCpu.getPreviewRecipeIdForPattern(pattern);
            final String resultItem = craftingCpu == null ? "" : craftingCpu.getPreviewResultItemIdForPattern(pattern);
            final int resultCount = craftingCpu == null ? 0 : craftingCpu.getPreviewResultCountForPattern(pattern);
            final List<ItemStack> predictedOutputs = craftingCpu == null ? List.of() : craftingCpu.getPreviewOutputsForPattern(pattern);
            return new PlanStepContext(
                    craftingIo,
                    stepIndex,
                    cycleIndex,
                    Math.max(0, requestedCrafts),
                    pattern,
                    recipeId,
                    resultItem,
                    resultCount,
                predictedOutputs,
                    this.describeConfiguredRoute(craftingIo, stepIndex, true),
                    this.describeConfiguredRoute(craftingIo, stepIndex, false)
            );
        }

        private PlanStepExecutionResult executePlanStep(final CraftingCPUBlockEntity craftingCpu, final PlanStepContext stepContext) {
            return this.executePlanStep(craftingCpu, stepContext, false);
        }

        private PlanStepExecutionResult executePlanStep(final CraftingCPUBlockEntity craftingCpu,
                                                        final PlanStepContext stepContext,
                                                        final boolean requireFullReservation) {
            try {
            this.requireValidPlanRecipe(stepContext);
                final PlanRouteBinding inputRoute = this.resolvePlanRoute(stepContext.craftingIo(), stepContext.stepIndex(), true);
                final PlanRouteBinding outputRoute = this.resolvePlanRoute(stepContext.craftingIo(), stepContext.stepIndex(), false);
            final IItemHandler inputHandler = this.requirePlanItemInputHandler(inputRoute.materialIo(), inputRoute.side(), "plan crafting input");
            final IItemHandler outputHandler = this.requirePlanItemOutputHandler(outputRoute.materialIo(), outputRoute.side(), "plan crafting output");
            return requireFullReservation
                ? this.executeReservedPlanStep(craftingCpu, stepContext, inputRoute, outputRoute, inputHandler, outputHandler)
                : this.executeDirectPlanStep(craftingCpu, stepContext, inputRoute, outputRoute, inputHandler, outputHandler);
            } catch (final RuntimeException exception) {
            return this.planFailureResult(stepContext, exception);
            }
        }

        private PlanStepExecutionResult executeReservedPlanStep(final CraftingCPUBlockEntity craftingCpu,
                                    final PlanStepContext stepContext,
                                    final PlanRouteBinding inputRoute,
                                    final PlanRouteBinding outputRoute,
                                    final IItemHandler inputHandler,
                                    final IItemHandler outputHandler) {
            final int requestedCrafts = stepContext.requestedCrafts();
            final QueuedPlanReservation reservation = this.reserveRemainingQueuedPlan(stepContext.craftingIo(), craftingCpu);
            if (!reservation.reserved()) {
            final PlanStepContext blockingStep = reservation.stepContext() == null ? stepContext : reservation.stepContext();
            return new PlanStepExecutionResult(
                this.buildPlanStepSnapshot(
                    blockingStep,
                    0,
                    reservation.inputRouteLabel(),
                    reservation.outputRouteLabel(),
                    reservation.terminal() ? PLAN_STATUS_FAILED : PLAN_STATUS_BLOCKED,
                    reservation.errorClass(),
                    reservation.message()),
                true,
                reservation.failure());
            }

            final int crafted = stepContext.craftingIo().craftReservedQueuedPlanStep(craftingCpu, inputHandler, outputHandler);
            this.updateTrackedIntermediatesAfterCraft(stepContext, crafted);
            final String status = this.stepStatus(requestedCrafts, crafted);
            final String errorClass = crafted >= requestedCrafts
                ? ""
                : this.classifyStoppedCraftError(stepContext, craftingCpu, inputHandler, outputHandler, null, null);
            final String message = crafted >= requestedCrafts
                ? "Queued plan step crafted successfully with full material reservation."
                : this.stepMessage(status, errorClass);
            return new PlanStepExecutionResult(
                this.buildPlanStepSnapshot(stepContext, crafted, inputRoute.label(), outputRoute.label(), status, errorClass, message),
                crafted < requestedCrafts,
                null);
        }

        private PlanStepExecutionResult executeDirectPlanStep(final CraftingCPUBlockEntity craftingCpu,
                                      final PlanStepContext stepContext,
                                      final PlanRouteBinding inputRoute,
                                      final PlanRouteBinding outputRoute,
                                      final IItemHandler inputHandler,
                                      final IItemHandler outputHandler) {
            craftingCpu.setRecipePattern(stepContext.pattern());
            final int crafted = craftingCpu.craftWithHandlers(inputHandler, outputHandler, stepContext.requestedCrafts());
            final String status = this.stepStatus(stepContext.requestedCrafts(), crafted);
            final String errorClass = PLAN_STATUS_COMPLETED.equals(status)
                ? ""
                : this.classifyStoppedCraftError(stepContext, craftingCpu, inputHandler, outputHandler, null, null);
            return new PlanStepExecutionResult(
                this.buildPlanStepSnapshot(
                    stepContext,
                    crafted,
                    inputRoute.label(),
                    outputRoute.label(),
                    status,
                    errorClass,
                    this.stepMessage(status, errorClass)),
                crafted < stepContext.requestedCrafts(),
                null);
        }

        private PlanStepExecutionResult planFailureResult(final PlanStepContext stepContext, final RuntimeException exception) {
            final String errorClass = this.classifyPlanExecutionException(stepContext, exception);
            return new PlanStepExecutionResult(
                this.buildPlanStepSnapshot(
                    stepContext,
                    0,
                    stepContext.configuredInputRoute(),
                    stepContext.configuredOutputRoute(),
                    PLAN_STATUS_FAILED,
                    errorClass,
                    this.planFailureMessage(errorClass, exception.getMessage())),
                false,
                exception);
        }

        private QueuedPlanReservation reserveRemainingQueuedPlan(final CraftingIOBlockEntity craftingIo,
                                                                 final CraftingCPUBlockEntity craftingCpu) {
            final Map<InventoryReservationKey, SimulatedItemRouteState> reservedInventories = new LinkedHashMap<>();
            final int currentCycleIndex = craftingIo.getQueuedPlanCycleIndex();
            final int currentStepIndex = craftingIo.getQueuedPlanStepIndex();
            final int remainingCycles = craftingIo.getQueuedPlanReservationMode().reservesFullQueue()
                ? craftingIo.getQueuedPlanCycles()
                : 1;

            for (int cycleOffset = 0; cycleOffset < remainingCycles; cycleOffset++) {
                final int cycleIndex = currentCycleIndex + cycleOffset;
                final int firstStepIndex = cycleOffset == 0 ? currentStepIndex : 0;
                for (int stepIndex = firstStepIndex; stepIndex < craftingIo.getPlanStepCount(); stepIndex++) {
                    final QueuedPlanReservation reservation = this.reserveQueuedPlanStep(
                            craftingIo,
                            craftingCpu,
                            reservedInventories,
                            currentStepIndex,
                            cycleOffset,
                            cycleIndex,
                            stepIndex);
                    if (reservation != null) {
                        return reservation;
                    }
                }
            }

            return QueuedPlanReservation.success();
        }

        private QueuedPlanReservation reserveQueuedPlanStep(final CraftingIOBlockEntity craftingIo,
                                                            final CraftingCPUBlockEntity craftingCpu,
                                                            final Map<InventoryReservationKey, SimulatedItemRouteState> reservedInventories,
                                                            final int currentStepIndex,
                                                            final int cycleOffset,
                                                            final int cycleIndex,
                                                            final int stepIndex) {
            final int requestedCrafts = cycleOffset == 0 && stepIndex == currentStepIndex
                    ? craftingIo.getQueuedPlanRequestedCrafts()
                    : craftingIo.getPlanStepCrafts(stepIndex);
            if (requestedCrafts <= 0) {
                return null;
            }

            final PlanStepContext stepContext = this.createPlanStepContext(craftingIo, craftingCpu, stepIndex, cycleIndex, requestedCrafts);
                try {
                this.requireValidPlanRecipe(stepContext);
                final PlanRouteBinding inputRoute = this.resolvePlanRoute(craftingIo, stepIndex, true);
                final PlanRouteBinding outputRoute = this.resolvePlanRoute(craftingIo, stepIndex, false);
                final IItemHandler inputHandler = this.requirePlanItemInputHandler(inputRoute.materialIo(), inputRoute.side(), "queued plan reservation input");
                final IItemHandler outputHandler = this.requirePlanItemOutputHandler(outputRoute.materialIo(), outputRoute.side(), "queued plan reservation output");
                final SimulatedItemRouteState inputState = this.simulatedItemRouteState(reservedInventories, inputRoute, inputHandler);
                final SimulatedItemRouteState outputState = this.simulatedItemRouteState(reservedInventories, outputRoute, outputHandler);
                final IntermediateRouteIssue intermediateRouteIssue = this.validateTrackedIntermediateRouteState(stepContext, inputState.slots());
                if (intermediateRouteIssue != null) {
                    return QueuedPlanReservation.failure(
                            stepContext,
                            inputRoute.label(),
                            outputRoute.label(),
                            intermediateRouteIssue.errorClass(),
                            intermediateRouteIssue.message(),
                            planExecutionError(intermediateRouteIssue.errorClass(), intermediateRouteIssue.message()));
                }
                final int craftable = craftingCpu.simulatePatternCraftsWithHandlers(
                    stepContext.pattern(),
                    inputHandler,
                    outputHandler,
                    requestedCrafts,
                    inputState.slots(),
                    outputState.slots());
                if (craftable >= requestedCrafts) {
                    return null;
                }

                final String errorClass = this.classifyStoppedCraftError(
                    stepContext,
                    craftingCpu,
                    inputHandler,
                    outputHandler,
                    inputState.slots(),
                    outputState.slots());
                return QueuedPlanReservation.blocked(
                    stepContext,
                    inputRoute.label(),
                    outputRoute.label(),
                    errorClass,
                    this.queuedPlanReservationMessage(stepContext, inputRoute.label(), outputRoute.label(), errorClass, craftable));
                } catch (final RuntimeException exception) {
                final String errorClass = this.classifyPlanExecutionException(stepContext, exception);
                return QueuedPlanReservation.failure(
                    stepContext,
                    stepContext.configuredInputRoute(),
                    stepContext.configuredOutputRoute(),
                    errorClass,
                    this.planFailureMessage(errorClass, exception.getMessage()),
                    exception);
                }
        }

        private SimulatedItemRouteState simulatedItemRouteState(final Map<InventoryReservationKey, SimulatedItemRouteState> reservedInventories,
                                                                final PlanRouteBinding route,
                                                                final IItemHandler handler) {
            final InventoryReservationKey key = new InventoryReservationKey(route.materialIo().getBlockPos().relative(route.side()), handler.getSlots());
            return reservedInventories.computeIfAbsent(key, ignored -> new SimulatedItemRouteState(XLItemFluidAccess.copySlots(handler)));
        }

        private QueuedPlanReservationMode resolveQueuedPlanReservationMode(final String rawMode) {
            if (rawMode == null || rawMode.isBlank()) {
                throw new IllegalArgumentException("Queued plan reservation mode must be 'full_queue' or 'active_cycle'.");
            }

            final String normalized = rawMode.trim().toLowerCase(Locale.ROOT);
            final QueuedPlanReservationMode mode = QueuedPlanReservationMode.fromSerializedName(normalized);
            if (!mode.serializedName().equals(normalized)) {
                throw new IllegalArgumentException("Unknown queued plan reservation mode '" + rawMode + "'. Expected 'full_queue' or 'active_cycle'.");
            }
            return mode;
        }

        private String queuedPlanReservationMessage(final PlanStepContext blockingStep,
                                                    final String inputRoute,
                                                    final String outputRoute,
                                                    final String errorClass,
                                                    final int craftable) {
            return "Remaining plan blocked at cycle "
                    + (blockingStep.cycleIndex() + 1)
                    + " step "
                    + (blockingStep.stepIndex() + 1)
                    + " via "
                    + inputRoute
                    + " -> "
                    + outputRoute
                    + "; "
                    + errorClass
                    + " after "
                    + craftable
                    + "/"
                    + blockingStep.requestedCrafts()
                    + " reserved crafts.";
        }

        private void updateQueuedPlanJobState(final CraftingIOBlockEntity craftingIo, final ComputerPlanStepSnapshot snapshot) {
            if (snapshot == null) {
                craftingIo.markQueuedPlanResumable();
                return;
            }

            if (PLAN_STATUS_FAILED.equals(snapshot.status())) {
                craftingIo.markQueuedPlanFailed(snapshot.errorClass(), snapshot.message(), snapshot.inputRoute(), snapshot.outputRoute());
                return;
            }
            if (PLAN_STATUS_BLOCKED.equals(snapshot.status())) {
                craftingIo.markQueuedPlanBlocked(snapshot.errorClass(), snapshot.message(), snapshot.inputRoute(), snapshot.outputRoute());
                return;
            }
            if (PLAN_STATUS_PARTIAL.equals(snapshot.status())) {
                craftingIo.markQueuedPlanResumable();
                return;
            }
            if (!craftingIo.hasQueuedPlan()) {
                craftingIo.markQueuedPlanCompleted();
                return;
            }
            craftingIo.markQueuedPlanResumable();
        }

        private void rememberQueuedPlanJobSnapshot(final CraftingIOBlockEntity craftingIo) {
            PythonHostApi.this.planJobSnapshot = this.buildQueuedPlanJobSnapshot(craftingIo);
        }

        private ComputerPlanJobSnapshot buildQueuedPlanJobSnapshot(final CraftingIOBlockEntity craftingIo) {
            if (craftingIo == null) {
                return ComputerPlanJobSnapshot.empty();
            }

            final String status = craftingIo.getQueuedPlanJobStatus().serializedName();
            final int totalCycles = craftingIo.getQueuedPlanTotalCycles();
            final int totalSteps = craftingIo.getPlanStepCount();
            final boolean completed = QueuedPlanJobStatus.COMPLETED.serializedName().equals(status);
            final int cycleIndex;
            if (craftingIo.hasQueuedPlan()) {
                cycleIndex = craftingIo.getQueuedPlanCycleIndex();
            } else if (completed && totalCycles > 0) {
                cycleIndex = totalCycles - 1;
            } else {
                cycleIndex = 0;
            }
            final int stepIndex;
            if (craftingIo.hasQueuedPlan()) {
                stepIndex = craftingIo.getQueuedPlanStepIndex();
            } else if (completed && totalSteps > 0) {
                stepIndex = totalSteps - 1;
            } else {
                stepIndex = 0;
            }
            final CraftingCPUBlockEntity craftingCpu = this.resolveCraftingCpu(craftingIo.getLinkedCpuEndpoint());
            final QueuedPlanReservationAnalysis reservationAnalysis = this.analyzeQueuedPlanReservation(craftingIo, craftingCpu);
            final ArrayList<ComputerPlanTrackedRouteSnapshot> trackedIntermediates = new ArrayList<>(craftingIo.copyTrackedIntermediateStates().size());
            for (final CraftingIOBlockEntity.TrackedIntermediateState trackedIntermediateState : craftingIo.copyTrackedIntermediateStates()) {
                trackedIntermediates.add(new ComputerPlanTrackedRouteSnapshot(
                        trackedIntermediateState.routeName(),
                        trackedIntermediateState.itemId(),
                        trackedIntermediateState.expectedCount()));
            }

            return new ComputerPlanJobSnapshot(
                    this.binding.apiName(),
                    totalCycles,
                    craftingIo.getQueuedPlanCycles(),
                    cycleIndex,
                    stepIndex,
                    totalSteps,
                    craftingIo.hasQueuedPlan() ? craftingIo.getQueuedPlanRequestedCrafts() : 0,
                    status,
                    craftingIo.getQueuedPlanReservationMode().serializedName(),
                    this.effectiveQueuedPlanActionHint(craftingIo, reservationAnalysis),
                    craftingIo.getQueuedPlanJobErrorClass(),
                    craftingIo.getQueuedPlanJobMessage(),
                    craftingIo.getQueuedPlanJobInputRoute(),
                    craftingIo.getQueuedPlanJobOutputRoute(),
                    reservationAnalysis.reservableCycles(),
                    reservationAnalysis.reservableSteps(),
                    reservationAnalysis.blockedCycleIndex(),
                    reservationAnalysis.blockedStepIndex(),
                    trackedIntermediates);
        }

        private QueuedPlanReservationAnalysis analyzeQueuedPlanReservation(final CraftingIOBlockEntity craftingIo,
                                                                           final CraftingCPUBlockEntity craftingCpu) {
            if (craftingIo == null || craftingCpu == null || !craftingIo.hasQueuedPlan()) {
                return new QueuedPlanReservationAnalysis(0, 0, 0, 0);
            }

            final Map<InventoryReservationKey, SimulatedItemRouteState> reservedInventories = new LinkedHashMap<>();
            final int currentCycleIndex = craftingIo.getQueuedPlanCycleIndex();
            final int currentStepIndex = craftingIo.getQueuedPlanStepIndex();
            final int remainingCycles = craftingIo.getQueuedPlanReservationMode().reservesFullQueue()
                    ? craftingIo.getQueuedPlanCycles()
                    : 1;
            int reservableCycles = 0;
            int reservableSteps = 0;

            for (int cycleOffset = 0; cycleOffset < remainingCycles; cycleOffset++) {
                final int cycleIndex = currentCycleIndex + cycleOffset;
                final int firstStepIndex = cycleOffset == 0 ? currentStepIndex : 0;
                int cycleReservableSteps = 0;
                for (int stepIndex = firstStepIndex; stepIndex < craftingIo.getPlanStepCount(); stepIndex++) {
                    final QueuedPlanReservation reservation = this.reserveQueuedPlanStep(
                            craftingIo,
                            craftingCpu,
                            reservedInventories,
                            currentStepIndex,
                            cycleOffset,
                            cycleIndex,
                            stepIndex);
                    if (reservation != null) {
                        final PlanStepContext blockingStep = reservation.stepContext();
                        return new QueuedPlanReservationAnalysis(
                                reservableCycles,
                                reservableSteps,
                                blockingStep == null ? cycleIndex : blockingStep.cycleIndex(),
                                blockingStep == null ? stepIndex : blockingStep.stepIndex());
                    }
                    cycleReservableSteps++;
                }
                reservableCycles++;
                reservableSteps += cycleReservableSteps;
            }

            final int blockedCycleIndex = reservableCycles <= 0 ? currentCycleIndex : currentCycleIndex + reservableCycles - 1;
            final int blockedStepIndex = Math.max(0, craftingIo.getPlanStepCount() - 1);
            return new QueuedPlanReservationAnalysis(reservableCycles, reservableSteps, blockedCycleIndex, blockedStepIndex);
        }

        private String effectiveQueuedPlanActionHint(final CraftingIOBlockEntity craftingIo,
                                                     final QueuedPlanReservationAnalysis reservationAnalysis) {
            final String status = craftingIo.getQueuedPlanJobStatus().serializedName();
            if (craftingIo.getQueuedPlanReservationMode().reservesFullQueue()
                    && (QueuedPlanJobStatus.BLOCKED.serializedName().equals(status)
                    || QueuedPlanJobStatus.FAILED.serializedName().equals(status))
                    && reservationAnalysis.reservableCycles() > 0
                    && reservationAnalysis.blockedCycleIndex() > craftingIo.getQueuedPlanCycleIndex()) {
                return "switch_to_active_cycle";
            }
            return craftingIo.getQueuedPlanJobActionHint();
        }

        private JsonObject planJobSnapshotJson(final ComputerPlanJobSnapshot planJobSnapshot) {
            final JsonObject object = new JsonObject();
            object.addProperty("device_api_name", planJobSnapshot.deviceApiName());
            object.addProperty("total_cycles", planJobSnapshot.totalCycles());
            object.addProperty("cycles", planJobSnapshot.remainingCycles());
            object.addProperty("cycle_index", planJobSnapshot.cycleIndex());
            object.addProperty("step_index", planJobSnapshot.stepIndex());
            object.addProperty("total_steps", planJobSnapshot.totalSteps());
            object.addProperty("remaining_crafts", planJobSnapshot.remainingCrafts());
            object.addProperty("job_status", planJobSnapshot.status());
            object.addProperty("reservation_mode", planJobSnapshot.reservationMode());
            object.addProperty("action_hint", planJobSnapshot.actionHint());
            object.addProperty("error_class", planJobSnapshot.errorClass());
            object.addProperty("message", planJobSnapshot.message());
            object.addProperty("input_route", planJobSnapshot.inputRoute());
            object.addProperty("output_route", planJobSnapshot.outputRoute());
            object.addProperty("reservable_cycles", planJobSnapshot.reservableCycles());
            object.addProperty("reservable_steps", planJobSnapshot.reservableSteps());
            object.addProperty("blocked_cycle_index", planJobSnapshot.blockedCycleIndex());
            object.addProperty("blocked_step_index", planJobSnapshot.blockedStepIndex());
            object.addProperty("tracked_intermediate_count", planJobSnapshot.trackedIntermediateTotal());
            object.add("tracked_intermediates", this.trackedIntermediatesJson(planJobSnapshot.trackedIntermediates()));
            return object;
        }

        private JsonArray trackedIntermediatesJson(final List<ComputerPlanTrackedRouteSnapshot> trackedIntermediates) {
            final JsonArray array = new JsonArray();
            for (final ComputerPlanTrackedRouteSnapshot trackedIntermediate : trackedIntermediates) {
                final JsonObject object = new JsonObject();
                object.addProperty("route", trackedIntermediate.routeName());
                object.addProperty("item", trackedIntermediate.itemId());
                object.addProperty("count", trackedIntermediate.expectedCount());
                array.add(object);
            }
            return array;
        }

        private String stepStatus(final int requestedCrafts, final int crafted) {
            if (crafted >= requestedCrafts) {
                return PLAN_STATUS_COMPLETED;
            }
            if (crafted > 0) {
                return PLAN_STATUS_PARTIAL;
            }
            return PLAN_STATUS_FAILED;
        }

        private void requireValidPlanRecipe(final PlanStepContext stepContext) {
            if (!stepContext.recipeId().isBlank() && !stepContext.resultItem().isBlank()) {
                return;
            }
            throw planExecutionError(PLAN_ERROR_RECIPE_INVALID, "Plan step does not resolve to a valid crafting recipe.");
        }

        private String classifyStoppedCraftError(final PlanStepContext stepContext,
                                                 final CraftingCPUBlockEntity craftingCpu,
                                                 final IItemHandler inputHandler,
                                                 final IItemHandler outputHandler,
                                                 final List<ItemStack> simulatedInputSlots,
                                                 final List<ItemStack> simulatedOutputSlots) {
            return switch (craftingCpu.diagnosePatternFailureWithHandlers(
                    stepContext.pattern(),
                    inputHandler,
                    outputHandler,
                    simulatedInputSlots,
                    simulatedOutputSlots)) {
                case MATERIAL_MISSING -> PLAN_ERROR_MATERIAL_MISSING;
                case OUTPUT_FULL -> this.outputCapacityErrorClass(stepContext);
                case RECIPE_INVALID -> PLAN_ERROR_RECIPE_INVALID;
                case NONE -> PLAN_ERROR_INTERNAL;
            };
        }

        private String outputCapacityErrorClass(final PlanStepContext stepContext) {
            return this.routeFeedsPlanInput(stepContext.craftingIo(), stepContext.configuredOutputRoute())
                    ? PLAN_ERROR_BUFFER_FULL
                    : PLAN_ERROR_OUTPUT_FULL;
        }

        private void updateTrackedIntermediatesAfterCraft(final PlanStepContext stepContext, final int crafted) {
            if (crafted <= 0) {
                return;
            }

            final String inputRouteName = stepContext.configuredInputRoute();
            if (!inputRouteName.isBlank()) {
                for (final CraftingIOBlockEntity.TrackedIntermediateState trackedIntermediateState : stepContext.craftingIo().copyTrackedIntermediateStates(inputRouteName)) {
                    final int consumedPerCraft = this.countPatternItem(stepContext.pattern(), trackedIntermediateState.itemId());
                    if (consumedPerCraft > 0) {
                        stepContext.craftingIo().noteConsumedIntermediate(inputRouteName, trackedIntermediateState.itemId(), consumedPerCraft * crafted);
                    }
                }
            }

            final String outputRouteName = stepContext.configuredOutputRoute();
            if (!outputRouteName.isBlank() && this.routeFeedsPlanInput(stepContext.craftingIo(), outputRouteName)) {
                for (final ItemStack predictedOutput : stepContext.predictedOutputs()) {
                    if (!predictedOutput.isEmpty()) {
                        stepContext.craftingIo().noteProducedIntermediate(
                                outputRouteName,
                                XLItemFluidAccess.itemId(predictedOutput),
                                predictedOutput.getCount() * crafted);
                    }
                }
            }
        }

        private IntermediateRouteIssue validateTrackedIntermediateRouteState(final PlanStepContext stepContext,
                                                                             final List<ItemStack> routeSlots) {
            final String routeName = stepContext.configuredInputRoute();
            if (routeName == null || routeName.isBlank()) {
                return null;
            }

            final List<CraftingIOBlockEntity.TrackedIntermediateState> trackedIntermediateStates = stepContext.craftingIo().copyTrackedIntermediateStates(routeName);
            if (trackedIntermediateStates.isEmpty()) {
                return null;
            }

            final Set<String> allowedItemIds = new LinkedHashSet<>();
            for (final CraftingIOBlockEntity.TrackedIntermediateState trackedIntermediateState : trackedIntermediateStates) {
                if (!trackedIntermediateState.itemId().isBlank()) {
                    allowedItemIds.add(trackedIntermediateState.itemId());
                }
            }

            for (final ItemStack stack : routeSlots) {
                if (stack.isEmpty()) {
                    continue;
                }

                final String itemId = XLItemFluidAccess.itemId(stack);
                if (!itemId.isBlank() && !allowedItemIds.contains(itemId)) {
                    return new IntermediateRouteIssue(
                            PLAN_ERROR_INTERMEDIATE_CONTAMINATED,
                            this.trackedIntermediateContaminatedMessage(stepContext, itemId));
                }
            }

            for (final CraftingIOBlockEntity.TrackedIntermediateState trackedIntermediateState : trackedIntermediateStates) {
                final int availableCount = this.countItem(routeSlots, trackedIntermediateState.itemId());
                if (availableCount < trackedIntermediateState.expectedCount()) {
                    return new IntermediateRouteIssue(
                            PLAN_ERROR_INTERMEDIATE_MISSING,
                            this.trackedIntermediateMissingMessage(stepContext, trackedIntermediateState, availableCount));
                }
            }
            return null;
        }

        private String trackedIntermediateMissingMessage(final PlanStepContext stepContext,
                                                         final CraftingIOBlockEntity.TrackedIntermediateState trackedIntermediateState,
                                                         final int availableCount) {
            return "Plan step cannot continue because tracked intermediate item '"
                    + trackedIntermediateState.itemId()
                    + "' is missing on route '"
                    + stepContext.configuredInputRoute()
                    + "' (expected "
                    + trackedIntermediateState.expectedCount()
                    + ", found "
                    + availableCount
                    + ").";
        }

        private String trackedIntermediateContaminatedMessage(final PlanStepContext stepContext, final String unexpectedItemId) {
            return "Plan step cannot continue because buffer route '"
                    + stepContext.configuredInputRoute()
                    + "' contains unexpected item '"
                    + unexpectedItemId
                    + "' alongside tracked intermediates.";
        }

        private int countPatternItem(final List<ItemStack> pattern, final String itemId) {
            if (pattern == null || pattern.isEmpty() || itemId == null || itemId.isBlank()) {
                return 0;
            }

            int total = 0;
            for (final ItemStack stack : pattern) {
                if (!stack.isEmpty() && itemId.equals(XLItemFluidAccess.itemId(stack))) {
                    total += stack.getCount();
                }
            }
            return total;
        }

        private int countItem(final List<ItemStack> slots, final String itemId) {
            if (slots == null || slots.isEmpty() || itemId == null || itemId.isBlank()) {
                return 0;
            }

            int total = 0;
            for (final ItemStack stack : slots) {
                if (!stack.isEmpty() && itemId.equals(XLItemFluidAccess.itemId(stack))) {
                    total += stack.getCount();
                }
            }
            return total;
        }

        private boolean routeFeedsPlanInput(final CraftingIOBlockEntity craftingIo, final String routeName) {
            if (routeName == null || routeName.isBlank()) {
                return false;
            }

            for (int stepIndex = 0; stepIndex < craftingIo.getPlanStepCount(); stepIndex++) {
                if (routeName.equals(craftingIo.getPlanStepInputRoute(stepIndex))) {
                    return true;
                }
            }
            return false;
        }

        private String classifyPlanExecutionException(final PlanStepContext stepContext, final RuntimeException exception) {
            if (exception instanceof PlanExecutionException planExecutionException) {
                return planExecutionException.errorClass();
            }

            final String message = exception.getMessage() == null ? "" : exception.getMessage();
            if (message.contains("could not consume the required recipe inputs")) {
                return PLAN_ERROR_MATERIAL_MISSING;
            }
            if (message.contains("failed to insert crafted output")) {
                return this.outputCapacityErrorClass(stepContext);
            }
            return PLAN_ERROR_INTERNAL;
        }

        private String planFailureMessage(final String errorClass, final String fallbackMessage) {
            if (fallbackMessage != null && !fallbackMessage.isBlank()) {
                return fallbackMessage;
            }
            return this.stepMessage(PLAN_STATUS_FAILED, errorClass);
        }

        private void recordCpuUnavailableStep(final CraftingIOBlockEntity craftingIo,
                                              final int stepIndex,
                                              final int cycleIndex,
                                              final int requestedCrafts,
                                              final RuntimeException exception) {
            final PlanStepContext stepContext = this.createPlanStepContext(craftingIo, null, stepIndex, cycleIndex, requestedCrafts);
            this.recordPlanStep(this.buildPlanStepSnapshot(
                    stepContext,
                    0,
                    stepContext.configuredInputRoute(),
                    stepContext.configuredOutputRoute(),
                    PLAN_STATUS_FAILED,
                    PLAN_ERROR_CPU_UNAVAILABLE,
                    this.planFailureMessage(PLAN_ERROR_CPU_UNAVAILABLE, exception.getMessage())));
        }

        private String stepMessage(final String status, final String errorClass) {
            if (PLAN_STATUS_COMPLETED.equals(status)) {
                return "Plan step crafted successfully.";
            }

            if (PLAN_STATUS_SKIPPED.equals(status)) {
                return this.skippedStepMessage(errorClass);
            }

            final String classifiedMessage = this.classifiedStepMessage(status, errorClass);
            if (!classifiedMessage.isBlank()) {
                return classifiedMessage;
            }

            if (PLAN_STATUS_BLOCKED.equals(status)) {
                return "Plan step is waiting for enough reserved input material and output space.";
            }
            if (PLAN_STATUS_PARTIAL.equals(status)) {
                return "Plan step partially completed before inputs or output space ran out.";
            }
            return "Plan step could not craft with the configured inventory routes.";
        }

        private String skippedStepMessage(final String errorClass) {
            if (PLAN_ERROR_UPSTREAM_FAILED.equals(errorClass)) {
                return "Skipped because a previous step failed.";
            }
            if (PLAN_ERROR_UPSTREAM_BLOCKED.equals(errorClass)) {
                return "Skipped because a previous step could not complete.";
            }
            return "Plan step was skipped.";
        }

        private String classifiedStepMessage(final String status, final String errorClass) {
            return switch (errorClass) {
                case PLAN_ERROR_RECIPE_INVALID -> "Plan step does not resolve to a valid crafting recipe.";
                case PLAN_ERROR_CPU_UNAVAILABLE -> "Plan step cannot run because the linked Crafting CPU is unavailable.";
                case PLAN_ERROR_ROUTE_MISSING -> "Plan step is missing a configured inventory route.";
                case PLAN_ERROR_MATERIAL_MISSING -> this.resourceStepMessage(
                        status,
                        "Plan step is waiting for the required input materials.",
                        "Plan step partially completed before required input materials ran out.",
                        "Plan step could not start because the required input materials are missing.");
                case PLAN_ERROR_BUFFER_FULL -> this.resourceStepMessage(
                        status,
                        "Plan step is waiting for free capacity in its intermediate buffer route.",
                        "Plan step partially completed before the intermediate buffer route ran out of space.",
                        "Plan step could not start because the intermediate buffer route is full.");
                case PLAN_ERROR_INTERMEDIATE_CONTAMINATED -> "Plan step cannot continue because its intermediate buffer route contains unexpected foreign items.";
                case PLAN_ERROR_OUTPUT_FULL -> this.resourceStepMessage(
                        status,
                        "Plan step is waiting for free capacity in its output route.",
                        "Plan step partially completed before the output route ran out of space.",
                        "Plan step could not start because the output route is full.");
                case PLAN_ERROR_INTERMEDIATE_MISSING -> "Plan step cannot continue because previously tracked intermediate items are missing from its buffer route.";
                default -> "";
            };
        }

        private String resourceStepMessage(final String status,
                                           final String blockedMessage,
                                           final String partialMessage,
                                           final String failedMessage) {
            if (PLAN_STATUS_BLOCKED.equals(status)) {
                return blockedMessage;
            }
            if (PLAN_STATUS_PARTIAL.equals(status)) {
                return partialMessage;
            }
            return failedMessage;
        }

        private ComputerPlanStepSnapshot buildPlanStepSnapshot(final PlanStepContext stepContext,
                                                               final int completedCrafts,
                                                               final String inputRoute,
                                                               final String outputRoute,
                                                               final String status,
                                                               final String errorClass,
                                                               final String message) {
            return new ComputerPlanStepSnapshot(
                    this.binding.apiName(),
                    stepContext.cycleIndex(),
                    stepContext.stepIndex(),
                    stepContext.craftingIo().getPlanStepWindowX(stepContext.stepIndex()),
                    stepContext.craftingIo().getPlanStepWindowY(stepContext.stepIndex()),
                    inputRoute,
                    outputRoute,
                    stepContext.recipeId(),
                    stepContext.resultItem(),
                    stepContext.requestedCrafts(),
                    completedCrafts,
                    stepContext.craftingIo().getQueuedPlanReservationMode().serializedName(),
                    errorClass,
                    status,
                    message
            );
        }

        private void recordPlanStep(final ComputerPlanStepSnapshot snapshot) {
            recordLimited(PythonHostApi.this.planStepSnapshots, snapshot, MAX_RECORDED_PLAN_STEPS);
            final ComputerOutputEntry outputEntry = snapshot.outputEntry();
            recordLimited(PythonHostApi.this.outputEntries, outputEntry, MAX_RECORDED_OUTPUT_ENTRIES);
            if (PythonHostApi.this.executionTranscript != null) {
                PythonHostApi.this.executionTranscript.recordStructuredOutput(outputEntry);
            }
        }

        private void recordSkippedSteps(final CraftingIOBlockEntity craftingIo,
                                        final CraftingCPUBlockEntity craftingCpu,
                                        final int cycleIndex,
                                        final int startStepIndex,
                                        final int totalCycles,
                                        final String errorClass) {
            final String message = this.stepMessage(PLAN_STATUS_SKIPPED, errorClass);
            for (int cycle = cycleIndex; cycle < totalCycles; cycle++) {
                final int firstStep = cycle == cycleIndex ? startStepIndex : 0;
                for (int stepIndex = firstStep; stepIndex < craftingIo.getPlanStepCount(); stepIndex++) {
                    final PlanStepContext stepContext = this.createPlanStepContext(craftingIo, craftingCpu, stepIndex, cycle);
                    this.recordPlanStep(this.buildPlanStepSnapshot(
                            stepContext,
                            0,
                            stepContext.configuredInputRoute(),
                            stepContext.configuredOutputRoute(),
                            PLAN_STATUS_SKIPPED,
                            errorClass,
                            message));
                }
            }
        }

        private static PlanExecutionException planExecutionError(final String errorClass, final String message) {
            return new PlanExecutionException(errorClass, message == null ? "Crafting plan execution failed." : message);
        }

        private void requireType(final String expectedType, final String action) {
            if (!expectedType.equals(this.type())) {
                throw this.unsupported(action);
            }
        }

        private void requireLocalOnly(final String action) {
            if (!this.binding.isBridged()) {
                return;
            }
            throw new IllegalStateException("Bridged endpoint '" + this.binding.apiName() + "' of type '" + this.binding.type() + "' cannot perform " + action + "; this action is local-only.");
        }

        private void requireRemoteWriteAllowed(final String action) {
            if (!this.binding.isBridged()) {
                return;
            }

            if (bridgeRemoteWritable(this.binding)) {
                return;
            }
            throw new IllegalStateException("Bridged endpoint '" + this.binding.apiName() + "' of type '" + this.binding.type() + "' is " + remotePolicyId(this.binding) + " over XLAPI and does not allow " + action + ".");
        }

        private IllegalStateException unsupported(final String action) {
            return new IllegalStateException("Endpoint '" + this.binding.apiName() + "' of type '" + this.binding.type() + "' does not support " + action + ".");
        }

        private record PlanRouteBinding(String label, MaterialIOBlockEntity materialIo, Direction side) {
        }

        private record InventoryReservationKey(BlockPos inventoryPos, int slotCount) {
        }

        private record SimulatedItemRouteState(List<ItemStack> slots) {
        }

        private record QueuedPlanReservation(boolean reserved,
                                             boolean terminal,
                                             PlanStepContext stepContext,
                                             String inputRouteLabel,
                                             String outputRouteLabel,
                                             String errorClass,
                                             String message,
                                             RuntimeException failure) {
            private static QueuedPlanReservation success() {
                return new QueuedPlanReservation(true, false, null, "", "", "", "", null);
            }

            private static QueuedPlanReservation blocked(final PlanStepContext stepContext,
                                                         final String inputRouteLabel,
                                                         final String outputRouteLabel,
                                                         final String errorClass,
                                                         final String message) {
                return new QueuedPlanReservation(false, false, stepContext, inputRouteLabel, outputRouteLabel, errorClass, message, null);
            }

            private static QueuedPlanReservation failure(final PlanStepContext stepContext,
                                                         final String inputRouteLabel,
                                                         final String outputRouteLabel,
                                                         final String errorClass,
                                                         final String message,
                                                         final RuntimeException failure) {
                return new QueuedPlanReservation(false, true, stepContext, inputRouteLabel, outputRouteLabel, errorClass, message, failure);
            }
        }

            private record QueuedPlanReservationAnalysis(int reservableCycles,
                                     int reservableSteps,
                                     int blockedCycleIndex,
                                     int blockedStepIndex) {
            }

        private record PlanStepContext(
                CraftingIOBlockEntity craftingIo,
                int stepIndex,
                int cycleIndex,
                int requestedCrafts,
                List<ItemStack> pattern,
                String recipeId,
                String resultItem,
                int resultCount,
                List<ItemStack> predictedOutputs,
                String configuredInputRoute,
                String configuredOutputRoute) {
        }

        private record PlanStepExecutionResult(ComputerPlanStepSnapshot snapshot, boolean stopPlan, RuntimeException failure) {
        }

            private record IntermediateRouteIssue(String errorClass, String message) {
        }

        private static final class PlanExecutionException extends IllegalStateException {
            private final String errorClass;

            private PlanExecutionException(final String errorClass, final String message) {
                super(message);
                this.errorClass = errorClass == null || errorClass.isBlank() ? PLAN_ERROR_INTERNAL : errorClass;
            }

            private String errorClass() {
                return this.errorClass;
            }
        }
    }

    public static final class WorldBridge {
        private final ServerLevel level;

        private WorldBridge(final ServerLevel level) {
            this.level = level;
        }

        @HostAccess.Export
        public boolean available() {
            return this.level != null;
        }

        @HostAccess.Export
        public String dimensionId() {
            if (this.level == null) {
                return UNKNOWN;
            }
            final ResourceLocation location = this.level.dimension().location();
            return location == null ? UNKNOWN : location.toString();
        }

        @HostAccess.Export
        public long gameTime() {
            return this.level == null ? 0L : this.level.getGameTime();
        }

        @HostAccess.Export
        public long dayTime() {
            return this.level == null ? 0L : this.level.getDayTime() % 24000L;
        }

        @HostAccess.Export
        public boolean isDay() {
            return this.level != null && this.level.isDay();
        }

        @HostAccess.Export
        public boolean isNight() {
            return this.level != null && this.level.isNight();
        }

        @HostAccess.Export
        public boolean isRaining() {
            return this.level != null && this.level.isRaining();
        }

        @HostAccess.Export
        public int rainLevel() {
            if (this.level == null) {
                return 0;
            }
            return Mth.clamp(Math.round(this.level.getRainLevel(1.0F) * 15.0F), 0, 15);
        }

        @HostAccess.Export
        public boolean isThundering() {
            return this.level != null && this.level.isThundering();
        }

        @HostAccess.Export
        public int moonPhase() {
            return this.level == null ? 0 : this.level.getMoonPhase();
        }

        @HostAccess.Export
        public String realTime() {
            return REAL_TIME_FORMATTER.format(Instant.now());
        }
    }

    private static String remotePolicyId(final PythonPeripheralBinding binding) {
        if (binding == null || !binding.isBridged()) {
            return REMOTE_POLICY_LOCAL;
        }
        return remoteWritableType(binding.type()) ? BRIDGE_POLICY_READ_WRITE : BRIDGE_POLICY_READ_ONLY;
    }

    private static boolean bridgeRemoteWritable(final PythonPeripheralBinding binding) {
        return binding != null && binding.isBridged() && remoteWritableType(binding.type());
    }

    private static boolean remoteWritableType(final String endpointType) {
        return switch (endpointType == null ? UNKNOWN : endpointType) {
            case TYPE_REDSTONE_IO -> true;
            case TYPE_SCREEN, TYPE_LIGHT_SENSOR, TYPE_RAIN_SENSOR, TYPE_CLOCK, TYPE_MATERIAL_IO, TYPE_CRAFTING_IO, TYPE_CRAFTING_CPU, TYPE_XLAPI_BLOCK -> false;
            default -> false;
        };
    }
}