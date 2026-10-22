@echo off
rem Startet zertifikat.ps1, z. B. "zertifikat.cmd -ZertifikatDatei D:\Zertifikate\berichtsheft.pfx" oder "zertifikat.cmd -Exportieren"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0zertifikat.ps1" %*
if errorlevel 1 pause
