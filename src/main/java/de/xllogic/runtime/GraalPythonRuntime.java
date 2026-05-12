package de.xllogic.runtime;

import de.xllogic.XLLogicMod;
import de.xllogic.common.config.XLServerConfig;
import de.xllogic.runtime.debug.XLRuntimeDebugger;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.EnvironmentAccess;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.ResourceLimits;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;

public final class GraalPythonRuntime implements PythonRuntime {
    private static final String PYTHON_LANGUAGE = "python";
    private static final String PY_CLASS_INIT = "    def __init__(self, bridge):\n";
    private static final String PY_ASSIGN_BRIDGE = "        self._bridge = bridge\n";
    private static final String PY_DEF_AVAILABLE = "    def available(self):\n";
    private static final String PY_CLOSE_DICT = "        }\n";
    private static final String DEVICE_METHOD_INVENTORY = "inventory";
    private static final String DEVICE_METHOD_RECIPE = "recipe";
    private static final String DEVICE_EXTENSION_REDSTONE = "redstone";
    private static final String DEVICE_EXTENSION_INVENTORY = "inventory";
    private static final String DEVICE_EXTENSION_RECIPE = "recipe";
    private static final String DEVICE_EXTENSION_CRAFTING = "crafting";
    private static final String DEVICE_EXTENSION_BRIDGE = "bridge";
    private static final String DEVICE_EXTENSION_STATE = "state";
    private static final String DEVICE_EXTENSION_SCREEN = "screen";
    private static final String BOOTSTRAP_BIND_NAME = "__xl_bind";
    private static final String BOOTSTRAP_BIND_METADATA_NAME = "__xl_bind_metadata";
    private static final String BOOTSTRAP_BIND_RUNTIME_OBJECTS_NAME = "__xl_bind_runtime_objects";
    private static final String PROGRAM_ITERATOR_NAME = "__xl_program_iter__";
    private static final String PROGRAM_RESUME_NAME = "__xl_resume_program";
    private static final String PERSISTENT_RUNNING_SUMMARY = "Program running across server ticks.";
    private static final String DEBUG_EXECUTE_FLUSH_TRANSCRIPT = "python.execute.flushTranscript";
    private static final String DEBUG_EXECUTE_PREPARE_DIAGNOSTICS = "python.execute.prepareDiagnostics";
    private static final String DEBUG_SESSION_SLICE_FLUSH_TRANSCRIPT = "python.session.slice.flushTranscript";
    private static final String DEBUG_SESSION_SLICE_PREPARE_DIAGNOSTICS = "python.session.slice.prepareDiagnostics";
    private static final int WATCHDOG_GRACE_CAP_MILLIS = 250;
    private static final Engine SHARED_ENGINE = createSharedEngine();
    private static final Map<String, String> DEVICE_DIRECT_METHOD_ALIASES = Map.copyOf(deviceDirectMethodAliases());
    private static final Map<String, String> DEVICE_LAZY_METHOD_EXTENSION_NAMES = Map.copyOf(deviceLazyMethodExtensions());
    private static final Map<String, String> DEVICE_LAZY_EXTENSION_SOURCES = Map.copyOf(deviceLazyExtensionSources());
    private static final PythonHostApi WARMUP_HOST_API = PythonExecutionContext.empty().hostApi();
    private static final String WARMUP_BOOTSTRAP_BIND_SOURCE = """
            _bootstrap = {
                'computer': {'name': 'warmup', 'position': '0,0,0', 'endpoint_count': 0},
                'endpoints': []
            }
            computer = _bootstrap['computer']
            endpoints = _bootstrap['endpoints']
            peripherals = {}
            endpoint_names = []
            device_names = []
            """;
            private static final String OUTPUT_HELPER_PREWARM_SOURCE_TEXT = """
                show_kv('warmup', {'status': 'ready'})
                show_table('warmup', ['key'], [['value']])
                """;
    private static final Source BOOTSTRAP_SOURCE = createCachedSource("xllogic-bootstrap.py", buildBootstrapSource());
            private static final Source OUTPUT_HELPER_PREWARM_SOURCE = createCachedSource("xllogic-output-prewarm.py", OUTPUT_HELPER_PREWARM_SOURCE_TEXT);
    private static final Queue<PreparedPersistentContext> PREPARED_PERSISTENT_CONTEXTS = new ConcurrentLinkedQueue<>();
    private static final AtomicBoolean RUNTIME_WARMUP_SCHEDULED = new AtomicBoolean(false);
    private static final AtomicBoolean RUNTIME_WARMUP_COMPLETED = new AtomicBoolean(false);
    private static final AtomicBoolean PREPARED_PERSISTENT_CONTEXT_SCHEDULED = new AtomicBoolean(false);
    private static final AtomicBoolean PREPARED_PERSISTENT_CONTEXT_FALLBACK_LOGGED = new AtomicBoolean(false);
    private static final AtomicInteger SESSION_WORKER_COUNTER = new AtomicInteger();
    private static final ExecutorService SESSION_EXECUTOR = Executors.newCachedThreadPool(runnable -> {
        final Thread thread = new Thread(runnable, "xllogic-python-session-" + SESSION_WORKER_COUNTER.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    });
    private static final ScheduledExecutorService WATCHDOG_EXECUTOR = Executors.newSingleThreadScheduledExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "xllogic-python-watchdog");
        thread.setDaemon(true);
        return thread;
    });
    private final boolean available;

    public GraalPythonRuntime() {
        this.available = detectAvailability();
        if (this.available) {
            ensureRuntimeWarmup();
            ensurePreparedPersistentContext();
        }
    }

    @Override
    public String displayName() {
        return "GraalPy";
    }

    @Override
    public boolean available() {
        return this.available;
    }

    @Override
    public PythonExecutionSession startSession(final String source, final PythonExecutionContext executionContext,
                                               final PythonExecutionLimits executionLimits) {
        if (!this.available) {
            return PythonExecutionSession.immediate(
                    () -> PythonExecutionResult.failure(List.of(), List.of(), "GraalPy is not available on the current classpath."));
        }

        ensureRuntimeWarmup();
        ensurePreparedPersistentContext();

        return withRuntimeClassLoader(() -> new PersistentExecutionSession(source, executionContext, executionLimits));
    }

    @Override
    public PythonExecutionResult execute(final String source, final PythonExecutionContext executionContext, final PythonExecutionLimits executionLimits) {
        if (!this.available) {
            return PythonExecutionResult.failure(List.of(), List.of(), "GraalPy is not available on the current classpath.");
        }

        ensureRuntimeWarmup();
        ensurePreparedPersistentContext();

        return withRuntimeClassLoader(() -> this.executeInternal(source, executionContext, executionLimits));
    }

    private PythonExecutionResult executeInternal(final String source, final PythonExecutionContext executionContext,
                                                  final PythonExecutionLimits executionLimits) {
        final long debugStartedAt = XLRuntimeDebugger.beginSection("python.execute.immediate");
        try {
            final PythonHostApi hostApi = executionContext.hostApi();
            final PythonExecutionTranscript transcript = new PythonExecutionTranscript(XLServerConfig.INSTANCE.maxStdoutBytes(), XLServerConfig.INSTANCE.maxStderrBytes());
            final AtomicBoolean statementBudgetExceeded = new AtomicBoolean(false);
            final AtomicBoolean watchdogLimitExceeded = new AtomicBoolean(false);
            hostApi.beginExecution(transcript);

            final Context.Builder contextBuilder = createContextBuilder(transcript);
            if (executionLimits != null && executionLimits.limited()) {
                contextBuilder.resourceLimits(ResourceLimits.newBuilder()
                        .statementLimit(executionLimits.maxStatements(), sourceFilter -> true)
                        .onLimit(event -> statementBudgetExceeded.set(true))
                        .build());
            }

            try (Context polyglotContext = buildContextTimed(contextBuilder, "python.execute.contextBuild")) {
                final ScheduledFuture<?> watchdogTask = scheduleWatchdog(polyglotContext, watchdogLimitExceeded);
                try {
                    bindHostBridgeTimed(polyglotContext, hostApi, "python.execute.bindBridge");
                    evalTimed(polyglotContext, BOOTSTRAP_SOURCE, "python.execute.bootstrapEval");
                    evalTimed(polyglotContext, source, "python.execute.sourceEval");
                    flushTranscriptTimed(transcript, DEBUG_EXECUTE_FLUSH_TRANSCRIPT);
                    prepareExecutionDiagnosticsTimed(hostApi, DEBUG_EXECUTE_PREPARE_DIAGNOSTICS);
                    return PythonExecutionResult.success(
                        transcript.stdoutLines(),
                        transcript.stderrLines(),
                        successSummary(executionContext),
                        transcript.outputEntries(),
                        hostApi.planStepSnapshots(),
                        hostApi.planJobSnapshot());
                } finally {
                    if (watchdogTask != null) {
                        watchdogTask.cancel(false);
                    }
                }
            } catch (final PolyglotException exception) {
                flushTranscriptTimed(transcript, DEBUG_EXECUTE_FLUSH_TRANSCRIPT);
                    prepareExecutionDiagnosticsTimed(hostApi, DEBUG_EXECUTE_PREPARE_DIAGNOSTICS);
                    return PythonExecutionResult.failure(
                        transcript.stdoutLines(),
                        transcript.stderrLines(),
                        failureSummary(exception, executionLimits, statementBudgetExceeded.get(), watchdogLimitExceeded.get()),
                        transcript.outputEntries(),
                        hostApi.planStepSnapshots(),
                        hostApi.planJobSnapshot());
            } catch (final RuntimeException exception) {
                flushTranscriptTimed(transcript, DEBUG_EXECUTE_FLUSH_TRANSCRIPT);
                    prepareExecutionDiagnosticsTimed(hostApi, DEBUG_EXECUTE_PREPARE_DIAGNOSTICS);
                    return PythonExecutionResult.failure(
                        transcript.stdoutLines(),
                        transcript.stderrLines(),
                        runtimeFailureSummary(exception, watchdogLimitExceeded.get()),
                        transcript.outputEntries(),
                        hostApi.planStepSnapshots(),
                        hostApi.planJobSnapshot());
            } finally {
                hostApi.finishExecution();
            }
        } finally {
            XLRuntimeDebugger.endSection("python.execute.immediate", debugStartedAt);
        }
    }

    private final class PersistentExecutionSession implements PythonExecutionSession {
        private final String source;
        private final PythonExecutionContext executionContext;
        private final PythonExecutionLimits executionLimits;
        private final PythonHostApi hostApi;
        private final PythonExecutionTranscript transcript;
        private final AtomicBoolean advanceScheduled = new AtomicBoolean(false);
        private final AtomicBoolean finished = new AtomicBoolean(false);
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private volatile Context polyglotContext;
        private volatile Value resumeFunction;
        private volatile ComputerRuntimeSnapshot snapshot;
        private volatile boolean initialized;

        private PersistentExecutionSession(final String source, final PythonExecutionContext executionContext,
                                           final PythonExecutionLimits executionLimits) {
            this.source = source;
            this.executionContext = executionContext;
            this.executionLimits = executionLimits;
            this.hostApi = executionContext.hostApi();
            this.transcript = new PythonExecutionTranscript(XLServerConfig.INSTANCE.maxStdoutBytes(), XLServerConfig.INSTANCE.maxStderrBytes());
            this.hostApi.beginExecution(this.transcript);
            this.snapshot = this.runningSnapshot();
        }

        @Override
        public void advanceTick() {
            if (this.finished.get() || this.closed.get()) {
                return;
            }

            if (!this.advanceScheduled.compareAndSet(false, true)) {
                return;
            }

            SESSION_EXECUTOR.execute(() -> {
                try {
                    withRuntimeClassLoader(() -> {
                        if (!this.finished.get() && !this.closed.get()) {
                            this.advanceTickInternal();
                        }
                        return null;
                    });
                } finally {
                    this.advanceScheduled.set(false);
                }
            });
        }

        private void advanceTickInternal() {
            final long debugStartedAt = XLRuntimeDebugger.beginSection("python.session.slice");
            try {
                if (!this.ensureInitialized()) {
                    return;
                }

                final AtomicBoolean watchdogLimitExceeded = new AtomicBoolean(false);
                final ScheduledFuture<?> watchdogTask = scheduleWatchdog(this.polyglotContext, watchdogLimitExceeded);
                try {
                    final boolean stillRunning = executeResumeTimed(this.resumeFunction, "python.session.slice.resume");
                    flushTranscriptTimed(this.transcript, DEBUG_SESSION_SLICE_FLUSH_TRANSCRIPT);
                    if (stillRunning) {
                        this.snapshot = this.runningSnapshot();
                        return;
                    }

                    prepareExecutionDiagnosticsTimed(this.hostApi, DEBUG_SESSION_SLICE_PREPARE_DIAGNOSTICS);
                    this.finishWithResult(PythonExecutionResult.success(
                            this.transcript.stdoutLines(),
                            this.transcript.stderrLines(),
                            successSummary(this.executionContext),
                            this.transcript.outputEntries(),
                            this.hostApi.planStepSnapshots(),
                            this.hostApi.planJobSnapshot()));
                } catch (final PolyglotException exception) {
                    flushTranscriptTimed(this.transcript, DEBUG_SESSION_SLICE_FLUSH_TRANSCRIPT);
                    prepareExecutionDiagnosticsTimed(this.hostApi, DEBUG_SESSION_SLICE_PREPARE_DIAGNOSTICS);
                    this.finishWithFailure(failureSummary(exception, this.executionLimits, false, watchdogLimitExceeded.get()));
                } catch (final RuntimeException exception) {
                    flushTranscriptTimed(this.transcript, DEBUG_SESSION_SLICE_FLUSH_TRANSCRIPT);
                    prepareExecutionDiagnosticsTimed(this.hostApi, DEBUG_SESSION_SLICE_PREPARE_DIAGNOSTICS);
                    this.finishWithFailure(runtimeFailureSummary(exception, watchdogLimitExceeded.get()));
                } finally {
                    if (watchdogTask != null) {
                        watchdogTask.cancel(false);
                    }
                }
            } finally {
                XLRuntimeDebugger.endSection("python.session.slice", debugStartedAt);
            }
        }

        private boolean ensureInitialized() {
            final long debugStartedAt = XLRuntimeDebugger.beginSection("python.session.initialize");
            try {
                if (this.initialized || this.finished.get() || this.closed.get()) {
                    return this.initialized;
                }

                final PreparedPersistentContext preparedContext = takePreparedPersistentContext();
                try {
                    if (preparedContext != null) {
                        this.polyglotContext = preparedContext.context();
                        bindPreparedContextTimed(preparedContext, this.hostApi, this.transcript, "python.session.initialize.bindBridge");
                        executePreparedBootstrapTimed(
                                preparedContext,
                                "python.session.initialize.bootstrapRebind",
                                "python.session.initialize.bootstrapRebind.metadata",
                                "python.session.initialize.bootstrapRebind.runtimeObjects");
                    } else {
                        this.polyglotContext = buildContextTimed(createContextBuilder(this.transcript), "python.session.initialize.contextBuild");
                        bindHostBridgeTimed(this.polyglotContext, this.hostApi, "python.session.initialize.bindBridge");
                        evalTimed(this.polyglotContext, BOOTSTRAP_SOURCE, "python.session.initialize.bootstrapEval");
                    }
                    evalTimed(this.polyglotContext, buildPersistentProgramSource(this.source), "python.session.initialize.programEval");
                    this.resumeFunction = resolveBindingMemberTimed(this.polyglotContext, PROGRAM_RESUME_NAME, "python.session.initialize.resolveResume");
                    if (this.resumeFunction == null || !this.resumeFunction.canExecute()) {
                        throw new IllegalStateException("Persistent Python session could not bind a resume function.");
                    }
                    this.initialized = true;
                    return true;
                } catch (final PolyglotException exception) {
                    flushTranscriptTimed(this.transcript, "python.session.initialize.flushTranscript");
                    prepareExecutionDiagnosticsTimed(this.hostApi, "python.session.initialize.prepareDiagnostics");
                    this.finishWithFailure(failureSummary(exception, this.executionLimits, false, false));
                } catch (final RuntimeException exception) {
                    flushTranscriptTimed(this.transcript, "python.session.initialize.flushTranscript");
                    prepareExecutionDiagnosticsTimed(this.hostApi, "python.session.initialize.prepareDiagnostics");
                    this.finishWithFailure(runtimeFailureSummary(exception, false));
                }
                return false;
            } finally {
                XLRuntimeDebugger.endSection("python.session.initialize", debugStartedAt);
            }
        }

        @Override
        public boolean finished() {
            return this.finished.get();
        }

        @Override
        public ComputerRuntimeSnapshot snapshot() {
            return this.snapshot;
        }

        @Override
        public void close() {
            withRuntimeClassLoader(() -> {
                this.closeInternal();
                return null;
            });
        }

        private ComputerRuntimeSnapshot runningSnapshot() {
            final List<ComputerOutputEntry> outputEntries = this.transcript.outputEntries();
            final List<ComputerOutputEntry> visibleEntries = outputEntries.isEmpty()
                ? List.of(ComputerOutputEntry.hint(persistentRunningHint()))
                    : outputEntries;
            return new ComputerRuntimeSnapshot(
                    true,
                    true,
                    runningSummary(this.executionContext),
                    List.of(),
                    visibleEntries,
                    this.hostApi.planStepSnapshots(),
                    this.hostApi.planJobSnapshot());
        }

        private void finishWithResult(final PythonExecutionResult result) {
            this.snapshot = ComputerRuntimeSnapshot.fromExecutionResult(result);
            this.finished.set(true);
            this.closeInternal();
        }

        private void finishWithFailure(final String summary) {
            this.snapshot = ComputerRuntimeSnapshot.fromExecutionResult(PythonExecutionResult.failure(
                    this.transcript.stdoutLines(),
                    this.transcript.stderrLines(),
                    summary,
                    this.transcript.outputEntries(),
                    this.hostApi.planStepSnapshots(),
                    this.hostApi.planJobSnapshot()));
            this.finished.set(true);
            this.closeInternal();
        }

        private void closeInternal() {
            if (!this.closed.compareAndSet(false, true)) {
                return;
            }

            try {
                if (this.polyglotContext != null) {
                    this.polyglotContext.close(true);
                }
            } catch (final RuntimeException ignored) {
                // The context may already be closed or aborting from a watchdog-triggered cancellation.
            } finally {
                this.hostApi.finishExecution();
            }
        }
    }

    private static Context.Builder createContextBuilder(final PythonExecutionTranscript transcript) {
        return createContextBuilder(transcript.stdoutStream(), transcript.stderrStream());
    }

    private static Context.Builder createContextBuilder(final OutputStream stdout, final OutputStream stderr) {
        final Context.Builder contextBuilder = Context.newBuilder(PYTHON_LANGUAGE)
                .out(stdout)
                .err(stderr)
                .allowIO(IOAccess.NONE)
                .allowEnvironmentAccess(EnvironmentAccess.NONE)
                .allowCreateProcess(false)
                .allowCreateThread(false)
                .allowNativeAccess(false)
                .allowPolyglotAccess(PolyglotAccess.NONE)
                .allowHostClassLoading(false)
                .useSystemExit(false)
                .allowHostAccess(HostAccess.EXPLICIT)
                .allowHostClassLookup(className -> false);
        if (SHARED_ENGINE != null) {
            contextBuilder.engine(SHARED_ENGINE);
        } else {
            contextBuilder.option("engine.WarnInterpreterOnly", "false");
        }
        return contextBuilder;
    }

    private static Context buildContextTimed(final Context.Builder contextBuilder, final String debugSectionKey) {
        final long debugStartedAt = XLRuntimeDebugger.beginSection(debugSectionKey);
        try {
            return contextBuilder.build();
        } finally {
            XLRuntimeDebugger.endSection(debugSectionKey, debugStartedAt);
        }
    }

    private static void bindHostBridgeTimed(final Context polyglotContext, final PythonHostApi hostApi, final String debugSectionKey) {
        final long debugStartedAt = XLRuntimeDebugger.beginSection(debugSectionKey);
        try {
            polyglotContext.getBindings(PYTHON_LANGUAGE).putMember("__xl", hostApi.exportedBridge());
        } finally {
            XLRuntimeDebugger.endSection(debugSectionKey, debugStartedAt);
        }
    }

    private static void bindPreparedContextTimed(final PreparedPersistentContext preparedContext,
                                                 final PythonHostApi hostApi,
                                                 final PythonExecutionTranscript transcript,
                                                 final String debugSectionKey) {
        final long debugStartedAt = XLRuntimeDebugger.beginSection(debugSectionKey);
        try {
            preparedContext.bindSession(hostApi, transcript);
        } finally {
            XLRuntimeDebugger.endSection(debugSectionKey, debugStartedAt);
        }
    }

    private static void evalTimed(final Context polyglotContext, final String source, final String debugSectionKey) {
        final long debugStartedAt = XLRuntimeDebugger.beginSection(debugSectionKey);
        try {
            polyglotContext.eval(PYTHON_LANGUAGE, source);
        } finally {
            XLRuntimeDebugger.endSection(debugSectionKey, debugStartedAt);
        }
    }

    private static void evalTimed(final Context polyglotContext, final Source source, final String debugSectionKey) {
        final long debugStartedAt = XLRuntimeDebugger.beginSection(debugSectionKey);
        try {
            polyglotContext.eval(source);
        } finally {
            XLRuntimeDebugger.endSection(debugSectionKey, debugStartedAt);
        }
    }

    private static Value resolveBindingMemberTimed(final Context polyglotContext, final String memberName, final String debugSectionKey) {
        final long debugStartedAt = XLRuntimeDebugger.beginSection(debugSectionKey);
        try {
            return polyglotContext.getBindings(PYTHON_LANGUAGE).getMember(memberName);
        } finally {
            XLRuntimeDebugger.endSection(debugSectionKey, debugStartedAt);
        }
    }

    private static boolean executeResumeTimed(final Value resumeFunction, final String debugSectionKey) {
        final long debugStartedAt = XLRuntimeDebugger.beginSection(debugSectionKey);
        try {
            return resumeFunction.execute().asBoolean();
        } finally {
            XLRuntimeDebugger.endSection(debugSectionKey, debugStartedAt);
        }
    }

    private static void executePreparedBootstrapTimed(final PreparedPersistentContext preparedContext,
                                                     final String debugSectionKey,
                                                     final String metadataDebugSectionKey,
                                                     final String runtimeObjectsDebugSectionKey) {
        final long debugStartedAt = XLRuntimeDebugger.beginSection(debugSectionKey);
        try {
            executePreparedBootstrapPhaseTimed(preparedContext::rebindMetadata, metadataDebugSectionKey);
            executePreparedBootstrapPhaseTimed(preparedContext::rebindRuntimeObjects, runtimeObjectsDebugSectionKey);
        } finally {
            XLRuntimeDebugger.endSection(debugSectionKey, debugStartedAt);
        }
    }

    private static void executePreparedBootstrapPhaseTimed(final Runnable action, final String debugSectionKey) {
        final long debugStartedAt = XLRuntimeDebugger.beginSection(debugSectionKey);
        try {
            action.run();
        } finally {
            XLRuntimeDebugger.endSection(debugSectionKey, debugStartedAt);
        }
    }

    private static void flushTranscriptTimed(final PythonExecutionTranscript transcript, final String debugSectionKey) {
        final long debugStartedAt = XLRuntimeDebugger.beginSection(debugSectionKey);
        try {
            transcript.flush();
        } finally {
            XLRuntimeDebugger.endSection(debugSectionKey, debugStartedAt);
        }
    }

    private static void prepareExecutionDiagnosticsTimed(final PythonHostApi hostApi, final String debugSectionKey) {
        final long debugStartedAt = XLRuntimeDebugger.beginSection(debugSectionKey);
        try {
            hostApi.prepareExecutionDiagnostics();
        } finally {
            XLRuntimeDebugger.endSection(debugSectionKey, debugStartedAt);
        }
    }

    private static Engine createSharedEngine() {
        return withRuntimeClassLoader(() -> {
            try {
                return Engine.newBuilder()
                        .option("engine.WarnInterpreterOnly", "false")
                        .build();
            } catch (final RuntimeException exception) {
                XLLogicMod.LOGGER.warn("Failed to create shared GraalPy engine.", exception);
                return null;
            }
        });
    }

    private static void ensureRuntimeWarmup() {
        if (SHARED_ENGINE == null || RUNTIME_WARMUP_COMPLETED.get() || !RUNTIME_WARMUP_SCHEDULED.compareAndSet(false, true)) {
            return;
        }

        withRuntimeClassLoader(() -> {
            warmupRuntime();
            return null;
        });

        if (!RUNTIME_WARMUP_COMPLETED.get()) {
            RUNTIME_WARMUP_SCHEDULED.set(false);
        }
    }

    private static void warmupRuntime() {
        if (RUNTIME_WARMUP_COMPLETED.get()) {
            return;
        }

        final long debugStartedAt = XLRuntimeDebugger.beginSection("python.runtime.warmup");
        final PythonExecutionTranscript transcript = new PythonExecutionTranscript(
                XLServerConfig.INSTANCE.maxStdoutBytes(),
                XLServerConfig.INSTANCE.maxStderrBytes());
        boolean warmupSucceeded = false;
        try (Context warmupContextHandle = buildContextTimed(createContextBuilder(transcript), "python.runtime.warmup.contextBuild")) {
            final long bindingsStartedAt = XLRuntimeDebugger.beginSection("python.runtime.warmup.bindings");
            try {
                warmupContextHandle.getBindings(PYTHON_LANGUAGE).putMember("__xl", new WarmupBootstrapBridge());
            } finally {
                XLRuntimeDebugger.endSection("python.runtime.warmup.bindings", bindingsStartedAt);
            }

            evalTimed(warmupContextHandle, BOOTSTRAP_SOURCE, "python.runtime.warmup.bootstrapEval");
            evalTimed(warmupContextHandle, OUTPUT_HELPER_PREWARM_SOURCE, "python.runtime.warmup.outputPrewarm");
            warmupSucceeded = true;
        } catch (final RuntimeException exception) {
            XLLogicMod.LOGGER.warn("GraalPy runtime warmup failed.", exception);
        } finally {
            XLRuntimeDebugger.endSection("python.runtime.warmup", debugStartedAt);
        }

        if (!warmupSucceeded) {
            return;
        }

        prepareInitialPersistentContext();
        RUNTIME_WARMUP_COMPLETED.set(true);
        ensurePreparedPersistentContext();
    }

    private static Source createCachedSource(final String name, final String source) {
        return Source.newBuilder(PYTHON_LANGUAGE, source, name)
                .cached(true)
                .buildLiteral();
    }

    private static PreparedPersistentContext takePreparedPersistentContext() {
        ensureRuntimeWarmup();

        PreparedPersistentContext preparedContext = PREPARED_PERSISTENT_CONTEXTS.poll();
        if (preparedContext != null) {
            XLLogicMod.LOGGER.info("Using reusable GraalPy persistent-session context. queuedRemaining={}", PREPARED_PERSISTENT_CONTEXTS.size());
            ensurePreparedPersistentContext();
            return preparedContext;
        }

        if (PREPARED_PERSISTENT_CONTEXT_FALLBACK_LOGGED.compareAndSet(false, true)) {
            if (RUNTIME_WARMUP_COMPLETED.get()) {
                XLLogicMod.LOGGER.warn("Reusable GraalPy persistent-session context was unavailable at session start. Falling back to full bootstrap.");
            } else {
                XLLogicMod.LOGGER.warn("GraalPy runtime warmup did not complete before session start. Falling back to full bootstrap.");
            }
        }

        ensurePreparedPersistentContext();
        return null;
    }

    private static void prepareInitialPersistentContext() {
        if (!PREPARED_PERSISTENT_CONTEXTS.isEmpty()) {
            return;
        }

        final PreparedPersistentContext preparedContext = createPreparedPersistentContext();
        if (preparedContext != null) {
            PREPARED_PERSISTENT_CONTEXTS.offer(preparedContext);
            XLLogicMod.LOGGER.info("Primed reusable GraalPy persistent-session context during warmup. queuedContexts={}", PREPARED_PERSISTENT_CONTEXTS.size());
        } else {
            XLLogicMod.LOGGER.warn("Failed to prime a reusable GraalPy persistent-session context during warmup.");
        }
    }

    private static void ensurePreparedPersistentContext() {
        if (!RUNTIME_WARMUP_COMPLETED.get() || SHARED_ENGINE == null || !PREPARED_PERSISTENT_CONTEXTS.isEmpty()) {
            return;
        }
        if (!PREPARED_PERSISTENT_CONTEXT_SCHEDULED.compareAndSet(false, true)) {
            return;
        }

        SESSION_EXECUTOR.execute(() -> withRuntimeClassLoader(() -> {
            try {
                if (!PREPARED_PERSISTENT_CONTEXTS.isEmpty()) {
                    return null;
                }

                final PreparedPersistentContext preparedContext = createPreparedPersistentContext();
                if (preparedContext != null) {
                    PREPARED_PERSISTENT_CONTEXTS.offer(preparedContext);
                    PREPARED_PERSISTENT_CONTEXT_FALLBACK_LOGGED.set(false);
                    XLLogicMod.LOGGER.info("Prepared a reusable GraalPy persistent-session context asynchronously. queuedContexts={}", PREPARED_PERSISTENT_CONTEXTS.size());
                } else {
                    XLLogicMod.LOGGER.warn("Failed to replenish a reusable GraalPy persistent-session context.");
                }
                return null;
            } finally {
                PREPARED_PERSISTENT_CONTEXT_SCHEDULED.set(false);
            }
        }));
    }

    private static PreparedPersistentContext createPreparedPersistentContext() {
        final DelegatingOutputStream stdout = new DelegatingOutputStream();
        final DelegatingOutputStream stderr = new DelegatingOutputStream();
        final PreparedBootstrapBridge bootstrapBridge = new PreparedBootstrapBridge();
        try {
            final Context polyglotContext = buildContextTimed(
                    createContextBuilder(stdout, stderr),
                    "python.runtime.preparedPersistentContext.contextBuild");
            return initializePreparedPersistentContext(polyglotContext, bootstrapBridge, stdout, stderr);
        } catch (final RuntimeException exception) {
            XLLogicMod.LOGGER.warn("Failed to prepare a reusable GraalPy persistent-session context.", exception);
            return null;
        }
    }

    private static PreparedPersistentContext initializePreparedPersistentContext(final Context polyglotContext,
                                                                                 final PreparedBootstrapBridge bootstrapBridge,
                                                                                 final DelegatingOutputStream stdout,
                                                                                 final DelegatingOutputStream stderr) {
        try {
            polyglotContext.getBindings(PYTHON_LANGUAGE).putMember("__xl", bootstrapBridge);
            evalTimed(polyglotContext, BOOTSTRAP_SOURCE, "python.runtime.preparedPersistentContext.bootstrapEval");
            evalTimed(polyglotContext, OUTPUT_HELPER_PREWARM_SOURCE, "python.runtime.preparedPersistentContext.outputPrewarm");
            final Value bindMetadataFunction = resolveBindingMemberTimed(
                    polyglotContext,
                    BOOTSTRAP_BIND_METADATA_NAME,
                    "python.runtime.preparedPersistentContext.resolveBindMetadata");
            final Value bindRuntimeObjectsFunction = resolveBindingMemberTimed(
                    polyglotContext,
                    BOOTSTRAP_BIND_RUNTIME_OBJECTS_NAME,
                    "python.runtime.preparedPersistentContext.resolveBindRuntimeObjects");
            if (bindMetadataFunction == null || !bindMetadataFunction.canExecute()
                    || bindRuntimeObjectsFunction == null || !bindRuntimeObjectsFunction.canExecute()) {
                XLLogicMod.LOGGER.warn("Prepared GraalPy persistent-session context bootstrap did not expose reusable bind functions.");
                polyglotContext.close(true);
                return null;
            }
            return new PreparedPersistentContext(
                    polyglotContext,
                    bootstrapBridge,
                    stdout,
                    stderr,
                    bindMetadataFunction,
                    bindRuntimeObjectsFunction);
        } catch (final RuntimeException exception) {
            polyglotContext.close(true);
            throw exception;
        }
    }

    private static boolean detectAvailability() {
        if (SHARED_ENGINE == null) {
            return false;
        }

        return withRuntimeClassLoader(() -> {
            try {
                final boolean pythonAvailable = SHARED_ENGINE.getLanguages().containsKey(PYTHON_LANGUAGE);
                if (!pythonAvailable) {
                    XLLogicMod.LOGGER.warn("GraalPy engine initialized without python language. Available languages: {}", SHARED_ENGINE.getLanguages().keySet());
                }
                return pythonAvailable;
            } catch (final RuntimeException exception) {
                XLLogicMod.LOGGER.warn("Failed to detect GraalPy availability.", exception);
                return false;
            }
        });
    }

    private static <T> T withRuntimeClassLoader(final Supplier<T> action) {
        final Thread currentThread = Thread.currentThread();
        final ClassLoader previousLoader = currentThread.getContextClassLoader();
        final ClassLoader runtimeLoader = GraalPythonRuntime.class.getClassLoader();
        if (previousLoader == runtimeLoader) {
            return action.get();
        }

        currentThread.setContextClassLoader(runtimeLoader);
        try {
            return action.get();
        } finally {
            currentThread.setContextClassLoader(previousLoader);
        }
    }

    private static String successSummary(final PythonExecutionContext executionContext) {
        if (executionContext.endpointCount() == 0) {
            return "Execution completed.";
        }
        return "Execution completed with " + executionContext.endpointCount() + " bound endpoints.";
    }

    private static String runningSummary(final PythonExecutionContext executionContext) {
        if (executionContext.endpointCount() == 0) {
            return PERSISTENT_RUNNING_SUMMARY;
        }
        return PERSISTENT_RUNNING_SUMMARY + " Bound endpoints: " + executionContext.endpointCount() + ".";
    }

    private static String persistentRunningHint() {
        return "Loop helper: yielded sessions resume every "
                + XLServerConfig.INSTANCE.persistentResumeIntervalTicks()
                + " server ticks by default. Use 'yield from pause(1)' or 'yield from repeat(step, 1)' for the beginner helpers, or 'yield from sleep_ticks(1)' and 'yield from run_loop(step, 1)' for the low-level helpers.";
    }

    private static String buildPersistentProgramSource(final String source) {
        final String normalizedSource = normalizeSource(source);
        final StringBuilder builder = new StringBuilder();
        builder.append("def next_tick():\n");
        builder.append("    yield None\n");
        builder.append("\n");
        builder.append("def sleep_ticks(ticks=1):\n");
        builder.append("    try:\n");
        builder.append("        remaining = int(ticks)\n");
        builder.append("    except Exception:\n");
        builder.append("        remaining = 1\n");
        builder.append("    if remaining <= 0:\n");
        builder.append("        remaining = 1\n");
        builder.append("    while remaining > 0:\n");
        builder.append("        remaining -= 1\n");
        builder.append("        yield None\n");
        builder.append("\n");
        builder.append("def run_loop(step, delay_ticks=1):\n");
        builder.append("    while True:\n");
        builder.append("        step()\n");
        builder.append("        yield from sleep_ticks(delay_ticks)\n");
        builder.append("\n");
        builder.append("def __xl_program__():\n");
        if (normalizedSource.isBlank()) {
            builder.append("    pass\n");
        } else {
            appendIndentedPython(builder, normalizedSource, "    ");
            if (!normalizedSource.endsWith("\n")) {
                builder.append('\n');
            }
        }
        builder.append("    if False:\n");
        builder.append("        yield None\n");
        builder.append("\n");
        builder.append(PROGRAM_ITERATOR_NAME).append(" = __xl_program__()\n");
        builder.append("def ").append(PROGRAM_RESUME_NAME).append("():\n");
        builder.append("    global ").append(PROGRAM_ITERATOR_NAME).append("\n");
        builder.append("    try:\n");
        builder.append("        next(").append(PROGRAM_ITERATOR_NAME).append(")\n");
        builder.append("        return True\n");
        builder.append("    except StopIteration:\n");
        builder.append("        ").append(PROGRAM_ITERATOR_NAME).append(" = None\n");
        builder.append("        return False\n");
        return builder.toString();
    }

    private static String normalizeSource(final String source) {
        if (source == null || source.isEmpty()) {
            return "";
        }
        return source.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static void appendIndentedPython(final StringBuilder builder, final String source, final String indentation) {
        final String[] lines = source.split("\n", -1);
        final int lineCount = source.endsWith("\n") ? lines.length - 1 : lines.length;
        for (int index = 0; index < lineCount; index++) {
            builder.append(indentation).append(lines[index]).append('\n');
        }
    }

    private static ScheduledFuture<?> scheduleWatchdog(final Context polyglotContext, final AtomicBoolean watchdogLimitExceeded) {
        final int maxCpuTimeMillis = XLServerConfig.INSTANCE.maxCpuTimeMillis();
        if (maxCpuTimeMillis <= 0) {
            return null;
        }

        final int checkIntervalMillis = Math.max(1, XLServerConfig.INSTANCE.cpuTimeCheckIntervalMillis());
        final long startedAtNanos = System.nanoTime();
        final long timeoutNanos = TimeUnit.MILLISECONDS.toNanos(maxCpuTimeMillis + watchdogGraceMillis(maxCpuTimeMillis, checkIntervalMillis));
        final long initialDelayMillis = Math.min(checkIntervalMillis, maxCpuTimeMillis);
        return WATCHDOG_EXECUTOR.scheduleAtFixedRate(() -> {
            if (watchdogLimitExceeded.get()) {
                return;
            }

            if (System.nanoTime() - startedAtNanos >= timeoutNanos) {
                watchdogLimitExceeded.set(true);
                try {
                    polyglotContext.close(true);
                } catch (final RuntimeException ignored) {
                    // The context may already be tearing down from another failure path.
                }
            }
        }, initialDelayMillis, checkIntervalMillis, TimeUnit.MILLISECONDS);
    }

    private static long watchdogGraceMillis(final int maxCpuTimeMillis, final int checkIntervalMillis) {
        if (maxCpuTimeMillis <= 0) {
            return 0L;
        }

        return Math.min(WATCHDOG_GRACE_CAP_MILLIS, Math.max(checkIntervalMillis * 2L, maxCpuTimeMillis / 4L));
    }

    static String bootstrapExtensionSource(final String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        return DEVICE_LAZY_EXTENSION_SOURCES.getOrDefault(name, "");
    }

    private static String failureSummary(final PolyglotException exception, final PythonExecutionLimits executionLimits,
                                         final boolean statementBudgetExceeded, final boolean watchdogLimitExceeded) {
        if (statementBudgetExceeded && executionLimits != null && executionLimits.limited()) {
            if (!executionLimits.limitExceededSummary().isBlank()) {
                return executionLimits.limitExceededSummary();
            }
            return "Execution stopped after " + executionLimits.maxStatements() + " Python statements.";
        }
        if (watchdogLimitExceeded) {
            return watchdogFailureSummary();
        }
        final OutputLimitExceededException outputLimitExceeded = findOutputLimitExceeded(exception);
        if (outputLimitExceeded != null) {
            return outputLimitExceeded.summary();
        }
        final String outputLimitSummary = outputLimitFailureSummary(exception.getMessage());
        if (!outputLimitSummary.isBlank()) {
            return outputLimitSummary;
        }
        final String blockedAccessSummary = blockedAccessFailureSummary(exception.getMessage());
        if (!blockedAccessSummary.isBlank()) {
            return blockedAccessSummary;
        }
        final String sandboxSummary = sandboxFailureSummary(exception);
        if (!sandboxSummary.isBlank()) {
            return sandboxSummary;
        }
        if (exception.getMessage() != null && !exception.getMessage().isBlank()) {
            return exception.getMessage();
        }
        return "Python execution failed.";
    }

    private static String runtimeFailureSummary(final RuntimeException exception, final boolean watchdogLimitExceeded) {
        if (watchdogLimitExceeded) {
            return watchdogFailureSummary();
        }
        final OutputLimitExceededException outputLimitExceeded = findOutputLimitExceeded(exception);
        if (outputLimitExceeded != null) {
            return outputLimitExceeded.summary();
        }
        final String outputLimitSummary = outputLimitFailureSummary(exception.getMessage());
        if (!outputLimitSummary.isBlank()) {
            return outputLimitSummary;
        }
        if (exception.getMessage() != null && !exception.getMessage().isBlank()) {
            return exception.getMessage();
        }
        return "Python execution failed.";
    }

    private static String blockedAccessFailureSummary(final String message) {
        if (message == null || message.isBlank()) {
            return "";
        }

        final String normalizedMessage = message.toLowerCase(Locale.ROOT);
        if (normalizedMessage.contains("xl logic sandbox blocks host os, filesystem, process, and network access")) {
            return message;
        }
        return "";
    }

    private static String sandboxFailureSummary(final PolyglotException exception) {
        if (!exception.isCancelled() && !exception.isResourceExhausted()) {
            return "";
        }

        final String message = exception.getMessage();
        final String normalizedMessage = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (normalizedMessage.contains("cpu") && normalizedMessage.contains("time")) {
            return "Execution stopped after exceeding the configured CPU time limit of " + XLServerConfig.INSTANCE.maxCpuTimeMillis() + " ms.";
        }
        if (normalizedMessage.contains("output stream")) {
            return "Execution stopped after exceeding the configured stdout limit of " + XLServerConfig.INSTANCE.maxStdoutBytes() + " B.";
        }
        if (normalizedMessage.contains("error stream")) {
            return "Execution stopped after exceeding the configured stderr limit of " + XLServerConfig.INSTANCE.maxStderrBytes() + " B.";
        }
        if (exception.isResourceExhausted()) {
            return "Execution stopped after exceeding a configured runtime sandbox limit.";
        }
        return "Execution cancelled by a runtime sandbox policy.";
    }

    private static String watchdogFailureSummary() {
        return "Execution stopped after exceeding the configured runtime watchdog limit of " + XLServerConfig.INSTANCE.maxCpuTimeMillis() + " ms.";
    }

    private static String outputLimitFailureSummary(final String message) {
        if (message == null || message.isBlank()) {
            return "";
        }

        final String normalizedMessage = message.toLowerCase(Locale.ROOT);
        if (normalizedMessage.contains("stderr exceeded the configured byte limit")
                || (normalizedMessage.contains("outputlimitexceededexception") && normalizedMessage.contains("stderr"))) {
            return "Execution stopped after exceeding the configured stderr limit of " + XLServerConfig.INSTANCE.maxStderrBytes() + " B.";
        }
        if (normalizedMessage.contains("stdout exceeded the configured byte limit")
                || (normalizedMessage.contains("outputlimitexceededexception") && normalizedMessage.contains("stdout"))) {
            return "Execution stopped after exceeding the configured stdout limit of " + XLServerConfig.INSTANCE.maxStdoutBytes() + " B.";
        }
        return "";
    }

    private static OutputLimitExceededException findOutputLimitExceeded(final Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof OutputLimitExceededException outputLimitExceeded) {
                return outputLimitExceeded;
            }
            current = current.getCause();
        }
        if (throwable instanceof PolyglotException polyglotException && polyglotException.isHostException()) {
            current = polyglotException.asHostException();
            while (current != null) {
                if (current instanceof OutputLimitExceededException outputLimitExceeded) {
                    return outputLimitExceeded;
                }
                current = current.getCause();
            }
        }
        return null;
    }

    private static String buildBootstrapSource() {
        final StringBuilder builder = new StringBuilder();
        builder.append("import builtins\n");
        builder.append("import io\n");
        builder.append("import json\n");
        builder.append("import sys\n\n");
        builder.append("_XL_SANDBOX_MESSAGE = 'XL Logic sandbox blocks host OS, filesystem, process, and network access.'\n");
        builder.append("_XL_BLOCKED_MODULE_ROOTS = frozenset((\n");
        builder.append("    'ctypes',\n");
        builder.append("    'fcntl',\n");
        builder.append("    'glob',\n");
        builder.append("    'java',\n");
        builder.append("    'mmap',\n");
        builder.append("    'multiprocessing',\n");
        builder.append("    'nt',\n");
        builder.append("    'os',\n");
        builder.append("    'pathlib',\n");
        builder.append("    'posix',\n");
        builder.append("    'polyglot',\n");
        builder.append("    'pwd',\n");
        builder.append("    'resource',\n");
        builder.append("    'shutil',\n");
        builder.append("    'signal',\n");
        builder.append("    'socket',\n");
        builder.append("    'subprocess',\n");
        builder.append("    'tempfile',\n");
        builder.append("))\n");
        builder.append("_XL_ORIGINAL_IMPORT = builtins.__import__\n\n");
        builder.append("def _xl_blocked_access_message(detail):\n");
        builder.append("    return _XL_SANDBOX_MESSAGE + ' ' + str(detail)\n\n");
        builder.append("def _xl_blocked_import(name, globals=None, locals=None, fromlist=(), level=0):\n");
        builder.append("    root = str(name).split('.', 1)[0]\n");
        builder.append("    if root in _XL_BLOCKED_MODULE_ROOTS:\n");
        builder.append("        raise ImportError(_xl_blocked_access_message(\"Module '" + "\" + root + \"' is unavailable.\"))\n");
        builder.append("    return _XL_ORIGINAL_IMPORT(name, globals, locals, fromlist, level)\n\n");
        builder.append("class _XLBlockedModuleFinder:\n");
        builder.append("    def find_spec(self, fullname, path=None, target=None):\n");
        builder.append("        root = str(fullname).split('.', 1)[0]\n");
        builder.append("        if root in _XL_BLOCKED_MODULE_ROOTS:\n");
        builder.append("            raise ImportError(_xl_blocked_access_message(\"Module '" + "\" + root + \"' is unavailable.\"))\n");
        builder.append("        return None\n\n");
        builder.append("def _xl_blocked_open(*args, **kwargs):\n");
        builder.append("    raise PermissionError(_xl_blocked_access_message('File access is unavailable.'))\n\n");
        builder.append("builtins.__import__ = _xl_blocked_import\n");
        builder.append("builtins.open = _xl_blocked_open\n");
        builder.append("io.open = _xl_blocked_open\n");
        builder.append("for _xl_blocked_module_name in tuple(_XL_BLOCKED_MODULE_ROOTS):\n");
        builder.append("    sys.modules.pop(_xl_blocked_module_name, None)\n");
        builder.append("sys.meta_path.insert(0, _XLBlockedModuleFinder())\n\n");
        builder.append("_bootstrap = {'computer': {}, 'endpoints': []}\n");
        builder.append("computer = {}\n");
        builder.append("endpoints = []\n");
        builder.append("peripherals = {}\n");
        builder.append("endpoint_names = []\n");
        builder.append("device_names = []\n");
        builder.append("devices = None\n");
        builder.append("computer_api = None\n");
        builder.append("world = None\n");
        builder.append("output = None\n");
        builder.append("screen = None\n");
        builder.append("network = None\n");
        builder.append("class LazyBridge:\n");
        builder.append("    def __init__(self, loader):\n");
        builder.append("        self._loader = loader\n");
        builder.append("        self._value = None\n");
        builder.append("\n");
        builder.append("    def _resolve(self):\n");
        builder.append("        if self._value is None:\n");
        builder.append("            self._value = self._loader()\n");
        builder.append("        return self._value\n");
        builder.append("\n");
        builder.append("    def __getattr__(self, name):\n");
        builder.append("        return getattr(self._resolve(), name)\n");
        builder.append("\n");
        builder.append("class ComputerAPI:\n");
        builder.append(PY_CLASS_INIT);
        builder.append(PY_ASSIGN_BRIDGE);
        builder.append("\n");
        builder.append(PY_DEF_AVAILABLE);
        builder.append("        return self._bridge.available()\n");
        builder.append("\n");
        builder.append("    def name(self):\n");
        builder.append("        return self._bridge.computerName()\n");
        builder.append("\n");
        builder.append("    def position(self):\n");
        builder.append("        return self._bridge.computerPosition()\n");
        builder.append("\n");
        builder.append("    def endpoint_count(self):\n");
        builder.append("        return self._bridge.endpointCount()\n");
        builder.append("\n");
        builder.append("    def network_summary(self):\n");
        builder.append("        return self._bridge.networkSummary()\n");
        builder.append("\n");
        builder.append("    def list_devices(self):\n");
        builder.append("        return list(devices.keys())\n");
        builder.append("\n");
        builder.append("    def get_device(self, name):\n");
        builder.append("        return get_device(name)\n");
        builder.append("\n");
        builder.append("class WorldAPI:\n");
        builder.append("    def __init__(self, bridge_loader):\n");
        builder.append("        self._bridge = LazyBridge(bridge_loader) if callable(bridge_loader) else bridge_loader\n");
        builder.append("\n");
        builder.append(PY_DEF_AVAILABLE);
        builder.append("        return self._bridge.available()\n");
        builder.append("\n");
        builder.append("    def dimension(self):\n");
        builder.append("        return self._bridge.dimensionId()\n");
        builder.append("\n");
        builder.append("    def game_time(self):\n");
        builder.append("        return self._bridge.gameTime()\n");
        builder.append("\n");
        builder.append("    def day_time(self):\n");
        builder.append("        return self._bridge.dayTime()\n");
        builder.append("\n");
        builder.append("    def is_day(self):\n");
        builder.append("        return self._bridge.isDay()\n");
        builder.append("\n");
        builder.append("    def is_night(self):\n");
        builder.append("        return self._bridge.isNight()\n");
        builder.append("\n");
        builder.append("    def is_raining(self):\n");
        builder.append("        return self._bridge.isRaining()\n");
        builder.append("\n");
        builder.append("    def rain_level(self):\n");
        builder.append("        return self._bridge.rainLevel()\n");
        builder.append("\n");
        builder.append("    def is_thundering(self):\n");
        builder.append("        return self._bridge.isThundering()\n");
        builder.append("\n");
        builder.append("    def moon_phase(self):\n");
        builder.append("        return self._bridge.moonPhase()\n");
        builder.append("\n");
        builder.append("    def real_time(self):\n");
        builder.append("        return self._bridge.realTime()\n");
        builder.append("\n");
        builder.append("class OutputAPI:\n");
        builder.append(PY_CLASS_INIT);
        builder.append(PY_ASSIGN_BRIDGE);
        builder.append("\n");
        builder.append("    def line(self, text, tone='info', channel='info'):\n");
        builder.append("        return self._bridge.emitOutput(tone, channel, 'line', '', str(text), '')\n");
        builder.append("\n");
        builder.append("    def kv(self, title, fields, tone='info', channel='data', text=''):\n");
        builder.append("        payload = self._normalize_fields(fields)\n");
        builder.append("        return self._bridge.emitOutput(tone, channel, 'key_value', str(title), str(text), json.dumps(payload))\n");
        builder.append("\n");
        builder.append("    def table(self, title, columns, rows, tone='info', channel='data', text=''):\n");
        builder.append("        payload = {\n");
        builder.append("            'columns': [str(column) for column in columns],\n");
        builder.append("            'rows': self._normalize_rows(columns, rows)\n");
        builder.append(PY_CLOSE_DICT);
        builder.append("        return self._bridge.emitOutput(tone, channel, 'table', str(title), str(text), json.dumps(payload))\n");
        builder.append("\n");
        builder.append("    def plan_card(self, title, fields, tone='info', text=''):\n");
        builder.append("        payload = self._normalize_fields(fields)\n");
        builder.append("        return self._bridge.emitOutput(tone, 'plan', 'plan_card', str(title), str(text), json.dumps(payload))\n");
        builder.append("\n");
        builder.append("    def _normalize_fields(self, fields):\n");
        builder.append("        iterator = fields.items() if hasattr(fields, 'items') else fields\n");
        builder.append("        normalized = []\n");
        builder.append("        for entry in iterator:\n");
        builder.append("            if hasattr(entry, 'items'):\n");
        builder.append("                for key, value in entry.items():\n");
        builder.append("                    normalized.append({'key': str(key), 'value': str(value)})\n");
        builder.append("            elif isinstance(entry, (list, tuple)) and len(entry) >= 2:\n");
        builder.append("                normalized.append({'key': str(entry[0]), 'value': str(entry[1])})\n");
        builder.append("        return normalized\n");
        builder.append("\n");
        builder.append("    def _normalize_rows(self, columns, rows):\n");
        builder.append("        normalized = []\n");
        builder.append("        column_names = [str(column) for column in columns]\n");
        builder.append("        for row in rows:\n");
        builder.append("            if hasattr(row, 'items'):\n");
        builder.append("                normalized.append([str(row.get(column, '')) for column in column_names])\n");
        builder.append("            else:\n");
        builder.append("                normalized.append([str(value) for value in row])\n");
        builder.append("        return normalized\n");
        builder.append("\n");
        builder.append("class ScreenAPI:\n");
        builder.append("    def __init__(self, output_api):\n");
        builder.append("        self._output = output_api\n");
        builder.append("\n");
        builder.append("    def print(self, text, tone='info'):\n");
        builder.append("        return self._output.line(text, tone=tone)\n");
        builder.append("\n");
        builder.append("    def show(self, title, fields, tone='info', text=''):\n");
        builder.append("        return self._output.kv(title, fields, tone=tone, text=text)\n");
        builder.append("\n");
        builder.append("    def table(self, title, columns, rows, tone='info', text=''):\n");
        builder.append("        return self._output.table(title, columns, rows, tone=tone, text=text)\n");
        builder.append("\n");
        builder.append("    def plan(self, title, fields, tone='info', text=''):\n");
        builder.append("        return self._output.plan_card(title, fields, tone=tone, text=text)\n");
        builder.append("\n");
        builder.append("    def line(self, text, tone='info', channel='info'):\n");
        builder.append("        return self._output.line(text, tone=tone, channel=channel)\n");
        builder.append("\n");
        builder.append("    def kv(self, title, fields, tone='info', text=''):\n");
        builder.append("        return self._output.kv(title, fields, tone=tone, text=text)\n");
        builder.append("\n");
        builder.append("    def plan_card(self, title, fields, tone='info', text=''):\n");
        builder.append("        return self._output.plan_card(title, fields, tone=tone, text=text)\n");
        builder.append("\n");
        builder.append("class DeviceAPI:\n");
        builder.append("    _SIDES = ('down', 'up', 'north', 'south', 'west', 'east')\n");
        builder.append("\n");
        builder.append("    def __init__(self, bridge_loader, metadata=None):\n");
        builder.append("        self._bridge = LazyBridge(bridge_loader) if callable(bridge_loader) else bridge_loader\n");
        builder.append("        self._metadata = metadata or {}\n");
        builder.append("\n");
        builder.append("    def _meta(self, key, resolver=None, fallback=None):\n");
        builder.append("        if key in self._metadata:\n");
        builder.append("            return self._metadata[key]\n");
        builder.append("        if resolver is not None:\n");
        builder.append("            return resolver()\n");
        builder.append("        return fallback\n");
        builder.append("\n");
        appendPythonStringDictionary(builder, "_DIRECT_METHODS", DEVICE_DIRECT_METHOD_ALIASES, "    ");
        appendPythonStringDictionary(builder, "_LAZY_METHOD_EXTENSIONS", DEVICE_LAZY_METHOD_EXTENSION_NAMES, "    ");
        builder.append("    _INSTALLED_EXTENSIONS = set()\n");
        builder.append("\n");
        builder.append("    def api_name(self):\n");
        builder.append("        return self._meta('api_name', lambda: self._bridge.apiName())\n");
        builder.append("\n");
        builder.append("    def name(self):\n");
        builder.append("        return self._meta('name', lambda: self._bridge.name())\n");
        builder.append("\n");
        builder.append("    def type(self):\n");
        builder.append("        return self._meta('type', lambda: self._bridge.type())\n");
        builder.append("\n");
        builder.append("    def position(self):\n");
        builder.append("        return self._meta('position', lambda: self._bridge.position())\n");
        builder.append("\n");
        builder.append("    def distance(self):\n");
        builder.append("        return self._meta('distance', lambda: self._bridge.distance())\n");
        builder.append("\n");
        builder.append("    def network_scope(self):\n");
        builder.append("        return self._meta('scope', lambda: self._bridge.networkScope())\n");
        builder.append("\n");
        builder.append("    def is_remote(self):\n");
        builder.append("        return self._meta('remote', lambda: self._bridge.isRemote())\n");
        builder.append("\n");
        builder.append("    def bridge_name(self):\n");
        builder.append("        return self._meta('bridge_name', lambda: self._bridge.bridgeEndpointName())\n");
        builder.append("\n");
        builder.append("    def bridge_group(self):\n");
        builder.append("        return self._meta('bridge_group', lambda: self._bridge.bridgeUplinkGroup())\n");
        builder.append("\n");
        builder.append("    def remote_policy(self):\n");
        builder.append("        return self._meta('remote_policy', lambda: self._bridge.remotePolicy())\n");
        builder.append("\n");
        builder.append("    def remote_writable(self):\n");
        builder.append("        return self._meta('remote_writable', lambda: self._bridge.remoteWritable())\n");
        builder.append("\n");
        builder.append(PY_DEF_AVAILABLE);
        builder.append("        return self._bridge.online()\n");
        builder.append("\n");
        builder.append("    def summary(self):\n");
        builder.append("        return self._bridge.summary()\n");
        builder.append("\n");
        builder.append("    def describe(self):\n");
        builder.append("        return self._bridge.describeState()\n");
        builder.append("\n");
        builder.append("    def _install_extension(self, name):\n");
        builder.append("        if name in type(self)._INSTALLED_EXTENSIONS:\n");
        builder.append("            return\n");
        builder.append("        source = globals()['__xl'].bootstrapExtensionSource(name)\n");
        builder.append("        if not source:\n");
        builder.append("            return\n");
        builder.append("        namespace = {}\n");
        builder.append("        exec(source, globals(), namespace)\n");
        builder.append("        for member_name, member_value in namespace.items():\n");
        builder.append("            if callable(member_value):\n");
        builder.append("                setattr(type(self), member_name, member_value)\n");
        builder.append("        type(self)._INSTALLED_EXTENSIONS.add(name)\n");
        builder.append("\n");
        builder.append("    def __getattr__(self, name):\n");
        builder.append("        direct = type(self)._DIRECT_METHODS.get(name)\n");
        builder.append("        if direct is not None:\n");
        builder.append("            return getattr(self._bridge, direct)\n");
        builder.append("        extension = type(self)._LAZY_METHOD_EXTENSIONS.get(name)\n");
        builder.append("        if extension is not None:\n");
        builder.append("            self._install_extension(extension)\n");
        builder.append("            return object.__getattribute__(self, name)\n");
        builder.append("        raise AttributeError(name)\n");
        builder.append("\n");
        builder.append("class DeviceRegistry:\n");
        builder.append("    def __init__(self, metadata_by_name):\n");
        builder.append("        self._metadata_by_name = dict(metadata_by_name)\n");
        builder.append("        self._devices = {}\n");
        builder.append("\n");
        builder.append("    def _build(self, name):\n");
        builder.append("        metadata = self._metadata_by_name.get(name)\n");
        builder.append("        if metadata is None:\n");
        builder.append("            return None\n");
        builder.append("        return DeviceAPI(lambda api_name=name: globals()['__xl'].getDevice(api_name), metadata)\n");
        builder.append("\n");
        builder.append("    def get(self, name, default=None):\n");
        builder.append("        if name not in self._devices:\n");
        builder.append("            device = self._build(name)\n");
        builder.append("            if device is None:\n");
        builder.append("                return default\n");
        builder.append("            self._devices[name] = device\n");
        builder.append("        return self._devices.get(name, default)\n");
        builder.append("\n");
        builder.append("    def keys(self):\n");
        builder.append("        return self._metadata_by_name.keys()\n");
        builder.append("\n");
        builder.append("    def values(self):\n");
        builder.append("        return [self.get(name) for name in self._metadata_by_name.keys()]\n");
        builder.append("\n");
        builder.append("    def items(self):\n");
        builder.append("        return [(name, self.get(name)) for name in self._metadata_by_name.keys()]\n");
        builder.append("\n");
        builder.append("    def __getitem__(self, name):\n");
        builder.append("        device = self.get(name)\n");
        builder.append("        if device is None:\n");
        builder.append("            raise KeyError(name)\n");
        builder.append("        return device\n");
        builder.append("\n");
        builder.append("    def __contains__(self, name):\n");
        builder.append("        return name in self._metadata_by_name\n");
        builder.append("\n");
        builder.append("    def __iter__(self):\n");
        builder.append("        return iter(self._metadata_by_name)\n");
        builder.append("\n");
        builder.append("    def __len__(self):\n");
        builder.append("        return len(self._metadata_by_name)\n");
        builder.append("\n");
        builder.append("class NetworkAPI:\n");
        builder.append("    def __init__(self, registry):\n");
        builder.append("        self._registry = registry\n");
        builder.append("\n");
        builder.append("    def names(self, device_type=None):\n");
        builder.append("        if device_type is None:\n");
        builder.append("            return list(self._registry.keys())\n");
        builder.append("        return [name for name, metadata in self._registry._metadata_by_name.items() if metadata.get('type') == device_type]\n");
        builder.append("\n");
        builder.append("    def all(self, device_type=None):\n");
        builder.append("        return [self._registry.get(name) for name in self.names(device_type)]\n");
        builder.append("\n");
        builder.append("    def get(self, name, default=None):\n");
        builder.append("        return self._registry.get(name, default)\n");
        builder.append("\n");
        builder.append("    def require(self, name):\n");
        builder.append("        found = self.get(name)\n");
        builder.append("        if found is None:\n");
        builder.append("            raise KeyError('Unknown device: ' + str(name))\n");
        builder.append("        return found\n");
        builder.append("\n");
        builder.append("    def find(self, device_type):\n");
        builder.append("        names = self.names(device_type)\n");
        builder.append("        return self._registry.get(names[0]) if names else None\n");
        builder.append("\n");
        builder.append("    def types(self):\n");
        builder.append("        seen = set()\n");
        builder.append("        ordered = []\n");
        builder.append("        for metadata in self._registry._metadata_by_name.values():\n");
        builder.append("            device_type = metadata.get('type')\n");
        builder.append("            if device_type and device_type not in seen:\n");
        builder.append("                seen.add(device_type)\n");
        builder.append("                ordered.append(device_type)\n");
        builder.append("        return ordered\n");
        builder.append("\n");
        builder.append("def ").append(BOOTSTRAP_BIND_METADATA_NAME).append("():\n");
        builder.append("    global _bootstrap, computer, endpoints, peripherals, endpoint_names, device_names\n");
        builder.append("    exec(__xl.bootstrapBindSource(), globals())\n");
        builder.append("\n");
        builder.append("def ").append(BOOTSTRAP_BIND_RUNTIME_OBJECTS_NAME).append("():\n");
        builder.append("    global devices, computer_api, world, output, screen, network\n");
        builder.append("    computer_api = ComputerAPI(__xl)\n");
        builder.append("    world = WorldAPI(lambda: __xl.world())\n");
        builder.append("    output = OutputAPI(__xl)\n");
        builder.append("    devices = DeviceRegistry(peripherals)\n");
        builder.append("    screen = ScreenAPI(output)\n");
        builder.append("    network = NetworkAPI(devices)\n");
        builder.append("\n");
        builder.append("def ").append(BOOTSTRAP_BIND_NAME).append("():\n");
        builder.append("    ").append(BOOTSTRAP_BIND_METADATA_NAME).append("()\n");
        builder.append("    ").append(BOOTSTRAP_BIND_RUNTIME_OBJECTS_NAME).append("()\n");
        builder.append("\n");
        builder.append(BOOTSTRAP_BIND_NAME).append("()\n");
        builder.append("def list_endpoints():\n");
        builder.append("    return list(peripherals.keys())\n");
        builder.append("\n");
        builder.append("def get_endpoint(name):\n");
        builder.append("    return peripherals.get(name)\n");
        builder.append("\n");
        builder.append("def list_devices():\n");
        builder.append("    return list(devices.keys())\n");
        builder.append("\n");
        builder.append("def get_device(name):\n");
        builder.append("    return devices.get(name)\n");
        builder.append("\n");
        builder.append("def show_table(title, columns, rows, tone='info', text=''):\n");
        builder.append("    return output.table(title, columns, rows, tone=tone, text=text)\n");
        builder.append("\n");
        builder.append("def show_kv(title, fields, tone='info', text=''):\n");
        builder.append("    return output.kv(title, fields, tone=tone, text=text)\n");
        builder.append("\n");
        builder.append("def show_plan_card(title, fields, tone='info', text=''):\n");
        builder.append("    return output.plan_card(title, fields, tone=tone, text=text)\n");
        builder.append("\n");
        builder.append("def say(text, tone='info'):\n");
        builder.append("    return screen.print(text, tone=tone)\n");
        builder.append("\n");
        builder.append("def show(title, fields, tone='info', text=''):\n");
        builder.append("    return screen.show(title, fields, tone=tone, text=text)\n");
        builder.append("\n");
        builder.append("def device(name):\n");
        builder.append("    return network.get(name)\n");
        builder.append("\n");
        builder.append("def require_device(name):\n");
        builder.append("    return network.require(name)\n");
        builder.append("\n");
        builder.append("def find_device(device_type):\n");
        builder.append("    return network.find(device_type)\n");
        builder.append("\n");
        builder.append("def list_device_names(device_type=None):\n");
        builder.append("    return network.names(device_type)\n");
        builder.append("\n");
        builder.append("def devices_by_type(device_type):\n");
        builder.append("    return network.all(device_type)\n");
        builder.append("\n");
        builder.append("def pause(ticks=1):\n");
        builder.append("    yield from sleep_ticks(ticks)\n");
        builder.append("\n");
        builder.append("def repeat(step, every_ticks=1):\n");
        builder.append("    yield from run_loop(step, every_ticks)\n");
        return builder.toString();
    }

    public static final class WarmupBootstrapBridge {
        @HostAccess.Export
        public String bootstrapBindSource() {
            return WARMUP_BOOTSTRAP_BIND_SOURCE;
        }

        @HostAccess.Export
        public boolean emitOutput(final String tone, final String channel, final String kind, final String title, final String text, final String payloadJson) {
            return WARMUP_HOST_API.emitOutput(tone, channel, kind, title, text, payloadJson);
        }
    }

    private static void appendPythonStringDictionary(final StringBuilder builder,
                                                     final String variableName,
                                                     final Map<String, String> entries,
                                                     final String indentation) {
        builder.append(indentation).append(variableName).append(" = {\n");
        for (final Map.Entry<String, String> entry : entries.entrySet()) {
            builder.append(indentation)
                    .append("    ")
                    .append(pythonString(entry.getKey()))
                    .append(": ")
                    .append(pythonString(entry.getValue()))
                    .append(",\n");
        }
        builder.append(indentation).append("}\n");
        builder.append("\n");
    }

    private static Map<String, String> deviceDirectMethodAliases() {
        final Map<String, String> aliases = new LinkedHashMap<>();
        aliases.put("rename", "rename");
        aliases.put("get_mode", "getMode");
        aliases.put("set_mode", "setMode");
        aliases.put("read", "read");
        aliases.put("write", "write");
        aliases.put("channel", "channel");
        aliases.put("set_channel", "setChannel");
        aliases.put("light_level", "lightLevel");
        aliases.put("is_raining", "isRaining");
        aliases.put("rain_level", "rainLevel");
        aliases.put("game_time", "gameTime");
        aliases.put("day_time", "dayTime");
        aliases.put("real_time", "realTime");
        aliases.put("grid_width", "gridWidth");
        aliases.put("grid_height", "gridHeight");
        aliases.put("set_grid_size", "setGridSize");
        aliases.put("grid_slot_count", "gridSlotCount");
        aliases.put("set_grid_slot", "setGridSlot");
        aliases.put("clear_grid", "clearGrid");
        aliases.put("route_count", "routeCount");
        aliases.put("set_route", "setRoute");
        aliases.put("clear_route", "clearRoute");
        aliases.put("clear_routes", "clearRoutes");
        aliases.put("set_window_origin", "setWindowOrigin");
        aliases.put("linked_cpu", "linkedCpu");
        aliases.put("set_linked_cpu", "setLinkedCpu");
        aliases.put("material_input_device", "materialInputDevice");
        aliases.put("set_material_input_device", "setMaterialInputDevice");
        aliases.put("material_input_side", "materialInputSide");
        aliases.put("set_material_input_side", "setMaterialInputSide");
        aliases.put("material_output_device", "materialOutputDevice");
        aliases.put("set_material_output_device", "setMaterialOutputDevice");
        aliases.put("material_output_side", "materialOutputSide");
        aliases.put("set_material_output_side", "setMaterialOutputSide");
        aliases.put("apply_recipe_window", "applyRecipeWindow");
        aliases.put("craft_linked", "craftLinked");
        aliases.put("plan_step_count", "planStepCount");
        aliases.put("append_plan_step", "appendPlanStep");
        aliases.put("set_plan_step", "setPlanStep");
        aliases.put("remove_plan_step", "removePlanStep");
        aliases.put("clear_plan", "clearCraftPlan");
        aliases.put("rebuild_plan", "rebuildPlanFromGrid");
        aliases.put("craft_plan", "craftPlan");
        aliases.put("queued_plan_cycles", "queuedPlanCycles");
        aliases.put("set_queued_plan_cycles", "setQueuedPlanCycles");
        aliases.put("queue_plan", "queuePlanCycles");
        aliases.put("can_resume_queued_plan", "canResumeQueuedPlan");
        aliases.put("resume_queued_plan", "resumeQueuedPlan");
        aliases.put("can_abort_queued_plan", "canAbortQueuedPlan");
        aliases.put("abort_queued_plan", "abortQueuedPlan");
        aliases.put("clear_queued_plan", "clearQueuedPlan");
        aliases.put("queued_plan_reservation_mode", "queuedPlanReservationMode");
        aliases.put("set_queued_plan_reservation_mode", "setQueuedPlanReservationMode");
        aliases.put("craft_queued_plan", "craftQueuedPlan");
        aliases.put("is_busy", "isBusy");
        aliases.put("set_busy", "setBusy");
        aliases.put("queued_jobs", "queuedJobs");
        aliases.put("set_queued_jobs", "setQueuedJobs");
        aliases.put("uplink_group", "uplinkGroup");
        aliases.put("set_uplink_group", "setUplinkGroup");
        aliases.put("relay_enabled", "relayEnabled");
        aliases.put("set_relay_enabled", "setRelayEnabled");
        aliases.put("forwarded_messages", "forwardedMessages");
        aliases.put("inbox_count", "bridgeInboxCount");
        aliases.put("item_input_enabled", "itemInputEnabled");
        aliases.put("set_item_input_enabled", "setItemInputEnabled");
        aliases.put("item_output_enabled", "itemOutputEnabled");
        aliases.put("set_item_output_enabled", "setItemOutputEnabled");
        aliases.put("fluid_input_enabled", "fluidInputEnabled");
        aliases.put("set_fluid_input_enabled", "setFluidInputEnabled");
        aliases.put("fluid_output_enabled", "fluidOutputEnabled");
        aliases.put("set_fluid_output_enabled", "setFluidOutputEnabled");
        aliases.put("inventory_size", "itemSlotCount");
        aliases.put("count_item", "countItem");
        aliases.put("transfer_item", "transferItem");
        aliases.put("transfer_item_to", "transferItemTo");
        aliases.put("tank_count", "fluidTankCount");
        aliases.put("transfer_fluid", "transferFluid");
        aliases.put("transfer_fluid_to", "transferFluidTo");
        aliases.put("recipe_slot_count", "recipeSlotCount");
        aliases.put("set_recipe_slot", "setRecipeSlot");
        aliases.put("clear_recipe", "clearRecipe");
        aliases.put("craft", "craft");
        aliases.put("craft_queued", "craftQueued");
        aliases.put("clear_output", "clearScreenOutput");
        return aliases;
    }

    private static Map<String, String> deviceLazyMethodExtensions() {
        final Map<String, String> extensions = new LinkedHashMap<>();
        extensions.put("levels", DEVICE_EXTENSION_REDSTONE);
        extensions.put("channels", DEVICE_EXTENSION_REDSTONE);
        extensions.put("stack", DEVICE_EXTENSION_INVENTORY);
        extensions.put(DEVICE_METHOD_INVENTORY, DEVICE_EXTENSION_INVENTORY);
        extensions.put("tank", DEVICE_EXTENSION_INVENTORY);
        extensions.put("tanks", DEVICE_EXTENSION_INVENTORY);
        extensions.put("recipe_slot", DEVICE_EXTENSION_RECIPE);
        extensions.put(DEVICE_METHOD_RECIPE, DEVICE_EXTENSION_RECIPE);
        extensions.put("preview", DEVICE_EXTENSION_RECIPE);
        extensions.put("grid_slot", DEVICE_EXTENSION_CRAFTING);
        extensions.put("grid", DEVICE_EXTENSION_CRAFTING);
        extensions.put("route", DEVICE_EXTENSION_CRAFTING);
        extensions.put("routes", DEVICE_EXTENSION_CRAFTING);
        extensions.put("window_origin", DEVICE_EXTENSION_CRAFTING);
        extensions.put("linked_preview", DEVICE_EXTENSION_CRAFTING);
        extensions.put("plan_step", DEVICE_EXTENSION_CRAFTING);
        extensions.put("plan", DEVICE_EXTENSION_CRAFTING);
        extensions.put("queued_plan", DEVICE_EXTENSION_CRAFTING);
        extensions.put("remote_computers", DEVICE_EXTENSION_BRIDGE);
        extensions.put("peek_messages", DEVICE_EXTENSION_BRIDGE);
        extensions.put("poll_messages", DEVICE_EXTENSION_BRIDGE);
        extensions.put("send_message", DEVICE_EXTENSION_BRIDGE);
        extensions.put("send_command", DEVICE_EXTENSION_BRIDGE);
        extensions.put("request_status", DEVICE_EXTENSION_BRIDGE);
        extensions.put("ping", DEVICE_EXTENSION_BRIDGE);
        extensions.put("request_devices", DEVICE_EXTENSION_BRIDGE);
        extensions.put("request_runtime", DEVICE_EXTENSION_BRIDGE);
        extensions.put("peek_responses", DEVICE_EXTENSION_BRIDGE);
        extensions.put("poll_responses", DEVICE_EXTENSION_BRIDGE);
        extensions.put("side_aliases", DEVICE_EXTENSION_STATE);
        extensions.put(DEVICE_EXTENSION_STATE, DEVICE_EXTENSION_STATE);
        extensions.put("print", DEVICE_EXTENSION_SCREEN);
        extensions.put("line", DEVICE_EXTENSION_SCREEN);
        extensions.put("show", DEVICE_EXTENSION_SCREEN);
        extensions.put("kv", DEVICE_EXTENSION_SCREEN);
        extensions.put("table", DEVICE_EXTENSION_SCREEN);
        extensions.put("plan_card", DEVICE_EXTENSION_SCREEN);
        return extensions;
    }

    private static Map<String, String> deviceLazyExtensionSources() {
        final Map<String, String> extensions = new LinkedHashMap<>();
        extensions.put(DEVICE_EXTENSION_REDSTONE, deviceRedstoneExtensionSource());
        extensions.put(DEVICE_EXTENSION_INVENTORY, deviceInventoryExtensionSource());
        extensions.put(DEVICE_EXTENSION_RECIPE, deviceRecipeExtensionSource());
        extensions.put(DEVICE_EXTENSION_CRAFTING, deviceCraftingExtensionSource());
        extensions.put(DEVICE_EXTENSION_BRIDGE, deviceBridgeExtensionSource());
        extensions.put(DEVICE_EXTENSION_STATE, deviceStateExtensionSource());
        extensions.put(DEVICE_EXTENSION_SCREEN, deviceScreenExtensionSource());
        return extensions;
    }

    private static String deviceRedstoneExtensionSource() {
        return """
                def levels(self):
                    return {side: self.read(side) for side in self._SIDES}

                def channels(self):
                    return {side: self.channel(side) for side in self._SIDES}
                """.stripIndent();
    }

    private static String deviceInventoryExtensionSource() {
        return """
                def stack(self, side, slot):
                    return {
                        'slot': slot,
                        'item': self._bridge.itemId(side, slot),
                        'count': self._bridge.itemCount(side, slot)
                    }

                def inventory(self, side):
                    return [self.stack(side, slot) for slot in range(self.inventory_size(side)) if self._bridge.itemCount(side, slot) > 0]

                def tank(self, side, tank):
                    return {
                        'tank': tank,
                        'fluid': self._bridge.fluidId(side, tank),
                        'amount': self._bridge.fluidAmount(side, tank)
                    }

                def tanks(self, side):
                    return [self.tank(side, tank) for tank in range(self.tank_count(side)) if self._bridge.fluidAmount(side, tank) > 0]
                """.stripIndent();
    }

    private static String deviceRecipeExtensionSource() {
        return """
                def recipe_slot(self, slot):
                    return {
                        'slot': slot,
                        'item': self._bridge.recipeItemId(slot),
                        'count': self._bridge.recipeItemCount(slot)
                    }

                def recipe(self):
                    return [self.recipe_slot(slot) for slot in range(self.recipe_slot_count())]

                def preview(self):
                    return {
                        'recipe_id': self._bridge.previewRecipeId(),
                        'result_item': self._bridge.previewResultItem(),
                        'result_count': self._bridge.previewResultCount()
                    }
                """.stripIndent();
    }

    private static String deviceCraftingExtensionSource() {
        return """
                def grid_slot(self, slot):
                    return {
                        'slot': slot,
                        'item': self._bridge.gridItemId(slot),
                        'count': self._bridge.gridItemCount(slot)
                    }

                def grid(self):
                    return [self.grid_slot(slot) for slot in range(self.grid_slot_count())]

                def route(self, index):
                    return {
                        'index': index,
                        'name': self._bridge.routeName(index),
                        'device': self._bridge.routeDevice(index),
                        'side': self._bridge.routeSide(index)
                    }

                def routes(self):
                    return [self.route(index) for index in range(self.route_count())]

                def window_origin(self):
                    return {
                        'x': self._bridge.windowX(),
                        'y': self._bridge.windowY()
                    }

                def linked_preview(self):
                    return {
                        'recipe_id': self._bridge.linkedPreviewRecipeId(),
                        'result_item': self._bridge.linkedPreviewResultItem(),
                        'result_count': self._bridge.linkedPreviewResultCount()
                    }

                def plan_step(self, index):
                    return {
                        'index': index,
                        'x': self._bridge.planStepWindowX(index),
                        'y': self._bridge.planStepWindowY(index),
                        'crafts': self._bridge.planStepCrafts(index),
                        'input_route': self._bridge.planStepInputRoute(index),
                        'output_route': self._bridge.planStepOutputRoute(index),
                        'preview': {
                            'recipe_id': self._bridge.planStepPreviewRecipeId(index),
                            'result_item': self._bridge.planStepPreviewResultItem(index),
                            'result_count': self._bridge.planStepPreviewResultCount(index)
                        }
                    }

                def plan(self):
                    return [self.plan_step(index) for index in range(self.plan_step_count())]

                def queued_plan(self):
                    return json.loads(self._bridge.queuedPlanStateJson())
                """.stripIndent();
    }

    private static String deviceBridgeExtensionSource() {
        return """
                def remote_computer_count(self):
                    return self._bridge.remoteComputerCount()

                def remote_computers(self):
                    return json.loads(self._bridge.remoteComputersJson())

                def peek_messages(self, limit=16):
                    return json.loads(self._bridge.peekBridgeMessagesJson(limit))

                def poll_messages(self, limit=16):
                    return json.loads(self._bridge.pollBridgeMessagesJson(limit))

                def send_message(self, message, channel='default', target=''):
                    return self._bridge.sendBridgeMessage(target, channel, str(message))

                def send_command(self, command, payload='', target=''):
                    return json.loads(self._bridge.sendBridgeCommandJson(target, str(command), str(payload)))

                def request_status(self, target=''):
                    return json.loads(self._bridge.requestRemoteStatusJson(target))

                def ping(self, payload='ping', target=''):
                    return json.loads(self._bridge.requestRemotePingJson(target, str(payload)))

                def request_devices(self, target=''):
                    return json.loads(self._bridge.requestRemoteDevicesJson(target))

                def request_runtime(self, target='', output_limit=8, plan_limit=6):
                    return json.loads(self._bridge.requestRemoteRuntimeJson(target, output_limit, plan_limit))

                def peek_responses(self, limit=16):
                    return json.loads(self._bridge.peekBridgeResponsesJson(limit))

                def poll_responses(self, limit=16):
                    return json.loads(self._bridge.pollBridgeResponsesJson(limit))
                """.stripIndent();
    }

    private static String deviceScreenExtensionSource() {
        return """
                def _screen_require(self):
                    if self.type() != 'screen':
                        raise TypeError('Endpoint ' + str(self.name()) + ' is not a screen')

                def _screen_normalize_fields(self, fields):
                    iterator = fields.items() if hasattr(fields, 'items') else fields
                    normalized = []
                    for entry in iterator:
                        if hasattr(entry, 'items'):
                            for key, value in entry.items():
                                normalized.append({'key': str(key), 'value': str(value)})
                        elif isinstance(entry, (list, tuple)) and len(entry) >= 2:
                            normalized.append({'key': str(entry[0]), 'value': str(entry[1])})
                    return normalized

                def _screen_normalize_rows(self, columns, rows):
                    normalized = []
                    column_names = [str(column) for column in columns]
                    for row in rows:
                        if hasattr(row, 'items'):
                            normalized.append([str(row.get(column, '')) for column in column_names])
                        else:
                            normalized.append([str(value) for value in row])
                    return normalized

                def _screen_emit(self, tone, channel, kind, title, text, payload_json=''):
                    self._screen_require()
                    return self._bridge.emitScreenOutput(str(tone), str(channel), str(kind), str(title), str(text), str(payload_json))

                def print(self, text, tone='info'):
                    return self.line(text, tone=tone)

                def line(self, text, tone='info', channel='info'):
                    return self._screen_emit(tone, channel, 'line', '', str(text), '')

                def kv(self, title, fields, tone='info', channel='data', text=''):
                    payload = self._screen_normalize_fields(fields)
                    return self._screen_emit(tone, channel, 'key_value', str(title), str(text), json.dumps(payload))

                def show(self, title, fields, tone='info', text=''):
                    return self.kv(title, fields, tone=tone, text=text)

                def table(self, title, columns, rows, tone='info', channel='data', text=''):
                    payload = {
                        'columns': [str(column) for column in columns],
                        'rows': self._screen_normalize_rows(columns, rows)
                    }
                    return self._screen_emit(tone, channel, 'table', str(title), str(text), json.dumps(payload))

                def plan_card(self, title, fields, tone='info', text=''):
                    payload = self._screen_normalize_fields(fields)
                    return self._screen_emit(tone, 'plan', 'plan_card', str(title), str(text), json.dumps(payload))
                """.stripIndent();
    }

    private static String deviceStateExtensionSource() {
        return """
                def side_aliases(self):
                    return json.loads(self._bridge.sideAliasesJson())

                def state(self):
                    data = {
                        'api_name': self.api_name(),
                        'name': self.name(),
                        'type': self.type(),
                        'position': self.position(),
                        'distance': self.distance(),
                        'scope': self.network_scope(),
                        'remote': self.is_remote(),
                        'bridge_name': self.bridge_name(),
                        'bridge_group': self.bridge_group(),
                        'remote_policy': self.remote_policy(),
                        'remote_writable': self.remote_writable(),
                        'online': self.available(),
                        'summary': self.summary()
                    }
                    if not data['online']:
                        return data
                    device_type = self.type()
                    if device_type == 'redstone_io':
                        data['mode'] = self.get_mode()
                        data['side_aliases'] = self.side_aliases()
                        data['levels'] = self.levels()
                        data['channels'] = self.channels()
                    elif device_type == 'light_sensor':
                        data['light_level'] = self.light_level()
                    elif device_type == 'rain_sensor':
                        data['raining'] = self.is_raining()
                        data['rain_level'] = self.rain_level()
                    elif device_type == 'clock':
                        data['game_time'] = self.game_time()
                        data['day_time'] = self.day_time()
                        data['real_time'] = self.real_time()
                    elif device_type == 'material_io':
                        data['mode'] = self.get_mode()
                        data['side_aliases'] = self.side_aliases()
                        data['item_input_enabled'] = self.item_input_enabled()
                        data['item_output_enabled'] = self.item_output_enabled()
                        data['fluid_input_enabled'] = self.fluid_input_enabled()
                        data['fluid_output_enabled'] = self.fluid_output_enabled()
                        data['item_slot_counts'] = {side: self.inventory_size(side) for side in self._SIDES}
                        data['fluid_tank_counts'] = {side: self.tank_count(side) for side in self._SIDES}
                    elif device_type == 'crafting_io':
                        data['grid_width'] = self.grid_width()
                        data['grid_height'] = self.grid_height()
                        data['window'] = self.window_origin()
                        data['linked_cpu'] = self.linked_cpu()
                        data['material_input_device'] = self.material_input_device()
                        data['material_input_side'] = self.material_input_side()
                        data['material_output_device'] = self.material_output_device()
                        data['material_output_side'] = self.material_output_side()
                        data['routes'] = self.routes()
                        data['linked_preview'] = self.linked_preview()
                        data['plan'] = self.plan()
                        data['queued_plan'] = self.queued_plan()
                        data['recipe'] = [slot for slot in self.grid() if slot['count'] > 0]
                    elif device_type == 'crafting_cpu':
                        data['busy'] = self.is_busy()
                        data['side_aliases'] = self.side_aliases()
                        data['queued_jobs'] = self.queued_jobs()
                        data['preview'] = self.preview()
                        data['recipe'] = [slot for slot in self.recipe() if slot['count'] > 0]
                    elif device_type == 'xlapi_block':
                        data['uplink_group'] = self.uplink_group()
                        data['relay_enabled'] = self.relay_enabled()
                        data['forwarded_messages'] = self.forwarded_messages()
                        data['remote_computers'] = self.remote_computers()
                        data['inbox_count'] = self.inbox_count()
                    return data
                """.stripIndent();
    }

    private static String pythonString(final String value) {
        final String escaped = value
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
        return '\'' + escaped + '\'';
    }

    private static final class DelegatingOutputStream extends OutputStream {
        private final AtomicReference<OutputStream> delegate = new AtomicReference<>(OutputStream.nullOutputStream());

        private void bind(final OutputStream delegate) {
            this.delegate.set(delegate == null ? OutputStream.nullOutputStream() : delegate);
        }

        @Override
        public void write(final int value) throws java.io.IOException {
            this.delegate.get().write(value);
        }

        @Override
        public void write(final byte[] buffer, final int offset, final int length) throws java.io.IOException {
            this.delegate.get().write(buffer, offset, length);
        }

        @Override
        public void flush() throws java.io.IOException {
            this.delegate.get().flush();
        }
    }

    public static final class PreparedBootstrapBridge {
        private volatile PythonHostApi hostApi = WARMUP_HOST_API;

        private void bind(final PythonHostApi hostApi) {
            this.hostApi = hostApi == null ? WARMUP_HOST_API : hostApi;
        }

        @HostAccess.Export
        public boolean available() {
            return this.hostApi.available();
        }

        @HostAccess.Export
        public String computerName() {
            return this.hostApi.computerName();
        }

        @HostAccess.Export
        public String computerPosition() {
            return this.hostApi.computerPosition();
        }

        @HostAccess.Export
        public int endpointCount() {
            return this.hostApi.endpointCount();
        }

        @HostAccess.Export
        public String networkSummary() {
            return this.hostApi.networkSummary();
        }

        @HostAccess.Export
        public Object getDevice(final String apiName) {
            return this.hostApi.exportedDeviceBridge(apiName);
        }

        @HostAccess.Export
        public Object world() {
            return this.hostApi.exportedWorldBridge();
        }

        @HostAccess.Export
        public String bootstrapExtensionSource(final String name) {
            return GraalPythonRuntime.bootstrapExtensionSource(name);
        }

        @HostAccess.Export
        public String bootstrapBindSource() {
            return this.hostApi.bootstrapBindSource();
        }

        @HostAccess.Export
        public boolean emitOutput(final String tone, final String channel, final String kind, final String title, final String text, final String payloadJson) {
            return this.hostApi.emitOutput(tone, channel, kind, title, text, payloadJson);
        }
    }

    private static final class PreparedPersistentContext {
        private final Context polyglotContext;
        private final PreparedBootstrapBridge bootstrapBridge;
        private final DelegatingOutputStream stdout;
        private final DelegatingOutputStream stderr;
        private final Value bindMetadataFunction;
        private final Value bindRuntimeObjectsFunction;

        private PreparedPersistentContext(final Context polyglotContext,
                                         final PreparedBootstrapBridge bootstrapBridge,
                                         final DelegatingOutputStream stdout,
                                         final DelegatingOutputStream stderr,
                                         final Value bindMetadataFunction,
                                         final Value bindRuntimeObjectsFunction) {
            this.polyglotContext = polyglotContext;
            this.bootstrapBridge = bootstrapBridge;
            this.stdout = stdout;
            this.stderr = stderr;
            this.bindMetadataFunction = bindMetadataFunction;
            this.bindRuntimeObjectsFunction = bindRuntimeObjectsFunction;
        }

        private Context context() {
            return this.polyglotContext;
        }

        private void bindSession(final PythonHostApi hostApi, final PythonExecutionTranscript transcript) {
            this.bootstrapBridge.bind(hostApi);
            this.stdout.bind(transcript.stdoutStream());
            this.stderr.bind(transcript.stderrStream());
        }

        private void rebindMetadata() {
            this.bindMetadataFunction.execute();
        }

        private void rebindRuntimeObjects() {
            this.bindRuntimeObjectsFunction.execute();
        }
    }
}
