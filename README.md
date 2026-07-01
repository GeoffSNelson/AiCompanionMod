# AI Companion Mod

AI Companion Mod adds an AI-controlled companion entity to Minecraft. The companion can chat, follow players, help with tasks, and perform server-side actions such as building, mining, guarding, and gathering depending on its behavior logic.

## Current Compatibility

This release branch targets:

- Minecraft `1.21.1`
- NeoForge `21.1.233` or newer
- Java `21`

This branch is a native NeoForge release. It has been tested directly in All of Create Aeronautics 2.0.

## Community Release Plan

Public releases are shipped as separate branches and jars for each Minecraft version and loader. The existing `mc1.20.1-fabric` branch remains available for older servers.

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
