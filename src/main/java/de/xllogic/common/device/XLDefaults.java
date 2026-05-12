package de.xllogic.common.device;

public final class XLDefaults {
    private static final String LEGACY_STARTER_SCRIPT = "def main():\n"
            + "    show_kv(\"Computer\", {\n"
            + "        \"name\": computer['name'],\n"
            + "        \"position\": computer['position'],\n"
            + "        \"endpoints\": computer['endpoint_count'],\n"
            + "    })\n"
            + "    show_table(\"Devices\", [\"API\", \"Type\", \"Scope\", \"Policy\", \"Writable\"], [\n"
            + "        [\n"
            + "            name,\n"
            + "            get_device(name).type(),\n"
            + "            get_device(name).network_scope(),\n"
            + "            get_device(name).remote_policy(),\n"
            + "            \"yes\" if get_device(name).remote_writable() else \"no\",\n"
            + "        ]\n"
            + "        for name in list_devices()\n"
            + "    ], text=\"Current network devices and bridge policy\")\n"
            + "\n"
            + "    if world.available():\n"
            + "        show_kv(\"World\", {\n"
            + "            \"dimension\": world.dimension(),\n"
            + "            \"day_time\": world.day_time(),\n"
            + "            \"raining\": world.is_raining(),\n"
            + "        }, text=\"Server-backed world state\")\n"
            + "\n"
            + "    remote_devices = [get_device(name) for name in list_devices() if get_device(name).is_remote()]\n"
            + "    if remote_devices:\n"
            + "        show_table(\"Remote Devices\", [\"API\", \"Type\", \"Policy\", \"Writable\"], [\n"
            + "            [device.api_name(), device.type(), device.remote_policy(), \"yes\" if device.remote_writable() else \"no\"]\n"
            + "            for device in remote_devices\n"
            + "        ], text=\"Bridged devices currently visible to this computer\")\n"
            + "\n"
            + "    material = get_device(\"material_io\")\n"
            + "    if material is not None and material.available():\n"
            + "        show_table(\"North Inventory\", [\"Item\", \"Count\"], [\n"
            + "            [stack['item'], stack['count']]\n"
            + "            for stack in material.inventory(\"north\")\n"
            + "        ], text=\"Material I/O north side\")\n"
            + "\n"
            + "    cpu = get_device(\"crafting_cpu\")\n"
            + "    if cpu is not None and cpu.available():\n"
            + "        show_kv(\"Craft Preview\", cpu.preview())\n"
            + "\n"
            + "    crafting = get_device(\"crafting_io\")\n"
            + "    if crafting is not None and crafting.available():\n"
            + "        show_kv(\"Crafting Frontend\", {\n"
            + "            \"linked_cpu\": crafting.linked_cpu(),\n"
            + "            \"window\": crafting.window_origin(),\n"
            + "            \"preview\": crafting.linked_preview(),\n"
            + "            \"routes\": crafting.routes(),\n"
            + "            \"plan_steps\": crafting.plan_step_count(),\n"
            + "        }, text=\"Frontend and plan summary\")\n"
            + "        if crafting.plan_step_count() > 0:\n"
            + "            show_plan_card(\"First Plan Step\", crafting.plan_step(0), text=\"Current routed step\")\n"
            + "\n"
            + "    bridge = get_device(\"xlapi_block\")\n"
            + "    if bridge is not None and bridge.available():\n"
            + "        remote_computers = bridge.remote_computers()\n"
            + "        show_kv(\"XLAPI Bridge\", {\n"
            + "            \"uplink_group\": bridge.uplink_group(),\n"
            + "            \"relay_enabled\": bridge.relay_enabled(),\n"
            + "            \"remote_computers\": len(remote_computers),\n"
            + "            \"inbox_count\": bridge.inbox_count(),\n"
            + "        }, text=\"Bridge overview for the current local segment\")\n"
            + "\n"
            + "main()\n";
            private static final String PRE_COOPERATIVE_STARTER_SCRIPT = "def main():\n"
            + "    show_kv(\"Computer\", {\n"
            + "        \"name\": computer['name'],\n"
            + "        \"position\": computer['position'],\n"
            + "        \"endpoints\": computer['endpoint_count'],\n"
            + "    }, text=\"Current computer state\")\n"
            + "\n"
            + "    devices = []\n"
            + "    for name in list_devices():\n"
            + "        device = get_device(name)\n"
            + "        devices.append([name, device.type(), device.network_scope(), device.remote_policy()])\n"
            + "\n"
            + "    show_table(\"Devices\", [\"API\", \"Type\", \"Scope\", \"Policy\"], devices,\n"
            + "               text=\"Visible local and bridged devices\")\n"
            + "\n"
            + "    if world.available():\n"
            + "        show_kv(\"World\", {\n"
            + "            \"dimension\": world.dimension(),\n"
            + "            \"day_time\": world.day_time(),\n"
            + "            \"raining\": world.is_raining(),\n"
            + "        }, text=\"Server-backed world state\")\n"
            + "\n"
            + "    bridge = get_device(\"xlapi_block\")\n"
            + "    if bridge is not None and bridge.available():\n"
            + "        show_kv(\"XLAPI Bridge\", {\n"
            + "            \"uplink_group\": bridge.uplink_group(),\n"
            + "            \"relay_enabled\": bridge.relay_enabled(),\n"
            + "            \"remote_computers\": len(bridge.remote_computers()),\n"
            + "        }, text=\"Bridge overview for the current local segment\")\n"
            + "\n"
            + "main()\n";
            private static final String PRE_REMOTE_COMPUTER_COUNT_STARTER_SCRIPT = "# XL Logic starter for the cooperative tick runtime.\n"
                + "# F5 starts or stops the bound program. Long-running loops resume\n"
                + "# on the server's cooperative slice cadence, which is throttled\n"
                + "# to 20 ticks by default. Use 'yield from sleep_ticks(1)' for the\n"
                + "# default pace or larger values to slow loops further.\n"
                + "\n"
                + "def collect_devices():\n"
                + "    rows = []\n"
                + "    for name in list_devices():\n"
                + "        device = get_device(name)\n"
                + "        rows.append([name, device.type(), device.network_scope(), device.remote_policy()])\n"
                + "    return rows\n"
                + "\n"
                + "def render_overview():\n"
                + "    show_kv(\"Computer\", {\n"
                + "        \"name\": computer['name'],\n"
                + "        \"position\": computer['position'],\n"
                + "        \"endpoints\": computer['endpoint_count'],\n"
                + "    }, text=\"Current computer state\")\n"
                + "\n"
                + "    show_table(\"Devices\", [\"API\", \"Type\", \"Scope\", \"Policy\"], collect_devices(),\n"
                + "               text=\"Visible local and bridged devices\")\n"
                + "\n"
                + "    if world.available():\n"
                + "        show_kv(\"World\", {\n"
                + "            \"dimension\": world.dimension(),\n"
                + "            \"day_time\": world.day_time(),\n"
                + "            \"raining\": world.is_raining(),\n"
                + "        }, text=\"Server-backed world state\")\n"
                + "\n"
                + "    bridge = get_device(\"xlapi_block\")\n"
                + "    if bridge is not None and bridge.available():\n"
                + "        show_kv(\"XLAPI Bridge\", {\n"
                + "            \"uplink_group\": bridge.uplink_group(),\n"
                + "            \"relay_enabled\": bridge.relay_enabled(),\n"
                + "            \"remote_computers\": len(bridge.remote_computers()),\n"
                + "        }, text=\"Bridge overview for the current local segment\")\n"
                + "\n"
                + "render_overview()\n"
                + "\n"
                + "# Cooperative loop template: uncomment this block when the\n"
                + "# computer should keep running across throttled server slices.\n"
                + "# last_world = {\"day_bucket\": None, \"raining\": None}\n"
                + "#\n"
                + "# def monitor_tick():\n"
                + "#     if not world.available():\n"
                + "#         return\n"
                + "#     snapshot = {\n"
                + "#         \"day_bucket\": world.day_time() // 20,\n"
                + "#         \"raining\": world.is_raining(),\n"
                + "#     }\n"
                + "#     if snapshot != last_world:\n"
                + "#         show_kv(\"World Tick\", snapshot, text=\"Only emits when the watched state changes\")\n"
                + "#         last_world.update(snapshot)\n"
                + "#\n"
                + "# yield from run_loop(monitor_tick, 1)\n";
                private static final String PRE_ENDPOINT_METADATA_STARTER_SCRIPT = "# XL Logic starter for the cooperative tick runtime.\n"
                    + "# F5 starts or stops the bound program. Long-running loops resume\n"
                    + "# on the server's cooperative slice cadence, which is throttled\n"
                    + "# to 20 ticks by default. Use 'yield from sleep_ticks(1)' for the\n"
                    + "# default pace or larger values to slow loops further.\n"
                    + "\n"
                    + "def collect_devices():\n"
                    + "    rows = []\n"
                    + "    for name in list_devices():\n"
                    + "        device = get_device(name)\n"
                    + "        rows.append([name, device.type(), device.network_scope(), device.remote_policy()])\n"
                    + "    return rows\n"
                    + "\n"
                    + "def render_overview():\n"
                    + "    show_kv(\"Computer\", {\n"
                    + "        \"name\": computer['name'],\n"
                    + "        \"position\": computer['position'],\n"
                    + "        \"endpoints\": computer['endpoint_count'],\n"
                    + "    }, text=\"Current computer state\")\n"
                    + "\n"
                    + "    show_table(\"Devices\", [\"API\", \"Type\", \"Scope\", \"Policy\"], collect_devices(),\n"
                    + "               text=\"Visible local and bridged devices\")\n"
                    + "\n"
                    + "    if world.available():\n"
                    + "        show_kv(\"World\", {\n"
                    + "            \"dimension\": world.dimension(),\n"
                    + "            \"day_time\": world.day_time(),\n"
                    + "            \"raining\": world.is_raining(),\n"
                    + "        }, text=\"Server-backed world state\")\n"
                    + "\n"
                    + "    bridge = get_device(\"xlapi_block\")\n"
                    + "    if bridge is not None and bridge.available():\n"
                    + "        show_kv(\"XLAPI Bridge\", {\n"
                    + "            \"uplink_group\": bridge.uplink_group(),\n"
                    + "            \"relay_enabled\": bridge.relay_enabled(),\n"
                    + "            \"remote_computers\": bridge.remote_computer_count(),\n"
                    + "        }, text=\"Bridge overview for the current local segment\")\n"
                    + "\n"
                    + "render_overview()\n"
                    + "\n"
                    + "# Cooperative loop template: uncomment this block when the\n"
                    + "# computer should keep running across throttled server slices.\n"
                    + "# last_world = {\"day_bucket\": None, \"raining\": None}\n"
                    + "#\n"
                    + "# def monitor_tick():\n"
                    + "#     if not world.available():\n"
                    + "#         return\n"
                    + "#     snapshot = {\n"
                    + "#         \"day_bucket\": world.day_time() // 20,\n"
                    + "#         \"raining\": world.is_raining(),\n"
                    + "#     }\n"
                    + "#     if snapshot != last_world:\n"
                    + "#         show_kv(\"World Tick\", snapshot, text=\"Only emits when the watched state changes\")\n"
                    + "#         last_world.update(snapshot)\n"
                    + "#\n"
                    + "# yield from run_loop(monitor_tick, 1)\n";
        static final String PRE_BEGINNER_STARTER_SCRIPT = "# XL Logic starter for the cooperative tick runtime.\n"
            + "# F5 starts or stops the bound program. Long-running loops resume\n"
            + "# on the server's cooperative slice cadence, which is throttled\n"
            + "# to 20 ticks by default. Use 'yield from sleep_ticks(1)' for the\n"
            + "# default pace or larger values to slow loops further.\n"
            + "\n"
            + "def collect_devices():\n"
            + "    rows = []\n"
            + "    for endpoint in endpoints:\n"
            + "        rows.append([endpoint['api_name'], endpoint['type'], endpoint['scope'], endpoint['remote_policy']])\n"
            + "    return rows\n"
            + "\n"
            + "def render_overview():\n"
            + "    show_kv(\"Computer\", {\n"
            + "        \"name\": computer['name'],\n"
            + "        \"position\": computer['position'],\n"
            + "        \"endpoints\": computer['endpoint_count'],\n"
            + "    }, text=\"Current computer state\")\n"
            + "\n"
            + "    show_table(\"Devices\", [\"API\", \"Type\", \"Scope\", \"Policy\"], collect_devices(),\n"
            + "               text=\"Visible local and bridged devices\")\n"
            + "\n"
            + "    if world.available():\n"
            + "        show_kv(\"World\", {\n"
            + "            \"dimension\": world.dimension(),\n"
            + "            \"day_time\": world.day_time(),\n"
            + "            \"raining\": world.is_raining(),\n"
            + "        }, text=\"Server-backed world state\")\n"
            + "\n"
            + "    bridge = get_device(\"xlapi_block\")\n"
            + "    if bridge is not None and bridge.available():\n"
            + "        show_kv(\"XLAPI Bridge\", {\n"
            + "            \"uplink_group\": bridge.uplink_group(),\n"
            + "            \"relay_enabled\": bridge.relay_enabled(),\n"
            + "            \"remote_computers\": bridge.remote_computer_count(),\n"
            + "        }, text=\"Bridge overview for the current local segment\")\n"
            + "\n"
            + "render_overview()\n"
            + "\n"
            + "# Cooperative loop template: uncomment this block when the\n"
            + "# computer should keep running across throttled server slices.\n"
            + "# last_world = {\"day_bucket\": None, \"raining\": None}\n"
            + "#\n"
            + "# def monitor_tick():\n"
            + "#     if not world.available():\n"
            + "#         return\n"
            + "#     snapshot = {\n"
            + "#         \"day_bucket\": world.day_time() // 20,\n"
            + "#         \"raining\": world.is_raining(),\n"
            + "#     }\n"
            + "#     if snapshot != last_world:\n"
            + "#         show_kv(\"World Tick\", snapshot, text=\"Only emits when the watched state changes\")\n"
            + "#         last_world.update(snapshot)\n"
            + "#\n"
            + "# yield from run_loop(monitor_tick, 1)\n";
        public static final String STARTER_SCRIPT = "# XL Logic beginner starter.\n"
            + "# F5 starts or stops the program. Use the helper objects below\n"
            + "# to explore the network before you learn the full API surface.\n"
            + "# For background programs, write a small step function and use\n"
            + "# 'yield from repeat(step, 1)' or 'yield from pause(1)'.\n"
            + "\n"
            + "def collect_device_rows():\n"
            + "    rows = []\n"
            + "    for name in list_device_names():\n"
            + "        current = device(name)\n"
            + "        rows.append([name, current.type(), current.network_scope(), current.remote_policy()])\n"
            + "    return rows\n"
            + "\n"
            + "def show_overview():\n"
            + "    screen.show(\"Computer\", {\n"
            + "        \"name\": computer['name'],\n"
            + "        \"position\": computer['position'],\n"
            + "        \"endpoints\": computer['endpoint_count'],\n"
            + "    }, text=\"Basic information about this computer\")\n"
            + "\n"
            + "    screen.table(\"Devices\", [\"API\", \"Type\", \"Scope\", \"Policy\"], collect_device_rows(),\n"
            + "                 text=\"All visible local and bridged devices\")\n"
            + "\n"
            + "    if world.available():\n"
            + "        screen.show(\"World\", {\n"
            + "            \"dimension\": world.dimension(),\n"
            + "            \"day_time\": world.day_time(),\n"
            + "            \"raining\": world.is_raining(),\n"
            + "        }, text=\"Live world data from the server\")\n"
            + "\n"
            + "    bridge = network.find(\"xlapi_block\")\n"
            + "    if bridge is not None and bridge.available():\n"
            + "        screen.show(\"XLAPI Bridge\", {\n"
            + "            \"uplink_group\": bridge.uplink_group(),\n"
            + "            \"relay_enabled\": bridge.relay_enabled(),\n"
            + "            \"remote_computers\": bridge.remote_computer_count(),\n"
            + "        }, text=\"Bridge summary for this segment\")\n"
            + "\n"
            + "show_overview()\n"
            + "\n"
            + "# Example named screen targets: name screen blocks first, then\n"
            + "# write different content to each panel from one program.\n"
            + "# left_panel = get_device(\"left_panel\")\n"
            + "# right_panel = get_device(\"right_panel\")\n"
            + "# if left_panel is not None:\n"
            + "#     left_panel.show(\"World\", {\"raining\": world.is_raining() if world.available() else \"unknown\"})\n"
            + "# if right_panel is not None:\n"
            + "#     right_panel.table(\"Devices\", [\"API\", \"Type\"], [[name, device(name).type()] for name in list_device_names()])\n"
            + "\n"
            + "# Example loop template:\n"
            + "# def tick():\n"
            + "#     if world.available():\n"
            + "#         screen.print(\"Day time: \" + str(world.day_time()))\n"
            + "#\n"
            + "# yield from repeat(tick, 1)\n";

    public static String migrateBundledStarterScript(final String script) {
        if (script == null) {
            return null;
        }

        final String canonicalScript = canonicalizeStarterScript(script);
        if (matchesBundledStarterVariant(canonicalScript, LEGACY_STARTER_SCRIPT)
                || matchesBundledStarterVariant(canonicalScript, PRE_COOPERATIVE_STARTER_SCRIPT)
                || matchesBundledStarterVariant(canonicalScript, PRE_REMOTE_COMPUTER_COUNT_STARTER_SCRIPT)
                || matchesBundledStarterVariant(canonicalScript, PRE_ENDPOINT_METADATA_STARTER_SCRIPT)
                || matchesBundledStarterVariant(canonicalScript, PRE_BEGINNER_STARTER_SCRIPT)
                || matchesBundledStarterVariant(canonicalScript, STARTER_SCRIPT)) {
            return STARTER_SCRIPT;
        }
        return script;
    }

    private static String normalizeLineSeparators(final String script) {
        return script.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static String canonicalizeStarterScript(final String script) {
        final String normalized = normalizeLineSeparators(script);
        return normalized.endsWith("\n") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private static boolean matchesBundledStarterVariant(final String candidate, final String bundledScript) {
        String variant = canonicalizeStarterScript(bundledScript);
        for (int pass = 0; pass < 5; pass++) {
            if (candidate.equals(variant)) {
                return true;
            }
            if (variant.length() > candidate.length()) {
                return false;
            }
            variant = canonicalizeStarterScript(simulateLegacyEditorLoadBug(variant));
        }
        return false;
    }

    private static String simulateLegacyEditorLoadBug(final String script) {
        final String normalized = canonicalizeStarterScript(script);
        if (normalized.isEmpty()) {
            return normalized;
        }

        final String[] lines = normalized.split("\n", -1);
        final StringBuilder builder = new StringBuilder(lines[0]);
        String inflatedPreviousLine = lines[0];
        for (int index = 1; index < lines.length; index++) {
            String indentation = leadingWhitespace(inflatedPreviousLine);
            if (inflatedPreviousLine.stripTrailing().endsWith(":")) {
                indentation += "    ";
            }
            inflatedPreviousLine = indentation + lines[index];
            builder.append('\n').append(inflatedPreviousLine);
        }
        return builder.toString();
    }

    private static String leadingWhitespace(final String line) {
        int index = 0;
        while (index < line.length()) {
            final char current = line.charAt(index);
            if (current != ' ' && current != '\t') {
                break;
            }
            index++;
        }
        return line.substring(0, index);
    }

    private XLDefaults() {
    }
}
