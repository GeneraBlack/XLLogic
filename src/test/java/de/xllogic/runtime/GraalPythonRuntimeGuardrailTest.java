package de.xllogic.runtime;

import de.xllogic.common.config.XLServerConfig;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class GraalPythonRuntimeGuardrailTest {
    private static final String SANDBOX_MESSAGE = "XL Logic sandbox blocks host OS, filesystem, process, and network access.";

    private final GraalPythonRuntime runtime = new GraalPythonRuntime();

    @Test
    @Timeout(10)
    void cancelsBusyLoopWhenCpuTimeLimitIsExceeded() {
        assumeTrue(this.runtime.available(), "GraalPy runtime unavailable in test environment");

        try (TestConfigOverride ignored = new TestConfigOverride()
            .setIntOverride("testMaxCpuTimeMillisOverride", 25)
            .setIntOverride("testCpuTimeCheckIntervalMillisOverride", 5)
            .setLongOverride("testMaxStdoutBytesOverride", 0L)
            .setLongOverride("testMaxStderrBytesOverride", 0L)) {
            final PythonExecutionResult result = this.runtime.execute(
                    "while True:\n    pass\n",
                    PythonExecutionContext.empty(),
                    PythonExecutionLimits.unbounded());

            assertFalse(result.success());
            assertEquals("Execution stopped after exceeding the configured runtime watchdog limit of 25 ms.", result.summary());
        }
    }

    @Test
    @Timeout(10)
    void stopsExecutionWhenStdoutLimitIsExceeded() {
        assumeTrue(this.runtime.available(), "GraalPy runtime unavailable in test environment");

        try (TestConfigOverride ignored = new TestConfigOverride()
            .setIntOverride("testMaxCpuTimeMillisOverride", 0)
            .setLongOverride("testMaxStdoutBytesOverride", 128L)
            .setLongOverride("testMaxStderrBytesOverride", 0L)) {
            final PythonExecutionResult result = this.runtime.execute(
                    "print('x' * 4096)\n",
                    PythonExecutionContext.empty(),
                    PythonExecutionLimits.unbounded());

            assertFalse(result.success());
            assertEquals("Execution stopped after exceeding the configured stdout limit of 128 B.", result.summary());
        }
    }

    @Test
    @Timeout(10)
    void stopsExecutionWhenStderrLimitIsExceeded() {
        assumeTrue(this.runtime.available(), "GraalPy runtime unavailable in test environment");

        try (TestConfigOverride ignored = new TestConfigOverride()
            .setIntOverride("testMaxCpuTimeMillisOverride", 0)
            .setLongOverride("testMaxStdoutBytesOverride", 0L)
            .setLongOverride("testMaxStderrBytesOverride", 128L)) {
            final PythonExecutionResult result = this.runtime.execute(
                    "import sys\nsys.stderr.write('x' * 4096)\nsys.stderr.flush()\n",
                    PythonExecutionContext.empty(),
                    PythonExecutionLimits.unbounded());

            assertFalse(result.success());
            assertEquals("Execution stopped after exceeding the configured stderr limit of 128 B.", result.summary());
        }
    }

    @Test
    @Timeout(10)
    void blocksOperatingSystemModuleImports() {
        assumeTrue(this.runtime.available(), "GraalPy runtime unavailable in test environment");

        try (TestConfigOverride ignored = new TestConfigOverride()
            .setIntOverride("testMaxCpuTimeMillisOverride", 0)
            .setLongOverride("testMaxStdoutBytesOverride", 0L)
            .setLongOverride("testMaxStderrBytesOverride", 0L)) {
            final PythonExecutionResult result = this.runtime.execute(
                    "import os\n",
                    PythonExecutionContext.empty(),
                    PythonExecutionLimits.unbounded());

            assertFalse(result.success());
            assertTrue(result.summary().contains(SANDBOX_MESSAGE), result.summary());
            assertTrue(result.summary().contains("os"), result.summary());
        }
    }

    @Test
    @Timeout(10)
    void blocksJavaInteropImports() {
        assumeTrue(this.runtime.available(), "GraalPy runtime unavailable in test environment");

        try (TestConfigOverride ignored = new TestConfigOverride()
            .setIntOverride("testMaxCpuTimeMillisOverride", 0)
            .setLongOverride("testMaxStdoutBytesOverride", 0L)
            .setLongOverride("testMaxStderrBytesOverride", 0L)) {
            final PythonExecutionResult result = this.runtime.execute(
                    "import java\n",
                    PythonExecutionContext.empty(),
                    PythonExecutionLimits.unbounded());

            assertFalse(result.success());
            assertTrue(result.summary().contains(SANDBOX_MESSAGE), result.summary());
            assertTrue(result.summary().contains("java"), result.summary());
        }
    }

    @Test
    @Timeout(10)
    void blocksProcessModuleImports() {
        assumeTrue(this.runtime.available(), "GraalPy runtime unavailable in test environment");

        try (TestConfigOverride ignored = new TestConfigOverride()
            .setIntOverride("testMaxCpuTimeMillisOverride", 0)
            .setLongOverride("testMaxStdoutBytesOverride", 0L)
            .setLongOverride("testMaxStderrBytesOverride", 0L)) {
            final PythonExecutionResult result = this.runtime.execute(
                    "import subprocess\n",
                    PythonExecutionContext.empty(),
                    PythonExecutionLimits.unbounded());

            assertFalse(result.success());
            assertTrue(result.summary().contains(SANDBOX_MESSAGE), result.summary());
            assertTrue(result.summary().contains("subprocess"), result.summary());
        }
    }

    @Test
    @Timeout(10)
    void blocksDirectFileAccess() {
        assumeTrue(this.runtime.available(), "GraalPy runtime unavailable in test environment");

        try (TestConfigOverride ignored = new TestConfigOverride()
            .setIntOverride("testMaxCpuTimeMillisOverride", 0)
            .setLongOverride("testMaxStdoutBytesOverride", 0L)
            .setLongOverride("testMaxStderrBytesOverride", 0L)) {
            final PythonExecutionResult result = this.runtime.execute(
                    "open('server.properties', 'r')\n",
                    PythonExecutionContext.empty(),
                    PythonExecutionLimits.unbounded());

            assertFalse(result.success());
            assertTrue(result.summary().contains(SANDBOX_MESSAGE), result.summary());
            assertTrue(result.summary().contains("File access is unavailable."), result.summary());
        }
    }

    @Test
    @Timeout(10)
    void bootstrapsDeviceProxyWithOverloadedHostMethods() {
        assumeTrue(this.runtime.available(), "GraalPy runtime unavailable in test environment");

        final List<PythonPeripheralBinding> bindings = List.of(
                new PythonPeripheralBinding("crafting_io", "crafting_io", "crafting_io", BlockPos.ZERO, "0,0,0", 0, "local", "", 0,
                    "", "", "", "", "", ""));
        final PythonHostApi hostApi = PythonHostApi.unavailable("test", BlockPos.ZERO, bindings);
        final PythonExecutionContext context = new PythonExecutionContext("test", BlockPos.ZERO, bindings, hostApi);

        try (TestConfigOverride ignored = new TestConfigOverride()
                .setIntOverride("testMaxCpuTimeMillisOverride", 0)
                .setLongOverride("testMaxStdoutBytesOverride", 0L)
                .setLongOverride("testMaxStderrBytesOverride", 0L)) {
            final PythonExecutionResult result = this.runtime.execute(
                    "device = get_device('crafting_io')\n"
                            + "show_table('Devices', ['API', 'Type', 'Scope', 'Policy'], [[device.api_name(), device.type(), device.network_scope(), device.remote_policy()]])\n",
                    context,
                    PythonExecutionLimits.unbounded());

            assertTrue(result.success(), result.summary());
            assertTrue(result.summary().startsWith("Execution completed"));
            assertTrue(result.outputEntries().stream().anyMatch(entry -> "table".equals(entry.kind())));
        }
    }

    @Test
    @Timeout(10)
    void recordsHostCallDiagnosticsWhenThresholdIsForcedToZero() {
        assumeTrue(this.runtime.available(), "GraalPy runtime unavailable in test environment");

        final List<PythonPeripheralBinding> bindings = List.of(
                new PythonPeripheralBinding("crafting_io", "crafting_io", "crafting_io", BlockPos.ZERO, "0,0,0", 0, "local", "", 0,
                    "", "", "", "", "", ""));
        final PythonHostApi hostApi = PythonHostApi.unavailable("test", BlockPos.ZERO, bindings);
        final PythonExecutionContext context = new PythonExecutionContext("test", BlockPos.ZERO, bindings, hostApi);
        final String previousThreshold = System.getProperty("xllogic.hostCallDiagnosticsThresholdMillis");
        final String previousTopEntries = System.getProperty("xllogic.hostCallDiagnosticsTopEntries");

        System.setProperty("xllogic.hostCallDiagnosticsThresholdMillis", "0");
        System.setProperty("xllogic.hostCallDiagnosticsTopEntries", "4");
        try (TestConfigOverride ignored = new TestConfigOverride()
                .setIntOverride("testMaxCpuTimeMillisOverride", 0)
                .setLongOverride("testMaxStdoutBytesOverride", 0L)
                .setLongOverride("testMaxStderrBytesOverride", 0L)) {
            final PythonExecutionResult result = this.runtime.execute(
                    "device = get_device('crafting_io')\n"
                        + "show_table('Probe', ['Summary', 'Online'], [[device.summary(), device.available()]])\n",
                    context,
                    PythonExecutionLimits.unbounded());

            assertTrue(result.success(), result.summary());
            assertTrue(result.outputEntries().stream().anyMatch(entry -> "Python host bridge".equals(entry.title())));
            assertTrue(result.outputEntries().stream().anyMatch(entry -> "Slowest host calls".equals(entry.title())));
        } finally {
            restoreSystemProperty("xllogic.hostCallDiagnosticsThresholdMillis", previousThreshold);
            restoreSystemProperty("xllogic.hostCallDiagnosticsTopEntries", previousTopEntries);
        }
    }

    @Test
    @Timeout(10)
    void bootstrapsLazyWorldAndDeviceRegistryAccess() {
        assumeTrue(this.runtime.available(), "GraalPy runtime unavailable in test environment");

        final List<PythonPeripheralBinding> bindings = List.of(
                new PythonPeripheralBinding("crafting_io", "crafting_io", "crafting_io", BlockPos.ZERO, "0,0,0", 0, "local", "", 0,
                    "", "", "", "", "", ""));
        final PythonHostApi hostApi = PythonHostApi.unavailable("test", BlockPos.ZERO, bindings);
        final PythonExecutionContext context = new PythonExecutionContext("test", BlockPos.ZERO, bindings, hostApi);

        try (TestConfigOverride ignored = new TestConfigOverride()
                .setIntOverride("testMaxCpuTimeMillisOverride", 0)
                .setLongOverride("testMaxStdoutBytesOverride", 0L)
                .setLongOverride("testMaxStderrBytesOverride", 0L)) {
            final PythonExecutionResult result = this.runtime.execute(
                    "device = devices['crafting_io']\n"
                            + "show_kv('Lazy bootstrap', {\n"
                            + "    'device': device.api_name(),\n"
                            + "    'listed': 'crafting_io' in devices,\n"
                            + "    'count': len(list(devices.keys())),\n"
                        + "    'world_available': world.available(),\n"
                        + "    'state_online': device.state()['online']\n"
                            + "})\n",
                    context,
                    PythonExecutionLimits.unbounded());

            assertTrue(result.success(), result.summary());
            assertTrue(result.summary().startsWith("Execution completed"));
            assertTrue(result.outputEntries().stream().anyMatch(entry -> "Lazy bootstrap".equals(entry.title())));
        }
    }

            @Test
            @Timeout(10)
            void exposesScreenDeviceHelpers() {
            assumeTrue(this.runtime.available(), "GraalPy runtime unavailable in test environment");

            final List<PythonPeripheralBinding> bindings = List.of(
                new PythonPeripheralBinding("main_screen", "Main Screen", "screen", BlockPos.ZERO, "0,0,0", 0, "local", "", 0,
                    "", "", "", "", "", ""));
            final PythonHostApi hostApi = PythonHostApi.unavailable("test", BlockPos.ZERO, bindings);
            final PythonExecutionContext context = new PythonExecutionContext("test", BlockPos.ZERO, bindings, hostApi);

            try (TestConfigOverride ignored = new TestConfigOverride()
                .setIntOverride("testMaxCpuTimeMillisOverride", 0)
                .setLongOverride("testMaxStdoutBytesOverride", 0L)
                .setLongOverride("testMaxStderrBytesOverride", 0L)) {
                final PythonExecutionResult result = this.runtime.execute(
                    "device = get_device('main_screen')\n"
                        + "show_kv('Screen helpers', {\n"
                        + "    'type': device.type(),\n"
                        + "    'line': callable(device.line),\n"
                        + "    'show': callable(device.show),\n"
                        + "    'kv': callable(device.kv),\n"
                        + "    'table': callable(device.table),\n"
                        + "    'plan_card': callable(device.plan_card),\n"
                        + "    'clear_output': callable(device.clear_output)\n"
                        + "})\n",
                    context,
                    PythonExecutionLimits.unbounded());

                assertTrue(result.success(), result.summary());
                assertTrue(result.outputEntries().stream().anyMatch(entry -> "Screen helpers".equals(entry.title())));
            }
            }

                @Test
                @Timeout(10)
                void exposesMaterialIoEndpointTransferHelpers() {
                assumeTrue(this.runtime.available(), "GraalPy runtime unavailable in test environment");

                final List<PythonPeripheralBinding> bindings = List.of(
                    new PythonPeripheralBinding("source_io", "Source I/O", "material_io", BlockPos.ZERO, "0,0,0", 0, "local", "", 0,
                        "", "", "", "", "", ""),
                    new PythonPeripheralBinding("sink_io", "Sink I/O", "material_io", BlockPos.ZERO, "1,0,0", 1, "local", "", 0,
                        "", "", "", "", "", ""));
                final PythonHostApi hostApi = PythonHostApi.unavailable("test", BlockPos.ZERO, bindings);
                final PythonExecutionContext context = new PythonExecutionContext("test", BlockPos.ZERO, bindings, hostApi);

                try (TestConfigOverride ignored = new TestConfigOverride()
                    .setIntOverride("testMaxCpuTimeMillisOverride", 0)
                    .setLongOverride("testMaxStdoutBytesOverride", 0L)
                    .setLongOverride("testMaxStderrBytesOverride", 0L)) {
                    final PythonExecutionResult result = this.runtime.execute(
                        "device = get_device('source_io')\n"
                            + "show_kv('Material I/O endpoint transfer', {\n"
                            + "    'type': device.type(),\n"
                            + "    'transfer_item_to': callable(device.transfer_item_to),\n"
                            + "    'transfer_fluid_to': callable(device.transfer_fluid_to)\n"
                            + "})\n",
                        context,
                        PythonExecutionLimits.unbounded());

                    assertTrue(result.success(), result.summary());
                    assertTrue(result.outputEntries().stream().anyMatch(entry -> "Material I/O endpoint transfer".equals(entry.title())));
                }
                }

            @Test
            @Timeout(10)
            void bootstrapsBeginnerHelperObjectsAndFunctions() {
                assumeTrue(this.runtime.available(), "GraalPy runtime unavailable in test environment");

                final List<PythonPeripheralBinding> bindings = List.of(
                        new PythonPeripheralBinding("timekeeper", "Clock", "clock", BlockPos.ZERO, "0,0,0", 0, "local", "", 0,
                                "", "", "", "", "", ""),
                        new PythonPeripheralBinding("weather_sensor", "Weather", "rain_sensor", BlockPos.ZERO, "1,0,0", 1, "local", "", 0,
                                "", "", "", "", "", ""));
                final PythonHostApi hostApi = PythonHostApi.unavailable("test", BlockPos.ZERO, bindings);
                final PythonExecutionContext context = new PythonExecutionContext("test", BlockPos.ZERO, bindings, hostApi);

                try (TestConfigOverride ignored = new TestConfigOverride()
                        .setIntOverride("testMaxCpuTimeMillisOverride", 0)
                        .setLongOverride("testMaxStdoutBytesOverride", 0L)
                        .setLongOverride("testMaxStderrBytesOverride", 0L)) {
                    final PythonExecutionResult result = this.runtime.execute(
                            "clock = find_device('clock')\n"
                                + "named = require_device('timekeeper')\n"
                                + "screen.show('Beginner helpers', {\n"
                                + "    'by_type': clock.type(),\n"
                                + "    'by_name': named.api_name(),\n"
                                + "    'visible_names': len(list_device_names()),\n"
                                + "    'clock_devices': len(devices_by_type('clock')),\n"
                                + "    'types': ','.join(network.types())\n"
                                + "})\n"
                                + "say('hello beginner')\n",
                            context,
                            PythonExecutionLimits.unbounded());

                    assertTrue(result.success(), result.summary());
                    assertTrue(result.outputEntries().stream().anyMatch(entry -> "Beginner helpers".equals(entry.title())));
                    assertTrue(result.outputEntries().stream().anyMatch(entry -> "line".equals(entry.kind())));
                }
            }

    @Test
    @Timeout(10)
    void advancesPersistentSessionAcrossTicks() {
        assumeTrue(this.runtime.available(), "GraalPy runtime unavailable in test environment");

        try (TestConfigOverride ignored = new TestConfigOverride()
                .setIntOverride("testMaxCpuTimeMillisOverride", 25)
                .setLongOverride("testMaxStdoutBytesOverride", 0L)
                .setLongOverride("testMaxStderrBytesOverride", 0L);
             PythonExecutionSession session = this.runtime.startSession(
                     "counter = 0\n"
                             + "while counter < 3:\n"
                             + "    print(counter)\n"
                             + "    counter += 1\n"
                             + "    yield from sleep_ticks(1)\n"
                             + "print('done')\n",
                     PythonExecutionContext.empty(),
                     PythonExecutionLimits.unbounded())) {
            assertFalse(session.finished());
            assertTrue(session.snapshot().running());

                awaitSessionCondition(session, () -> session.snapshot().outputLines().stream().anyMatch(line -> line.contains("0")), 5_000L,
                    "Timed out waiting for the first persistent session slice.");
            assertFalse(session.finished());
            assertTrue(session.snapshot().running());
            assertTrue(session.snapshot().outputLines().stream().anyMatch(line -> line.contains("0")));

                awaitSessionCondition(session, () -> session.snapshot().outputLines().stream().anyMatch(line -> line.contains("1")), 5_000L,
                    "Timed out waiting for the second persistent session slice.");
            assertFalse(session.finished());
            assertTrue(session.snapshot().outputLines().stream().anyMatch(line -> line.contains("1")));

                awaitSessionCondition(session, () -> session.snapshot().outputLines().stream().anyMatch(line -> line.contains("2")), 5_000L,
                    "Timed out waiting for the third persistent session slice.");
            assertFalse(session.finished());
            assertTrue(session.snapshot().outputLines().stream().anyMatch(line -> line.contains("2")));

                awaitSessionCondition(session, session::finished, 5_000L,
                    "Timed out waiting for the persistent session to finish.");
            assertTrue(session.finished());
            assertFalse(session.snapshot().running());
            assertTrue(session.snapshot().success());
            assertEquals("Execution completed.", session.snapshot().summary());
            assertTrue(session.snapshot().outputLines().stream().anyMatch(line -> line.contains("done")));
        }
    }

    @Test
    @Timeout(10)
    void cancelsBusyLoopWhenPersistentSliceWatchdogIsExceeded() {
        assumeTrue(this.runtime.available(), "GraalPy runtime unavailable in test environment");

        try (TestConfigOverride ignored = new TestConfigOverride()
                .setIntOverride("testMaxCpuTimeMillisOverride", 25)
                .setLongOverride("testMaxStdoutBytesOverride", 0L)
                .setLongOverride("testMaxStderrBytesOverride", 0L);
             PythonExecutionSession session = this.runtime.startSession(
                     "while True:\n"
                             + "    pass\n",
                     PythonExecutionContext.empty(),
                     PythonExecutionLimits.unbounded())) {
            awaitSessionCondition(session, session::finished, 5_000L,
                    "Timed out waiting for the persistent slice watchdog to stop the session.");

            assertTrue(session.finished());
            assertFalse(session.snapshot().success());
            assertEquals("Execution stopped after exceeding the configured runtime watchdog limit of 25 ms.", session.snapshot().summary());
        }
    }

    private static void awaitSessionCondition(final PythonExecutionSession session,
                                              final BooleanSupplier condition,
                                              final long timeoutMillis,
                                              final String failureMessage) {
        awaitCondition(condition, timeoutMillis, failureMessage, session::advanceTick);
    }

    private static void awaitCondition(final BooleanSupplier condition, final long timeoutMillis, final String failureMessage) {
        awaitCondition(condition, timeoutMillis, failureMessage, () -> {
        });
    }

    private static void awaitCondition(final BooleanSupplier condition,
                                       final long timeoutMillis,
                                       final String failureMessage,
                                       final Runnable progressAction) {
        final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError(failureMessage);
            }
            progressAction.run();
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10L));
        }
    }

    private static void restoreSystemProperty(final String propertyName, final String propertyValue) {
        if (propertyValue == null) {
            System.clearProperty(propertyName);
            return;
        }
        System.setProperty(propertyName, propertyValue);
    }

    private static final class TestConfigOverride implements AutoCloseable {
        private final List<Runnable> restoreActions = new ArrayList<>();

        private TestConfigOverride setIntOverride(final String fieldName, final int value) {
            this.overrideField(fieldName, value);
            return this;
        }

        private TestConfigOverride setLongOverride(final String fieldName, final long value) {
            this.overrideField(fieldName, value);
            return this;
        }

        private <T> void overrideField(final String fieldName, final T value) {
            final Field field = configField(fieldName);
            try {
                final Object originalValue = field.get(null);
                this.restoreActions.add(() -> setField(field, originalValue));
                setField(field, value);
            } catch (final IllegalAccessException exception) {
                throw new AssertionError("Failed to apply runtime test override '" + fieldName + "'.", exception);
            }
        }

        private static Field configField(final String fieldName) {
            try {
                final Field field = XLServerConfig.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (final ReflectiveOperationException exception) {
                throw new AssertionError("Failed to access config field '" + fieldName + "' for runtime test overrides.", exception);
            }
        }

        private static void setField(final Field field, final Object value) {
            try {
                field.set(null, value);
            } catch (final IllegalAccessException exception) {
                throw new AssertionError("Failed to restore runtime test override '" + field.getName() + "'.", exception);
            }
        }

        @Override
        public void close() {
            Collections.reverse(this.restoreActions);
            for (final Runnable restoreAction : this.restoreActions) {
                restoreAction.run();
            }
        }
    }
}