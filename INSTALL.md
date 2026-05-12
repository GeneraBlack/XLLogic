# XL Logic Installation

This file describes how to install XL Logic on a normal Minecraft client and on a dedicated server.

Public project website: https://xllogic.bls-isp.net

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.218
- Java 21

XL Logic is not a client-only mod. If you want to join a server that uses it, both sides need the mod.

## Which JAR to use

Build the project with:

```bash
gradlew.bat build
```

The build now produces a dedicated release artifact with bundled GraalPy libraries:

- `build/libs/xllogic-0.1.0.jar`

Use that jar for installation. The build is configured so the release artifact is the bundled artifact.

## Client installation

1. Install Minecraft 1.21.1 with NeoForge 21.1.218.
2. Copy `build/libs/xllogic-0.1.0.jar` into your client `mods` folder.
3. Start the game with Java 21.

No separate Python installation is required.

## Dedicated server installation

1. Set up a Minecraft 1.21.1 NeoForge server on Java 21.
2. Copy the same `build/libs/xllogic-0.1.0.jar` into the server `mods` folder.
3. Start the server once so NeoForge creates the config files.
4. If needed, review `config/xllogic-server.toml` on the server after the first start.

## Multiplayer note

Clients connecting to a server that runs XL Logic should also have the same XL Logic mod version installed locally.

## Summary

For a normal install, the intended path is:

- Client: add `xllogic-0.1.0.jar` to the client `mods` folder
- Server: add `xllogic-0.1.0.jar` to the server `mods` folder

If Minecraft, NeoForge, Java, and the XL Logic mod version match, that is the correct installation setup.