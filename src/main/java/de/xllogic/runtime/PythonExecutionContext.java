package de.xllogic.runtime;

import de.xllogic.common.network.XLNetworkEndpointSnapshot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public record PythonExecutionContext(String computerName, BlockPos computerBlockPos, List<PythonPeripheralBinding> peripherals, PythonHostApi hostApi) {
    private static final Set<String> RESERVED_NAMES = Set.of(
            "computer",
            "endpoints",
            "peripherals",
            "list_endpoints",
            "get_endpoint",
            "endpoint_names",
            "devices",
            "device_names",
            "list_devices",
            "get_device",
            "output",
            "show_table",
            "show_kv",
            "show_plan_card",
            "sleep_ticks",
            "run_loop",
            "json",
            "world",
            "computer_api",
            "screen",
            "network",
            "pause",
            "repeat",
            "say",
            "show",
            "device",
            "require_device",
            "find_device",
            "list_device_names",
            "devices_by_type"
    );
    private static final PythonExecutionContext EMPTY = new PythonExecutionContext("standalone", BlockPos.ZERO, List.of(), PythonHostApi.unavailable("standalone", BlockPos.ZERO, List.of()));

    public PythonExecutionContext {
        computerName = sanitizeComputerName(computerName);
        computerBlockPos = computerBlockPos == null ? BlockPos.ZERO : computerBlockPos;
        peripherals = peripherals == null ? List.of() : List.copyOf(peripherals);
        hostApi = hostApi == null ? PythonHostApi.unavailable(computerName, computerBlockPos, peripherals) : hostApi;
    }

    public static PythonExecutionContext empty() {
        return EMPTY;
    }

    public static PythonExecutionContext fromSnapshots(final String computerName, final BlockPos computerPos, final List<XLNetworkEndpointSnapshot> snapshots) {
        return createContext(computerName, computerPos, snapshots, null);
    }

    public static PythonExecutionContext forServerExecution(final ServerLevel level, final String computerName, final BlockPos computerPos, final List<XLNetworkEndpointSnapshot> snapshots) {
        return createContext(computerName, computerPos, snapshots, level);
    }

    public String computerPosition() {
        return this.computerBlockPos.toShortString();
    }

    public int endpointCount() {
        return this.peripherals.size();
    }

    public PythonPeripheralBinding peripheral(final String apiName) {
        if (apiName == null || apiName.isBlank()) {
            return null;
        }
        for (final PythonPeripheralBinding binding : this.peripherals) {
            if (apiName.equals(binding.apiName())) {
                return binding;
            }
        }
        return null;
    }

    private static PythonExecutionContext createContext(final String computerName, final BlockPos computerPos, final List<XLNetworkEndpointSnapshot> snapshots, final ServerLevel level) {
        final Map<String, Integer> countsByBaseName = new HashMap<>();
        final List<PythonPeripheralBinding> bindings = new ArrayList<>();
        final BlockPos safeComputerPos = computerPos == null ? BlockPos.ZERO : computerPos;
        final String sanitizedComputerName = sanitizeComputerName(computerName);
        final List<XLNetworkEndpointSnapshot> safeSnapshots = snapshots == null ? List.of() : snapshots;

        for (final XLNetworkEndpointSnapshot snapshot : safeSnapshots) {
            String baseName = bindingBaseName(snapshot);
            if (baseName.isBlank()) {
                baseName = sanitize(snapshot.endpointType());
            }
            if (baseName.isBlank()) {
                baseName = "endpoint";
            }
            if (RESERVED_NAMES.contains(baseName)) {
                baseName = baseName + "_device";
            }

            final int count = countsByBaseName.merge(baseName, 1, Integer::sum);
            final String apiName = count == 1 ? baseName : baseName + "_" + count;
            bindings.add(new PythonPeripheralBinding(
                    apiName,
                    snapshot.endpointName(),
                    snapshot.endpointType(),
                    snapshot.pos(),
                    snapshot.pos().toShortString(),
                    snapshot.distance(),
                    snapshot.networkScope(),
                    snapshot.bridgeEndpointName(),
                    snapshot.bridgeUplinkGroup(),
                    snapshot.downAlias(),
                    snapshot.upAlias(),
                    snapshot.northAlias(),
                    snapshot.southAlias(),
                    snapshot.westAlias(),
                    snapshot.eastAlias()
            ));
        }

        final PythonHostApi hostApi = level == null
                ? PythonHostApi.unavailable(sanitizedComputerName, safeComputerPos, bindings)
                : PythonHostApi.server(level, sanitizedComputerName, safeComputerPos, bindings);
        return new PythonExecutionContext(sanitizedComputerName, safeComputerPos, bindings, hostApi);
    }

    private static String sanitizeComputerName(final String rawName) {
        final String sanitized = sanitize(rawName);
        return sanitized.isBlank() ? "computer" : sanitized;
    }

    private static String bindingBaseName(final XLNetworkEndpointSnapshot snapshot) {
        String baseName = sanitize(snapshot.endpointName());
        if (!snapshot.isBridged()) {
            return baseName;
        }

        String bridgeName = sanitize(snapshot.bridgeEndpointName());
        if (bridgeName.isBlank()) {
            bridgeName = "bridge_" + Math.max(0, snapshot.bridgeUplinkGroup());
        }
        if (baseName.isBlank()) {
            return "remote_" + bridgeName;
        }
        return "remote_" + bridgeName + "_" + baseName;
    }

    private static String sanitize(final String rawName) {
        return rawName == null ? "" : rawName.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_", "")
                .replaceAll("_$", "");
    }
}
