@echo off
rem Berichtsheft-Setup: installieren, aktualisieren, Einstellungen aendern, deinstallieren.
rem Fragt nach Administratorrechten und oeffnet den Assistenten.
start "" powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "%~dp0setup\setup.ps1"
