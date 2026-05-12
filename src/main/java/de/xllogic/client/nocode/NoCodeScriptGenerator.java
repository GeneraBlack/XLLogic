package de.xllogic.client.nocode;

import java.util.List;

public final class NoCodeScriptGenerator {
    private static final String DEFAULT_ITEM_ID = "minecraft:cobblestone";
    private static final String DEFAULT_FLUID_ID = "minecraft:water";
    private static final int DEFAULT_WORLD_TIME_WINDOW_END = 12_000;
    private static final int WORLD_DAWN_START = 23_000;
    private static final int WORLD_DAWN_END = 1_000;
    private static final int WORLD_EVENING_START = 11_000;
    private static final int WORLD_EVENING_END = 14_000;

    private NoCodeScriptGenerator() {
    }

    public static String generate(final NoCodeProgram program) {
        final NoCodeProgram safeProgram = program == null ? new NoCodeProgram() : program.copy();
        final StringBuilder builder = new StringBuilder();
        builder.append("# XL Logic no-code builder.\n");
        builder.append(NoCodeProgramCodec.metadataComment(safeProgram)).append('\n');
        builder.append("# Reopen this script with the Builder button to edit the blocks again.\n\n");
        builder.append("def _xllogic_no_code_step():\n");
        if (safeProgram.blocks().isEmpty()) {
            builder.append("    screen.print(")
                    .append(pythonLiteral("Add blocks in the builder before you run this program."))
                    .append(")\n");
        } else {
            final List<NoCodeBlock> blocks = safeProgram.blocks();
            int index = 0;
            while (index < blocks.size()) {
                final int consumedIndex = appendBlockGroup(builder, blocks, index);
                if (consumedIndex < blocks.size() - 1) {
                    builder.append('\n');
                }
                index = consumedIndex + 1;
            }
        }
        builder.append('\n');
        if (safeProgram.repeat()) {
            builder.append("yield from repeat(_xllogic_no_code_step, ")
                    .append(safeProgram.repeatTicks())
                    .append(")\n");
        } else {
            builder.append("_xllogic_no_code_step()\n");
        }
        return builder.toString();
    }

    private static int appendBlockGroup(final StringBuilder builder, final List<NoCodeBlock> blocks, final int index) {
        if (blocks == null || index < 0 || index >= blocks.size()) {
            return index;
        }
        final NoCodeBlock block = blocks.get(index);
        if (block == null) {
            return index;
        }
        if (isGuardBlock(block.kind())) {
            return appendGuardBlock(builder, blocks, index, block);
        }
        appendActionBlock(builder, block, index);
        return index;
    }

    private static int appendGuardBlock(final StringBuilder builder,
                                        final List<NoCodeBlock> blocks,
                                        final int index,
                                        final NoCodeBlock block) {
        if (index + 1 >= blocks.size()) {
            appendLine(builder, 1, "screen.print(" + pythonLiteral("Condition block '" + block.kind().label() + "' needs another block after it.") + ")");
            return index;
        }

        final StringBuilder thenBuilder = new StringBuilder();
        final int thenConsumedIndex = appendBlockGroup(thenBuilder, blocks, index + 1);
        int consumedIndex = thenConsumedIndex;
        String elseBlock = null;
        if (thenConsumedIndex + 2 < blocks.size()) {
            final NoCodeBlock maybeElse = blocks.get(thenConsumedIndex + 1);
            if (maybeElse != null && isElseBlock(maybeElse.kind())) {
                final StringBuilder elseBuilder = new StringBuilder();
                consumedIndex = appendBlockGroup(elseBuilder, blocks, thenConsumedIndex + 2);
                elseBlock = elseBuilder.toString();
            }
        }

        switch (block.kind()) {
            case IF_WORLD_DAY_NEXT -> {
                appendLine(builder, 1, "if not world.available():");
                appendLine(builder, 2, "screen.print(\"World access is unavailable right now.\")");
                appendLine(builder, 1, "elif world.is_day():");
                builder.append(indentBlock(thenBuilder.toString(), 1));
                appendOptionalElse(builder, 1, elseBlock);
            }
            case IF_WORLD_NIGHT_NEXT -> {
                appendLine(builder, 1, "if not world.available():");
                appendLine(builder, 2, "screen.print(\"World access is unavailable right now.\")");
                appendLine(builder, 1, "elif world.is_night():");
                builder.append(indentBlock(thenBuilder.toString(), 1));
                appendOptionalElse(builder, 1, elseBlock);
            }
            case IF_WORLD_THUNDERING_NEXT -> {
                appendLine(builder, 1, "if not world.available():");
                appendLine(builder, 2, "screen.print(\"World access is unavailable right now.\")");
                appendLine(builder, 1, "elif world.is_thundering():");
                builder.append(indentBlock(thenBuilder.toString(), 1));
                appendOptionalElse(builder, 1, elseBlock);
            }
            case IF_WORLD_RAIN_LEVEL_AT_LEAST_NEXT, IF_WORLD_RAIN_LEVEL_GREATER_THAN_NEXT, IF_WORLD_RAIN_LEVEL_LESS_THAN_NEXT, IF_WORLD_RAIN_LEVEL_EQUALS_NEXT -> {
                final String operator = comparisonOperator(block.kind());
                appendLine(builder, 1, "if not world.available():");
                appendLine(builder, 2, "screen.print(\"World access is unavailable right now.\")");
                appendLine(builder, 1, "else:");
                appendLine(builder, 2, "world_rain_" + index + " = world.rain_level()");
                appendLine(builder, 2, "if world_rain_" + index + " " + operator + " " + block.level() + ":");
                builder.append(indentBlock(thenBuilder.toString(), 2));
                appendOptionalElse(builder, 2, elseBlock);
            }
            case IF_WORLD_TIME_WINDOW_NEXT -> {
                final int startTick = clampWorldDayTime(block.level());
                final int endTick = worldTimeWindowEnd(block.text());
                appendLine(builder, 1, "if not world.available():");
                appendLine(builder, 2, "screen.print(\"World access is unavailable right now.\")");
                appendLine(builder, 1, "else:");
                appendLine(builder, 2, "world_time_" + index + " = world.day_time()");
                appendLine(builder, 2, "window_start_" + index + " = " + startTick);
                appendLine(builder, 2, "window_end_" + index + " = " + endTick);
                appendLine(builder, 2, "in_window_" + index + " = ((window_start_" + index + " <= window_end_" + index + " and window_start_" + index + " <= world_time_" + index + " <= window_end_" + index + ") or (window_start_" + index + " > window_end_" + index + " and (world_time_" + index + " >= window_start_" + index + " or world_time_" + index + " <= window_end_" + index + ")))");
                appendLine(builder, 2, "if in_window_" + index + ":");
                builder.append(indentBlock(thenBuilder.toString(), 2));
                appendOptionalElse(builder, 2, elseBlock);
            }
            case IF_WORLD_DAWN_NEXT -> {
                appendLine(builder, 1, "if not world.available():");
                appendLine(builder, 2, "screen.print(\"World access is unavailable right now.\")");
                appendLine(builder, 1, "else:");
                appendLine(builder, 2, "world_time_" + index + " = world.day_time()");
                appendLine(builder, 2, "in_window_" + index + " = ((" + WORLD_DAWN_START + " <= " + WORLD_DAWN_END + " and " + WORLD_DAWN_START + " <= world_time_" + index + " <= " + WORLD_DAWN_END + ") or (" + WORLD_DAWN_START + " > " + WORLD_DAWN_END + " and (world_time_" + index + " >= " + WORLD_DAWN_START + " or world_time_" + index + " <= " + WORLD_DAWN_END + ")))");
                appendLine(builder, 2, "if in_window_" + index + ":");
                builder.append(indentBlock(thenBuilder.toString(), 2));
                appendOptionalElse(builder, 2, elseBlock);
            }
            case IF_WORLD_EVENING_NEXT -> {
                appendLine(builder, 1, "if not world.available():");
                appendLine(builder, 2, "screen.print(\"World access is unavailable right now.\")");
                appendLine(builder, 1, "else:");
                appendLine(builder, 2, "world_time_" + index + " = world.day_time()");
                appendLine(builder, 2, "in_window_" + index + " = ((" + WORLD_EVENING_START + " <= " + WORLD_EVENING_END + " and " + WORLD_EVENING_START + " <= world_time_" + index + " <= " + WORLD_EVENING_END + ") or (" + WORLD_EVENING_START + " > " + WORLD_EVENING_END + " and (world_time_" + index + " >= " + WORLD_EVENING_START + " or world_time_" + index + " <= " + WORLD_EVENING_END + ")))");
                appendLine(builder, 2, "if in_window_" + index + ":");
                builder.append(indentBlock(thenBuilder.toString(), 2));
                appendOptionalElse(builder, 2, elseBlock);
            }
            case IF_WORLD_MOON_PHASE_AT_LEAST_NEXT, IF_WORLD_MOON_PHASE_GREATER_THAN_NEXT, IF_WORLD_MOON_PHASE_LESS_THAN_NEXT, IF_WORLD_MOON_PHASE_EQUALS_NEXT -> {
                final String operator = comparisonOperator(block.kind());
                appendLine(builder, 1, "if not world.available():");
                appendLine(builder, 2, "screen.print(\"World access is unavailable right now.\")");
                appendLine(builder, 1, "else:");
                appendLine(builder, 2, "world_moon_phase_" + index + " = world.moon_phase()");
                appendLine(builder, 2, "if world_moon_phase_" + index + " " + operator + " " + block.level() + ":");
                builder.append(indentBlock(thenBuilder.toString(), 2));
                appendOptionalElse(builder, 2, elseBlock);
            }
            case IF_RAINING_NEXT -> {
                final String variable = "rain_sensor_" + index;
                final String apiName = fallbackDevice(block.deviceApiName());
                appendLine(builder, 1, variable + " = device(" + pythonLiteral(apiName) + ")");
                appendLine(builder, 1, "if " + variable + " is None:");
                appendLine(builder, 2, "screen.print(" + pythonLiteral("Rain sensor '" + apiName + "' is missing.") + ")");
                appendLine(builder, 1, "elif not " + variable + ".available():");
                appendLine(builder, 2, "screen.print(" + pythonLiteral("Rain sensor '" + apiName + "' is unavailable right now.") + ")");
                appendLine(builder, 1, "elif " + variable + ".is_raining():");
                builder.append(indentBlock(thenBuilder.toString(), 1));
                appendOptionalElse(builder, 1, elseBlock);
            }
            case IF_DRY_NEXT -> {
                final String variable = "rain_sensor_" + index;
                final String apiName = fallbackDevice(block.deviceApiName());
                appendLine(builder, 1, variable + " = device(" + pythonLiteral(apiName) + ")");
                appendLine(builder, 1, "if " + variable + " is None:");
                appendLine(builder, 2, "screen.print(" + pythonLiteral("Rain sensor '" + apiName + "' is missing.") + ")");
                appendLine(builder, 1, "elif not " + variable + ".available():");
                appendLine(builder, 2, "screen.print(" + pythonLiteral("Rain sensor '" + apiName + "' is unavailable right now.") + ")");
                appendLine(builder, 1, "else:");
                appendLine(builder, 2, "if not " + variable + ".is_raining():");
                builder.append(indentBlock(thenBuilder.toString(), 2));
                appendOptionalElse(builder, 2, elseBlock);
            }
            case IF_REDSTONE_AT_LEAST_NEXT, IF_REDSTONE_GREATER_THAN_NEXT, IF_REDSTONE_LESS_THAN_NEXT, IF_REDSTONE_EQUALS_NEXT -> {
                final String variable = "redstone_" + index;
                final String apiName = fallbackDevice(block.deviceApiName());
                final String sideName = fallbackSide(block.sideName());
                final String operator = comparisonOperator(block.kind());
                appendLine(builder, 1, variable + " = device(" + pythonLiteral(apiName) + ")");
                appendLine(builder, 1, "if " + variable + " is None:");
                appendLine(builder, 2, "screen.print(" + pythonLiteral("Redstone device '" + apiName + "' is missing.") + ")");
                appendLine(builder, 1, "elif not " + variable + ".available():");
                appendLine(builder, 2, "screen.print(" + pythonLiteral("Redstone device '" + apiName + "' is unavailable right now.") + ")");
                appendLine(builder, 1, "else:");
                appendLine(builder, 2, variable + ".set_mode(\"input\")");
                appendLine(builder, 2, "level_" + index + " = " + variable + ".read(" + pythonLiteral(sideName) + ")");
                appendLine(builder, 2, "if level_" + index + " " + operator + " " + block.level() + ":");
                builder.append(indentBlock(thenBuilder.toString(), 2));
                appendOptionalElse(builder, 2, elseBlock);
            }
            case IF_ITEM_COUNT_AT_LEAST_NEXT, IF_ITEM_COUNT_GREATER_THAN_NEXT, IF_ITEM_COUNT_LESS_THAN_NEXT, IF_ITEM_COUNT_EQUALS_NEXT -> {
                final String variable = "material_" + index;
                final String apiName = fallbackDevice(block.deviceApiName());
                final String sideName = fallbackSide(block.sideName());
                final String itemId = fallbackItemId(block.text());
                final String operator = comparisonOperator(block.kind());
                appendLine(builder, 1, variable + " = device(" + pythonLiteral(apiName) + ")");
                appendLine(builder, 1, "if " + variable + " is None:");
                appendLine(builder, 2, "screen.print(" + pythonLiteral("Material I/O device '" + apiName + "' is missing.") + ")");
                appendLine(builder, 1, "elif not " + variable + ".available():");
                appendLine(builder, 2, "screen.print(" + pythonLiteral("Material I/O device '" + apiName + "' is unavailable right now.") + ")");
                appendLine(builder, 1, "else:");
                appendLine(builder, 2, "count_" + index + " = " + variable + ".count_item(" + pythonLiteral(sideName) + ", " + pythonLiteral(itemId) + ")");
                appendLine(builder, 2, "if count_" + index + " " + operator + " " + Math.max(1, block.level()) + ":");
                builder.append(indentBlock(thenBuilder.toString(), 2));
                appendOptionalElse(builder, 2, elseBlock);
            }
            case IF_FLUID_AMOUNT_AT_LEAST_NEXT, IF_FLUID_AMOUNT_GREATER_THAN_NEXT, IF_FLUID_AMOUNT_LESS_THAN_NEXT, IF_FLUID_AMOUNT_EQUALS_NEXT -> {
                final String variable = "material_" + index;
                final String apiName = fallbackDevice(block.deviceApiName());
                final String sideName = fallbackSide(block.sideName());
                final String fluidId = fallbackFluidId(block.text());
                final String operator = comparisonOperator(block.kind());
                appendLine(builder, 1, variable + " = device(" + pythonLiteral(apiName) + ")");
                appendLine(builder, 1, "if " + variable + " is None:");
                appendLine(builder, 2, "screen.print(" + pythonLiteral("Material I/O device '" + apiName + "' is missing.") + ")");
                appendLine(builder, 1, "elif not " + variable + ".available():");
                appendLine(builder, 2, "screen.print(" + pythonLiteral("Material I/O device '" + apiName + "' is unavailable right now.") + ")");
                appendLine(builder, 1, "else:");
                appendLine(builder, 2, "amount_" + index + " = 0");
                appendLine(builder, 2, "for tank_" + index + " in " + variable + ".tanks(" + pythonLiteral(sideName) + "):");
                appendLine(builder, 3, "if tank_" + index + ".get(\"fluid\") != " + pythonLiteral(fluidId) + ":");
                appendLine(builder, 4, "continue");
                appendLine(builder, 3, "amount_" + index + " += tank_" + index + ".get(\"amount\", 0)");
                appendLine(builder, 2, "if amount_" + index + " " + operator + " " + Math.max(1, block.level()) + ":");
                builder.append(indentBlock(thenBuilder.toString(), 2));
                appendOptionalElse(builder, 2, elseBlock);
            }
            default -> appendActionBlock(builder, block, index);
        }
        return consumedIndex;
    }

    private static boolean isGuardBlock(final NoCodeBlockKind kind) {
        return switch (kind) {
            case IF_WORLD_DAY_NEXT, IF_WORLD_NIGHT_NEXT, IF_WORLD_THUNDERING_NEXT,
                IF_WORLD_RAIN_LEVEL_AT_LEAST_NEXT, IF_WORLD_RAIN_LEVEL_GREATER_THAN_NEXT, IF_WORLD_RAIN_LEVEL_LESS_THAN_NEXT, IF_WORLD_RAIN_LEVEL_EQUALS_NEXT,
                IF_WORLD_TIME_WINDOW_NEXT,
                IF_WORLD_DAWN_NEXT, IF_WORLD_EVENING_NEXT,
                IF_WORLD_MOON_PHASE_AT_LEAST_NEXT, IF_WORLD_MOON_PHASE_GREATER_THAN_NEXT, IF_WORLD_MOON_PHASE_LESS_THAN_NEXT, IF_WORLD_MOON_PHASE_EQUALS_NEXT,
                IF_RAINING_NEXT, IF_DRY_NEXT,
                IF_REDSTONE_AT_LEAST_NEXT, IF_REDSTONE_GREATER_THAN_NEXT, IF_REDSTONE_LESS_THAN_NEXT, IF_REDSTONE_EQUALS_NEXT,
                IF_ITEM_COUNT_AT_LEAST_NEXT, IF_ITEM_COUNT_GREATER_THAN_NEXT, IF_ITEM_COUNT_LESS_THAN_NEXT, IF_ITEM_COUNT_EQUALS_NEXT,
                IF_FLUID_AMOUNT_AT_LEAST_NEXT, IF_FLUID_AMOUNT_GREATER_THAN_NEXT, IF_FLUID_AMOUNT_LESS_THAN_NEXT, IF_FLUID_AMOUNT_EQUALS_NEXT -> true;
            default -> false;
        };
    }

    private static boolean isElseBlock(final NoCodeBlockKind kind) {
        return kind == NoCodeBlockKind.ELSE_NEXT;
    }

    private static String comparisonOperator(final NoCodeBlockKind kind) {
        if (kind == null) {
            return ">=";
        }
        return switch (kind) {
            case IF_WORLD_RAIN_LEVEL_AT_LEAST_NEXT -> ">=";
            case IF_WORLD_RAIN_LEVEL_GREATER_THAN_NEXT -> ">";
            case IF_WORLD_RAIN_LEVEL_LESS_THAN_NEXT -> "<";
            case IF_WORLD_RAIN_LEVEL_EQUALS_NEXT -> "==";
            case IF_WORLD_MOON_PHASE_AT_LEAST_NEXT -> ">=";
            case IF_WORLD_MOON_PHASE_GREATER_THAN_NEXT -> ">";
            case IF_WORLD_MOON_PHASE_LESS_THAN_NEXT -> "<";
            case IF_WORLD_MOON_PHASE_EQUALS_NEXT -> "==";
            case IF_REDSTONE_AT_LEAST_NEXT, IF_ITEM_COUNT_AT_LEAST_NEXT, IF_FLUID_AMOUNT_AT_LEAST_NEXT -> ">=";
            case IF_REDSTONE_GREATER_THAN_NEXT, IF_ITEM_COUNT_GREATER_THAN_NEXT, IF_FLUID_AMOUNT_GREATER_THAN_NEXT -> ">";
            case IF_REDSTONE_LESS_THAN_NEXT, IF_ITEM_COUNT_LESS_THAN_NEXT, IF_FLUID_AMOUNT_LESS_THAN_NEXT -> "<";
            case IF_REDSTONE_EQUALS_NEXT, IF_ITEM_COUNT_EQUALS_NEXT, IF_FLUID_AMOUNT_EQUALS_NEXT -> "==";
            default -> ">=";
        };
    }

    private static void appendOptionalElse(final StringBuilder builder, final int indentLevel, final String elseBlock) {
        if (elseBlock == null || elseBlock.isBlank()) {
            return;
        }
        appendLine(builder, indentLevel, "else:");
        builder.append(indentBlock(elseBlock, indentLevel));
    }

    private static void appendActionBlock(final StringBuilder builder, final NoCodeBlock block, final int index) {
        if (block == null) {
            return;
        }
        switch (block.kind()) {
            case PRINT_TEXT -> appendLine(builder, 1, "screen.print(" + pythonLiteral(fallbackText(block.text())) + ")");
            case SHOW_WORLD -> {
                appendLine(builder, 1, "if world.available():");
                appendLine(builder, 2, "screen.show(\"World\", {");
                appendLine(builder, 3, "\"dimension\": world.dimension(),");
                appendLine(builder, 3, "\"day_time\": world.day_time(),");
                appendLine(builder, 3, "\"raining\": world.is_raining(),");
                appendLine(builder, 2, "}, text=\"Current world data\")");
                appendLine(builder, 1, "else:");
                appendLine(builder, 2, "screen.print(\"World access is unavailable right now.\")");
            }
            case SHOW_CLOCK -> {
                final String variable = "clock_" + index;
                final String apiName = fallbackDevice(block.deviceApiName());
                appendLine(builder, 1, variable + " = device(" + pythonLiteral(apiName) + ")");
                appendLine(builder, 1, "if " + variable + " is None:");
                appendLine(builder, 2, "screen.print(" + pythonLiteral("Clock device '" + apiName + "' is missing.") + ")");
                appendLine(builder, 1, "elif not " + variable + ".available():");
                appendLine(builder, 2, "screen.print(" + pythonLiteral("Clock device '" + apiName + "' is unavailable right now.") + ")");
                appendLine(builder, 1, "else:");
                appendLine(builder, 2, "screen.show(\"Clock\", {");
                appendLine(builder, 3, "\"device\": " + variable + ".name(),");
                appendLine(builder, 3, "\"day_time\": " + variable + ".day_time(),");
                appendLine(builder, 3, "\"game_time\": " + variable + ".game_time(),");
                appendLine(builder, 3, "\"real_time\": " + variable + ".real_time(),");
                appendLine(builder, 2, "}, text=\"Current time reported by the clock device\")");
            }
            case SHOW_RAIN_SENSOR -> {
                final String variable = "rain_sensor_" + index;
                final String apiName = fallbackDevice(block.deviceApiName());
                appendLine(builder, 1, variable + " = device(" + pythonLiteral(apiName) + ")");
                appendLine(builder, 1, "if " + variable + " is None:");
                appendLine(builder, 2, "screen.print(" + pythonLiteral("Rain sensor '" + apiName + "' is missing.") + ")");
                appendLine(builder, 1, "elif not " + variable + ".available():");
                appendLine(builder, 2, "screen.print(" + pythonLiteral("Rain sensor '" + apiName + "' is unavailable right now.") + ")");
                appendLine(builder, 1, "else:");
                appendLine(builder, 2, "screen.show(\"Rain sensor\", {");
                appendLine(builder, 3, "\"device\": " + variable + ".name(),");
                appendLine(builder, 3, "\"raining\": " + variable + ".is_raining(),");
                appendLine(builder, 3, "\"rain_level\": " + variable + ".rain_level(),");
                appendLine(builder, 2, "}, text=\"Current rain status above the sensor\")");
            }
            case LIST_DEVICES -> {
                appendLine(builder, 1, "rows = []");
                appendLine(builder, 1, "for name in list_device_names():");
                appendLine(builder, 2, "current = device(name)");
                appendLine(builder, 2, "if current is None:");
                appendLine(builder, 3, "continue");
                appendLine(builder, 2, "rows.append([name, current.type(), current.network_scope(), current.remote_policy()])");
                appendLine(builder, 1, "screen.table(\"Devices\", [\"API\", \"Type\", \"Scope\", \"Policy\"], rows,");
                appendLine(builder, 2, "text=\"All visible local and bridged devices\")");
            }
            case SHOW_DEVICE_STATE -> {
                final String variable = "device_" + index;
                final String apiName = fallbackDevice(block.deviceApiName());
                appendLine(builder, 1, variable + " = device(" + pythonLiteral(apiName) + ")");
                appendLine(builder, 1, "if " + variable + " is None:");
                appendLine(builder, 2, "screen.print(" + pythonLiteral("Device '" + apiName + "' is missing.") + ")");
                appendLine(builder, 1, "elif not " + variable + ".available():");
                appendLine(builder, 2, "screen.print(" + pythonLiteral("Device '" + apiName + "' is unavailable right now.") + ")");
                appendLine(builder, 1, "else:");
                appendLine(builder, 2, "screen.show(" + variable + ".name(), " + variable + ".state(), text=\"Current device state\")");
            }
            case SHOW_MATERIAL_IO -> {
                final String variable = "material_" + index;
                final String apiName = fallbackDevice(block.deviceApiName());
                appendLine(builder, 1, variable + " = device(" + pythonLiteral(apiName) + ")");
                appendLine(builder, 1, "if " + variable + " is None:");
                appendLine(builder, 2, "screen.print(" + pythonLiteral("Material I/O device '" + apiName + "' is missing.") + ")");
                appendLine(builder, 1, "elif not " + variable + ".available():");
                appendLine(builder, 2, "screen.print(" + pythonLiteral("Material I/O device '" + apiName + "' is unavailable right now.") + ")");
                appendLine(builder, 1, "else:");
                appendLine(builder, 2, "screen.show(\"Material I/O\", " + variable + ".state(), text=\"Current material routes, modes and capacities\")");
            }
            case COUNT_MATERIAL_ITEM -> {
                final String variable = "material_" + index;
                final String apiName = fallbackDevice(block.deviceApiName());
                final String sideName = fallbackSide(block.sideName());
                final String itemId = fallbackItemId(block.text());
                appendLine(builder, 1, variable + " = device(" + pythonLiteral(apiName) + ")");
                appendLine(builder, 1, "if " + variable + " is None:");
                appendLine(builder, 2, "screen.print(" + pythonLiteral("Material I/O device '" + apiName + "' is missing.") + ")");
                appendLine(builder, 1, "elif not " + variable + ".available():");
                appendLine(builder, 2, "screen.print(" + pythonLiteral("Material I/O device '" + apiName + "' is unavailable right now.") + ")");
                appendLine(builder, 1, "else:");
                appendLine(builder, 2, "count_" + index + " = " + variable + ".count_item(" + pythonLiteral(sideName) + ", " + pythonLiteral(itemId) + ")");
                appendLine(builder, 2, "screen.show(\"Item count\", {");
                appendLine(builder, 3, "\"device\": " + variable + ".name(),");
                appendLine(builder, 3, "\"side\": " + pythonLiteral(sideName) + ",");
                appendLine(builder, 3, "\"item\": " + pythonLiteral(itemId) + ",");
                appendLine(builder, 3, "\"count\": count_" + index + ",");
                appendLine(builder, 2, "}, text=\"Matching items on one material I/O side\")");
            }
            case MOVE_MATERIAL_ITEM -> {
                final String variable = "material_" + index;
                final String apiName = fallbackDevice(block.deviceApiName());
                final String sourceSide = fallbackSide(block.sideName());
                final String targetSide = fallbackTargetSide(block.targetSideName());
                final String itemId = fallbackItemId(block.text());
                appendLine(builder, 1, variable + " = device(" + pythonLiteral(apiName) + ")");
                appendLine(builder, 1, "if " + variable + " is None:");
                appendLine(builder, 2, "screen.print(" + pythonLiteral("Material I/O device '" + apiName + "' is missing.") + ")");
                appendLine(builder, 1, "elif not " + variable + ".available():");
                appendLine(builder, 2, "screen.print(" + pythonLiteral("Material I/O device '" + apiName + "' is unavailable right now.") + ")");
                appendLine(builder, 1, "elif " + pythonLiteral(sourceSide) + " == " + pythonLiteral(targetSide) + ":");
                appendLine(builder, 2, "screen.print(" + pythonLiteral("Choose different source and target sides for the material transfer.") + ")");
                appendLine(builder, 1, "else:");
                appendLine(builder, 2, "moved_" + index + " = 0");
                appendLine(builder, 2, "for stack_" + index + " in " + variable + ".inventory(" + pythonLiteral(sourceSide) + "):");
                appendLine(builder, 3, "if stack_" + index + ".get(\"item\") != " + pythonLiteral(itemId) + ":");
                appendLine(builder, 4, "continue");
                appendLine(builder, 3, "moved_" + index + " = " + variable + ".transfer_item(" + pythonLiteral(sourceSide) + ", " + pythonLiteral(targetSide) + ", stack_" + index + ".get(\"slot\", 0), " + Math.max(1, block.level()) + ")");
                appendLine(builder, 3, "if moved_" + index + " > 0:");
                appendLine(builder, 4, "break");
                appendLine(builder, 2, "screen.show(\"Item transfer\", {");
                appendLine(builder, 3, "\"device\": " + variable + ".name(),");
                appendLine(builder, 3, "\"source_side\": " + pythonLiteral(sourceSide) + ",");
                appendLine(builder, 3, "\"target_side\": " + pythonLiteral(targetSide) + ",");
                appendLine(builder, 3, "\"item\": " + pythonLiteral(itemId) + ",");
                appendLine(builder, 3, "\"requested\": " + Math.max(1, block.level()) + ",");
                appendLine(builder, 3, "\"moved\": moved_" + index + ",");
                appendLine(builder, 2, "}, text=\"Moved matching items through one material I/O device\")");
            }
            case MOVE_MATERIAL_ITEM_TO -> {
                final String variable = "material_" + index;
                final String targetVariable = "target_material_" + index;
                final String apiName = fallbackDevice(block.deviceApiName());
                final String targetApiName = block.targetDeviceApiName() == null ? "" : block.targetDeviceApiName().trim();
                final String sourceSide = fallbackSide(block.sideName());
                final String targetSide = fallbackTargetSide(block.targetSideName());
                final String itemId = fallbackItemId(block.text());
                appendLine(builder, 1, variable + " = device(" + pythonLiteral(apiName) + ")");
                appendLine(builder, 1, "if " + variable + " is None:");
                appendLine(builder, 2, "screen.print(" + pythonLiteral("Material I/O device '" + apiName + "' is missing.") + ")");
                appendLine(builder, 1, "elif not " + variable + ".available():");
                appendLine(builder, 2, "screen.print(" + pythonLiteral("Material I/O device '" + apiName + "' is unavailable right now.") + ")");
                appendLine(builder, 1, "elif " + pythonLiteral(targetApiName) + " == \"\":");
                appendLine(builder, 2, "screen.print(" + pythonLiteral("Choose a target Material I/O device for the item transfer.") + ")");
                appendLine(builder, 1, "else:");
                appendLine(builder, 2, targetVariable + " = device(" + pythonLiteral(targetApiName) + ")");
                appendLine(builder, 2, "if " + targetVariable + " is None:");
                appendLine(builder, 3, "screen.print(" + pythonLiteral("Target Material I/O device '" + targetApiName + "' is missing.") + ")");
                appendLine(builder, 2, "elif not " + targetVariable + ".available():");
                appendLine(builder, 3, "screen.print(" + pythonLiteral("Target Material I/O device '" + targetApiName + "' is unavailable right now.") + ")");
                appendLine(builder, 2, "elif " + pythonLiteral(apiName) + " == " + pythonLiteral(targetApiName) + " and " + pythonLiteral(sourceSide) + " == " + pythonLiteral(targetSide) + ":");
                appendLine(builder, 3, "screen.print(" + pythonLiteral("Choose a different source or target endpoint for the item transfer.") + ")");
                appendLine(builder, 2, "else:");
                appendLine(builder, 3, "moved_" + index + " = 0");
                appendLine(builder, 3, "for stack_" + index + " in " + variable + ".inventory(" + pythonLiteral(sourceSide) + "):");
                appendLine(builder, 4, "if stack_" + index + ".get(\"item\") != " + pythonLiteral(itemId) + ":");
                appendLine(builder, 5, "continue");
                appendLine(builder, 4, "moved_" + index + " = " + variable + ".transfer_item_to(" + pythonLiteral(targetApiName) + ", " + pythonLiteral(sourceSide) + ", " + pythonLiteral(targetSide) + ", stack_" + index + ".get(\"slot\", 0), " + Math.max(1, block.level()) + ")");
                appendLine(builder, 4, "if moved_" + index + " > 0:");
                appendLine(builder, 5, "break");
                appendLine(builder, 3, "screen.show(\"Item transfer\", {");
                appendLine(builder, 4, "\"source_device\": " + variable + ".name(),");
                appendLine(builder, 4, "\"target_device\": " + targetVariable + ".name(),");
                appendLine(builder, 4, "\"source_side\": " + pythonLiteral(sourceSide) + ",");
                appendLine(builder, 4, "\"target_side\": " + pythonLiteral(targetSide) + ",");
                appendLine(builder, 4, "\"item\": " + pythonLiteral(itemId) + ",");
                appendLine(builder, 4, "\"requested\": " + Math.max(1, block.level()) + ",");
                appendLine(builder, 4, "\"moved\": moved_" + index + ",");
                appendLine(builder, 3, "}, text=\"Moved matching items between two material I/O devices\")");
            }
            case SHOW_MATERIAL_FLUIDS -> {
                final String variable = "material_" + index;
                final String apiName = fallbackDevice(block.deviceApiName());
                final String sideName = fallbackSide(block.sideName());
                appendLine(builder, 1, variable + " = device(" + pythonLiteral(apiName) + ")");
                appendLine(builder, 1, "if " + variable + " is None:");
                appendLine(builder, 2, "screen.print(" + pythonLiteral("Material I/O device '" + apiName + "' is missing.") + ")");
                appendLine(builder, 1, "elif not " + variable + ".available():");
                appendLine(builder, 2, "screen.print(" + pythonLiteral("Material I/O device '" + apiName + "' is unavailable right now.") + ")");
                appendLine(builder, 1, "else:");
                appendLine(builder, 2, "rows_" + index + " = []");
                appendLine(builder, 2, "for tank_" + index + " in " + variable + ".tanks(" + pythonLiteral(sideName) + "):");
                appendLine(builder, 3, "rows_" + index + ".append([tank_" + index + ".get(\"tank\", 0), tank_" + index + ".get(\"fluid\", \"\"), tank_" + index + ".get(\"amount\", 0)])");
                appendLine(builder, 2, "screen.table(\"Fluid tanks\", [\"Tank\", \"Fluid\", \"Amount\"], rows_" + index + ",");
                appendLine(builder, 3, "text=\"Non-empty fluid tanks on the selected material I/O side\")");
            }
            case MOVE_MATERIAL_FLUID -> {
                final String variable = "material_" + index;
                final String apiName = fallbackDevice(block.deviceApiName());
                final String sourceSide = fallbackSide(block.sideName());
                final String targetSide = fallbackTargetSide(block.targetSideName());
                final String fluidId = fallbackFluidId(block.text());
                appendLine(builder, 1, variable + " = device(" + pythonLiteral(apiName) + ")");
                appendLine(builder, 1, "if " + variable + " is None:");
                appendLine(builder, 2, "screen.print(" + pythonLiteral("Material I/O device '" + apiName + "' is missing.") + ")");
                appendLine(builder, 1, "elif not " + variable + ".available():");
                appendLine(builder, 2, "screen.print(" + pythonLiteral("Material I/O device '" + apiName + "' is unavailable right now.") + ")");
                appendLine(builder, 1, "elif " + pythonLiteral(sourceSide) + " == " + pythonLiteral(targetSide) + ":");
                appendLine(builder, 2, "screen.print(" + pythonLiteral("Choose different source and target sides for the fluid transfer.") + ")");
                appendLine(builder, 1, "else:");
                appendLine(builder, 2, "moved_" + index + " = 0");
                appendLine(builder, 2, "for tank_" + index + " in " + variable + ".tanks(" + pythonLiteral(sourceSide) + "):");
                appendLine(builder, 3, "if tank_" + index + ".get(\"fluid\") != " + pythonLiteral(fluidId) + ":");
                appendLine(builder, 4, "continue");
                appendLine(builder, 3, "moved_" + index + " = " + variable + ".transfer_fluid(" + pythonLiteral(sourceSide) + ", " + pythonLiteral(targetSide) + ", tank_" + index + ".get(\"tank\", 0), " + block.level() + ")");
                appendLine(builder, 3, "if moved_" + index + " > 0:");
                appendLine(builder, 4, "break");
                appendLine(builder, 2, "screen.show(\"Fluid transfer\", {");
                appendLine(builder, 3, "\"device\": " + variable + ".name(),");
                appendLine(builder, 3, "\"source_side\": " + pythonLiteral(sourceSide) + ",");
                appendLine(builder, 3, "\"target_side\": " + pythonLiteral(targetSide) + ",");
                appendLine(builder, 3, "\"fluid\": " + pythonLiteral(fluidId) + ",");
                appendLine(builder, 3, "\"requested\": " + block.level() + ",");
                appendLine(builder, 3, "\"moved\": moved_" + index + ",");
                appendLine(builder, 2, "}, text=\"Moved matching fluid through one material I/O device\")");
            }
            case MOVE_MATERIAL_FLUID_TO -> {
                final String variable = "material_" + index;
                final String targetVariable = "target_material_" + index;
                final String apiName = fallbackDevice(block.deviceApiName());
                final String targetApiName = block.targetDeviceApiName() == null ? "" : block.targetDeviceApiName().trim();
                final String sourceSide = fallbackSide(block.sideName());
                final String targetSide = fallbackTargetSide(block.targetSideName());
                final String fluidId = fallbackFluidId(block.text());
                appendLine(builder, 1, variable + " = device(" + pythonLiteral(apiName) + ")");
                appendLine(builder, 1, "if " + variable + " is None:");
                appendLine(builder, 2, "screen.print(" + pythonLiteral("Material I/O device '" + apiName + "' is missing.") + ")");
                appendLine(builder, 1, "elif not " + variable + ".available():");
                appendLine(builder, 2, "screen.print(" + pythonLiteral("Material I/O device '" + apiName + "' is unavailable right now.") + ")");
                appendLine(builder, 1, "elif " + pythonLiteral(targetApiName) + " == \"\":");
                appendLine(builder, 2, "screen.print(" + pythonLiteral("Choose a target Material I/O device for the fluid transfer.") + ")");
                appendLine(builder, 1, "else:");
                appendLine(builder, 2, targetVariable + " = device(" + pythonLiteral(targetApiName) + ")");
                appendLine(builder, 2, "if " + targetVariable + " is None:");
                appendLine(builder, 3, "screen.print(" + pythonLiteral("Target Material I/O device '" + targetApiName + "' is missing.") + ")");
                appendLine(builder, 2, "elif not " + targetVariable + ".available():");
                appendLine(builder, 3, "screen.print(" + pythonLiteral("Target Material I/O device '" + targetApiName + "' is unavailable right now.") + ")");
                appendLine(builder, 2, "elif " + pythonLiteral(apiName) + " == " + pythonLiteral(targetApiName) + " and " + pythonLiteral(sourceSide) + " == " + pythonLiteral(targetSide) + ":");
                appendLine(builder, 3, "screen.print(" + pythonLiteral("Choose a different source or target endpoint for the fluid transfer.") + ")");
                appendLine(builder, 2, "else:");
                appendLine(builder, 3, "moved_" + index + " = 0");
                appendLine(builder, 3, "for tank_" + index + " in " + variable + ".tanks(" + pythonLiteral(sourceSide) + "):");
                appendLine(builder, 4, "if tank_" + index + ".get(\"fluid\") != " + pythonLiteral(fluidId) + ":");
                appendLine(builder, 5, "continue");
                appendLine(builder, 4, "moved_" + index + " = " + variable + ".transfer_fluid_to(" + pythonLiteral(targetApiName) + ", " + pythonLiteral(sourceSide) + ", " + pythonLiteral(targetSide) + ", tank_" + index + ".get(\"tank\", 0), " + block.level() + ")");
                appendLine(builder, 4, "if moved_" + index + " > 0:");
                appendLine(builder, 5, "break");
                appendLine(builder, 3, "screen.show(\"Fluid transfer\", {");
                appendLine(builder, 4, "\"source_device\": " + variable + ".name(),");
                appendLine(builder, 4, "\"target_device\": " + targetVariable + ".name(),");
                appendLine(builder, 4, "\"source_side\": " + pythonLiteral(sourceSide) + ",");
                appendLine(builder, 4, "\"target_side\": " + pythonLiteral(targetSide) + ",");
                appendLine(builder, 4, "\"fluid\": " + pythonLiteral(fluidId) + ",");
                appendLine(builder, 4, "\"requested\": " + block.level() + ",");
                appendLine(builder, 4, "\"moved\": moved_" + index + ",");
                appendLine(builder, 3, "}, text=\"Moved matching fluid between two material I/O devices\")");
            }
            case READ_REDSTONE -> {
                final String variable = "redstone_" + index;
                final String apiName = fallbackDevice(block.deviceApiName());
                final String sideName = fallbackSide(block.sideName());
                appendLine(builder, 1, variable + " = device(" + pythonLiteral(apiName) + ")");
                appendLine(builder, 1, "if " + variable + " is None:");
                appendLine(builder, 2, "screen.print(" + pythonLiteral("Redstone device '" + apiName + "' is missing.") + ")");
                appendLine(builder, 1, "elif not " + variable + ".available():");
                appendLine(builder, 2, "screen.print(" + pythonLiteral("Redstone device '" + apiName + "' is unavailable right now.") + ")");
                appendLine(builder, 1, "else:");
                appendLine(builder, 2, variable + ".set_mode(\"input\")");
                appendLine(builder, 2, "level_" + index + " = " + variable + ".read(" + pythonLiteral(sideName) + ")");
                appendLine(builder, 2, "screen.show(\"Redstone input\", {");
                appendLine(builder, 3, "\"device\": " + variable + ".name(),");
                appendLine(builder, 3, "\"side\": " + pythonLiteral(sideName) + ",");
                appendLine(builder, 3, "\"level\": level_" + index + ",");
                appendLine(builder, 2, "}, text=\"Measured one redstone side\")");
            }
            case WRITE_REDSTONE -> {
                final String variable = "redstone_" + index;
                final String apiName = fallbackDevice(block.deviceApiName());
                final String sideName = fallbackSide(block.sideName());
                appendLine(builder, 1, variable + " = device(" + pythonLiteral(apiName) + ")");
                appendLine(builder, 1, "if " + variable + " is None:");
                appendLine(builder, 2, "screen.print(" + pythonLiteral("Redstone device '" + apiName + "' is missing.") + ")");
                appendLine(builder, 1, "elif not " + variable + ".available():");
                appendLine(builder, 2, "screen.print(" + pythonLiteral("Redstone device '" + apiName + "' is unavailable right now.") + ")");
                appendLine(builder, 1, "else:");
                appendLine(builder, 2, variable + ".set_mode(\"output\")");
                appendLine(builder, 2, "applied_" + index + " = " + variable + ".write(" + pythonLiteral(sideName) + ", " + block.level() + ")");
                appendLine(builder, 2, "screen.show(\"Redstone output\", {");
                appendLine(builder, 3, "\"device\": " + variable + ".name(),");
                appendLine(builder, 3, "\"side\": " + pythonLiteral(sideName) + ",");
                appendLine(builder, 3, "\"level\": applied_" + index + ",");
                appendLine(builder, 2, "}, text=\"Applied one redstone output level\")");
            }
            case ELSE_NEXT ->
                appendLine(builder, 1, "screen.print(" + pythonLiteral("Otherwise must follow a condition block and guard the next block.") + ")");
            case IF_WORLD_DAY_NEXT, IF_WORLD_NIGHT_NEXT, IF_WORLD_THUNDERING_NEXT,
                IF_WORLD_RAIN_LEVEL_AT_LEAST_NEXT, IF_WORLD_RAIN_LEVEL_GREATER_THAN_NEXT, IF_WORLD_RAIN_LEVEL_LESS_THAN_NEXT, IF_WORLD_RAIN_LEVEL_EQUALS_NEXT,
                IF_WORLD_TIME_WINDOW_NEXT, IF_WORLD_DAWN_NEXT, IF_WORLD_EVENING_NEXT,
                IF_WORLD_MOON_PHASE_AT_LEAST_NEXT, IF_WORLD_MOON_PHASE_GREATER_THAN_NEXT, IF_WORLD_MOON_PHASE_LESS_THAN_NEXT, IF_WORLD_MOON_PHASE_EQUALS_NEXT,
                IF_RAINING_NEXT, IF_DRY_NEXT,
                IF_REDSTONE_AT_LEAST_NEXT, IF_REDSTONE_GREATER_THAN_NEXT, IF_REDSTONE_LESS_THAN_NEXT, IF_REDSTONE_EQUALS_NEXT,
                IF_ITEM_COUNT_AT_LEAST_NEXT, IF_ITEM_COUNT_GREATER_THAN_NEXT, IF_ITEM_COUNT_LESS_THAN_NEXT, IF_ITEM_COUNT_EQUALS_NEXT,
                IF_FLUID_AMOUNT_AT_LEAST_NEXT, IF_FLUID_AMOUNT_GREATER_THAN_NEXT, IF_FLUID_AMOUNT_LESS_THAN_NEXT, IF_FLUID_AMOUNT_EQUALS_NEXT ->
                appendLine(builder, 1, "screen.print(" + pythonLiteral("Condition block '" + block.kind().label() + "' must guard the next block.") + ")");
        }
    }

    private static String indentBlock(final String blockText, final int extraLevels) {
        if (blockText == null || blockText.isEmpty() || extraLevels <= 0) {
            return blockText == null ? "" : blockText;
        }
        final StringBuilder builder = new StringBuilder();
        final String prefix = "    ".repeat(extraLevels);
        for (final String line : blockText.split("\\n")) {
            if (line.isEmpty()) {
                builder.append('\n');
            } else {
                builder.append(prefix).append(line).append('\n');
            }
        }
        return builder.toString();
    }

    private static void appendLine(final StringBuilder builder, final int indentLevel, final String line) {
        builder.append("    ".repeat(Math.max(0, indentLevel))).append(line).append('\n');
    }

    private static String fallbackText(final String text) {
        return text == null || text.isBlank() ? "Hello from XL Logic" : text;
    }

    private static String fallbackDevice(final String apiName) {
        return apiName == null || apiName.isBlank() ? "device_name" : apiName;
    }

    private static String fallbackSide(final String sideName) {
        return sideName == null || sideName.isBlank() ? "north" : sideName;
    }

    private static String fallbackTargetSide(final String sideName) {
        return sideName == null || sideName.isBlank() ? "south" : sideName;
    }

    private static String fallbackItemId(final String itemId) {
        return itemId == null || itemId.isBlank() ? DEFAULT_ITEM_ID : itemId;
    }

    private static String fallbackFluidId(final String fluidId) {
        return fluidId == null || fluidId.isBlank() ? DEFAULT_FLUID_ID : fluidId;
    }

    private static int worldTimeWindowEnd(final String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return DEFAULT_WORLD_TIME_WINDOW_END;
        }
        try {
            return clampWorldDayTime(Integer.parseInt(rawValue.trim()));
        } catch (NumberFormatException exception) {
            return DEFAULT_WORLD_TIME_WINDOW_END;
        }
    }

    private static int clampWorldDayTime(final int value) {
        return Math.max(0, Math.min(23_999, value));
    }

    private static String pythonLiteral(final String value) {
        final String safeValue = value == null ? "" : value;
        return '"' + safeValue
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "") + '"';
    }
}