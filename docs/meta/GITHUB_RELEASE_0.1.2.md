# XL Logic 0.1.2

Hotfix release addressing NeoForge mod loading failure with Truffle Multi-Release JAR configuration.

## Highlights in 0.1.2

### Truffle & GraalPy Multi-Release Fix
- **Multi-Release Manifest Attribute**: Added `Multi-Release: true` to `META-INF/MANIFEST.MF` in the Shadow JAR distribution. This resolves the `InternalError: Truffle could not be initialized because Multi-Release classes are not configured correctly` crash that occurred when loaded by NeoForge/ModLauncher.
- **Fail-Safe JVM Initialization**: Programmatically set `-Dpolyglotimpl.DisableMultiReleaseCheck=true` during mod initialization, runtime factory initialization, and shared engine bootstrap to guarantee compatibility across diverse modpack launchers and custom classloaders.
- **Resilient Error Recovery**: Hardened runtime factory catch blocks to catch `Throwable` (preventing unhandled `VirtualMachineError` / `InternalError` from crashing client mod loading).
- **Lazy Runtime Loading**: Deferred server Python runtime initialization until execution is actually requested, preventing unnecessary polyglot engine overhead during early NeoForge mod construction.

## Installation

- Requirements: Minecraft 1.21.1, NeoForge 21.1.218+, Java 21.
- Place `xllogic-0.1.2.jar` in your `.minecraft/mods` folder (both client and server).

## Links

- Website: https://xllogic.bls-isp.net
- Documentation: https://xllogic.bls-isp.net/docs.html
- Source Repository: https://github.com/GeneraBlack/XLLogic
