package de.xllogic.client.nocode;

import de.xllogic.runtime.PythonExecutionContext;
import de.xllogic.runtime.PythonPeripheralBinding;
import java.util.List;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NoCodeScriptGeneratorTest {
    private static final String DEFAULT_SIDE = "north";
    private static final String CLOCK_MAIN = "clock_main";
    private static final String RAIN_SENSOR_MAIN = "rain_sensor_main";
    private static final String MATERIAL_IO_MAIN = "material_io_main";
    private static final String MATERIAL_IO_TARGET = "material_io_target";
    private static final String SOURCE_IO = "source_io";
    private static final String SINK_IO = "sink_io";
    private static final String REDSTONE_IO_MAIN = "redstone_io_main";
    private static final String IRON_INGOT = "minecraft:iron_ingot";

    @Test
    void generatedScriptCarriesRoundTripMetadata() {
        final NoCodeProgram program = new NoCodeProgram();
        program.blocks().add(new NoCodeBlock(NoCodeBlockKind.PRINT_TEXT, "Hello builder", "", DEFAULT_SIDE, 0));
        program.blocks().add(new NoCodeBlock(NoCodeBlockKind.SHOW_WORLD, "", "", DEFAULT_SIDE, 0));

        final String script = NoCodeScriptGenerator.generate(program);
        final NoCodeProgramCodec.DecodedProgram decodedProgram = NoCodeProgramCodec.decode(script);

        assertTrue(decodedProgram.foundMetadata(), "expected generated script metadata");
        assertTrue(decodedProgram.matchesGeneratedScript(), "expected the round-trip decode to match the generated script");
        assertFalse(decodedProgram.parseError(), "expected valid no-code metadata");
        assertTrue(script.contains("screen.print(\"Hello builder\")"), "expected the generated script to contain the text block");
        assertTrue(script.contains("screen.show(\"World\""), "expected the generated script to contain the world block");
    }

    @Test
    void decodeDetectsManualScriptChangesAfterGeneration() {
        final NoCodeProgram program = new NoCodeProgram();
        program.blocks().add(new NoCodeBlock(NoCodeBlockKind.PRINT_TEXT, "Original", "", DEFAULT_SIDE, 0));

        final String changedScript = NoCodeScriptGenerator.generate(program) + "\n# manual edit\n";
        final NoCodeProgramCodec.DecodedProgram decodedProgram = NoCodeProgramCodec.decode(changedScript);

        assertTrue(decodedProgram.foundMetadata(), "expected metadata even after a manual script edit");
        assertFalse(decodedProgram.matchesGeneratedScript(), "expected manual edits to mark the builder state as stale");
    }

    @Test
    void repeatProgramGeneratesRedstoneWriteLoop() {
        final NoCodeProgram program = new NoCodeProgram();
        program.setRepeat(true);
        program.setRepeatTicks(10);
        program.blocks().add(new NoCodeBlock(NoCodeBlockKind.WRITE_REDSTONE, "", "redstone_io", "east", 12));

        final String script = NoCodeScriptGenerator.generate(program);

        assertTrue(script.contains("yield from repeat(_xllogic_no_code_step, 10)"), "expected a repeat loop");
        assertTrue(script.contains("redstone_0.set_mode(\"output\")"), "expected write blocks to switch into output mode");
        assertTrue(script.contains("redstone_0.write(\"east\", 12)"), "expected the configured side and level in the generated script");
    }

    @Test
    void sensorAndMaterialBlocksGenerateExpectedPython() {
        final NoCodeProgram program = new NoCodeProgram();
        program.blocks().add(new NoCodeBlock(NoCodeBlockKind.SHOW_CLOCK, "", CLOCK_MAIN, DEFAULT_SIDE, 0));
        program.blocks().add(new NoCodeBlock(NoCodeBlockKind.SHOW_RAIN_SENSOR, "", RAIN_SENSOR_MAIN, DEFAULT_SIDE, 0));
        program.blocks().add(new NoCodeBlock(NoCodeBlockKind.COUNT_MATERIAL_ITEM, IRON_INGOT, MATERIAL_IO_MAIN, "west", 0));
        program.blocks().add(new NoCodeBlock(NoCodeBlockKind.MOVE_MATERIAL_ITEM, IRON_INGOT, MATERIAL_IO_MAIN, "west", "east", 64));

        final String script = NoCodeScriptGenerator.generate(program);

        assertTrue(script.contains("clock_0.day_time()"), "expected the clock block to read the device day time");
        assertTrue(script.contains("rain_sensor_1.is_raining()"), "expected the rain sensor block to check rain state");
        assertTrue(script.contains("material_2.count_item(\"west\", \"minecraft:iron_ingot\")"), "expected the material count block to use count_item");
        assertTrue(script.contains("material_3.transfer_item(\"west\", \"east\""), "expected the material move block to use transfer_item");
        assertTrue(script.contains(", 64)"), "expected item transfer amounts to keep values above redstone range");
    }

    @Test
    void metadataRoundTripKeepsMaterialTransferSides() {
        final NoCodeProgram program = new NoCodeProgram();
        program.blocks().add(new NoCodeBlock(NoCodeBlockKind.MOVE_MATERIAL_ITEM, "minecraft:redstone", MATERIAL_IO_MAIN, "down", "up", 2));

        final String script = NoCodeScriptGenerator.generate(program);
        final NoCodeProgramCodec.DecodedProgram decodedProgram = NoCodeProgramCodec.decode(script);

        assertTrue(decodedProgram.foundMetadata(), "expected generated script metadata");
        assertEquals("down", decodedProgram.program().blocks().getFirst().sideName(), "expected the stored source side");
        assertEquals("up", decodedProgram.program().blocks().getFirst().targetSideName(), "expected the stored target side");
        assertEquals(2, decodedProgram.program().blocks().getFirst().level(), "expected the stored transfer amount");
    }

    @Test
    void metadataRoundTripKeepsTargetMaterialTransferDevice() {
        final NoCodeProgram program = new NoCodeProgram();
        program.blocks().add(new NoCodeBlock(NoCodeBlockKind.MOVE_MATERIAL_ITEM_TO, "minecraft:redstone", MATERIAL_IO_MAIN, "down", MATERIAL_IO_TARGET, "up", 2));

        final String script = NoCodeScriptGenerator.generate(program);
        final NoCodeProgramCodec.DecodedProgram decodedProgram = NoCodeProgramCodec.decode(script);

        assertTrue(decodedProgram.foundMetadata(), "expected generated script metadata");
        assertEquals(MATERIAL_IO_MAIN, decodedProgram.program().blocks().getFirst().deviceApiName(), "expected the stored source device");
        assertEquals(MATERIAL_IO_TARGET, decodedProgram.program().blocks().getFirst().targetDeviceApiName(), "expected the stored target device");
        assertEquals("down", decodedProgram.program().blocks().getFirst().sideName(), "expected the stored source side");
        assertEquals("up", decodedProgram.program().blocks().getFirst().targetSideName(), "expected the stored target side");
        assertEquals(2, decodedProgram.program().blocks().getFirst().level(), "expected the stored transfer amount");
    }

    @Test
    void fluidBlocksGenerateExpectedPython() {
        final NoCodeProgram program = new NoCodeProgram();
        program.blocks().add(new NoCodeBlock(NoCodeBlockKind.SHOW_MATERIAL_FLUIDS, "", MATERIAL_IO_MAIN, "up", 0));
        program.blocks().add(new NoCodeBlock(NoCodeBlockKind.MOVE_MATERIAL_FLUID, "minecraft:water", MATERIAL_IO_MAIN, "up", "down", 1_000));

        final String script = NoCodeScriptGenerator.generate(program);

        assertTrue(script.contains("material_0.tanks(\"up\")"), "expected the fluid table block to inspect tanks on the selected side");
        assertTrue(script.contains("screen.table(\"Fluid tanks\""), "expected the fluid table block to render a table");
        assertTrue(script.contains("material_1.transfer_fluid(\"up\", \"down\""), "expected the fluid move block to use transfer_fluid");
        assertTrue(script.contains(", 1000)"), "expected fluid transfer amounts to keep bucket-sized values");
    }

    @Test
    void directMaterialTransferBlocksGenerateExpectedPython() {
        final NoCodeProgram program = new NoCodeProgram();
        program.blocks().add(new NoCodeBlock(NoCodeBlockKind.MOVE_MATERIAL_ITEM_TO, IRON_INGOT, MATERIAL_IO_MAIN, "west", MATERIAL_IO_TARGET, "east", 64));
        program.blocks().add(new NoCodeBlock(NoCodeBlockKind.MOVE_MATERIAL_FLUID_TO, "minecraft:water", MATERIAL_IO_MAIN, "up", MATERIAL_IO_TARGET, "down", 1_000));

        final String script = NoCodeScriptGenerator.generate(program);

        assertTrue(script.contains("material_0.transfer_item_to(\"material_io_target\", \"west\", \"east\""), "expected the direct item transfer block to use transfer_item_to");
        assertTrue(script.contains("target_material_0 = device(\"material_io_target\")"), "expected the generated script to resolve the target material I/O device");
        assertTrue(script.contains("material_1.transfer_fluid_to(\"material_io_target\", \"up\", \"down\""), "expected the direct fluid transfer block to use transfer_fluid_to");
        assertTrue(script.contains("\"target_device\": target_material_1.name()"), "expected the generated result view to include the target device");
    }

    @Test
    void logicGuardBlocksGenerateConditionalPython() {
        final NoCodeProgram program = new NoCodeProgram();
        program.blocks().add(new NoCodeBlock(NoCodeBlockKind.IF_RAINING_NEXT, "", RAIN_SENSOR_MAIN, DEFAULT_SIDE, 0));
        program.blocks().add(new NoCodeBlock(NoCodeBlockKind.WRITE_REDSTONE, "", REDSTONE_IO_MAIN, "east", 15));
        program.blocks().add(new NoCodeBlock(NoCodeBlockKind.IF_ITEM_COUNT_AT_LEAST_NEXT, IRON_INGOT, MATERIAL_IO_MAIN, "west", 32));
        program.blocks().add(new NoCodeBlock(NoCodeBlockKind.PRINT_TEXT, "Items ready", "", DEFAULT_SIDE, 0));

        final String script = NoCodeScriptGenerator.generate(program);

        assertTrue(script.contains("elif rain_sensor_0.is_raining():"), "expected the rain guard block to generate a rain check");
        assertTrue(script.contains("redstone_1.write(\"east\", 15)"), "expected the guarded redstone write block to be generated");
        assertTrue(script.contains("count_2 = material_2.count_item(\"west\", \"minecraft:iron_ingot\")"), "expected the item guard block to count items on the selected side");
        assertTrue(script.contains("if count_2 >= 32:"), "expected the item guard block to compare against its threshold");
        assertTrue(script.contains("screen.print(\"Items ready\")"), "expected the guarded print block to remain in the generated script");
        assertFalse(script.contains("Condition block '"), "expected valid guard blocks to guard the next block instead of falling back to an error message");
    }

    @Test
    void logicGuardBlocksSupportNestedConditions() {
        final NoCodeProgram program = new NoCodeProgram();
        program.blocks().add(new NoCodeBlock(NoCodeBlockKind.IF_DRY_NEXT, "", RAIN_SENSOR_MAIN, DEFAULT_SIDE, 0));
        program.blocks().add(new NoCodeBlock(NoCodeBlockKind.IF_REDSTONE_AT_LEAST_NEXT, "", REDSTONE_IO_MAIN, "south", 9));
        program.blocks().add(new NoCodeBlock(NoCodeBlockKind.PRINT_TEXT, "Dry and powered", "", DEFAULT_SIDE, 0));

        final String script = NoCodeScriptGenerator.generate(program);

        assertTrue(script.contains("if not rain_sensor_0.is_raining():"), "expected the dry guard block to invert the rain sensor condition");
        assertTrue(script.contains("level_1 = redstone_1.read(\"south\")"), "expected the nested redstone guard to read the selected side");
        assertTrue(script.contains("if level_1 >= 9:"), "expected the nested redstone guard to compare against its minimum level");
        assertTrue(script.contains("screen.print(\"Dry and powered\")"), "expected nested guard blocks to preserve the final action");
    }

    @Test
    void elseBranchesAndComparisonGuardsGenerateExpectedPython() {
        final NoCodeProgram program = new NoCodeProgram();
        program.blocks().add(new NoCodeBlock(NoCodeBlockKind.IF_REDSTONE_GREATER_THAN_NEXT, "", REDSTONE_IO_MAIN, "east", 9));
        program.blocks().add(new NoCodeBlock(NoCodeBlockKind.PRINT_TEXT, "Above threshold", "", DEFAULT_SIDE, 0));
        program.blocks().add(new NoCodeBlock(NoCodeBlockKind.ELSE_NEXT));
        program.blocks().add(new NoCodeBlock(NoCodeBlockKind.PRINT_TEXT, "Not above threshold", "", DEFAULT_SIDE, 0));
        program.blocks().add(new NoCodeBlock(NoCodeBlockKind.IF_ITEM_COUNT_LESS_THAN_NEXT, IRON_INGOT, MATERIAL_IO_MAIN, "west", 32));
        program.blocks().add(new NoCodeBlock(NoCodeBlockKind.PRINT_TEXT, "Need restock", "", DEFAULT_SIDE, 0));
        program.blocks().add(new NoCodeBlock(NoCodeBlockKind.IF_FLUID_AMOUNT_EQUALS_NEXT, "minecraft:water", MATERIAL_IO_MAIN, "up", 1_000));
        program.blocks().add(new NoCodeBlock(NoCodeBlockKind.PRINT_TEXT, "Exact bucket ready", "", DEFAULT_SIDE, 0));

        final String script = NoCodeScriptGenerator.generate(program);

        assertTrue(script.contains("if level_0 > 9:"), "expected the redstone comparison guard to use the greater-than operator");
        assertTrue(script.contains("else:"), "expected the guarded branch to generate an else clause");
        assertTrue(script.contains("screen.print(\"Not above threshold\")"), "expected the else branch action to be preserved");
        assertTrue(script.contains("if count_4 < 32:"), "expected the item comparison guard to use the less-than operator");
        assertTrue(script.contains("if amount_6 == 1000:"), "expected the fluid comparison guard to use the equality operator");
        assertFalse(script.contains("Otherwise must follow a condition block"), "expected a valid else block to be consumed by the previous guard");
    }

    @Test
    void worldStateGuardsGenerateExpectedPython() {
        final NoCodeProgram program = new NoCodeProgram();
        program.blocks().add(new NoCodeBlock(NoCodeBlockKind.IF_WORLD_DAY_NEXT));
        program.blocks().add(new NoCodeBlock(NoCodeBlockKind.PRINT_TEXT, "Daytime task", "", DEFAULT_SIDE, 0));
        program.blocks().add(new NoCodeBlock(NoCodeBlockKind.ELSE_NEXT));
        program.blocks().add(new NoCodeBlock(NoCodeBlockKind.PRINT_TEXT, "Night fallback", "", DEFAULT_SIDE, 0));
        program.blocks().add(new NoCodeBlock(NoCodeBlockKind.IF_WORLD_RAIN_LEVEL_GREATER_THAN_NEXT, "", "", DEFAULT_SIDE, 3));
        program.blocks().add(new NoCodeBlock(NoCodeBlockKind.PRINT_TEXT, "Heavy rain", "", DEFAULT_SIDE, 0));
        program.blocks().add(new NoCodeBlock(NoCodeBlockKind.IF_WORLD_TIME_WINDOW_NEXT, "2000", "", DEFAULT_SIDE, "south", 18000));
        program.blocks().add(new NoCodeBlock(NoCodeBlockKind.PRINT_TEXT, "Quiet hours", "", DEFAULT_SIDE, 0));

        final String script = NoCodeScriptGenerator.generate(program);

        assertTrue(script.contains("elif world.is_day():"), "expected the world day guard to use the world day helper");
        assertTrue(script.contains("screen.print(\"Night fallback\")"), "expected the else branch after the world day guard to be preserved");
        assertTrue(script.contains("world_rain_4 = world.rain_level()"), "expected the world rain-level guard to read the world rain level");
        assertTrue(script.contains("if world_rain_4 > 3:"), "expected the world rain-level guard to use the chosen comparison operator");
        assertTrue(script.contains("window_start_6 = 18000"), "expected the time-window guard to keep the configured start tick");
        assertTrue(script.contains("window_end_6 = 2000"), "expected the time-window guard to keep the configured end tick");
        assertTrue(script.contains("in_window_6 ="), "expected the time-window guard to generate a wrap-aware in-window expression");
    }

    @Test
    void extendedWorldStateGuardsGenerateExpectedPython() {
        final NoCodeProgram program = new NoCodeProgram();
        program.blocks().add(new NoCodeBlock(NoCodeBlockKind.IF_WORLD_THUNDERING_NEXT));
        program.blocks().add(new NoCodeBlock(NoCodeBlockKind.PRINT_TEXT, "Thunder routine", "", DEFAULT_SIDE, 0));
        program.blocks().add(new NoCodeBlock(NoCodeBlockKind.IF_WORLD_MOON_PHASE_EQUALS_NEXT, "", "", DEFAULT_SIDE, 4));
        program.blocks().add(new NoCodeBlock(NoCodeBlockKind.PRINT_TEXT, "New moon routine", "", DEFAULT_SIDE, 0));
        program.blocks().add(new NoCodeBlock(NoCodeBlockKind.IF_WORLD_DAWN_NEXT));
        program.blocks().add(new NoCodeBlock(NoCodeBlockKind.PRINT_TEXT, "Dawn routine", "", DEFAULT_SIDE, 0));
        program.blocks().add(new NoCodeBlock(NoCodeBlockKind.IF_WORLD_EVENING_NEXT));
        program.blocks().add(new NoCodeBlock(NoCodeBlockKind.PRINT_TEXT, "Evening routine", "", DEFAULT_SIDE, 0));

        final String script = NoCodeScriptGenerator.generate(program);

        assertTrue(script.contains("elif world.is_thundering():"), "expected the thunder guard to use the world thunder helper");
        assertTrue(script.contains("world_moon_phase_2 = world.moon_phase()"), "expected the moon phase guard to read the current world moon phase");
        assertTrue(script.contains("if world_moon_phase_2 == 4:"), "expected the moon phase guard to use the chosen comparison");
        assertTrue(script.contains("world_time_4 = world.day_time()"), "expected the dawn guard to inspect the current world time");
        assertTrue(script.contains("world_time_6 = world.day_time()"), "expected the evening guard to inspect the current world time");
        assertTrue(script.contains("23000"), "expected the dawn preset window to include the configured sunrise start");
        assertTrue(script.contains("14000"), "expected the evening preset window to include the configured sunset end");
    }

    @Test
    void templatesPrefillVisibleDevicesAndLoopingPrograms() {
        final PythonExecutionContext executionContext = new PythonExecutionContext(
                "builder_test",
                BlockPos.ZERO,
                List.of(
                        binding(CLOCK_MAIN, "clock"),
                        binding(RAIN_SENSOR_MAIN, "rain_sensor"),
                        binding(MATERIAL_IO_MAIN, "material_io"),
                        binding(REDSTONE_IO_MAIN, "redstone_io")
                ),
                null
        );

        final NoCodeProgram discovery = NoCodeBuilderTemplate.DEVICE_TOUR.create(executionContext);
        final NoCodeProgram clock = NoCodeBuilderTemplate.LIVE_CLOCK.create(executionContext);
        final NoCodeProgram items = NoCodeBuilderTemplate.STOCK_CHECK.create(executionContext);
        final NoCodeProgram fluids = NoCodeBuilderTemplate.TANK_WATCH.create(executionContext);
        final NoCodeProgram routedFluids = NoCodeBuilderTemplate.SOURCE_TO_SINK_FLUIDS.create(executionContext);
        final NoCodeProgram rainLamp = NoCodeBuilderTemplate.RAIN_LAMP.create(executionContext);
        final NoCodeProgram stockAlert = NoCodeBuilderTemplate.STOCK_ALERT.create(executionContext);
        final NoCodeProgram nightLamp = NoCodeBuilderTemplate.NIGHT_LAMP.create(executionContext);
        final NoCodeProgram heavyRain = NoCodeBuilderTemplate.HEAVY_RAIN_ALERT.create(executionContext);
        final NoCodeProgram thunder = NoCodeBuilderTemplate.THUNDER_ALERT.create(executionContext);
        final NoCodeProgram evening = NoCodeBuilderTemplate.EVENING_LIGHTS.create(executionContext);

        assertEquals(2, discovery.blocks().size(), "expected the discovery template to combine two starter blocks");
        assertEquals(NoCodeBlockKind.LIST_DEVICES, discovery.blocks().get(0).kind(), "expected the discovery template to start with device listing");
        assertTrue(clock.repeat(), "expected the clock template to loop by default");
        assertEquals(CLOCK_MAIN, clock.blocks().getFirst().deviceApiName(), "expected the clock template to use the visible clock");
        assertEquals(NoCodeBlockKind.COUNT_MATERIAL_ITEM, items.blocks().getFirst().kind(), "expected the items template to use the item counter block");
        assertEquals(MATERIAL_IO_MAIN, items.blocks().getFirst().deviceApiName(), "expected the items template to use the visible material I/O device");
        assertEquals(DEFAULT_SIDE, items.blocks().getFirst().sideName(), "expected the items template to default to the first visible side");
        assertEquals(NoCodeBlockKind.SHOW_MATERIAL_FLUIDS, fluids.blocks().getFirst().kind(), "expected the fluids template to use the fluid viewer block");
        assertEquals(NoCodeBlockKind.MOVE_MATERIAL_FLUID_TO, routedFluids.blocks().getFirst().kind(), "expected the routed fluid template to use the direct fluid transfer block");
        assertEquals(1_000, routedFluids.blocks().getFirst().level(), "expected the routed fluid template to use a bucket-sized amount");
        assertEquals(NoCodeBlockKind.IF_RAINING_NEXT, rainLamp.blocks().get(0).kind(), "expected the rain lamp template to start with a rain guard");
        assertEquals(RAIN_SENSOR_MAIN, rainLamp.blocks().get(0).deviceApiName(), "expected the rain lamp template to use the visible rain sensor");
        assertEquals(NoCodeBlockKind.ELSE_NEXT, rainLamp.blocks().get(2).kind(), "expected the rain lamp template to include an else branch");
        assertEquals(15, rainLamp.blocks().get(1).level(), "expected the rain lamp template to turn the lamp on in the then branch");
        assertEquals(0, rainLamp.blocks().get(3).level(), "expected the rain lamp template to turn the lamp off in the else branch");
        assertEquals(NoCodeBlockKind.IF_ITEM_COUNT_AT_LEAST_NEXT, stockAlert.blocks().get(0).kind(), "expected the stock alert template to start with an item threshold guard");
        assertEquals(64, stockAlert.blocks().get(0).level(), "expected the stock alert template to watch a practical threshold");
        assertEquals(NoCodeBlockKind.ELSE_NEXT, stockAlert.blocks().get(2).kind(), "expected the stock alert template to include an else branch");
        assertEquals(NoCodeBlockKind.IF_WORLD_NIGHT_NEXT, nightLamp.blocks().get(0).kind(), "expected the night lamp template to use a world-night guard");
        assertEquals(15, nightLamp.blocks().get(1).level(), "expected the night lamp template to turn the lamp on in the then branch");
        assertEquals(NoCodeBlockKind.IF_WORLD_RAIN_LEVEL_AT_LEAST_NEXT, heavyRain.blocks().get(0).kind(), "expected the heavy-rain template to use a world rain threshold guard");
        assertEquals(10, heavyRain.blocks().get(0).level(), "expected the heavy-rain template to start at a strong rain threshold");
        assertEquals(NoCodeBlockKind.IF_WORLD_THUNDERING_NEXT, thunder.blocks().get(0).kind(), "expected the thunder template to use the world thunder guard");
        assertEquals(NoCodeBlockKind.IF_WORLD_EVENING_NEXT, evening.blocks().get(0).kind(), "expected the evening template to use the preset evening window");
        assertEquals(REDSTONE_IO_MAIN, evening.blocks().get(1).deviceApiName(), "expected the evening template to use the visible redstone device");
    }

    @Test
    void sourceToSinkTemplatePrefersNamedMaterialIoDevices() {
        final PythonExecutionContext executionContext = new PythonExecutionContext(
                "builder_test",
                BlockPos.ZERO,
                List.of(
                        binding(SOURCE_IO, "material_io"),
                        binding(SINK_IO, "material_io"),
                        binding(MATERIAL_IO_MAIN, "material_io")
                ),
                null
        );

        final NoCodeProgram route = NoCodeBuilderTemplate.SOURCE_TO_SINK.create(executionContext);

        assertTrue(route.repeat(), "expected the source-to-sink template to loop by default");
        assertEquals(1, route.blocks().size(), "expected a single direct transfer block");
        assertEquals(NoCodeBlockKind.MOVE_MATERIAL_ITEM_TO, route.blocks().getFirst().kind(), "expected the route template to use the direct item transfer block");
        assertEquals(SOURCE_IO, route.blocks().getFirst().deviceApiName(), "expected the route template to prefer source_io as the source device");
        assertEquals(SINK_IO, route.blocks().getFirst().targetDeviceApiName(), "expected the route template to prefer sink_io as the target device");
        assertEquals(16, route.blocks().getFirst().level(), "expected the route template to use a practical transfer amount");
    }

    @Test
    void sourceToSinkFluidsTemplatePrefersNamedMaterialIoDevices() {
        final PythonExecutionContext executionContext = new PythonExecutionContext(
                "builder_test",
                BlockPos.ZERO,
                List.of(
                        binding(SOURCE_IO, "material_io"),
                        binding(SINK_IO, "material_io"),
                        binding(MATERIAL_IO_MAIN, "material_io")
                ),
                null
        );

        final NoCodeProgram route = NoCodeBuilderTemplate.SOURCE_TO_SINK_FLUIDS.create(executionContext);

        assertTrue(route.repeat(), "expected the source-to-sink fluid template to loop by default");
        assertEquals(1, route.blocks().size(), "expected a single direct fluid transfer block");
        assertEquals(NoCodeBlockKind.MOVE_MATERIAL_FLUID_TO, route.blocks().getFirst().kind(), "expected the route template to use the direct fluid transfer block");
        assertEquals(SOURCE_IO, route.blocks().getFirst().deviceApiName(), "expected the route template to prefer source_io as the source device");
        assertEquals(SINK_IO, route.blocks().getFirst().targetDeviceApiName(), "expected the route template to prefer sink_io as the target device");
        assertEquals(1_000, route.blocks().getFirst().level(), "expected the route template to use a bucket-sized transfer amount");
    }

    private static PythonPeripheralBinding binding(final String apiName, final String type) {
        return new PythonPeripheralBinding(
                apiName,
                apiName,
                type,
                BlockPos.ZERO,
                "0,0,0",
                0,
                "local",
                "",
                0,
                "",
                "",
                "",
                "",
                "",
                ""
        );
    }
}