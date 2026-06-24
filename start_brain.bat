@echo off
setlocal
cd /d "%~dp0"

where python >nul 2>nul
if errorlevel 1 (
    echo [AI Companion Brain] Python was not found on PATH.
    echo Install Python 3.11 or newer, then run this file again.
    pause
    exit /b 1
)

if not exist ".venv\Scripts\python.exe" (
    echo [AI Companion Brain] Creating local Python environment...
    python -m venv .venv
    if errorlevel 1 (
        echo [AI Companion Brain] Failed to create .venv.
        pause
        exit /b 1
    )
)

echo [AI Companion Brain] Installing/updating dependencies...
".venv\Scripts\python.exe" -m pip install --upgrade pip
".venv\Scripts\python.exe" -m pip install -r requirements.txt
if errorlevel 1 (
    echo [AI Companion Brain] Dependency install failed.
    pause
    exit /b 1
)

echo [AI Companion Brain] Starting dashboard at http://127.0.0.1:8080/dashboard
".venv\Scripts\python.exe" mock_llm_server.py
pause
