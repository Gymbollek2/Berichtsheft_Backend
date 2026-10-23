@echo off
rem Baut release\Berichtsheft-Setup-<Version>.zip (auch per Doppelklick). Parameter werden durchgereicht, z. B. -MitTests
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-setup.ps1" %*
if errorlevel 1 pause
