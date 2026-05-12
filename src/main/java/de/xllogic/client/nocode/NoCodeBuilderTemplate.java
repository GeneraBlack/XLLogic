package de.xllogic.client.nocode;

import de.xllogic.runtime.PythonExecutionContext;
import de.xllogic.runtime.PythonPeripheralBinding;
import java.util.List;

public enum NoCodeBuilderTemplate {
    HELLO_WORLD("Hello", "Print one short greeting to the screen."),
    DEVICE_TOUR("Discovery", "List visible devices and show world information once."),
    LIVE_CLOCK("Clock", "Show one visible clock every second."),
    RAIN_WATCH("Rain", "Show one visible rain sensor every second."),
    STOCK_CHECK("Items", "Count one default item on a material I/O side every second."),
    TANK_WATCH("Fluids", "Show all non-empty tanks on one material I/O side every second."),
    SOURCE_TO_SINK("Source->Sink", "Move a default item from source_io to sink_io every second when those devices are visible."),
    SOURCE_TO_SINK_FLUIDS("Source Fluids", "Move a default fluid from source_io to sink_io every second when those devices are visible."),
    RAIN_LAMP("Rain Lamp", "Turn a redstone output on while rain is detected and off otherwise."),
    STOCK_ALERT("Stock Alert", "Show a message once one watched item reaches a chosen stock threshold."),
    NIGHT_LAMP("Night Lamp", "Turn a redstone output on only during the night and switch it off by day."),
    HEAVY_RAIN_ALERT("Heavy Rain", "Show a warning only while strong rain is currently active."),
    THUNDER_ALERT("Thunder", "Show a warning only while the world is thundering."),
    EVENING_LIGHTS("Evening", "Switch a redstone output on during a built-in evening window around sunset.");

    private final String buttonLabel;
    private final String description;

    NoCodeBuilderTemplate(final String buttonLabel, final String description) {
        this.buttonLabel = buttonLabel;
        this.description = description;
    }

    public String buttonLabel() {
        return this.buttonLabel;
    }

    public String description() {
        return this.description;
    }

    public NoCodeProgram create(final PythonExecutionContext executionContext) {
        final PythonExecutionContext safeContext = executionContext == null ? PythonExecutionContext.empty() : executionContext;
        return switch (this) {
            case HELLO_WORLD -> helloWorldProgram();
            case DEVICE_TOUR -> discoveryProgram();
            case LIVE_CLOCK -> liveClockProgram(safeContext);
            case RAIN_WATCH -> rainWatchProgram(safeContext);
            case STOCK_CHECK -> stockCheckProgram(safeContext);
            case TANK_WATCH -> tankWatchProgram(safeContext);
            case SOURCE_TO_SINK -> sourceToSinkProgram(safeContext);
            case SOURCE_TO_SINK_FLUIDS -> sourceToSinkFluidsProgram(safeContext);
            case RAIN_LAMP -> rainLampProgram(safeContext);
            case STOCK_ALERT -> stockAlertProgram(safeContext);
            case NIGHT_LAMP -> nightLampProgram(safeContext);
            case HEAVY_RAIN_ALERT -> heavyRainAlertProgram();
            case THUNDER_ALERT -> thunderAlertProgram();
            case EVENING_LIGHTS -> eveningLightsProgram(safeContext);
        };
    }

    private static NoCodeProgram helloWorldProgram() {
        final NoCodeProgram program = new NoCodeProgram();
        final NoCodeBlock block = new NoCodeBlock(NoCodeBlockKind.PRINT_TEXT);
        block.setText("Hello from XL Logic");
        program.blocks().add(block);
        return program;
    }

    private static NoCodeProgram discoveryProgram() {
        final NoCodeProgram program = new NoCodeProgram();
        program.blocks().add(new NoCodeBlock(NoCodeBlockKind.LIST_DEVICES));
        program.blocks().add(new NoCodeBlock(NoCodeBlockKind.SHOW_WORLD));
        return program;
    }

    private static NoCodeProgram liveClockProgram(final PythonExecutionContext executionContext) {
        final NoCodeProgram program = loopingProgram();
        program.blocks().add(typedDeviceBlock(NoCodeBlockKind.SHOW_CLOCK, executionContext, "clock"));
        return program;
    }

    private static NoCodeProgram rainWatchProgram(final PythonExecutionContext executionContext) {
        final NoCodeProgram program = loopingProgram();
        program.blocks().add(typedDeviceBlock(NoCodeBlockKind.SHOW_RAIN_SENSOR, executionContext, "rain_sensor"));
        return program;
    }

    private static NoCodeProgram stockCheckProgram(final PythonExecutionContext executionContext) {
        final NoCodeProgram program = loopingProgram();
        program.blocks().add(typedDeviceBlock(NoCodeBlockKind.COUNT_MATERIAL_ITEM, executionContext, "material_io"));
        return program;
    }

    private static NoCodeProgram tankWatchProgram(final PythonExecutionContext executionContext) {
        final NoCodeProgram program = loopingProgram();
        program.blocks().add(typedDeviceBlock(NoCodeBlockKind.SHOW_MATERIAL_FLUIDS, executionContext, "material_io"));
        return program;
    }

    private static NoCodeProgram sourceToSinkProgram(final PythonExecutionContext executionContext) {
        final NoCodeProgram program = loopingProgram();
        final NoCodeBlock transfer = new NoCodeBlock(NoCodeBlockKind.MOVE_MATERIAL_ITEM_TO);
        final String sourceApiName = preferredDeviceApiName(executionContext, "material_io", "source_io");
        final String targetApiName = preferredAlternativeDeviceApiName(executionContext, "material_io", "sink_io", sourceApiName);
        transfer.setDeviceApiName(sourceApiName);
        transfer.setTargetDeviceApiName(targetApiName);
        transfer.setSideName(firstSideName(executionContext, sourceApiName));
        transfer.setTargetSideName(firstTargetSideName(executionContext,
                targetApiName,
                transfer.sideName(),
                !sourceApiName.equals(targetApiName)));
        transfer.setLevel(16);
        program.blocks().add(transfer);
        return program;
    }

    private static NoCodeProgram sourceToSinkFluidsProgram(final PythonExecutionContext executionContext) {
        final NoCodeProgram program = loopingProgram();
        final NoCodeBlock transfer = new NoCodeBlock(NoCodeBlockKind.MOVE_MATERIAL_FLUID_TO);
        final String sourceApiName = preferredDeviceApiName(executionContext, "material_io", "source_io");
        final String targetApiName = preferredAlternativeDeviceApiName(executionContext, "material_io", "sink_io", sourceApiName);
        transfer.setDeviceApiName(sourceApiName);
        transfer.setTargetDeviceApiName(targetApiName);
        transfer.setSideName(firstSideName(executionContext, sourceApiName));
        transfer.setTargetSideName(firstTargetSideName(executionContext,
                targetApiName,
                transfer.sideName(),
                !sourceApiName.equals(targetApiName)));
        transfer.setLevel(1_000);
        program.blocks().add(transfer);
        return program;
    }

    private static NoCodeProgram rainLampProgram(final PythonExecutionContext executionContext) {
        final NoCodeProgram program = loopingProgram();
        final NoCodeBlock rainGuard = typedDeviceBlock(NoCodeBlockKind.IF_RAINING_NEXT, executionContext, "rain_sensor");
        final NoCodeBlock lampOn = typedDeviceBlock(NoCodeBlockKind.WRITE_REDSTONE, executionContext, "redstone_io");
        final NoCodeBlock otherwise = new NoCodeBlock(NoCodeBlockKind.ELSE_NEXT);
        final NoCodeBlock lampOff = typedDeviceBlock(NoCodeBlockKind.WRITE_REDSTONE, executionContext, "redstone_io");
        lampOn.setLevel(15);
        lampOff.setLevel(0);
        lampOff.setDeviceApiName(lampOn.deviceApiName());
        lampOff.setSideName(lampOn.sideName());
        program.blocks().add(rainGuard);
        program.blocks().add(lampOn);
        program.blocks().add(otherwise);
        program.blocks().add(lampOff);
        return program;
    }

    private static NoCodeProgram stockAlertProgram(final PythonExecutionContext executionContext) {
        final NoCodeProgram program = loopingProgram();
        final NoCodeBlock stockGuard = typedDeviceBlock(NoCodeBlockKind.IF_ITEM_COUNT_AT_LEAST_NEXT, executionContext, "material_io");
        final NoCodeBlock alertText = new NoCodeBlock(NoCodeBlockKind.PRINT_TEXT);
        final NoCodeBlock otherwise = new NoCodeBlock(NoCodeBlockKind.ELSE_NEXT);
        final NoCodeBlock normalText = new NoCodeBlock(NoCodeBlockKind.PRINT_TEXT);
        stockGuard.setLevel(64);
        alertText.setText("Stock threshold reached. Check the shared storage.");
        normalText.setText("Stock is below the watched threshold.");
        program.blocks().add(stockGuard);
        program.blocks().add(alertText);
        program.blocks().add(otherwise);
        program.blocks().add(normalText);
        return program;
    }

    private static NoCodeProgram nightLampProgram(final PythonExecutionContext executionContext) {
        final NoCodeProgram program = loopingProgram();
        final NoCodeBlock nightGuard = new NoCodeBlock(NoCodeBlockKind.IF_WORLD_NIGHT_NEXT);
        final NoCodeBlock lampOn = typedDeviceBlock(NoCodeBlockKind.WRITE_REDSTONE, executionContext, "redstone_io");
        final NoCodeBlock otherwise = new NoCodeBlock(NoCodeBlockKind.ELSE_NEXT);
        final NoCodeBlock lampOff = typedDeviceBlock(NoCodeBlockKind.WRITE_REDSTONE, executionContext, "redstone_io");
        lampOn.setLevel(15);
        lampOff.setLevel(0);
        lampOff.setDeviceApiName(lampOn.deviceApiName());
        lampOff.setSideName(lampOn.sideName());
        program.blocks().add(nightGuard);
        program.blocks().add(lampOn);
        program.blocks().add(otherwise);
        program.blocks().add(lampOff);
        return program;
    }

    private static NoCodeProgram heavyRainAlertProgram() {
        final NoCodeProgram program = loopingProgram();
        final NoCodeBlock heavyRainGuard = new NoCodeBlock(NoCodeBlockKind.IF_WORLD_RAIN_LEVEL_AT_LEAST_NEXT);
        final NoCodeBlock alertText = new NoCodeBlock(NoCodeBlockKind.PRINT_TEXT);
        final NoCodeBlock otherwise = new NoCodeBlock(NoCodeBlockKind.ELSE_NEXT);
        final NoCodeBlock calmText = new NoCodeBlock(NoCodeBlockKind.PRINT_TEXT);
        heavyRainGuard.setLevel(10);
        alertText.setText("Heavy rain is active. Protect exposed systems.");
        calmText.setText("Rain is below the heavy-rain threshold.");
        program.blocks().add(heavyRainGuard);
        program.blocks().add(alertText);
        program.blocks().add(otherwise);
        program.blocks().add(calmText);
        return program;
    }

    private static NoCodeProgram thunderAlertProgram() {
        final NoCodeProgram program = loopingProgram();
        final NoCodeBlock thunderGuard = new NoCodeBlock(NoCodeBlockKind.IF_WORLD_THUNDERING_NEXT);
        final NoCodeBlock alertText = new NoCodeBlock(NoCodeBlockKind.PRINT_TEXT);
        final NoCodeBlock otherwise = new NoCodeBlock(NoCodeBlockKind.ELSE_NEXT);
        final NoCodeBlock calmText = new NoCodeBlock(NoCodeBlockKind.PRINT_TEXT);
        alertText.setText("Thunderstorm active. Consider shutting down sensitive outputs.");
        calmText.setText("No thunderstorm is active right now.");
        program.blocks().add(thunderGuard);
        program.blocks().add(alertText);
        program.blocks().add(otherwise);
        program.blocks().add(calmText);
        return program;
    }

    private static NoCodeProgram eveningLightsProgram(final PythonExecutionContext executionContext) {
        final NoCodeProgram program = loopingProgram();
        final NoCodeBlock eveningGuard = new NoCodeBlock(NoCodeBlockKind.IF_WORLD_EVENING_NEXT);
        final NoCodeBlock lampOn = typedDeviceBlock(NoCodeBlockKind.WRITE_REDSTONE, executionContext, "redstone_io");
        final NoCodeBlock otherwise = new NoCodeBlock(NoCodeBlockKind.ELSE_NEXT);
        final NoCodeBlock lampOff = typedDeviceBlock(NoCodeBlockKind.WRITE_REDSTONE, executionContext, "redstone_io");
        lampOn.setLevel(15);
        lampOff.setLevel(0);
        lampOff.setDeviceApiName(lampOn.deviceApiName());
        lampOff.setSideName(lampOn.sideName());
        program.blocks().add(eveningGuard);
        program.blocks().add(lampOn);
        program.blocks().add(otherwise);
        program.blocks().add(lampOff);
        return program;
    }

    private static NoCodeProgram loopingProgram() {
        final NoCodeProgram program = new NoCodeProgram();
        program.setRepeat(true);
        program.setRepeatTicks(20);
        return program;
    }

    private static NoCodeBlock typedDeviceBlock(final NoCodeBlockKind kind,
                                                final PythonExecutionContext executionContext,
                                                final String deviceType) {
        final NoCodeBlock block = new NoCodeBlock(kind);
        final String apiName = firstDeviceApiName(executionContext, deviceType);
        block.setDeviceApiName(apiName);
        if (usesPrimarySide(kind)) {
            block.setSideName(firstSideName(executionContext, apiName));
        }
        if (usesTargetDevice(kind)) {
            block.setTargetDeviceApiName(firstAlternativeDeviceApiName(executionContext, deviceType, apiName));
        }
        if (usesTargetSide(kind)) {
            final String targetDeviceApiName = usesTargetDevice(kind) ? block.targetDeviceApiName() : apiName;
            block.setTargetSideName(firstTargetSideName(executionContext, targetDeviceApiName, block.sideName(), usesTargetDevice(kind)));
        }
        return block;
    }

    private static boolean usesPrimarySide(final NoCodeBlockKind kind) {
        return switch (kind) {
            case COUNT_MATERIAL_ITEM, SHOW_MATERIAL_FLUIDS, MOVE_MATERIAL_ITEM, MOVE_MATERIAL_ITEM_TO, MOVE_MATERIAL_FLUID, MOVE_MATERIAL_FLUID_TO,
                    IF_ITEM_COUNT_AT_LEAST_NEXT, IF_ITEM_COUNT_GREATER_THAN_NEXT, IF_ITEM_COUNT_LESS_THAN_NEXT, IF_ITEM_COUNT_EQUALS_NEXT,
                    IF_FLUID_AMOUNT_AT_LEAST_NEXT, IF_FLUID_AMOUNT_GREATER_THAN_NEXT, IF_FLUID_AMOUNT_LESS_THAN_NEXT, IF_FLUID_AMOUNT_EQUALS_NEXT,
                    READ_REDSTONE, WRITE_REDSTONE, IF_REDSTONE_AT_LEAST_NEXT, IF_REDSTONE_GREATER_THAN_NEXT, IF_REDSTONE_LESS_THAN_NEXT, IF_REDSTONE_EQUALS_NEXT -> true;
            default -> false;
        };
    }

    private static boolean usesTargetSide(final NoCodeBlockKind kind) {
        return switch (kind) {
            case MOVE_MATERIAL_ITEM, MOVE_MATERIAL_ITEM_TO, MOVE_MATERIAL_FLUID, MOVE_MATERIAL_FLUID_TO -> true;
            default -> false;
        };
    }

    private static boolean usesTargetDevice(final NoCodeBlockKind kind) {
        return switch (kind) {
            case MOVE_MATERIAL_ITEM_TO, MOVE_MATERIAL_FLUID_TO -> true;
            default -> false;
        };
    }

    private static String firstDeviceApiName(final PythonExecutionContext executionContext, final String deviceType) {
        if (executionContext == null || deviceType == null || deviceType.isBlank()) {
            return "";
        }
        for (final PythonPeripheralBinding binding : executionContext.peripherals()) {
            if (deviceType.equals(binding.type())) {
                return binding.apiName();
            }
        }
        return "";
    }

    private static String preferredDeviceApiName(final PythonExecutionContext executionContext,
                                                 final String deviceType,
                                                 final String preferredApiName) {
        if (executionContext != null && preferredApiName != null && !preferredApiName.isBlank()) {
            for (final PythonPeripheralBinding binding : executionContext.peripherals()) {
                if (deviceType.equals(binding.type()) && preferredApiName.equals(binding.apiName())) {
                    return binding.apiName();
                }
            }
        }
        return firstDeviceApiName(executionContext, deviceType);
    }

    private static String preferredAlternativeDeviceApiName(final PythonExecutionContext executionContext,
                                                            final String deviceType,
                                                            final String preferredApiName,
                                                            final String excludedApiName) {
        if (executionContext != null && preferredApiName != null && !preferredApiName.isBlank()) {
            for (final PythonPeripheralBinding binding : executionContext.peripherals()) {
                if (deviceType.equals(binding.type())
                        && preferredApiName.equals(binding.apiName())
                        && !binding.apiName().equals(excludedApiName)) {
                    return binding.apiName();
                }
            }
        }
        return firstAlternativeDeviceApiName(executionContext, deviceType, excludedApiName);
    }

    private static String firstSideName(final PythonExecutionContext executionContext, final String apiName) {
        if (executionContext == null || apiName == null || apiName.isBlank()) {
            return "north";
        }
        for (final PythonPeripheralBinding binding : executionContext.peripherals()) {
            if (apiName.equals(binding.apiName())) {
                return preferredSideName(binding.sideNames(), "north");
            }
        }
        return "north";
    }

    private static String firstAlternativeDeviceApiName(final PythonExecutionContext executionContext,
                                                        final String deviceType,
                                                        final String sourceApiName) {
        if (executionContext == null || deviceType == null || deviceType.isBlank()) {
            return "";
        }
        for (final PythonPeripheralBinding binding : executionContext.peripherals()) {
            if (deviceType.equals(binding.type()) && !binding.apiName().equals(sourceApiName)) {
                return binding.apiName();
            }
        }
        return firstDeviceApiName(executionContext, deviceType);
    }

    private static String firstTargetSideName(final PythonExecutionContext executionContext,
                                              final String apiName,
                                              final String sourceSideName,
                                              final boolean allowSameSide) {
        final String preferred = firstSideName(executionContext, apiName);
        if (allowSameSide || !preferred.equals(sourceSideName)) {
            return preferred;
        }
        if (executionContext == null || apiName == null || apiName.isBlank()) {
            return "south";
        }
        for (final PythonPeripheralBinding binding : executionContext.peripherals()) {
            if (!apiName.equals(binding.apiName())) {
                continue;
            }
            for (final String sideName : binding.sideNames()) {
                if (!sideName.equals(sourceSideName)) {
                    return sideName;
                }
            }
        }
        return preferred;
    }

    private static String preferredSideName(final List<String> sideNames, final String preferredDefault) {
        if (sideNames == null || sideNames.isEmpty()) {
            return preferredDefault;
        }
        if (sideNames.contains(preferredDefault)) {
            return preferredDefault;
        }
        return sideNames.get(0);
    }
}