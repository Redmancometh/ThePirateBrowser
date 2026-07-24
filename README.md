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

## Connect put.io

On the first launch, the built-in **Connect put.io** wizard opens automatically.
You can reopen it at any time from the button in the application header.

1. In the wizard, click **Open put.io API apps**. You can also visit
   [https://app.put.io/oauth](https://app.put.io/oauth) directly.
2. Sign in, choose **Create App**, and give the app a unique name.
3. If put.io requests these fields, use:
   - Application website:
     `https://github.com/Redmancometh/ThePirateBrowser`
   - Callback URL: `http://127.0.0.1:8765/callback`
4. Save the app, click its key icon, and open the Secrets page.
5. Copy the generated **OAuth token**, not the client secret.
6. Paste it into the wizard and click **Test & save**.

The wizard verifies the token against your put.io account before saving it in
your local `data/settings.json`. The callback URL includes a port because put.io
expects a valid local URL; this manual-token integration does not contact it.

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
runner after every push to `main` and for manual workflow runs. Every successful
`main` build replaces the `Latest Windows build` GitHub Release with the new
portable ZIP and SHA-256 checksum. The same files are also available as the
`ThePirateBrowser-windows-x64` workflow artifact.

Pushing a tag such as `v1.0.0` also creates a GitHub Release containing the
portable ZIP and its SHA-256 checksum.
