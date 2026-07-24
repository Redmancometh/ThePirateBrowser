@echo off
setlocal
set "APP_ROOT=%~dp0"

if not exist "%APP_ROOT%PirateBrowser.exe" goto incomplete
if not exist "%APP_ROOT%app\PirateBrowser.cfg" goto incomplete
if not exist "%APP_ROOT%app\pirate-browser.jar" goto incomplete
if not exist "%APP_ROOT%runtime\bin\server\jvm.dll" goto incomplete
if not exist "%APP_ROOT%runtime\lib\modules" goto incomplete
if not exist "%APP_ROOT%data\settings.json" goto incomplete

start "" "%APP_ROOT%PirateBrowser.exe"
exit /b 0

:incomplete
echo.
echo This Pirate Browser installation folder is incomplete.
echo Extract and keep the entire folder together, then run this launcher again.
echo Do not copy or run PirateBrowser.exe by itself.
echo.
if not defined PIRATE_BROWSER_NONINTERACTIVE timeout /t 8 /nobreak >nul
exit /b 1
