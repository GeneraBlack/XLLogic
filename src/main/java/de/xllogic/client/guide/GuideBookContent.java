package de.xllogic.client.guide;

import java.util.List;

public final class GuideBookContent {
        private static final String CODE_BLOCK_CLOSE = "    })";
    private static final List<Page> PAGES = List.of(
            page(
                    "Welcome",
                    "XL Logic runs Python on the server. This guide focuses on the simplified beginner layer so you can discover devices, print output, and write your first loops without learning the full API all at once.",
                    text(
                            "Use this book as a quick in-game reference while you build.",
                            "The full API still exists, but the beginner helpers keep the first programs short and readable."
                    ),
                    bullets(
                            "screen handles visible output.",
                            "network helps you discover devices.",
                            "pause and repeat keep long-running programs safe.",
                            "computer and world expose basic state."
                    ),
                    note(
                            "Tip: Start with output and device discovery before you try automation."
                    )
            ),
            page(
                    "Output",
                    "The easiest way to show information is the screen helper. It mirrors onto the computer output area and linked screens, while named screen devices can be addressed individually.",
                    text(
                            "Use screen.print for a single line and screen.show for small key-value cards.",
                            "Named screen devices also support line, show, kv, table, and plan_card so you can target one specific panel.",
                            "say and show are shortcuts if you prefer function-style calls."
                    ),
                    code(
                            "screen.print(\"Hello from XL Logic\")",
                            "",
                            "screen.show(\"Computer\", {",
                            "    \"name\": computer[\"name\"],",
                            "    \"position\": computer[\"position\"],",
                            "    \"endpoints\": computer[\"endpoint_count\"],",
                            "})",
                            "",
                            "wall_screen = get_device(\"wall_screen\")",
                            "if wall_screen is not None:",
                            "    wall_screen.show(\"Status\", {\"state\": \"online\"})"
                    )
            ),
            page(
                    "Targeted Screens",
                    "Give screens endpoint names, then write different content to each named panel from one computer program.",
                    text(
                            "Shift-right-click a screen side or back to open the naming UI, then save names such as left_panel and right_panel.",
                            "Use get_device(name) to fetch that screen and keep the regular screen helper for shared overview output."
                    ),
                    code(
                            "left_panel = get_device(\"left_panel\")",
                            "right_panel = get_device(\"right_panel\")",
                            "",
                            "screen.show(\"Computer\", {",
                            "    \"name\": computer[\"name\"],",
                            "    \"devices\": len(list_device_names()),",
                            "})",
                            "",
                            "if left_panel is not None:",
                            "    left_panel.show(\"World\", {",
                            "        \"raining\": world.is_raining() if world.available() else \"unknown\"",
                            CODE_BLOCK_CLOSE,
                            "",
                            "if right_panel is not None:",
                            "    right_panel.table(\"Devices\", [\"API\", \"Type\"], [",
                            "        [name, device(name).type()]",
                            "        for name in list_device_names()",
                            "    ])"
                    ),
                    note(
                            "Named screens support line, show, kv, table, plan_card, and clear_output."
                    )
            ),
            page(
                    "Finding Devices",
                    "The network helper keeps discovery simple. You can list names, inspect visible types, or grab the first matching device of a type.",
                    text(
                            "Use device(name) when you already know the API name.",
                            "Use find_device(type) when you want the first visible device of a certain kind."
                    ),
                    code(
                            "screen.show(\"Network\", {",
                            "    \"count\": len(list_device_names()),",
                            "    \"types\": \", \".join(network.types()),",
                            "})",
                            "",
                            "clock = find_device(\"clock\")",
                            "if clock is not None:",
                            "    screen.show(\"Clock\", {\"day_time\": clock.day_time()})"
                    )
            ),
            page(
                    "World Data",
                    "The world object exposes server-backed information about the current dimension, time, and weather.",
                    text(
                            "Always check world.available() before you rely on world state.",
                            "This makes your scripts robust when the runtime is missing world access."
                    ),
                    code(
                            "if world.available():",
                            "    screen.show(\"World\", {",
                            "        \"dimension\": world.dimension(),",
                            "        \"day_time\": world.day_time(),",
                            "        \"raining\": world.is_raining(),",
                            CODE_BLOCK_CLOSE
                    )
            ),
            page(
                    "Loops",
                    "Long-running programs must yield back to the server. The beginner helpers pause and repeat are thin wrappers around the lower-level cooperative runtime.",
                    text(
                            "Use yield from pause(1) inside a manual loop.",
                            "Use yield from repeat(step, 1) when the same step should run every tick."
                    ),
                    code(
                            "last_rain = None",
                            "",
                            "def tick():",
                            "    global last_rain",
                            "    if not world.available():",
                            "        return",
                            "    raining = world.is_raining()",
                            "    if raining != last_rain:",
                            "        screen.print(\"Raining: \" + str(raining))",
                            "        last_rain = raining",
                            "",
                            "yield from repeat(tick, 1)"
                    ),
                    note(
                            "If you forget to yield in a long loop, the watchdog will stop the program."
                    )
            ),
            page(
                    "Redstone",
                    "Redstone I/O is usually the first automation device you will program. Read a side, then write a side when you want to drive the world.",
                    text(
                            "You can use canonical sides like north, south, east, and west.",
                            "If you saved side aliases for a device, those aliases work too."
                    ),
                    code(
                            "redstone = find_device(\"redstone_io\")",
                            "if redstone is not None:",
                            "    level = redstone.read(\"north\")",
                            "    screen.show(\"North Signal\", {\"level\": level})",
                            "    if level > 0:",
                            "        redstone.write(\"south\", 15)",
                            "    else:",
                            "        redstone.write(\"south\", 0)"
                    )
            ),
            page(
                    "Material I/O",
                    "Material I/O lets you inspect inventories and move items or fluids between sides.",
                    text(
                            "Start with inventory(side) to inspect what the device can currently see.",
                            "Once that works, add transfer_item and transfer_fluid calls.",
                            "Named material I/O devices can also move resources directly between two endpoints with transfer_item_to and transfer_fluid_to."
                    ),
                    code(
                            "storage = find_device(\"material_io\")",
                            "if storage is not None:",
                            "    rows = []",
                            "    for stack in storage.inventory(\"north\"):",
                            "        rows.append([stack[\"item\"], stack[\"count\"]])",
                            "    screen.table(\"North Inventory\", [\"Item\", \"Count\"], rows)",
                            "",
                            "source = get_device(\"source_io\")",
                            "if source is not None:",
                            "    source.transfer_item_to(\"sink_io\", \"south\", \"south\", 0, 16)"
                    )
            ),
            page(
                    "Crafting",
                    "The crafting helpers are more advanced, but you can still start small: inspect a CPU preview, then connect a crafting I/O frontend later.",
                    text(
                            "find_device returns the first visible crafting block of the requested type.",
                            "Use preview methods first so you understand what the network thinks the current recipe is."
                    ),
                    code(
                            "cpu = find_device(\"crafting_cpu\")",
                            "if cpu is not None and cpu.available():",
                            "    screen.show(\"Craft Preview\", cpu.preview())",
                            "",
                            "frontend = find_device(\"crafting_io\")",
                            "if frontend is not None and frontend.available():",
                            "    screen.show(\"Crafting Frontend\", {",
                            "        \"linked_cpu\": frontend.linked_cpu(),",
                            "        \"plan_steps\": frontend.plan_step_count(),",
                            CODE_BLOCK_CLOSE
                    )
            ),
            page(
                    "Troubleshooting",
                    "Most script problems are discovery problems, missing yields, or trying to use a device that is visible but not available.",
                    bullets(
                            "Check list_device_names() first.",
                            "Check device.available() before you call more methods.",
                            "Check whether a remote device is read-only.",
                            "Use small probe scripts before you write long loops."
                    ),
                    code(
                            "screen.show(\"Debug\", {",
                            "    \"device_count\": len(list_device_names()),",
                            "    \"types\": \", \".join(network.types()),",
                            "    \"world_available\": world.available(),",
                            CODE_BLOCK_CLOSE
                    )
            )
    );

    private GuideBookContent() {
    }

    public static List<Page> pages() {
        return PAGES;
    }

    private static Page page(final String title, final String summary, final Block... blocks) {
        return new Page(title, summary, List.of(blocks));
    }

    private static Block text(final String... paragraphs) {
        return new Block(BlockKind.TEXT, List.of(paragraphs));
    }

    private static Block bullets(final String... items) {
        return new Block(BlockKind.BULLETS, List.of(items));
    }

    private static Block code(final String... lines) {
        return new Block(BlockKind.CODE, List.of(lines));
    }

    private static Block note(final String... paragraphs) {
        return new Block(BlockKind.NOTE, List.of(paragraphs));
    }

    public record Page(String title, String summary, List<Block> blocks) {
    }

    public record Block(BlockKind kind, List<String> lines) {
    }

    public enum BlockKind {
        TEXT,
        BULLETS,
        CODE,
        NOTE
    }
}