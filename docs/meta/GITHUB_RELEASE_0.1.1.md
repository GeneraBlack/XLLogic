# XL Logic 0.1.1

Maintenance and quality-of-life update for Minecraft 1.21.1 on NeoForge.

This release ensures full CurseForge packaging compliance, resolves screen rendering and execution synchronization issues, and introduces an extensive responsive UI overhaul across all in-game menus.

## Highlights in 0.1.1

### CurseForge Compliance
- **Zero Prohibited Executables/Scripts**: Stripped unused native upstream launcher stubs (`graalpy.exe`, `python.exe`) and shell scripts (`activate.bat`, `idle.bat`, etc.) from the release JAR.
- The embedded GraalPy Python runtime remains fully functional with pure JVM execution.
- Added automated JAR manifest verification to the release pipeline.

### Runtime Responsiveness & Display Synchronization
- **Real-Time Execution Cadence**: Generator multi-tick steps (`sleep_ticks(N)`) now step with true 1-to-1 server tick cadence (20 ticks/sec), eliminating a previous 20-tick delay throttle.
- **Accurate Screen Status Badges**: Fixed client-side block entity deserialization that previously caused running programs to falsely display `[ERR]` on linked screens. Displays now correctly show the pulsing cyan `RUNNING` badge.
- **Multiblock Frustum Culling**: Screens spanning multiple blocks ($2\times2$, $3\times3$, etc.) now calculate full multiblock bounding boxes, preventing follower blocks from disappearing when looking away from the controller.
- **Action Bar Feedback**: Screen scrolling and focus interaction messages are now sent to the Action Bar rather than filling persistent chat.

### Responsive GUI Menus Across All Resolutions
- **No-Code Builder (`NoCodeBuilderScreen`)**:
  - Added vertical scrolling (`catalogScroll`, mouse wheel, and `^`/`v` navigation buttons) to the block catalog, making all 47 block types fully accessible.
  - Responsive template grid scales between 3 and 7 columns, reducing 4 rows of templates to 2 on standard viewports.
  - Dynamic proportional column widths prevent the detail panel from collapsing into negative dimensions on narrow screens.
  - Action buttons (`Up`, `Down`, `Copy`, `Delete`) and bottom footer buttons adapt without overlapping block list items.
- **Endpoint Configuration (`EndpointNamingScreen`)**:
  - Added an adaptive 2-column side alias layout for displays under 320px height (e.g. GUI Scale 4 / 270px or 240px), preventing Save/Cancel buttons from being displaced off the bottom edge.
  - Right-aligned row labels with EditBox text baselines.
- **Guide Book (`GuideBookScreen`)**:
  - Dynamically scalable chapter button heights and spacing ensure all 10 chapters remain visible on compact screens.
  - Bottom controls (`Previous`, `Next`, `Done`) adapt proportionally without overlapping.
- **Python Computer Screen (`PythonComputerScreen`)**:
  - Reorganized header spacing and added compact button labels to eliminate text-button overlap on narrow screens and diff views.
  - Balanced editor and terminal heights to prevent either panel from collapsing on low screen heights.

### Block & Item Polish
- Added missing red-colored redstone cable recipes, models, and blockstate definitions.
- Hardened client block entity packet synchronization across all named network devices.

## Installation

- Requirements: Minecraft 1.21.1, NeoForge 21.1.218, Java 21.
- Place `xllogic-0.1.1.jar` in your `.minecraft/mods` folder (both client and server).

## Links

- Website: https://xllogic.bls-isp.net
- Documentation: https://xllogic.bls-isp.net/docs.html
- Source Repository: https://github.com/GeneraBlack/XLLogic
