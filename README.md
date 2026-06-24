# AI Companion Mod

AI Companion Mod adds an AI-controlled companion entity to Minecraft. The companion can chat, follow players, help with tasks, and perform server-side actions such as building, mining, guarding, and gathering depending on its behavior logic.

## Current Compatibility

This release branch targets:

- Minecraft `1.20.1`
- Fabric Loader
- Fabric API
- Java `17`

It has also been tested on a Forge `1.20.1` dedicated server through Sinytra Connector with Forgified Fabric API. That is the recommended Forge path until a native Forge/NeoForge jar exists.

## Community Release Plan

Public releases should be shipped as separate jars for each Minecraft version and mod loader. The first recommended compatibility target is Minecraft 1.20.1 Fabric, followed by Minecraft 1.20.1 Forge or NeoForge for Create-focused CurseForge servers.

See [COMPATIBILITY.md](COMPATIBILITY.md) for the full roadmap.

See [INSTALL.md](INSTALL.md) for server-owner setup instructions.

## Development Setup

Use the included Gradle wrapper:

```powershell
.\gradlew.bat build
```

The built jar is written to `build/libs/`.

To create a server-owner bundle with the mod jar, Brain files, and install docs:

```powershell
.\scripts\build_release.ps1
```

The bundle is written to `release/`.

## AI Companion Brain

The mod expects the Brain dashboard/server to run beside the Minecraft server:

```powershell
cd AICompanionBrain
.\start_brain.bat
```

The dashboard opens at `http://127.0.0.1:8080/dashboard`.

The default LLM provider is local Ollama at `http://localhost:11434/v1` using model `llama3`. You can override it with:

- `AI_COMPANION_LLM_BASE_URL`
- `AI_COMPANION_LLM_API_KEY`
- `AI_COMPANION_LLM_MODEL`

## License

See [LICENSE](LICENSE).
