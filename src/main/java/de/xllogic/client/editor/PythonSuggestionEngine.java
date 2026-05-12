package de.xllogic.client.editor;

import de.xllogic.runtime.PythonExecutionContext;
import de.xllogic.runtime.PythonPeripheralBinding;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.core.Direction;

public final class PythonSuggestionEngine {
    private static final String IDENTIFIER_PATTERN = "([A-Za-z_]\\w*)";
    private static final String QUOTED_STRING_PATTERN = "['\"]([^'\"]+)['\"]";
    private static final String DEVICE_LOOKUP_PATTERN = "(?:(?:computer_api\\.)?get_device|devices\\.get|device|require_device|network\\.get|network\\.require)";
    private static final String DEVICE_TYPE_LOOKUP_PATTERN = "(?:find_device|network\\.find)";
    private static final Pattern DEVICE_ASSIGNMENT = Pattern.compile("^\\s*" + IDENTIFIER_PATTERN + "\\s*=\\s*" + DEVICE_LOOKUP_PATTERN + "\\(\\s*" + QUOTED_STRING_PATTERN + "\\s*(?:,[^)]*)?\\)");
    private static final Pattern DEVICE_TYPE_ASSIGNMENT = Pattern.compile("^\\s*" + IDENTIFIER_PATTERN + "\\s*=\\s*" + DEVICE_TYPE_LOOKUP_PATTERN + "\\(\\s*" + QUOTED_STRING_PATTERN + "\\s*\\)");
    private static final Pattern DEVICE_INDEX_ASSIGNMENT = Pattern.compile("^\\s*" + IDENTIFIER_PATTERN + "\\s*=\\s*devices\\[\\s*" + QUOTED_STRING_PATTERN + "\\s*\\]");
    private static final Pattern ENDPOINT_ASSIGNMENT = Pattern.compile("^\\s*" + IDENTIFIER_PATTERN + "\\s*=\\s*(?:get_endpoint|peripherals\\.get)\\(\\s*" + QUOTED_STRING_PATTERN + "\\s*\\)");
    private static final Pattern ENDPOINT_INDEX_ASSIGNMENT = Pattern.compile("^\\s*" + IDENTIFIER_PATTERN + "\\s*=\\s*peripherals\\[\\s*" + QUOTED_STRING_PATTERN + "\\s*\\]");
    private static final Pattern SIMPLE_ALIAS = Pattern.compile("^\\s*" + IDENTIFIER_PATTERN + "\\s*=\\s*" + IDENTIFIER_PATTERN + "\\s*$");
    private static final Pattern DEVICE_METHOD_ASSIGNMENT = Pattern.compile("^\\s*" + IDENTIFIER_PATTERN + "\\s*=\\s*" + IDENTIFIER_PATTERN + "\\." + IDENTIFIER_PATTERN + "\\(");
    private static final Pattern ROUTE_NAME_ASSIGNMENT = Pattern.compile("(?:^|\\.)set_route\\(\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern DIRECT_DEVICE_CALL = Pattern.compile("^" + DEVICE_LOOKUP_PATTERN + "\\(\\s*" + QUOTED_STRING_PATTERN + "\\s*(?:,[^)]*)?\\)$");
    private static final Pattern DIRECT_DEVICE_INDEX = Pattern.compile("^devices\\[\\s*['\"]([^'\"]+)['\"]\\s*\\]$");
    private static final List<SuggestionItem> GLOBAL_SUGGESTIONS = buildGlobalSuggestions();
    private static final List<SuggestionItem> CANONICAL_SIDE_SUGGESTIONS = items(
            named("down", "side"),
            named("up", "side"),
            named("north", "side"),
            named("south", "side"),
            named("west", "side"),
            named("east", "side")
    );
    private static final List<SuggestionItem> GENERIC_DEVICE_MEMBERS = items(
            named("available", "Device API"),
            named("api_name", "Device API"),
            named("name", "Device API"),
            named("type", "Device API"),
            named("position", "Device API"),
            named("distance", "Device API"),
            named("network_scope", "Device API"),
            named("is_remote", "Device API"),
            named("bridge_name", "Device API"),
            named("bridge_group", "Device API"),
            named("remote_policy", "Device API"),
            named("remote_writable", "Device API"),
            named("summary", "Device API"),
            named("describe", "Device API"),
            named("rename", "Device API"),
                named("side_aliases", "Device API"),
            named("state", "Device API")
    );
    private static final List<SuggestionItem> DICT_MEMBERS = items(
            named("get", "dict"),
            named("items", "dict"),
            named("keys", "dict"),
            named("values", "dict")
    );
    private static final List<SuggestionItem> LIST_MEMBERS = items(
            named("append", "list"),
            named("clear", "list"),
            named("count", "list"),
            named("index", "list"),
            named("pop", "list")
    );
    private static final List<SuggestionItem> BASE_STATE_KEYS = items(
            named("api_name", "state key"),
            named("name", "state key"),
            named("type", "state key"),
            named("position", "state key"),
            named("distance", "state key"),
            named("scope", "state key"),
            named("remote", "state key"),
            named("bridge_name", "state key"),
            named("bridge_group", "state key"),
            named("remote_policy", "state key"),
            named("remote_writable", "state key"),
            named("online", "state key"),
            named("summary", "state key")
    );
        private static final Map<String, List<SuggestionItem>> STATIC_MEMBERS = Map.ofEntries(
            Map.entry("computer_api", items(
                    named("available", "Computer API"),
                    named("name", "Computer API"),
                    named("position", "Computer API"),
                    named("endpoint_count", "Computer API"),
                    named("network_summary", "Computer API"),
                    named("list_devices", "Computer API"),
                    named("get_device", "Computer API")
            )),
            Map.entry("world", items(
                    named("available", "World API"),
                    named("dimension", "World API"),
                    named("game_time", "World API"),
                    named("day_time", "World API"),
                    named("is_day", "World API"),
                    named("is_night", "World API"),
                    named("is_raining", "World API"),
                        named("rain_level", "World API"),
                    named("is_thundering", "World API"),
                    named("moon_phase", "World API"),
                    named("real_time", "World API")
            )),
            Map.entry("output", items(
                    named("line", "Output API"),
                    named("kv", "Output API"),
                    named("table", "Output API"),
                    named("plan_card", "Output API")
            )),
            Map.entry("screen", items(
                named("print", "Screen API"),
                named("show", "Screen API"),
                named("table", "Screen API"),
                named("plan", "Screen API"),
                named("line", "Screen API"),
                named("kv", "Screen API"),
                named("plan_card", "Screen API")
            )),
            Map.entry("network", items(
                named("names", "Network API"),
                named("all", "Network API"),
                named("get", "Network API"),
                named("require", "Network API"),
                named("find", "Network API"),
                named("types", "Network API")
            )),
            Map.entry("devices", DICT_MEMBERS),
            Map.entry("peripherals", DICT_MEMBERS),
            Map.entry("computer", DICT_MEMBERS),
            Map.entry("endpoints", LIST_MEMBERS),
            Map.entry("device_names", LIST_MEMBERS),
            Map.entry("endpoint_names", LIST_MEMBERS)
    );
    private static final Map<String, List<SuggestionItem>> DEVICE_TYPE_MEMBERS = Map.ofEntries(
            Map.entry("redstone_io", items(
                    named("get_mode", "redstone_io"),
                    named("set_mode", "redstone_io"),
                    named("read", "redstone_io"),
                    named("write", "redstone_io"),
                    named("channel", "redstone_io"),
                    named("set_channel", "redstone_io"),
                    named("levels", "redstone_io"),
                    named("channels", "redstone_io")
            )),
            Map.entry("light_sensor", items(named("light_level", "light_sensor"))),
            Map.entry("rain_sensor", items(
                    named("is_raining", "rain_sensor"),
                    named("rain_level", "rain_sensor")
            )),
            Map.entry("clock", items(
                    named("game_time", "clock"),
                    named("day_time", "clock"),
                    named("real_time", "clock")
            )),
            Map.entry("material_io", items(
                    named("get_mode", "material_io"),
                    named("set_mode", "material_io"),
                    named("item_input_enabled", "material_io"),
                    named("set_item_input_enabled", "material_io"),
                    named("item_output_enabled", "material_io"),
                    named("set_item_output_enabled", "material_io"),
                    named("fluid_input_enabled", "material_io"),
                    named("set_fluid_input_enabled", "material_io"),
                    named("fluid_output_enabled", "material_io"),
                    named("set_fluid_output_enabled", "material_io"),
                    named("inventory_size", "material_io"),
                    named("stack", "material_io"),
                    named("inventory", "material_io"),
                    named("count_item", "material_io"),
                    named("transfer_item", "material_io"),
                        named("transfer_item_to", "material_io"),
                    named("tank_count", "material_io"),
                    named("tank", "material_io"),
                    named("tanks", "material_io"),
                        named("transfer_fluid", "material_io"),
                        named("transfer_fluid_to", "material_io")
            )),
            Map.entry("crafting_io", items(
                    named("grid_width", "crafting_io"),
                    named("grid_height", "crafting_io"),
                    named("set_grid_size", "crafting_io"),
                    named("grid_slot_count", "crafting_io"),
                    named("grid_slot", "crafting_io"),
                    named("grid", "crafting_io"),
                    named("set_grid_slot", "crafting_io"),
                    named("clear_grid", "crafting_io"),
                    named("route_count", "crafting_io"),
                    named("route", "crafting_io"),
                    named("routes", "crafting_io"),
                    named("set_route", "crafting_io"),
                    named("clear_route", "crafting_io"),
                    named("clear_routes", "crafting_io"),
                    named("window_origin", "crafting_io"),
                    named("set_window_origin", "crafting_io"),
                    named("linked_cpu", "crafting_io"),
                    named("set_linked_cpu", "crafting_io"),
                    named("material_input_device", "crafting_io"),
                    named("set_material_input_device", "crafting_io"),
                    named("material_input_side", "crafting_io"),
                    named("set_material_input_side", "crafting_io"),
                    named("material_output_device", "crafting_io"),
                    named("set_material_output_device", "crafting_io"),
                    named("material_output_side", "crafting_io"),
                    named("set_material_output_side", "crafting_io"),
                    named("apply_recipe_window", "crafting_io"),
                    named("linked_preview", "crafting_io"),
                    named("craft_linked", "crafting_io"),
                    named("plan_step_count", "crafting_io"),
                    named("plan_step", "crafting_io"),
                    named("plan", "crafting_io"),
                    named("append_plan_step", "crafting_io"),
                    named("set_plan_step", "crafting_io"),
                    named("remove_plan_step", "crafting_io"),
                    named("clear_plan", "crafting_io"),
                    named("rebuild_plan", "crafting_io"),
                    named("craft_plan", "crafting_io")
            )),
            Map.entry("crafting_cpu", items(
                    named("recipe_slot_count", "crafting_cpu"),
                    named("recipe_slot", "crafting_cpu"),
                    named("recipe", "crafting_cpu"),
                    named("set_recipe_slot", "crafting_cpu"),
                    named("clear_recipe", "crafting_cpu"),
                    named("preview", "crafting_cpu"),
                    named("craft", "crafting_cpu"),
                    named("craft_queued", "crafting_cpu"),
                    named("is_busy", "crafting_cpu"),
                    named("set_busy", "crafting_cpu"),
                    named("queued_jobs", "crafting_cpu"),
                    named("set_queued_jobs", "crafting_cpu")
            )),
            Map.entry("xlapi_block", items(
                    named("uplink_group", "xlapi_block"),
                    named("set_uplink_group", "xlapi_block"),
                    named("relay_enabled", "xlapi_block"),
                    named("set_relay_enabled", "xlapi_block"),
                    named("forwarded_messages", "xlapi_block"),
                    named("remote_computer_count", "xlapi_block"),
                    named("remote_computers", "xlapi_block"),
                    named("inbox_count", "xlapi_block"),
                    named("peek_messages", "xlapi_block"),
                    named("poll_messages", "xlapi_block"),
                    named("send_message", "xlapi_block"),
                    named("send_command", "xlapi_block"),
                    named("request_status", "xlapi_block"),
                    named("ping", "xlapi_block"),
                    named("request_devices", "xlapi_block"),
                    named("request_runtime", "xlapi_block"),
                    named("peek_responses", "xlapi_block"),
                    named("poll_responses", "xlapi_block")
            ))
    );
    private static final Map<String, List<SuggestionItem>> STATE_KEY_MEMBERS = Map.ofEntries(
                Map.entry("redstone_io", items(named("mode", "state key"), named("side_aliases", "state key"), named("levels", "state key"), named("channels", "state key"))),
            Map.entry("light_sensor", items(named("light_level", "state key"))),
            Map.entry("rain_sensor", items(named("raining", "state key"), named("rain_level", "state key"))),
            Map.entry("clock", items(named("game_time", "state key"), named("day_time", "state key"), named("real_time", "state key"))),
            Map.entry("material_io", items(
                    named("mode", "state key"),
                    named("side_aliases", "state key"),
                    named("item_input_enabled", "state key"),
                    named("item_output_enabled", "state key"),
                    named("fluid_input_enabled", "state key"),
                    named("fluid_output_enabled", "state key"),
                    named("item_slot_counts", "state key"),
                    named("fluid_tank_counts", "state key")
            )),
            Map.entry("crafting_io", items(
                    named("grid_width", "state key"),
                    named("grid_height", "state key"),
                    named("window", "state key"),
                    named("linked_cpu", "state key"),
                    named("material_input_device", "state key"),
                    named("material_input_side", "state key"),
                    named("material_output_device", "state key"),
                    named("material_output_side", "state key"),
                    named("routes", "state key"),
                    named("linked_preview", "state key"),
                    named("plan", "state key"),
                    named("recipe", "state key")
            )),
                Map.entry("crafting_cpu", items(named("busy", "state key"), named("side_aliases", "state key"), named("queued_jobs", "state key"), named("preview", "state key"), named("recipe", "state key"))),
            Map.entry("xlapi_block", items(named("uplink_group", "state key"), named("relay_enabled", "state key"), named("forwarded_messages", "state key"), named("remote_computers", "state key"), named("inbox_count", "state key")))
    );
    private static final Map<String, List<SuggestionItem>> STATIC_SCHEMAS = Map.of(
            "computer", items(named("name", "computer key"), named("position", "computer key"), named("endpoint_count", "computer key")),
            "endpoint", items(
                    named("api_name", "endpoint key"),
                    named("name", "endpoint key"),
                    named("type", "endpoint key"),
                    named("position", "endpoint key"),
                    named("distance", "endpoint key"),
                    named("scope", "endpoint key"),
                    named("remote", "endpoint key"),
                    named("bridge_name", "endpoint key"),
                    named("bridge_group", "endpoint key")
            ),
            "route", items(named("index", "route key"), named("name", "route key"), named("device", "route key"), named("side", "route key")),
            "window", items(named("x", "window key"), named("y", "window key")),
            "preview", items(named("recipe_id", "preview key"), named("result_item", "preview key"), named("result_count", "preview key")),
            "slot", items(named("slot", "slot key"), named("item", "slot key"), named("count", "slot key")),
            "tank", items(named("tank", "tank key"), named("fluid", "tank key"), named("amount", "tank key")),
                "side_aliases", items(
                    named("down", "side alias key"),
                    named("up", "side alias key"),
                    named("north", "side alias key"),
                    named("south", "side alias key"),
                    named("west", "side alias key"),
                    named("east", "side alias key")
                ),
            "plan_step", items(
                    named("index", "plan key"),
                    named("x", "plan key"),
                    named("y", "plan key"),
                    named("crafts", "plan key"),
                    named("input_route", "plan key"),
                    named("output_route", "plan key"),
                    named("preview", "plan key")
            )
    );

    public SuggestionSession suggest(final TextDocument document, final PythonExecutionContext executionContext, final boolean forceAll) {
        final int lineIndex = document.getCursorLine();
        final int cursorColumn = document.getCursorColumn();
        final String line = document.getLine(lineIndex);
        final ScriptContext scriptContext = buildScriptContext(document, executionContext, lineIndex);

        final StringContext stringContext = this.stringContext(line, cursorColumn, executionContext, scriptContext);
        if (stringContext != null) {
            return this.sessionFor(lineIndex, stringContext.replaceStartColumn(), cursorColumn, stringContext.prefix(), stringContext.candidates(), true);
        }

        final MemberContext memberContext = this.memberContext(line, cursorColumn, scriptContext.variableTypes());
        if (memberContext != null) {
            return this.sessionFor(lineIndex, memberContext.replaceStartColumn(), cursorColumn, memberContext.prefix(), memberContext.candidates(), true);
        }

        final IdentifierContext identifierContext = this.identifierContext(line, cursorColumn);
        if (identifierContext == null) {
            if (!forceAll) {
                return SuggestionSession.empty(lineIndex, cursorColumn);
            }
            return this.sessionFor(lineIndex, cursorColumn, cursorColumn, "", GLOBAL_SUGGESTIONS, true);
        }

        final boolean allowEmptyPrefix = forceAll || !identifierContext.prefix().isEmpty();
        if (!allowEmptyPrefix) {
            return SuggestionSession.empty(lineIndex, cursorColumn);
        }
        return this.sessionFor(lineIndex, identifierContext.startColumn(), cursorColumn, identifierContext.prefix(), GLOBAL_SUGGESTIONS, allowEmptyPrefix);
    }

    private SuggestionSession sessionFor(final int lineIndex, final int replaceStartColumn, final int replaceEndColumn, final String prefix,
                                         final List<SuggestionItem> candidates, final boolean allowEmptyPrefix) {
        final List<SuggestionItem> filtered = filterCandidates(candidates, prefix, allowEmptyPrefix);
        if (filtered.isEmpty()) {
            return SuggestionSession.empty(lineIndex, replaceEndColumn);
        }
        return new SuggestionSession(lineIndex, replaceStartColumn, replaceEndColumn, prefix, filtered);
    }

    private StringContext stringContext(final String line, final int cursorColumn, final PythonExecutionContext executionContext, final ScriptContext scriptContext) {
        final QuoteContext quoteContext = this.quoteContext(line, cursorColumn);
        if (quoteContext == null) {
            return null;
        }

        final int replaceStartColumn = quoteContext.quoteStart() + 1;
        final List<SuggestionItem> indexCandidates = this.indexCandidates(line, quoteContext.quoteStart(), executionContext, scriptContext);
        if (!indexCandidates.isEmpty()) {
            return new StringContext(replaceStartColumn, quoteContext.content(), indexCandidates);
        }

        final CallContext callContext = this.callContext(line, quoteContext.quoteStart());
        if (callContext == null) {
            return null;
        }

        final List<SuggestionItem> callCandidates = this.callCandidates(callContext, line, quoteContext.quoteStart(), executionContext, scriptContext);
        if (callCandidates.isEmpty()) {
            return null;
        }
        return new StringContext(replaceStartColumn, quoteContext.content(), callCandidates);
    }

    private List<SuggestionItem> indexCandidates(final String line, final int quoteStart, final PythonExecutionContext executionContext, final ScriptContext scriptContext) {
        final int bracketIndex = skipWhitespaceLeft(line, quoteStart - 1);
        if (bracketIndex < 0 || line.charAt(bracketIndex) != '[') {
            return List.of();
        }

        final String expression = this.tailExpression(line.substring(0, bracketIndex));
        if (expression.isEmpty()) {
            return List.of();
        }

        if ("devices".equals(expression)) {
            return this.deviceNameSuggestions(executionContext, null);
        }
        if ("peripherals".equals(expression)) {
            return this.endpointNameSuggestions(executionContext);
        }
        return this.schemaCandidatesForExpression(expression, scriptContext);
    }

    private List<SuggestionItem> callCandidates(final CallContext callContext,
                                                final String line,
                                                final int quoteStart,
                                                final PythonExecutionContext executionContext,
                                                final ScriptContext scriptContext) {
        final String functionName = callContext.functionName();
        final int argumentIndex = callContext.argumentIndex();

        if ("get".equals(functionName)) {
            if ("devices".equals(callContext.qualifier())) {
                return this.deviceNameSuggestions(executionContext, null);
            }
            if ("peripherals".equals(callContext.qualifier())) {
                return this.endpointNameSuggestions(executionContext);
            }
            return this.schemaCandidatesForExpression(callContext.qualifier(), scriptContext);
        }

        if ("network".equals(callContext.qualifier())) {
            if (("get".equals(functionName) || "require".equals(functionName)) && argumentIndex == 0) {
                return this.deviceNameSuggestions(executionContext, null);
            }
            if (("find".equals(functionName) || "all".equals(functionName) || "names".equals(functionName)) && argumentIndex == 0) {
                return this.deviceTypeSuggestions(executionContext);
            }
        }

        if ("get_device".equals(functionName) && argumentIndex == 0) {
            return this.deviceNameSuggestions(executionContext, null);
        }
        if (("device".equals(functionName) || "require_device".equals(functionName)) && argumentIndex == 0) {
            return this.deviceNameSuggestions(executionContext, null);
        }
        if ("get_endpoint".equals(functionName) && argumentIndex == 0) {
            return this.endpointNameSuggestions(executionContext);
        }
        if (("find_device".equals(functionName) || "devices_by_type".equals(functionName) || "list_device_names".equals(functionName)) && argumentIndex == 0) {
            return this.deviceTypeSuggestions(executionContext);
        }
        if ("set_linked_cpu".equals(functionName) && argumentIndex == 0) {
            return this.deviceNameSuggestions(executionContext, "crafting_cpu");
        }
        if (("transfer_item_to".equals(functionName) || "transfer_fluid_to".equals(functionName)) && argumentIndex == 0) {
            return this.deviceNameSuggestions(executionContext, "material_io");
        }
        if (("set_material_input_device".equals(functionName) || "set_material_output_device".equals(functionName) || "set_route".equals(functionName) && argumentIndex == 1)
                && argumentIndex >= 0) {
            return this.deviceNameSuggestions(executionContext, null);
        }
        if (this.routeArgument(functionName, argumentIndex)) {
            return this.routeSuggestions(scriptContext.routeNames());
        }
        if (this.sideArgument(functionName, argumentIndex)) {
            return this.sideSuggestions(callContext, line, quoteStart, executionContext, scriptContext);
        }
        return List.of();
    }

    private boolean routeArgument(final String functionName, final int argumentIndex) {
        return switch (functionName) {
            case "clear_route" -> argumentIndex == 0;
            case "set_route" -> argumentIndex == 0;
            case "append_plan_step" -> argumentIndex == 3 || argumentIndex == 4;
            case "set_plan_step" -> argumentIndex == 4 || argumentIndex == 5;
            default -> false;
        };
    }

    private boolean sideArgument(final String functionName, final int argumentIndex) {
        return switch (functionName) {
            case "read", "channel", "inventory_size", "inventory", "tank_count", "tanks", "set_material_input_side", "set_material_output_side" -> argumentIndex == 0;
            case "write", "set_channel", "stack", "count_item", "tank" -> argumentIndex == 0;
            case "craft", "craft_queued", "transfer_item", "transfer_fluid" -> argumentIndex == 0 || argumentIndex == 1;
            case "transfer_item_to", "transfer_fluid_to" -> argumentIndex == 1 || argumentIndex == 2;
            case "set_route" -> argumentIndex == 2;
            default -> false;
        };
    }

    private MemberContext memberContext(final String line, final int cursorColumn, final Map<String, String> variableTypes) {
        int prefixStart = cursorColumn;
        while (prefixStart > 0 && isIdentifierPart(line.charAt(prefixStart - 1))) {
            prefixStart--;
        }

        if (prefixStart == 0 || line.charAt(prefixStart - 1) != '.') {
            return null;
        }

        int objectEnd = prefixStart - 1;
        int objectStart = objectEnd;
        while (objectStart > 0 && isIdentifierPart(line.charAt(objectStart - 1))) {
            objectStart--;
        }
        if (objectStart == objectEnd) {
            return null;
        }

        final String objectName = line.substring(objectStart, objectEnd);
        final List<SuggestionItem> candidates = this.memberCandidates(objectName, variableTypes);
        if (candidates.isEmpty()) {
            return null;
        }

        return new MemberContext(prefixStart, line.substring(prefixStart, cursorColumn), candidates);
    }

    private IdentifierContext identifierContext(final String line, final int cursorColumn) {
        int start = cursorColumn;
        while (start > 0 && isIdentifierPart(line.charAt(start - 1))) {
            start--;
        }
        if (start == cursorColumn && (cursorColumn == 0 || isIdentifierPart(line.charAt(Math.max(0, cursorColumn - 1))))) {
            return null;
        }
        return new IdentifierContext(start, line.substring(start, cursorColumn));
    }

    private List<SuggestionItem> memberCandidates(final String objectName, final Map<String, String> variableTypes) {
        if (STATIC_MEMBERS.containsKey(objectName)) {
            return STATIC_MEMBERS.get(objectName);
        }

        final String deviceType = variableTypes.get(objectName);
        if (deviceType == null) {
            return List.of();
        }

        final ArrayList<SuggestionItem> items = new ArrayList<>(GENERIC_DEVICE_MEMBERS);
        items.addAll(DEVICE_TYPE_MEMBERS.getOrDefault(deviceType, List.of()));
        return deduplicate(items);
    }

    private List<SuggestionItem> schemaCandidatesForExpression(final String expression, final ScriptContext scriptContext) {
        final String trimmedExpression = expression.trim();
        if (trimmedExpression.isEmpty()) {
            return List.of();
        }

        if (STATIC_SCHEMAS.containsKey(trimmedExpression)) {
            return STATIC_SCHEMAS.get(trimmedExpression);
        }
        if (scriptContext.variableSchemas().containsKey(trimmedExpression)) {
            return this.schemaCandidatesForSchema(scriptContext.variableSchemas().get(trimmedExpression));
        }

        if (trimmedExpression.startsWith("get_endpoint(") || trimmedExpression.startsWith("peripherals.get(") || trimmedExpression.startsWith("peripherals[")) {
            return STATIC_SCHEMAS.get("endpoint");
        }

        final Matcher methodMatcher = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\.([A-Za-z_][A-Za-z0-9_]*)\\([^)]*\\)$").matcher(trimmedExpression);
        if (methodMatcher.find()) {
            final String objectName = methodMatcher.group(1);
            final String methodName = methodMatcher.group(2);
            final String deviceType = scriptContext.variableTypes().get(objectName);
            final String schema = this.schemaForDeviceMethod(deviceType, methodName);
            if (schema != null) {
                return this.schemaCandidatesForSchema(schema);
            }
        }

        if (trimmedExpression.startsWith("get_device(")
            || trimmedExpression.startsWith("devices.get(")
            || trimmedExpression.startsWith("device(")
            || trimmedExpression.startsWith("require_device(")
            || trimmedExpression.startsWith("network.get(")
            || trimmedExpression.startsWith("network.require(")
            || trimmedExpression.startsWith("find_device(")
            || trimmedExpression.startsWith("network.find(")) {
            return this.schemaCandidatesForSchema("device_state:any");
        }
        return List.of();
    }

    private List<SuggestionItem> schemaCandidatesForSchema(final String schema) {
        if (schema == null || schema.isBlank()) {
            return List.of();
        }
        if (schema.startsWith("device_state:")) {
            final String deviceType = schema.substring("device_state:".length());
            final ArrayList<SuggestionItem> items = new ArrayList<>(BASE_STATE_KEYS);
            if ("any".equals(deviceType)) {
                for (final List<SuggestionItem> stateSuggestions : STATE_KEY_MEMBERS.values()) {
                    items.addAll(stateSuggestions);
                }
            } else {
                items.addAll(STATE_KEY_MEMBERS.getOrDefault(deviceType, List.of()));
            }
            return deduplicate(items);
        }
        return STATIC_SCHEMAS.getOrDefault(schema, List.of());
    }

    private String schemaForDeviceMethod(final String deviceType, final String methodName) {
        if (methodName == null) {
            return null;
        }
        return switch (methodName) {
            case "state" -> "device_state:" + (deviceType == null ? "any" : deviceType);
            case "side_aliases" -> "side_aliases";
            case "route" -> "route";
            case "window_origin" -> "window";
            case "linked_preview", "preview" -> "preview";
            case "grid_slot", "stack", "recipe_slot" -> "slot";
            case "tank" -> "tank";
            case "plan_step" -> "plan_step";
            default -> null;
        };
    }

    private CallContext callContext(final String line, final int quoteStart) {
        final int openParen = this.findEnclosingOpenParen(line, quoteStart - 1);
        if (openParen < 0) {
            return null;
        }

        final String callee = this.tailExpression(line.substring(0, openParen));
        if (callee.isBlank()) {
            return null;
        }

        final String functionName = this.trailingIdentifier(callee);
        if (functionName.isBlank()) {
            return null;
        }
        final String qualifier = callee.length() == functionName.length() ? "" : callee.substring(0, callee.length() - functionName.length() - 1);
        final int argumentIndex = this.argumentIndex(line, openParen + 1, quoteStart);
        return new CallContext(callee, qualifier, functionName, argumentIndex, openParen);
    }

    private int findEnclosingOpenParen(final String line, final int fromIndex) {
        int parenDepth = 0;
        int bracketDepth = 0;
        int braceDepth = 0;
        for (int index = fromIndex; index >= 0; index--) {
            final char current = line.charAt(index);
            switch (current) {
                case ')' -> parenDepth++;
                case ']' -> bracketDepth++;
                case '}' -> braceDepth++;
                case '(' -> {
                    if (parenDepth == 0 && bracketDepth == 0 && braceDepth == 0) {
                        return index;
                    }
                    parenDepth = Math.max(0, parenDepth - 1);
                }
                case '[' -> bracketDepth = Math.max(0, bracketDepth - 1);
                case '{' -> braceDepth = Math.max(0, braceDepth - 1);
                default -> {
                }
            }
        }
        return -1;
    }

    private int argumentIndex(final String line, final int startIndex, final int endIndex) {
        int argumentIndex = 0;
        int parenDepth = 0;
        int bracketDepth = 0;
        int braceDepth = 0;
        boolean inString = false;
        char delimiter = 0;
        boolean escaped = false;

        for (int index = startIndex; index < endIndex; index++) {
            final char current = line.charAt(index);
            if (inString) {
                if (current == delimiter && !escaped) {
                    inString = false;
                }
                escaped = current == '\\' && !escaped;
                if (current != '\\') {
                    escaped = false;
                }
                continue;
            }

            if (current == '\'' || current == '"') {
                inString = true;
                delimiter = current;
                escaped = false;
                continue;
            }

            switch (current) {
                case '(' -> parenDepth++;
                case ')' -> parenDepth--;
                case '[' -> bracketDepth++;
                case ']' -> bracketDepth--;
                case '{' -> braceDepth++;
                case '}' -> braceDepth--;
                case ',' -> {
                    if (parenDepth == 0 && bracketDepth == 0 && braceDepth == 0) {
                        argumentIndex++;
                    }
                }
                default -> {
                }
            }
        }
        return argumentIndex;
    }

    private String trailingIdentifier(final String expression) {
        int end = expression.length();
        while (end > 0 && !isIdentifierPart(expression.charAt(end - 1))) {
            end--;
        }
        int start = end;
        while (start > 0 && isIdentifierPart(expression.charAt(start - 1))) {
            start--;
        }
        return expression.substring(start, end);
    }

    private String tailExpression(final String source) {
        int end = source.length();
        while (end > 0 && Character.isWhitespace(source.charAt(end - 1))) {
            end--;
        }

        int start = end;
        while (start > 0 && this.expressionChar(source.charAt(start - 1))) {
            start--;
        }
        return source.substring(start, end).trim();
    }

    private boolean expressionChar(final char value) {
        return Character.isLetterOrDigit(value)
                || value == '_'
                || value == '.'
                || value == '('
                || value == ')'
                || value == '['
                || value == ']'
                || value == '\''
                || value == '"'
                || value == ',';
    }

    private QuoteContext quoteContext(final String line, final int cursorColumn) {
        char delimiter = 0;
        int quoteStart = -1;
        boolean escaped = false;
        for (int index = 0; index < Math.min(cursorColumn, line.length()); index++) {
            final char current = line.charAt(index);
            if (delimiter == 0) {
                if ((current == '\'' || current == '"') && !escaped) {
                    delimiter = current;
                    quoteStart = index;
                }
            } else if (current == delimiter && !escaped) {
                delimiter = 0;
                quoteStart = -1;
            }

            if (current == '\\' && !escaped) {
                escaped = true;
            } else {
                escaped = false;
            }
        }

        if (delimiter == 0 || quoteStart < 0) {
            return null;
        }
        return new QuoteContext(quoteStart, line.substring(quoteStart + 1, Math.min(cursorColumn, line.length())));
    }

    private List<SuggestionItem> sideSuggestions(final CallContext callContext,
                                                 final String line,
                                                 final int quoteStart,
                                                 final PythonExecutionContext executionContext,
                                                 final ScriptContext scriptContext) {
        final ArrayList<SuggestionItem> suggestions = new ArrayList<>(CANONICAL_SIDE_SUGGESTIONS);
        for (final PythonPeripheralBinding binding : this.relevantSideBindings(callContext, line, quoteStart, executionContext, scriptContext)) {
            suggestions.addAll(this.sideAliasSuggestions(binding));
        }
        return deduplicate(suggestions);
    }

    private List<PythonPeripheralBinding> relevantSideBindings(final CallContext callContext,
                                                               final String line,
                                                               final int quoteStart,
                                                               final PythonExecutionContext executionContext,
                                                               final ScriptContext scriptContext) {
        final LinkedHashSet<PythonPeripheralBinding> bindings = new LinkedHashSet<>();
        final PythonPeripheralBinding qualifierBinding = this.bindingForExpression(callContext.qualifier(), executionContext, scriptContext);

        switch (callContext.functionName()) {
            case "set_route" -> {
                final String routeDeviceApi = this.literalStringArgument(line, callContext, quoteStart, 1);
                final PythonPeripheralBinding routeBinding = executionContext.peripheral(routeDeviceApi);
                if (routeBinding != null) {
                    bindings.add(routeBinding);
                }
                if (bindings.isEmpty()) {
                    bindings.addAll(this.sideCapableBindings(executionContext, null));
                }
            }
            case "transfer_item_to", "transfer_fluid_to" -> {
                if (callContext.argumentIndex() == 2) {
                    final String targetDeviceApi = this.literalStringArgument(line, callContext, quoteStart, 0);
                    final PythonPeripheralBinding targetBinding = executionContext.peripheral(targetDeviceApi);
                    if (targetBinding != null) {
                        bindings.add(targetBinding);
                    }
                } else if (qualifierBinding != null) {
                    bindings.add(qualifierBinding);
                }
                if (bindings.isEmpty()) {
                    bindings.addAll(this.sideCapableBindings(executionContext, "material_io"));
                }
            }
            case "set_material_input_side", "set_material_output_side" -> bindings.addAll(this.sideCapableBindings(executionContext, "material_io"));
            default -> {
                if (qualifierBinding != null) {
                    bindings.add(qualifierBinding);
                }
                if (bindings.isEmpty()) {
                    bindings.addAll(this.sideCapableBindings(executionContext, this.sideBindingType(callContext.functionName())));
                }
            }
        }

        return List.copyOf(bindings);
    }

    private String sideBindingType(final String functionName) {
        return switch (functionName) {
            case "read", "write", "channel", "set_channel" -> "redstone_io";
            case "inventory_size", "inventory", "stack", "count_item", "tank_count", "tanks", "tank", "transfer_item", "transfer_fluid",
                    "transfer_item_to", "transfer_fluid_to",
                    "set_material_input_side", "set_material_output_side" -> "material_io";
            case "craft", "craft_queued" -> "crafting_cpu";
            default -> null;
        };
    }

    private List<PythonPeripheralBinding> sideCapableBindings(final PythonExecutionContext executionContext, final String requiredType) {
        final ArrayList<PythonPeripheralBinding> bindings = new ArrayList<>();
        for (final PythonPeripheralBinding binding : executionContext.peripherals()) {
            if (requiredType != null && !requiredType.equals(binding.type())) {
                continue;
            }
            if (!binding.hasSideAliases()) {
                continue;
            }
            bindings.add(binding);
        }
        return bindings;
    }

    private List<SuggestionItem> sideAliasSuggestions(final PythonPeripheralBinding binding) {
        final ArrayList<SuggestionItem> suggestions = new ArrayList<>();
        for (final Direction direction : Direction.values()) {
            final String alias = binding.sideAlias(direction);
            if (!alias.isBlank()) {
                suggestions.add(new SuggestionItem(alias, alias, binding.apiName() + " | " + direction.getSerializedName() + " alias"));
            }
        }
        return suggestions;
    }

    private PythonPeripheralBinding bindingForExpression(final String expression,
                                                        final PythonExecutionContext executionContext,
                                                        final ScriptContext scriptContext) {
        if (expression == null || expression.isBlank()) {
            return null;
        }

        final String trimmed = expression.trim();
        final String apiName = scriptContext.variableApiNames().get(trimmed);
        if (apiName != null) {
            return executionContext.peripheral(apiName);
        }

        final Matcher directCall = DIRECT_DEVICE_CALL.matcher(trimmed);
        if (directCall.matches()) {
            return executionContext.peripheral(directCall.group(1));
        }

        final Matcher directIndex = DIRECT_DEVICE_INDEX.matcher(trimmed);
        if (directIndex.matches()) {
            return executionContext.peripheral(directIndex.group(1));
        }

        return executionContext.peripheral(trimmed);
    }

    private String literalStringArgument(final String line, final CallContext callContext, final int quoteStart, final int argumentIndex) {
        final List<String> arguments = this.literalStringArgumentsBefore(line, callContext.openParenIndex() + 1, quoteStart);
        return argumentIndex >= 0 && argumentIndex < arguments.size() ? arguments.get(argumentIndex) : "";
    }

    private List<String> literalStringArgumentsBefore(final String line, final int startIndex, final int endIndex) {
        final ArrayList<String> arguments = new ArrayList<>();
        int parenDepth = 0;
        int bracketDepth = 0;
        int braceDepth = 0;
        boolean inString = false;
        boolean escaped = false;
        char delimiter = 0;
        StringBuilder literal = null;
        String currentArgument = null;

        for (int index = Math.max(0, startIndex); index < Math.min(endIndex, line.length()); index++) {
            final char current = line.charAt(index);
            if (inString) {
                if (current == delimiter && !escaped) {
                    inString = false;
                    if (literal != null) {
                        currentArgument = literal.toString();
                        literal = null;
                    }
                } else if (literal != null) {
                    literal.append(current);
                }

                if (current == '\\' && !escaped) {
                    escaped = true;
                } else {
                    escaped = false;
                }
                continue;
            }

            if ((current == '\'' || current == '"') && parenDepth == 0 && bracketDepth == 0 && braceDepth == 0) {
                inString = true;
                delimiter = current;
                escaped = false;
                literal = new StringBuilder();
                continue;
            }

            switch (current) {
                case '(' -> parenDepth++;
                case ')' -> parenDepth = Math.max(0, parenDepth - 1);
                case '[' -> bracketDepth++;
                case ']' -> bracketDepth = Math.max(0, bracketDepth - 1);
                case '{' -> braceDepth++;
                case '}' -> braceDepth = Math.max(0, braceDepth - 1);
                case ',' -> {
                    if (parenDepth == 0 && bracketDepth == 0 && braceDepth == 0) {
                        arguments.add(currentArgument == null ? "" : currentArgument);
                        currentArgument = null;
                    }
                }
                default -> {
                }
            }
        }

        return List.copyOf(arguments);
    }

    private List<SuggestionItem> deviceNameSuggestions(final PythonExecutionContext executionContext, final String requiredType) {
        final List<SuggestionItem> suggestions = new ArrayList<>();
        for (final PythonPeripheralBinding binding : executionContext.peripherals()) {
            if (requiredType != null && !requiredType.equals(binding.type())) {
                continue;
            }
            suggestions.add(new SuggestionItem(binding.apiName(), binding.apiName(), binding.type() + " device"));
        }
        return deduplicate(suggestions);
    }

    private List<SuggestionItem> deviceTypeSuggestions(final PythonExecutionContext executionContext) {
        final LinkedHashSet<String> deviceTypes = new LinkedHashSet<>();
        for (final PythonPeripheralBinding binding : executionContext.peripherals()) {
            if (binding.type() == null || binding.type().isBlank()) {
                continue;
            }
            deviceTypes.add(binding.type());
        }

        final ArrayList<SuggestionItem> suggestions = new ArrayList<>(deviceTypes.size());
        for (final String deviceType : deviceTypes) {
            suggestions.add(new SuggestionItem(deviceType, deviceType, "device type"));
        }
        return List.copyOf(suggestions);
    }

    private List<SuggestionItem> endpointNameSuggestions(final PythonExecutionContext executionContext) {
        final List<SuggestionItem> suggestions = new ArrayList<>();
        for (final PythonPeripheralBinding binding : executionContext.peripherals()) {
            suggestions.add(new SuggestionItem(binding.apiName(), binding.apiName(), binding.displayName() + " | " + binding.type()));
        }
        return deduplicate(suggestions);
    }

    private List<SuggestionItem> routeSuggestions(final Set<String> routeNames) {
        final List<SuggestionItem> suggestions = new ArrayList<>();
        for (final String routeName : routeNames) {
            suggestions.add(new SuggestionItem(routeName, routeName, "route"));
        }
        return deduplicate(suggestions);
    }

    private static ScriptContext buildScriptContext(final TextDocument document, final PythonExecutionContext executionContext, final int lineLimit) {
        final Map<String, String> apiTypes = new LinkedHashMap<>();
        for (final PythonPeripheralBinding binding : executionContext.peripherals()) {
            apiTypes.put(binding.apiName(), binding.type());
        }

        final Map<String, String> variableTypes = new LinkedHashMap<>();
        final Map<String, String> variableApiNames = new LinkedHashMap<>();
        final Map<String, String> variableSchemas = new LinkedHashMap<>();
        final LinkedHashSet<String> routeNames = new LinkedHashSet<>();

        for (int line = 0; line <= lineLimit && line < document.getLineCount(); line++) {
            final String source = document.getLine(line);
            registerAssignment(variableTypes, variableApiNames, apiTypes, DEVICE_ASSIGNMENT.matcher(source));
            registerTypeAssignment(variableTypes, DEVICE_TYPE_ASSIGNMENT.matcher(source));
            registerAssignment(variableTypes, variableApiNames, apiTypes, DEVICE_INDEX_ASSIGNMENT.matcher(source));
            registerRouteName(routeNames, ROUTE_NAME_ASSIGNMENT.matcher(source));
            registerEndpointAssignment(variableSchemas, ENDPOINT_ASSIGNMENT.matcher(source));
            registerEndpointAssignment(variableSchemas, ENDPOINT_INDEX_ASSIGNMENT.matcher(source));
            registerSimpleAlias(variableTypes, variableApiNames, variableSchemas, SIMPLE_ALIAS.matcher(source));
            registerMethodSchema(variableSchemas, variableTypes, DEVICE_METHOD_ASSIGNMENT.matcher(source));
        }
        return new ScriptContext(variableTypes, variableApiNames, variableSchemas, routeNames);
    }

    private static void registerAssignment(final Map<String, String> variableTypes,
                                           final Map<String, String> variableApiNames,
                                           final Map<String, String> apiTypes,
                                           final Matcher matcher) {
        if (!matcher.find()) {
            return;
        }
        final String variable = matcher.group(1);
        final String apiName = matcher.group(2);
        final String deviceType = apiTypes.get(apiName);
        if (deviceType != null) {
            variableTypes.put(variable, deviceType);
            variableApiNames.put(variable, apiName);
        }
    }

    private static void registerTypeAssignment(final Map<String, String> variableTypes, final Matcher matcher) {
        if (!matcher.find()) {
            return;
        }
        variableTypes.put(matcher.group(1), matcher.group(2));
    }

    private static void registerEndpointAssignment(final Map<String, String> variableSchemas, final Matcher matcher) {
        if (matcher.find()) {
            variableSchemas.put(matcher.group(1), "endpoint");
        }
    }

    private static void registerRouteName(final Set<String> routeNames, final Matcher matcher) {
        if (matcher.find()) {
            routeNames.add(matcher.group(1));
        }
    }

    private static void registerSimpleAlias(final Map<String, String> variableTypes,
                                            final Map<String, String> variableApiNames,
                                            final Map<String, String> variableSchemas,
                                            final Matcher matcher) {
        if (!matcher.find()) {
            return;
        }

        final String target = matcher.group(1);
        final String source = matcher.group(2);
        if (variableTypes.containsKey(source)) {
            variableTypes.put(target, variableTypes.get(source));
        }
        if (variableApiNames.containsKey(source)) {
            variableApiNames.put(target, variableApiNames.get(source));
        }
        if (variableSchemas.containsKey(source)) {
            variableSchemas.put(target, variableSchemas.get(source));
        }
        if ("computer".equals(source)) {
            variableSchemas.put(target, "computer");
        }
    }

    private static void registerMethodSchema(final Map<String, String> variableSchemas, final Map<String, String> variableTypes, final Matcher matcher) {
        if (!matcher.find()) {
            return;
        }

        final String target = matcher.group(1);
        final String sourceObject = matcher.group(2);
        final String methodName = matcher.group(3);
        final String deviceType = variableTypes.get(sourceObject);
        final String schema = switch (methodName) {
            case "state" -> "device_state:" + (deviceType == null ? "any" : deviceType);
            case "side_aliases" -> "side_aliases";
            case "route" -> "route";
            case "window_origin" -> "window";
            case "linked_preview", "preview" -> "preview";
            case "grid_slot", "stack", "recipe_slot" -> "slot";
            case "tank" -> "tank";
            case "plan_step" -> "plan_step";
            default -> null;
        };
        if (schema != null) {
            variableSchemas.put(target, schema);
        }
    }

    private static List<SuggestionItem> filterCandidates(final List<SuggestionItem> candidates, final String prefix, final boolean allowEmptyPrefix) {
        final String normalizedPrefix = prefix.toLowerCase(Locale.ROOT);
        if (normalizedPrefix.isEmpty() && !allowEmptyPrefix) {
            return List.of();
        }

        return deduplicate(candidates).stream()
                .filter(candidate -> normalizedPrefix.isEmpty()
                        || candidate.label().toLowerCase(Locale.ROOT).startsWith(normalizedPrefix)
                        || candidate.label().toLowerCase(Locale.ROOT).contains(normalizedPrefix))
                .sorted(Comparator
                        .comparing((SuggestionItem candidate) -> !candidate.label().toLowerCase(Locale.ROOT).startsWith(normalizedPrefix))
                        .thenComparing(SuggestionItem::label))
                .limit(8)
                .toList();
    }

    private static List<SuggestionItem> buildGlobalSuggestions() {
        final ArrayList<SuggestionItem> items = new ArrayList<>();
        for (final String keyword : PythonSyntaxHighlighter.keywords()) {
            items.add(new SuggestionItem(keyword, keyword, "keyword"));
        }
        for (final String builtin : PythonSyntaxHighlighter.builtins()) {
            items.add(new SuggestionItem(builtin, builtin, "builtin"));
        }
        items.addAll(items(
                named("main", "function"),
                named("json", "module"),
                named("computer", "global"),
                named("endpoints", "global"),
                named("peripherals", "global"),
                named("endpoint_names", "global"),
                named("list_endpoints", "global"),
                named("get_endpoint", "global"),
                named("devices", "global"),
                named("device_names", "global"),
                named("list_devices", "global"),
                named("get_device", "global"),
                named("computer_api", "global"),
                named("world", "global"),
                named("output", "global"),
                named("screen", "global"),
                named("network", "global"),
                named("pause", "helper"),
                named("repeat", "helper"),
                named("say", "helper"),
                named("show", "helper"),
                named("device", "helper"),
                named("require_device", "helper"),
                named("find_device", "helper"),
                named("list_device_names", "helper"),
                named("devices_by_type", "helper"),
                named("show_table", "helper"),
                named("show_kv", "helper"),
                named("show_plan_card", "helper")
        ));
        return deduplicate(items);
    }

    private static int skipWhitespaceLeft(final String line, final int startIndex) {
        int index = startIndex;
        while (index >= 0 && Character.isWhitespace(line.charAt(index))) {
            index--;
        }
        return index;
    }

    private static boolean isIdentifierPart(final char value) {
        return Character.isLetterOrDigit(value) || value == '_';
    }

    private static SuggestionItem named(final String label, final String detail) {
        return new SuggestionItem(label, label, detail);
    }

    private static List<SuggestionItem> items(final SuggestionItem... items) {
        return List.of(items);
    }

    private static List<SuggestionItem> deduplicate(final Collection<SuggestionItem> items) {
        final LinkedHashMap<String, SuggestionItem> unique = new LinkedHashMap<>();
        for (final SuggestionItem item : items) {
            unique.putIfAbsent(item.label(), item);
        }
        return List.copyOf(unique.values());
    }

    public record SuggestionItem(String label, String insertText, String detail) {
    }

    public record SuggestionSession(int line, int replaceStartColumn, int replaceEndColumn, String prefix, List<SuggestionItem> items) {
        public static SuggestionSession empty(final int line, final int column) {
            return new SuggestionSession(line, column, column, "", List.of());
        }

        public boolean visible() {
            return !this.items.isEmpty();
        }
    }

    private record ScriptContext(Map<String, String> variableTypes,
                                 Map<String, String> variableApiNames,
                                 Map<String, String> variableSchemas,
                                 Set<String> routeNames) {
    }

    private record QuoteContext(int quoteStart, String content) {
    }

    private record StringContext(int replaceStartColumn, String prefix, List<SuggestionItem> candidates) {
    }

    private record MemberContext(int replaceStartColumn, String prefix, List<SuggestionItem> candidates) {
    }

    private record IdentifierContext(int startColumn, String prefix) {
    }

    private record CallContext(String callee, String qualifier, String functionName, int argumentIndex, int openParenIndex) {
    }
}
