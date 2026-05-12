# XL Logic CurseForge Copy

## Short Description

Python-powered automation for Minecraft 1.21.1 on NeoForge, with cable-based networking, in-world screens, Material I/O routing, crafting automation, and an optional no-code builder.

## Full Description

XL Logic is a NeoForge automation mod for Minecraft 1.21.1 that takes the general ComputerCraft idea in a different direction: Python instead of Lua, visible cable-based networking instead of invisible device reach, and a stronger focus on in-world feedback through named screens and endpoint-driven automation.

The mod already includes a substantial playable prototype with these core systems:

- Server-authoritative GraalPy runtime for in-world computers
- In-game Python editor with syntax highlighting, suggestions, diagnostics, copy/paste, undo/redo, and output history
- Rich screen rendering with line output, key/value cards, tables, plan cards, and multiblock displays
- Visible cable-based network discovery with XLAPI bridge blocks for cross-segment access
- Individually targetable named screens
- Device APIs for redstone, sensors, Material I/O, Crafting I/O, Crafting CPU, and XLAPI messaging/status calls
- Direct item and fluid transfer between named Material I/O endpoints
- A no-code builder for beginners that generates real Python and targets the same runtime

XL Logic is also designed around runtime safety. Python scripts run through explicit XL Logic APIs rather than unrestricted host access. The embedded runtime intentionally blocks host OS access, local filesystem access, process launching, direct Java interop, and direct server-admin command paths.

Current requirements:

- Minecraft 1.21.1
- NeoForge 21.1.218
- Java 21

For normal installation on client and dedicated server, use the bundled XL Logic jar from the release build.

Project website:

https://xllogic.bls-isp.net

## Suggested CurseForge Page Sections

### Why XL Logic?

XL Logic is built around the idea that automation should feel physical and inspectable. Computers, screens, sensors, buses, bridges, crafting devices, and material routing are all represented as visible blocks inside the world instead of hidden background logic.

### Current Highlights

- Python instead of Lua
- Real cable segments and named endpoints
- Rich in-world screens
- Direct source-to-sink Material I/O routes
- Multiplayer-aware editor sessions
- Optional no-code builder for non-programmers

### Current Project State

XL Logic is still an active prototype, but it already supports real gameplay experiments, automation setups, and structured in-world control flows.