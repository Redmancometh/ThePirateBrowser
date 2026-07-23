# The Pirate Browser

A local JavaFX client for keeping up with saved Pirate Bay searches and sending
selected magnet links to put.io.

## Requirements for development

- JDK 21
- Maven 3.9+

The packaged application includes its own Java runtime.

## Run

```powershell
Copy-Item data/settings.example.json data/settings.json
mvn clean javafx:run
```

Put your local put.io credentials in `data/settings.json`. That file is ignored
by Git; only the credential-free `data/settings.example.json` template is
committed. Set `-Dpiratebrowser.dataDir=...` to use a different location.

## Test

```powershell
mvn test
```

## Build a Windows portable application

```powershell
mvn clean package
powershell -ExecutionPolicy Bypass -File packaging/windows/build-portable.ps1
```

The portable application is written to `target/portable/ThePirateBrowser`, and
the distributable archive is written to
`target/ThePirateBrowser-windows-x64-portable.zip`.

Extract the complete ZIP, keep its folder intact, and run
`Launch ThePirateBrowser.cmd`. The EXE depends on the adjacent `app` and `runtime`
directories and cannot be copied or launched by itself.

The portable download starts with a credential-free `data/settings.json`.
Enter your put.io token in the application's Preferences screen after launch.

## Automated Windows downloads

GitHub Actions builds and tests the Maven project on a GitHub-hosted Windows
runner after every push to `main` and for manual workflow runs. Download the
`ThePirateBrowser-windows-x64` artifact from the workflow run.

Pushing a tag such as `v1.0.0` also creates a GitHub Release containing the
portable ZIP and its SHA-256 checksum.
