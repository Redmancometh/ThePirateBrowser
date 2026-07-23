$ErrorActionPreference = "Stop"

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$inputDirectory = Join-Path $projectRoot "target\app"
$outputDirectory = Join-Path $projectRoot "target\portable"
$mainJar = "the-pirate-browser.jar"
$portableDirectory = Join-Path $outputDirectory "ThePirateBrowser"
$portableArchive = Join-Path $projectRoot "target\ThePirateBrowser-windows-x64-portable.zip"
$exampleSettingsFile = Join-Path $projectRoot "data\settings.example.json"
$launcherScript = Join-Path $projectRoot "packaging\windows\Launch ThePirateBrowser.cmd"
$jpackageCommand = Get-Command "jpackage" -ErrorAction SilentlyContinue

function Remove-DirectoryWithRetry {
    param([Parameter(Mandatory)][string]$LiteralPath)
    if (-not (Test-Path -LiteralPath $LiteralPath)) {
        return
    }
    for ($attempt = 1; $attempt -le 10; $attempt++) {
        Remove-Item -LiteralPath $LiteralPath -Recurse -Force -ErrorAction SilentlyContinue
        if (-not (Test-Path -LiteralPath $LiteralPath)) {
            return
        }
        Start-Sleep -Milliseconds 500
    }
    throw "Could not clean directory after waiting for file handles: $LiteralPath"
}

if ($null -eq $jpackageCommand) {
    $javaProperties = & cmd.exe /d /c "java -XshowSettings:properties -version 2>&1"
    $javaHomeLine = $javaProperties | Where-Object { $_ -match "^\s*java\.home\s*=" } | Select-Object -First 1
    if ($null -eq $javaHomeLine) {
        throw "Could not locate the active JDK."
    }
    $javaHome = ($javaHomeLine -split "=", 2)[1].Trim()
    $jpackagePath = Join-Path $javaHome "bin\jpackage.exe"
} else {
    $jpackagePath = $jpackageCommand.Source
}

if (-not (Test-Path -LiteralPath $jpackagePath)) {
    throw "jpackage was not found in the active JDK: $jpackagePath"
}

if (-not (Test-Path -LiteralPath (Join-Path $inputDirectory $mainJar))) {
    throw "Application files are missing. Run 'mvn clean package' first."
}
if (-not (Test-Path -LiteralPath $exampleSettingsFile)) {
    throw "Required example settings are missing: $exampleSettingsFile"
}
if (-not (Test-Path -LiteralPath $launcherScript)) {
    throw "Portable launcher is missing: $launcherScript"
}

New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
if (Test-Path -LiteralPath $portableDirectory) {
    $expectedParent = [IO.Path]::GetFullPath($outputDirectory).TrimEnd('\') + '\'
    $resolvedPortableDirectory = [IO.Path]::GetFullPath($portableDirectory)
    if (-not $resolvedPortableDirectory.StartsWith($expectedParent, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to clean unexpected portable path: $resolvedPortableDirectory"
    }
    Remove-DirectoryWithRetry -LiteralPath $portableDirectory
}

& $jpackagePath `
    --type app-image `
    --name ThePirateBrowser `
    --app-version 1.0.0 `
    --vendor "The Pirate Browser" `
    --description "Local Pirate Bay search monitor and put.io client" `
    --input $inputDirectory `
    --dest $outputDirectory `
    --main-jar $mainJar `
    --main-class com.thepiratebrowser.Launcher `
    --java-options "-Dpiratebrowser.dataDir=`$APPDIR\..\data"

$portableDataDirectory = Join-Path $portableDirectory "data"
New-Item -ItemType Directory -Force -Path $portableDataDirectory | Out-Null
$portableSettingsFile = Join-Path $portableDataDirectory "settings.json"
Copy-Item -LiteralPath $exampleSettingsFile -Destination $portableSettingsFile -Force
Copy-Item -LiteralPath $launcherScript -Destination (Join-Path $portableDirectory "Launch ThePirateBrowser.cmd") -Force

$sourceSettingsHash = (Get-FileHash -LiteralPath $exampleSettingsFile -Algorithm SHA256).Hash
$portableSettingsHash = (Get-FileHash -LiteralPath $portableSettingsFile -Algorithm SHA256).Hash
if ($sourceSettingsHash -ne $portableSettingsHash) {
    throw "Packaged settings do not match data\settings.example.json."
}

Compress-Archive -Path $portableDirectory -DestinationPath $portableArchive -Force

$requiredFiles = @(
    "ThePirateBrowser.exe",
    "Launch ThePirateBrowser.cmd",
    "app\ThePirateBrowser.cfg",
    "app\the-pirate-browser.jar",
    "runtime\bin\server\jvm.dll",
    "runtime\lib\modules",
    "data\settings.json"
)
foreach ($relativeFile in $requiredFiles) {
    if (-not (Test-Path -LiteralPath (Join-Path $portableDirectory $relativeFile))) {
        throw "Portable package is incomplete; missing $relativeFile"
    }
}

$launcherConfig = Get-Content -Raw -LiteralPath (Join-Path $portableDirectory "app\ThePirateBrowser.cfg")
if (-not $launcherConfig.Contains('java-options=-Dpiratebrowser.dataDir=$APPDIR\..\data')) {
    throw "Portable launcher is not wired to its bundled data directory."
}

$smokeRoot = Join-Path $projectRoot "target\portable smoke test"
$brokenRoot = Join-Path $projectRoot "target\broken launcher test"
foreach ($temporaryRoot in @($smokeRoot, $brokenRoot)) {
    if (Test-Path -LiteralPath $temporaryRoot) {
        Remove-DirectoryWithRetry -LiteralPath $temporaryRoot
    }
    New-Item -ItemType Directory -Path $temporaryRoot | Out-Null
}

try {
    Expand-Archive -LiteralPath $portableArchive -DestinationPath $smokeRoot
    $smokeLauncher = Join-Path $smokeRoot "ThePirateBrowser\Launch ThePirateBrowser.cmd"
    $smokeMarker = Join-Path $smokeRoot "ready.marker"
    $previousSmokeMarker = $env:PIRATE_BROWSER_SMOKE_MARKER
    $env:PIRATE_BROWSER_SMOKE_MARKER = $smokeMarker
    try {
        $smokeProcess = Start-Process -FilePath $smokeLauncher -WorkingDirectory $env:TEMP -WindowStyle Hidden -PassThru
    } finally {
        $env:PIRATE_BROWSER_SMOKE_MARKER = $previousSmokeMarker
    }
    for ($attempt = 1; $attempt -le 60 -and -not (Test-Path -LiteralPath $smokeMarker); $attempt++) {
        Start-Sleep -Milliseconds 250
    }
    $smokeProcesses = @(Get-Process -Name "ThePirateBrowser" -ErrorAction SilentlyContinue |
        Where-Object { $_.Path -and $_.Path.StartsWith($smokeRoot, [StringComparison]::OrdinalIgnoreCase) })
    if ($smokeProcesses.Count -eq 0) {
        $exitDetail = if ($smokeProcess.HasExited) { " with code $($smokeProcess.ExitCode)" } else { "" }
        throw "Packaged application did not remain running after startup$exitDetail."
    }
    $smokeProcessIds = $smokeProcesses.Id
    Stop-Process -Id $smokeProcessIds -Force
    Wait-Process -Id $smokeProcessIds -ErrorAction SilentlyContinue
    if (-not (Test-Path -LiteralPath $smokeMarker)) {
        throw "Packaged application never reported that its JavaFX stage was ready."
    }
    $smokeLines = @(Get-Content -LiteralPath $smokeMarker)
    $expectedSmokeSettings = "settingsFile=" +
        [IO.Path]::GetFullPath((Join-Path $smokeRoot "ThePirateBrowser\data\settings.json"))
    if ($smokeLines -notcontains "READY" -or
        $smokeLines -notcontains $expectedSmokeSettings -or
        $smokeLines -notcontains "tokenConfigured=false" -or
        $smokeLines -notcontains "clientSecretConfigured=false") {
        throw "Packaged application did not load its sanitized example settings: $($smokeLines -join '; ')"
    }

    $brokenLauncher = Join-Path $brokenRoot "Launch ThePirateBrowser.cmd"
    Copy-Item -LiteralPath $launcherScript -Destination $brokenLauncher
    $previousNoninteractive = $env:PIRATE_BROWSER_NONINTERACTIVE
    $env:PIRATE_BROWSER_NONINTERACTIVE = "1"
    try {
        $brokenOutput = & cmd.exe /d /c "`"$brokenLauncher`"" 2>&1
    } finally {
        $env:PIRATE_BROWSER_NONINTERACTIVE = $previousNoninteractive
    }
    if ($LASTEXITCODE -eq 0 -or ($brokenOutput -join "`n") -notmatch "entire folder") {
        throw "Incomplete-folder launcher diagnostic did not work."
    }
} finally {
    foreach ($temporaryRoot in @($smokeRoot, $brokenRoot)) {
        if (Test-Path -LiteralPath $temporaryRoot) {
            Remove-DirectoryWithRetry -LiteralPath $temporaryRoot
        }
    }
}

Write-Host "Portable application created at $portableDirectory"
Write-Host "Portable ZIP created at $portableArchive"
Write-Host "Portable structure, sanitized settings, ZIP extraction, launch, and misuse diagnostics verified."
