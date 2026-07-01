param(
    [string]$ReleaseRoot = "release"
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

$props = @{}
Get-Content "gradle.properties" | ForEach-Object {
    if ($_ -match "^\s*([^#][^=]+?)\s*=\s*(.+?)\s*$") {
        $props[$matches[1].Trim()] = $matches[2].Trim()
    }
}

$minecraftVersion = $props["minecraft_version"]
$modVersion = $props["mod_version"]
$modLoader = $props["mod_loader"]
$bundleName = "AICompanionMod-$modVersion-mc$minecraftVersion-$modLoader-server-bundle"
$bundleDir = Join-Path $repoRoot (Join-Path $ReleaseRoot $bundleName)
$modsDir = Join-Path $bundleDir "mods"
$brainDir = Join-Path $bundleDir "AICompanionBrain"

Write-Host "[release] Building mod jar..."
& .\gradlew.bat clean build

if (Test-Path $bundleDir) {
    Remove-Item -Recurse -Force $bundleDir
}

New-Item -ItemType Directory -Force $modsDir | Out-Null
New-Item -ItemType Directory -Force $brainDir | Out-Null

$jar = Get-ChildItem "build\libs" -Filter "AICompanionMod-$minecraftVersion-$modLoader-$modVersion.jar" | Select-Object -First 1
if (-not $jar) {
    throw "Could not find built mod jar in build\libs."
}

Copy-Item $jar.FullName $modsDir
Copy-Item "mock_llm_server.py" $brainDir
Copy-Item "requirements.txt" $brainDir
Copy-Item "start_brain.bat" $brainDir
Copy-Item "INSTALL.md" $bundleDir
Copy-Item "COMPATIBILITY.md" $bundleDir
Copy-Item "README.md" $bundleDir

$notes = @"
AI Companion Mod $modVersion for Minecraft $minecraftVersion

Install:
1. Copy the jar from this bundle's mods folder into your server mods folder.
2. Copy AICompanionBrain into the main server folder, next to server.properties.
3. Run AICompanionBrain\start_brain.bat before starting or testing companions.
4. Open http://127.0.0.1:8080/dashboard for the UI.

This jar requires Minecraft $minecraftVersion, NeoForge, and Java 21.
Install the same jar in each connecting client's modpack.
"@

Set-Content -Path (Join-Path $bundleDir "QUICK_START.txt") -Value $notes -Encoding UTF8

Write-Host "[release] Bundle ready:"
Write-Host "  $bundleDir"
