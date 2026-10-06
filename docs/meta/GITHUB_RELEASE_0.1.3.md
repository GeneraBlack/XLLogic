# XL Logic 0.1.3

Hotfix release resolving Python standard library unpack failures on clean client/server installations.

## Highlights in 0.1.3

### Python Standard Library Unpack Fix
- **Clean Installation Compatibility**: Resolved an issue where initial mod startup on fresh environments failed to unpack Python standard library modules (resulting in `ModuleNotFoundError: No module named 'json'` and missing path warnings).
- **Manifest Post-Processing**: Added automated build-time filtering for GraalPy internal resource manifests (`libpython.files` and `native.files`). References to excluded platform binaries and shell scripts are cleanly removed, preventing `NullPointerException` during Truffle's resource unpack routine.
- **Resource Exclude Safety Pattern**: Configured `org.graalvm.python.resources.exclude` across mod initialization, runtime factory, shared polyglot engine, and development runs to reliably ignore excluded executables.
- **100% CurseForge Compliant**: Maintained strict compliance with CurseForge moderation standards by ensuring zero `.exe`, `.bat`, `.cmd`, `.ps1`, `.sh`, `.csh`, or `.fish` files are bundled in the JAR.

## Installation

- Requirements: Minecraft 1.21.1, NeoForge 21.1.218+, Java 21.
- Place `xllogic-0.1.3.jar` in your `.minecraft/mods` folder (both client and server).

## Links

- Website: https://xllogic.bls-isp.net
- Documentation: https://xllogic.bls-isp.net/docs.html
- Source Repository: https://github.com/GeneraBlack/XLLogic
