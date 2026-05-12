package de.xllogic.client.editor;

import static org.junit.jupiter.api.Assertions.assertTrue;

import de.xllogic.runtime.PythonExecutionContext;
import de.xllogic.runtime.PythonPeripheralBinding;
import java.util.List;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class PythonSuggestionEngineTest {
    private final PythonSuggestionEngine engine = new PythonSuggestionEngine();

    @Test
    void directDeviceSideSuggestionsIncludeConfiguredAliases() {
        final PythonExecutionContext executionContext = executionContext(
                binding("inventoryconnector", "material_io", "", "", "", "source_buffer", "", "")
        );

        final List<String> labels = suggestionLabels("get_device(\"inventoryconnector\").transfer_item(\"", executionContext);

        assertTrue(labels.contains("south"), () -> "expected canonical side suggestion, got " + labels);
        assertTrue(labels.contains("source_buffer"), () -> "expected configured side alias suggestion, got " + labels);
    }

    @Test
    void routeSideSuggestionsUseTargetDeviceAliases() {
        final PythonExecutionContext executionContext = executionContext(
                binding("crafting_io", "crafting_io", "", "", "", "", "", ""),
                binding("source_io", "material_io", "", "", "", "input_bus", "", "")
        );

        final List<String> labels = suggestionLabels("craft = get_device(\"crafting_io\")\ncraft.set_route(\"source\", \"source_io\", \"", executionContext);

        assertTrue(labels.contains("input_bus"), () -> "expected route target alias suggestion, got " + labels);
    }

    @Test
    void memberSuggestionsExposeSideAliasesHelper() {
        final PythonExecutionContext executionContext = executionContext(
                binding("inventoryconnector", "material_io", "", "", "", "source_buffer", "", "")
        );

        final List<String> labels = suggestionLabels("connector = get_device(\"inventoryconnector\")\nconnector.si", executionContext);

        assertTrue(labels.contains("side_aliases"), () -> "expected side_aliases helper suggestion, got " + labels);
    }

    @Test
    void stateSuggestionsExposeSideAliasesKey() {
        final PythonExecutionContext executionContext = executionContext(
                binding("inventoryconnector", "material_io", "", "", "", "source_buffer", "", "")
        );

        final List<String> labels = suggestionLabels("connector = get_device(\"inventoryconnector\")\nstate = connector.state()\nstate[\"side", executionContext);

        assertTrue(labels.contains("side_aliases"), () -> "expected side_aliases state key suggestion, got " + labels);
    }

    @Test
    void globalSuggestionsExposeBeginnerHelpers() {
        final List<String> labels = suggestionLabels("sc", PythonExecutionContext.empty());

        assertTrue(labels.contains("screen"), () -> "expected beginner screen helper suggestion, got " + labels);
    }

    @Test
    void screenMemberSuggestionsExposeBeginnerMethods() {
        final List<String> labels = suggestionLabels("screen.sh", PythonExecutionContext.empty());

        assertTrue(labels.contains("show"), () -> "expected screen.show suggestion, got " + labels);
    }

    @Test
    void deviceHelperAssignmentsInferDeviceMembers() {
        final PythonExecutionContext executionContext = executionContext(
                binding("timekeeper", "clock", "", "", "", "", "", "")
        );

        final List<String> labels = suggestionLabels("clock_device = device(\"timekeeper\")\nclock_device.da", executionContext);

        assertTrue(labels.contains("day_time"), () -> "expected clock helper suggestions, got " + labels);
    }

    @Test
    void findDeviceSuggestionsExposeVisibleDeviceTypes() {
        final PythonExecutionContext executionContext = executionContext(
                binding("timekeeper", "clock", "", "", "", "", "", ""),
                binding("weather_sensor", "rain_sensor", "", "", "", "", "", "")
        );

        final List<String> labels = suggestionLabels("sensor = find_device(\"", executionContext);

        assertTrue(labels.contains("clock"), () -> "expected visible device type suggestion, got " + labels);
        assertTrue(labels.contains("rain_sensor"), () -> "expected visible device type suggestion, got " + labels);
    }

    private List<String> suggestionLabels(final String script, final PythonExecutionContext executionContext) {
        final TextDocument document = new TextDocument(script);
        final int line = document.getLineCount() - 1;
        document.setCursor(line, document.getLine(line).length());
        return this.engine.suggest(document, executionContext, false)
                .items()
                .stream()
                .map(PythonSuggestionEngine.SuggestionItem::label)
                .toList();
    }

    private static PythonExecutionContext executionContext(final PythonPeripheralBinding... bindings) {
        return new PythonExecutionContext("computer", BlockPos.ZERO, List.of(bindings), null);
    }

    private static PythonPeripheralBinding binding(final String apiName,
                                                   final String type,
                                                   final String downAlias,
                                                   final String upAlias,
                                                   final String northAlias,
                                                   final String southAlias,
                                                   final String westAlias,
                                                   final String eastAlias) {
        return new PythonPeripheralBinding(
                apiName,
                apiName,
                type,
                BlockPos.ZERO,
                BlockPos.ZERO.toShortString(),
                0,
                "local",
                "",
                0,
                downAlias,
                upAlias,
                northAlias,
                southAlias,
                westAlias,
                eastAlias
        );
    }
}
