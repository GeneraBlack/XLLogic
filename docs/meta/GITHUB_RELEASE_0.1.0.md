# XL Logic 0.1.0

First public prototype release for Minecraft 1.21.1 on NeoForge.

XL Logic explores ComputerCraft-style automation with Python instead of Lua, visible cable-based networking, individually targetable in-world screens, and endpoint-driven automation blocks.

## Highlights

- Server-authoritative GraalPy runtime for in-world computers
- Sandboxed Python environment with explicit XL Logic APIs instead of unrestricted host access
- In-game Python editor with syntax highlighting, suggestions, diagnostics, copy and paste, undo and redo, and structured output history
- Rich screen rendering with line output, key/value cards, tables, plan cards, and multiblock displays
- Visible cable-first device discovery and XLAPI bridge blocks for cross-segment access
- Individually targetable named screens
- Redstone, sensor, Material I/O, Crafting I/O, Crafting CPU, and XLAPI device APIs
- Direct item and fluid transfer between named Material I/O endpoints
- No-code builder that generates real Python for the same runtime
- Multiplayer-aware editor leasing, read-only viewers, recovery drafts, and resume support

## Included In This Prototype

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

## Installation

- Use the bundled XL Logic jar from the release assets.
- Install the same jar on the client and on the dedicated server.
- Requirements: Minecraft 1.21.1, NeoForge 21.1.218, Java 21.

See INSTALL.md for the short setup guide.

## Links

- Website: https://xllogic.bls-isp.net
- Documentation hub: https://xllogic.bls-isp.net/docs.html
- Source repository: https://github.com/GeneraBlack/XLLogic

## Current State

This is a real playable prototype, not a finished content-complete release. The core runtime, editor, screens, networking, routing, no-code builder, and several block families are already usable, but the project is still actively evolving.