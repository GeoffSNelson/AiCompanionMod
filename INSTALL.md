# AI Companion Install Guide

AI Companion has two parts:

- The Minecraft mod jar, installed in the server `mods` folder.
- The AI Companion Brain, a small local Python dashboard/server that the mod talks to on port `8080`.

## Minecraft 1.21.1 NeoForge

1. Install NeoForge for Minecraft `1.21.1` and use Java `21`.
2. Copy `mods/AICompanionMod-1.21.1-neoforge-1.1.0.jar` into the server `mods` folder and the matching client modpack `mods` folder.
3. No Fabric API, Forgified Fabric API, or Connector dependency is required by AI Companion.
4. Copy the `AICompanionBrain` folder into the main server folder, next to `server.properties`.
5. Start the Brain with `AICompanionBrain\start_brain.bat`.
6. Start the Minecraft server.
7. Open the dashboard at `http://127.0.0.1:8080/dashboard`.

## Brain Requirements

- Python `3.11` or newer.
- Internet access the first time `start_brain.bat` installs Python packages.
- A local or hosted OpenAI-compatible chat model.

By default, the Brain uses Ollama:

- Base URL: `http://localhost:11434/v1`
- Model: `llama3`
- API key value: `ollama`

For the default setup, install Ollama, pull the model, and keep Ollama running:

```powershell
ollama pull llama3
```

To use OpenAI instead, set these environment variables before starting the Brain:

```powershell
$env:AI_COMPANION_LLM_BASE_URL = "https://api.openai.com/v1"
$env:AI_COMPANION_LLM_API_KEY = "your_api_key_here"
$env:AI_COMPANION_LLM_MODEL = "gpt-4o-mini"
.\start_brain.bat
```

## Generated Files

The Brain and mod create these files while the server runs:

- `AICompanionBrain\bot_state.json`
- `AICompanionBrain\player_memory.json`
- `AICompanionBrain\server_settings.json`
- `config\aicompanion_companions.json`
- `config\aicompanion_schematics.json`

These are server state files. Keep them when moving a world/server if you want the companions to remember players and keep dashboard state.

## Troubleshooting

- If the dashboard says `Not Found`, use `http://127.0.0.1:8080/dashboard`.
- If Python says `No module named openai`, run `start_brain.bat` instead of running `mock_llm_server.py` directly.
- If companions do not answer in chat, confirm the Brain terminal shows `POST /api/npc/chat` and the Minecraft server log does not show a connection timeout.
- Confirm the server and client both use the NeoForge 1.21.1 jar; the older Fabric 1.20.1 jar cannot be mixed with it.
