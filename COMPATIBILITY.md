# AI Companion Mod Compatibility Roadmap

This branch currently builds for Fabric on Minecraft 1.20.1. Public releases should be published as separate files per Minecraft version and mod loader.

## Recommended Release Targets

Start with the versions most useful to server owners:

| Priority | Minecraft | Loader | Reason |
| --- | --- | --- | --- |
| 1 | 1.20.1 | Fabric | Current working target and useful for Fabric Create packs. |
| 2 | 1.20.1 | Forge via Sinytra Connector | Tested path for CurseForge Create packs before a native Forge jar exists. |
| 3 | 1.20.1 | Native Forge or NeoForge | Important for broader server adoption if community demand is high. |
| 4 | 1.21.x | Fabric | Keeps the current line available for newer Fabric servers. |
| 5 | 1.21.x | NeoForge | Useful if the community asks for modern non-Fabric support. |

## Current Build

- Minecraft: 1.20.1
- Loader: Fabric
- Java: 17
- Mod id: `aicompanion`
- Artifact: `AICompanionMod-1.20.1-fabric-1.0.0.jar`

## Tested Forge/Connector Setup

- Minecraft: 1.20.1
- Loader: Forge
- Bridge: Sinytra Connector
- Dependency: Forgified Fabric API
- Mod jar: `AICompanionMod-1.20.1-fabric-1.0.0.jar`
- Brain folder: copied into the server root beside `server.properties`

This setup was tested on a dedicated CurseForge-style server. Server owners still need the matching Connector and Forgified Fabric API files for their Minecraft version.

## Version-Sensitive Areas

These parts are likely to change between Minecraft or loader targets:

- Gradle/Loom setup and dependency versions.
- `fabric.mod.json` loader and Minecraft version constraints.
- Java version requirements. Minecraft 1.20.1 typically targets Java 17.
- Identifier construction such as `Identifier.of(...)`.
- Registry APIs for entities and items.
- Item settings that use registry keys.
- Entity save/load APIs such as `ReadView` and `WriteView`.
- Food checks through data components such as `DataComponentTypes.FOOD`.
- Client renderer APIs and render state classes.
- Player list packet construction and mixin accessors.
- Fabric event hooks for commands, chat, server lifecycle, ticks, and entity loading.
- Loader-specific config path lookup through `FabricLoader`.

## Suggested Branch / Folder Strategy

Use one of these approaches:

1. Keep separate branches:
   - `mc1.21.11-fabric`
   - `mc1.20.1-fabric`
   - `mc1.20.1-forge`

2. Use a multi-loader project layout:
   - `common/`
   - `fabric/`
   - `forge/` or `neoforge/`

For this project, separate branches are the simplest first step. A multi-loader layout becomes worthwhile after the first public version is stable.

## Completed Port: 1.20.1 Fabric

The 1.20.1 Fabric port is the current working release branch.

Completed changes:

- Set `minecraft_version=1.20.1`.
- Use Java 17 in Gradle.
- Replace APIs introduced after 1.20.1.
- Update Yarn mappings, Fabric Loader, Fabric API, and Loom versions.
- Convert entity custom data persistence back to the 1.20.1 NBT APIs.
- Replace data-component food checks with 1.20.1 item food APIs.
- Adjust renderer code to the 1.20.1 renderer signatures.
- Verify commands, chat events, entity registration, player-list packets, dashboard sync, and chat replies on a dedicated server.

## Release Checklist

Before uploading to CurseForge or Modrinth:

- Build a clean jar for each target version.
- Test dedicated server startup.
- Test client startup when the mod is installed client-side.
- Spawn a companion with the item and command.
- Test chat interaction.
- Test follow, guard, mining, building, eating, and respawn behavior.
- Confirm generated config/data files are documented.
- Run `.\scripts\build_release.ps1` and inspect the generated `release/` bundle.
- Add screenshots or short clips.
- Add install instructions for server owners.
- Add known limitations, especially around AI API configuration and world-changing actions.
- Label files with Minecraft version and loader, for example:
  - `AICompanionMod-1.0.0+mc1.20.1-fabric.jar`
  - `AICompanionMod-1.0.0+mc1.20.1-forge.jar`
