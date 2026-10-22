<#
.SYNOPSIS
    Entfernt den Windows-Dienst "Berichtsheft" und die Firewall-Regel.

.DESCRIPTION
    Die Daten (Datenbank, Logs) bleiben im Installationsordner erhalten, außer -AllesLoeschen ist angegeben.

.EXAMPLE
    .\uninstall.cmd
    .\uninstall.cmd -AllesLoeschen
#>
[CmdletBinding()]
param(
    [string]$Zielordner = 'F:\Program Files\Berichtsheft',
    [switch]$AllesLoeschen
)

$ErrorActionPreference = 'Stop'
$DienstName = 'Berichtsheft'

$istAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
        [Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $istAdmin) {
    Write-Host 'Administratorrechte werden benötigt – starte neu als Administrator …'
    $argumente = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-NoExit', '-File', "`"$PSCommandPath`"", '-Zielordner', "`"$Zielordner`"")
    if ($AllesLoeschen) { $argumente += '-AllesLoeschen' }
    Start-Process powershell.exe -Verb RunAs -ArgumentList $argumente
    exit
}

$dienstExe = Join-Path $Zielordner 'berichtsheft-service.exe'
if (Get-Service -Name $DienstName -ErrorAction SilentlyContinue) {
    Write-Host 'Dienst anhalten und entfernen …'
    Stop-Service -Name $DienstName -Force -ErrorAction SilentlyContinue
    if (Test-Path $dienstExe) { & $dienstExe uninstall } else { & sc.exe delete $DienstName | Out-Null }
} else {
    Write-Host "Dienst '$DienstName' ist nicht installiert."
}

Get-NetFirewallRule -DisplayName 'Berichtsheft*' -ErrorAction SilentlyContinue | Remove-NetFirewallRule
Write-Host 'Firewall-Regel entfernt.'

if ($AllesLoeschen) {
    $antwort = Read-Host "Wirklich ALLES in $Zielordner löschen, auch die Datenbank? (ja/nein)"
    if ($antwort -eq 'ja') {
        Set-Location $env:TEMP
        Remove-Item $Zielordner -Recurse -Force
        Write-Host "$Zielordner gelöscht."
    }
} else {
    Write-Host "Die Daten bleiben in $Zielordner\data erhalten."
}
