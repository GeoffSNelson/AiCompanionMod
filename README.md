# AI Companion Mod

AI Companion Mod adds an AI-controlled companion entity to Minecraft. The companion can chat, follow players, help with tasks, and perform server-side actions such as building, mining, guarding, and gathering depending on its behavior logic.

## Current Compatibility

This port currently targets:

- Minecraft 1.20.1
- Fabric Loader
- Fabric API
- Java 17

It will not run on Forge/NeoForge without a separate loader port.

## Community Release Plan

Public releases should be shipped as separate jars for each Minecraft version and mod loader. The first recommended compatibility target is Minecraft 1.20.1 Fabric, followed by Minecraft 1.20.1 Forge or NeoForge for Create-focused CurseForge servers.

See [COMPATIBILITY.md](COMPATIBILITY.md) for the full roadmap.

## Development Setup

Use the included Gradle wrapper:

```powershell
.\gradlew.bat build
```

The built jar is written to `build/libs/`.

## License

See [LICENSE](LICENSE).
