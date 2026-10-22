@echo off
rem Startet build-release.ps1 (auch per Doppelklick). Weitere Parameter werden durchgereicht, z. B. -Port 443
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-release.ps1" %*
if errorlevel 1 pause
