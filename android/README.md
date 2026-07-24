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

An API token is optional. With no token, tapping a result opens put.io's browser
handoff, where the user signs into put.io and confirms the magnet.

Builds configured with `PUTIO_CLIENT_ID` also offer **Link with put.io**. That
shows a short code, opens `https://put.io/link`, and saves the token returned by
put.io after approval. This enables direct transfers and private account
features without asking the user to create an API app or paste a key. No client
secret is used. A manual OAuth-token field remains available as a fallback.

Torrent sources can be enabled or disabled from **Sources**.
