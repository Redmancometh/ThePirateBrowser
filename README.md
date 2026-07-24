# Pirate Browser

A local JavaFX client for searching several torrent indexes, keeping up with
saved searches, and sending selected magnet links to put.io.

## Torrent sources

Searches run concurrently across all enabled sources and are normalized into
one results table. The **Source** column shows where each row came from, while
duplicate info hashes are collapsed to the result with the strongest seeder
count.

The built-in sources are:

- The Pirate Bay
- Nyaa
- EZTV
- YTS

Open **Settings → Torrent sources** to enable or disable each source. The same
selection is used for manual searches and automatically monitored saved
searches. If one source is unavailable, results from working sources are still
shown and the status bar identifies the unavailable source. EZTV's public API
exposes its latest 100 releases, so EZTV matching is limited to that current
window.

## Requirements for development

- JDK 21
- Maven 3.9+

The packaged application includes its own Java runtime.

## Run

```powershell
Copy-Item data/settings.example.json data/settings.json
mvn clean javafx:run
```

Local preferences and the optional put.io connection are stored in
`data/settings.json`. That file is ignored by Git; only the credential-free
`data/settings.example.json` template is committed. Set
`-Dpiratebrowser.dataDir=...` to use a different location.

## Connect put.io

No connection is required to search or send a magnet through put.io's browser
handoff. Sign into put.io in the browser and confirm the transfer there.

For transfer status, files, embedded playback, and casting, click
**Connect put.io**. Paste your private OAuth token, or use the optional linking
wizard. Builds configured with the project's public `PUTIO_CLIENT_ID` show a
short code and open `https://put.io/link`; approve the code and Pirate Browser
finishes connecting automatically. There is no callback URL, local port, or
client secret.

The resulting OAuth token is private account authorization, so Pirate Browser stores it
only in the local settings file. It is never built into distributed packages.
Developers can enable device linking by setting the public `PUTIO_CLIENT_ID`
environment variable before packaging.

## Test

```powershell
mvn test
```

## Build a Windows portable application

```powershell
mvn clean package
powershell -ExecutionPolicy Bypass -File packaging/windows/build-portable.ps1
```

The portable application is written to `target/portable/PirateBrowser`, and
the distributable archive is written to
`target/PirateBrowser-windows-x64-portable.zip`.

Extract the complete ZIP, keep its folder intact, and run
`Launch Pirate Browser.cmd`. The EXE depends on the adjacent `app` and `runtime`
directories and cannot be copied or launched by itself.

The portable download starts with a credential-free `data/settings.json`.
Link put.io from the application after launch if you want account features.

## Android APK

An installable Android debug APK is built by GitHub Actions. Download
`PirateBrowser-android-debug.apk` from the repository's **Latest builds**
Release, copy it to an Android device, and open it to install. Android may ask
you to allow installs from the browser or file manager you used to open the
APK.

This APK uses Android's development signing key so it can be installed directly
for testing. It is not a Google Play production release.

For a local Android build:

```powershell
cd android
.\gradlew.bat assembleDebug
```

The original APK is written under
`android/app/build/outputs/apk/debug/`.

## Automated downloads

GitHub Actions builds and tests the Maven desktop project, packages the Windows
application, and builds the Android APK on one GitHub-hosted Windows runner
after every push to `main` and for manual workflow runs. Every successful
`main` build replaces the **Latest builds** GitHub Release with all four files:

- `PirateBrowser-windows-x64-portable.zip`
- `PirateBrowser-windows-x64-portable.zip.sha256`
- `PirateBrowser-android-debug.apk`
- `PirateBrowser-android-debug.apk.sha256`

Building and publishing both platforms in one job ensures a Release is never
replaced with only one platform's files. The same files are also available as
the `PirateBrowser-windows-x64` and `PirateBrowser-android-debug` workflow artifacts.

Pushing a tag such as `v1.0.0` also creates a GitHub Release containing the
Windows portable ZIP, Android APK, and both SHA-256 checksums.
