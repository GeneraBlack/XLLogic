# XL Logic

XL Logic is a NeoForge prototype for Minecraft 1.21.1 that explores ComputerCraft-style automation with Python instead of Lua.

The project combines a server-authoritative GraalPy runtime, visible cable-based discovery, named endpoints, individually targetable screens, direct Material I/O routing, crafting automation, and an optional no-code builder that generates real Python.

## Current status

XL Logic is still a prototype, but it already contains a substantial playable vertical slice:

- A server-side GraalPy runtime bound to in-world computers
- A full in-game Python editor with syntax highlighting, suggestions, diagnostics, copy/paste, undo/redo, scrolling, and structured output history
- Rich screen output with line, key/value, table, and plan-card rendering
- Visible cable-first networking with segment discovery and XLAPI bridge boundaries
- Individually targetable screens and automatic multiblock screen composition
- Redstone, sensor, material, crafting, and bridge device APIs exposed to Python
- A no-code builder with templates, world checks, logic blocks, and direct source-to-sink transfer blocks
- Multiplayer-safe editor leasing with read-only viewers, recovery drafts, and three-way merge based resume behavior
- A static English project website and dedicated per-block reference pages under the repository docs

## Core feature set

### Computer runtime

- Computers run Python on the server, not on the client.
- The runtime persists script text, runtime snapshots, and structured output state through the block entity.
- Execution is guarded by configurable limits for cooldown, script size, watchdog time, and stdout/stderr output size.
- Long-lived programs use a cooperative tick runtime, so scripts can yield and resume across ticks.

### Screens and rich output

- Screens are discovered through the cable network instead of being manually linked.
- Multiple aligned screens can form a rectangular multiblock display surface.
- Screen faces render structured runtime output directly in-world.
- Focused navigation exists for tables and cards, including per-cell/per-row interaction and paging.

### Networking and bridges

- Network Cable is the visible discovery medium for the local computer segment.
- XLAPI blocks form segment boundaries and can bridge remote endpoints from other loaded segments in the same dimension.
- Bridged devices expose remote-policy metadata so scripts can distinguish local devices from remote read-only or remote writable ones.
- XLAPI currently supports mailbox-style messages and structured status, ping, devices, and runtime requests.

### Device APIs

- Redstone I/O supports per-side input/output control and bus channels.
- Light Sensor, Clock, and Rain Sensor expose environmental state to both comparators and Python.
- Material I/O can inspect inventories and tanks and move items or fluids locally or directly to another named Material I/O endpoint.
- Crafting CPU stores a real 3x3 recipe, exposes preview data, and crafts against adjacent inventories.
- Crafting I/O stores a 3x3, 5x5, or 7x7 recipe frontend, links to a Crafting CPU, manages named routes, and supports queued multi-step plans with resumable job state.

### No-code builder

- The builder generates real Python instead of a separate runtime format.
- It already supports templates, flat logic blocks, else markers, world-state conditions, comparisons, and direct source-to-sink routing blocks.
- Builder output targets the same runtime, devices, named endpoints, and screen output helpers as handwritten Python.

### Multiplayer editing

- Each computer uses an exclusive editor lease.
- Other players can open the same computer read-only.
- Heartbeats and cleanup cover logout, disconnect, teleport, dimension change, chunk unload, and restart cases.
- When the target disappears, editors can fall back to a local recovery draft and later resume through a three-way merge aware workflow.

## Block families

The current block family is broader than a single computer block prototype. XL Logic already includes:

- Computer
- Screen
- Network Cable
- XLAPI Block
- Redstone I/O
- Redstone Bus Cable
- Coloured Redstone Cable
- Light Sensor
- Clock
- Rain Sensor
- Material I/O
- Crafting I/O
- Crafting CPU

Important implementation detail: coloured redstone cables are currently documented and implemented as fixed-channel variants rather than a single block that is cycled in place. The legacy colored_redstone_cable identifier is kept for compatibility, but the gameplay-facing model is the 16-variant family.

## Python surface

Python scripts already receive a substantial in-game API surface.

Available globals and helpers include:

- `computer`, `computer_api`, `world`
- `endpoints`, `endpoint_names`, `peripherals`
- `devices`, `device_names`
- `list_endpoints()`, `get_endpoint(name)`
- `list_devices()`, `get_device(name)`
- Structured output helpers such as `show_kv(...)`, `show_table(...)`, and `show_plan_card(...)`

Device handles expose metadata such as scope, bridge source, and remote-policy information, and then add block-specific methods for redstone, sensors, material routing, crafting, and XLAPI bridge operations.

Example:

```python
source = get_device("source_io")
screen = get_device("left_panel")

def tick():
    moved = 0
    if source is not None:
        moved = source.transfer_item_to("sink_io", "south", "south", 0, 16)

    if screen is not None:
        screen.show("Route", {
            "target": "sink_io",
            "moved": moved,
            "day_time": world.day_time(),
            "raining": world.is_raining(),
        })

yield from repeat(tick, 20)
```

## Runtime safety model

The embedded Python runtime is intentionally sandboxed.

- Scripts run through explicit XL Logic APIs rather than arbitrary host integration.
- Host OS access, filesystem access, environment access, process creation, native access, host class loading, and unrestricted Java/polyglot interop are intentionally unavailable.
- Endpoint renaming and XLAPI reconfiguration remain local operations.
- Remote device writes are governed by explicit bridge policy instead of silently allowing every bridged mutation.

This means the mod is designed around in-game automation, not around exposing the host machine or server administration surface to Python.

## Documentation

The repository already contains an English website and reference set:

- Landing page: [docs/site/index.html](docs/site/index.html)
- Feature overview: [docs/site/features.html](docs/site/features.html)
- Python guide: [docs/site/python.html](docs/site/python.html)
- Builder guide: [docs/site/builder.html](docs/site/builder.html)
- Block overview: [docs/site/blocks.html](docs/site/blocks.html)
- Docs hub: [docs/site/docs.html](docs/site/docs.html)

Dedicated per-block reference pages live under [docs/site/blocks](docs/site/blocks).

Repository-native documents currently include:

- Beginner guide: [docs/site/docs/BEGINNER_PROGRAMMING_GUIDE.md](docs/site/docs/BEGINNER_PROGRAMMING_GUIDE.md)
- Block status matrix: [docs/site/docs/BLOCK_STATUS_MATRIX.md](docs/site/docs/BLOCK_STATUS_MATRIX.md)
- Network scenarios: [docs/site/docs/NETWORK_SCENARIOS.md](docs/site/docs/NETWORK_SCENARIOS.md)
- Bridge policy scenarios: [docs/site/docs/BRIDGE_POLICY_SCENARIOS.md](docs/site/docs/BRIDGE_POLICY_SCENARIOS.md)
- Roadmap: [docs/site/docs/ROADMAP.md](docs/site/docs/ROADMAP.md)

To preview the static site locally:

```bash
python -m http.server 4173 -d docs/site
```

Then open `http://localhost:4173`.

## Development

### Requirements

- Java 21
- Minecraft 1.21.1
- NeoForge 21.1.218

### Common commands

Use `gradlew.bat` on Windows and `./gradlew` on macOS/Linux.

```bash
gradlew runClient
gradlew test
gradlew runGameTestServer
```

## Validation

The repository already includes automated coverage for important subsystems.

- JUnit tests cover runtime guardrails, defaults, editor logic, screen layout, and no-code generation.
- NeoForge GameTests cover network topology, bus behavior, recovery flows, editor lease handoff, restart/load behavior, and related multiplayer/runtime edge cases.

## Near-term focus

The most sensible next steps from the current state are:

1. Keep refining the cooperative runtime and remaining edge cases around resume, recovery, and policy handling.
2. Expand structured XLAPI bridge behavior and its test coverage.
3. Continue deepening multi-step crafting and material-routing automation flows.
4. Improve multiplayer conflict UX where explicit takeover or more cooperative editing modes become useful.
5. Extend GameTests and scenario coverage for larger mixed topologies and additional bridge cases.
