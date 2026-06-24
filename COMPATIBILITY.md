# AI Companion Mod Compatibility Roadmap

This mod currently builds for Fabric on Minecraft 1.21.11. Public releases should be published as separate files per Minecraft version and mod loader.

## Recommended Release Targets

Start with the versions most useful to server owners:

| Priority | Minecraft | Loader | Reason |
| --- | --- | --- | --- |
| 1 | 1.20.1 | Fabric | Closest port from the current Fabric code and useful for Fabric Create packs. |
| 2 | 1.20.1 | Forge or NeoForge | Important for CurseForge Create packs and broader server adoption. |
| 3 | 1.21.x | Fabric | Keeps the current line available for newer Fabric servers. |
| 4 | 1.21.x | NeoForge | Useful if the community asks for modern non-Fabric support. |

## Current Build

- Minecraft: 1.21.11
- Loader: Fabric
- Java: 21
- Mod id: `aicompanion`
- Artifact: `AICompanionMod-1.0.0.jar`

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

## First Port: 1.20.1 Fabric

Porting to 1.20.1 Fabric should happen before Forge/NeoForge because it keeps the same loader family.

Expected changes:

- Set `minecraft_version=1.20.1`.
- Use Java 17 in Gradle.
- Replace APIs introduced after 1.20.1.
- Update Yarn mappings, Fabric Loader, Fabric API, and Loom versions.
- Convert entity custom data persistence back to the 1.20.1 NBT APIs.
- Replace data-component food checks with 1.20.1 item food APIs.
- Adjust renderer code to the 1.20.1 renderer signatures.
- Verify commands, chat events, entity registration, and player-list packets compile and work.

## Release Checklist

Before uploading to CurseForge or Modrinth:

- Build a clean jar for each target version.
- Test dedicated server startup.
- Test client startup when the mod is installed client-side.
- Spawn a companion with the item and command.
- Test chat interaction.
- Test follow, guard, mining, building, eating, and respawn behavior.
- Confirm generated config/data files are documented.
- Add screenshots or short clips.
- Add install instructions for server owners.
- Add known limitations, especially around AI API configuration and world-changing actions.
- Label files with Minecraft version and loader, for example:
  - `AICompanionMod-1.0.0+mc1.20.1-fabric.jar`
  - `AICompanionMod-1.0.0+mc1.20.1-forge.jar`

