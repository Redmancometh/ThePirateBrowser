# Pirate Browser for Android

This is the Android companion app. It searches all enabled torrent sources at
once, normalizes the results, and sends a selected magnet link to put.io.

## Build

Requirements:

- JDK 17 or newer
- Android SDK Platform 35 and Build Tools 35.0.0

From this directory:

```powershell
.\gradlew.bat assembleDebug
```

The APK is written to:

```text
app\build\outputs\apk\debug\app-debug.apk
```

For a release build, configure your own Android signing setup and run:

```powershell
.\gradlew.bat assembleRelease
```

To enable put.io's device-link flow, pass the application's public put.io client
ID at build time (this is not a secret):

```powershell
.\gradlew.bat -PPUTIO_CLIENT_ID=your_public_client_id assembleDebug
```

## Connect put.io

The put.io tab leads with a manual **OAuth token** field. Paste a token there to
enable transfer status, file browsing, playback, sharing/casting, rename, and
delete controls. The token is stored only in this app's private preferences; it
is never built into the APK.

Builds configured with `PUTIO_CLIENT_ID` also offer the **put.io linking
wizard** below the manual field. It shows a short code, opens
`https://put.io/link`, and saves the token returned by put.io after approval.
No client secret is used.

The bottom navigation keeps **Search**, **Saved**, **put.io**, and **Sources**
available at all times. Saved searches can be run, paused, edited, or deleted;
sources can be enabled or disabled immediately.
