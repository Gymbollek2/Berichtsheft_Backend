@echo off
rem Startet install.ps1 (auch per Doppelklick). Weitere Parameter werden durchgereicht, z. B. -Port 443
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0install.ps1" %*
if errorlevel 1 pause
