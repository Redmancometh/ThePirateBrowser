# Pirate Browser for iPhone

This directory contains the native SwiftUI iPhone client. It mirrors the main
Android navigation and capabilities:

- multi-source torrent search and minimum-seeder filtering;
- saved searches with run, check, edit, pause, and delete controls;
- manual put.io OAuth-token entry as the default setup path;
- Keychain-only token storage;
- put.io transfer refresh/cancel and file browse/play/share/rename/delete;
- native `AVPlayer` playback with Picture in Picture and AirPlay;
- configurable torrent sources and a visible release canary.

No OAuth token or private client secret is compiled into the application.

## Generate and test

Xcode 16 or newer and XcodeGen are required:

```bash
cd ios
brew install xcodegen
xcodegen generate
xcodebuild test \
  -project PirateBrowser.xcodeproj \
  -scheme PirateBrowser \
  -destination 'platform=iOS Simulator,name=iPhone 16 Pro'
```

The generated `.xcodeproj` is intentionally not committed. GitHub Actions
generates it and publishes an unsigned simulator artifact on every build.

## Install on a physical iPhone

A real-device or TestFlight build must be signed through an Apple Developer
team. After opening the generated project:

1. Select the `PirateBrowser` target.
2. Open **Signing & Capabilities**.
3. Select the Apple Developer team and use a unique bundle identifier if
   `com.thepiratebrowser.ios` is unavailable.
4. Run on a connected iPhone, or archive and upload through Xcode Organizer.

App Store Connect API credentials and signing certificates are deliberately not
stored in this repository.
