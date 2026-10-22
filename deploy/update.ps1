<#
.SYNOPSIS
    Spielt eine neue Version des Berichtshefts auf einem installierten Server ein.

.DESCRIPTION
    Neues Release-ZIP auf dem Server entpacken (NICHT in den Installationsordner) und daraus
    update.cmd als Administrator ausführen. Das Skript
      1. hält den Dienst an,
      2. sichert die Datenbank nach <Zielordner>\backup\<Datum>,
      3. ersetzt Anwendung, Frontend, Java-Laufzeit und Skripte,
      4. startet den Dienst wieder.
    Datenbank (data\), Logs (logs\) und Dienst-Konfiguration (berichtsheft-service.xml, inkl. Port,
    Zertifikat und JWT-Geheimnis) bleiben unverändert.

.PARAMETER Sso
    Windows-Anmeldung (Single Sign-on) einschalten. Zugriff hat nur, wessen Windows-Anmeldename
    (sAMAccountName) als Benutzername in der Personenliste steht. Bleibt bei späteren Updates eingeschaltet.

.PARAMETER SsoDomaene
    Nur Konten aus dieser Domäne zulassen (Kurzname, z. B. FIRMA; mehrere kommagetrennt).

.PARAMETER OhnePasswortLogin
    Anmeldung mit Benutzername/Passwort abschalten – dann geht es nur noch per Windows.

.PARAMETER SsoAus
    Windows-Anmeldung wieder ausschalten (Anmeldung nur mit Passwort).

.EXAMPLE
    .\update.cmd
    .\update.cmd -Zielordner D:\Apps\Berichtsheft
    .\update.cmd -Sso -SsoDomaene FIRMA
#>
[CmdletBinding()]
param(
    [string]$Zielordner = 'F:\Program Files\Berichtsheft',
    [switch]$Sso,
    [string]$SsoDomaene,
    [switch]$OhnePasswortLogin,
    [switch]$SsoAus
)

$ErrorActionPreference = 'Stop'
$DienstName = 'Berichtsheft'

$istAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
        [Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $istAdmin) {
    Write-Host 'Administratorrechte werden benötigt – starte neu als Administrator …'
    $argumente = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-NoExit', '-File', "`"$PSCommandPath`"", '-Zielordner', "`"$Zielordner`"")
    foreach ($schalter in 'Sso', 'OhnePasswortLogin', 'SsoAus') { if ($PSBoundParameters[$schalter]) { $argumente += "-$schalter" } }
    if ($SsoDomaene) { $argumente += '-SsoDomaene'; $argumente += "`"$SsoDomaene`"" }
    Start-Process powershell.exe -Verb RunAs -ArgumentList $argumente
    exit
}

function Schritt([string]$text) { Write-Host "`n==> $text" -ForegroundColor Cyan }

$quelle = (Resolve-Path $PSScriptRoot).Path.TrimEnd('\')
if (-not (Test-Path (Join-Path $quelle 'berichtsheft.jar')) -or -not (Test-Path (Join-Path $quelle 'frontend\index.html'))) {
    throw 'update.cmd bitte aus dem entpackten neuen Release-Ordner starten.'
}
if (-not (Test-Path (Join-Path $Zielordner 'berichtsheft-service.xml'))) {
    throw "Keine Installation in $Zielordner gefunden. Für die Erstinstallation install.cmd verwenden."
}
$zielVoll = (Resolve-Path $Zielordner).Path.TrimEnd('\')
if ($quelle -eq $zielVoll) {
    throw 'Bitte das neue Release in einen eigenen Ordner entpacken (nicht in den Installationsordner) und von dort starten.'
}
Get-ChildItem $quelle -Recurse -File | Unblock-File -ErrorAction SilentlyContinue

$alt = if (Test-Path (Join-Path $Zielordner 'VERSION.txt')) { (Get-Content (Join-Path $Zielordner 'VERSION.txt') -Raw).Trim() } else { 'unbekannt' }
$neu = if (Test-Path (Join-Path $quelle 'VERSION.txt')) { (Get-Content (Join-Path $quelle 'VERSION.txt') -Raw).Trim() } else { 'unbekannt' }
Write-Host "Installiert: $alt"
Write-Host "Neu:         $neu"

# ---------------------------------------------------------------- Dienst anhalten
Schritt 'Dienst anhalten'
$dienst = Get-Service -Name $DienstName -ErrorAction SilentlyContinue
if ($dienst -and $dienst.Status -ne 'Stopped') {
    Stop-Service -Name $DienstName -Force
    $dienst.WaitForStatus('Stopped', [TimeSpan]::FromSeconds(60))
}
Start-Sleep -Seconds 2 # Datenbankdatei freigeben lassen

# ---------------------------------------------------------------- Datenbank sichern
Schritt 'Datenbank sichern'
$sicherung = Join-Path $Zielordner ("backup\" + (Get-Date -Format 'yyyy-MM-dd_HH-mm'))
New-Item -ItemType Directory -Force -Path $sicherung | Out-Null
Get-ChildItem (Join-Path $Zielordner 'data') -Filter '*.db' -ErrorAction SilentlyContinue |
        ForEach-Object { Copy-Item $_.FullName $sicherung }
Write-Host "Gesichert nach $sicherung"

# ---------------------------------------------------------------- Dateien ersetzen
Schritt 'Neue Version kopieren'
foreach ($datei in 'berichtsheft.jar', 'berichtsheft-service.exe', 'VERSION.txt', 'install.ps1', 'install.cmd', 'update.ps1',
                   'update.cmd', 'uninstall.ps1', 'uninstall.cmd', 'zertifikat.ps1', 'zertifikat.cmd', 'berichtsheft-service.xml.vorlage', 'SERVER-ANLEITUNG.md') {
    $q = Join-Path $quelle $datei
    if (Test-Path $q) { Copy-Item $q $Zielordner -Force }
}
Remove-Item (Join-Path $Zielordner 'frontend') -Recurse -Force -ErrorAction SilentlyContinue
Copy-Item (Join-Path $quelle 'frontend') $Zielordner -Recurse -Force
if (Test-Path (Join-Path $quelle 'jre')) {
    Remove-Item (Join-Path $Zielordner 'jre') -Recurse -Force -ErrorAction SilentlyContinue
    Copy-Item (Join-Path $quelle 'jre') $Zielordner -Recurse -Force
}

# ---------------------------------------------------------------- Windows-Anmeldung (optional)
if ($Sso -or $SsoAus -or $SsoDomaene -or $OhnePasswortLogin) {
    Schritt 'Anmeldung konfigurieren'
    $xmlPfad = Join-Path $Zielordner 'berichtsheft-service.xml'
    [xml]$dienstKonfig = Get-Content $xmlPfad -Raw -Encoding UTF8
    function Setze-Env([string]$name, [string]$wert) {
        $dienstKonfig.service.env | Where-Object { $_.name -eq $name } | ForEach-Object { [void]$dienstKonfig.service.RemoveChild($_) }
        if (-not $wert) { return }
        $e = $dienstKonfig.CreateElement('env')
        $e.SetAttribute('name', $name)
        $e.SetAttribute('value', $wert)
        [void]$dienstKonfig.service.AppendChild($e)
    }
    if ($SsoAus) {
        Setze-Env 'SSO_ENABLED' ''; Setze-Env 'SSO_DOMAINS' ''; Setze-Env 'PASSWORT_LOGIN' ''
        Write-Host 'Windows-Anmeldung ausgeschaltet – Anmeldung mit Benutzername und Passwort.'
    } else {
        if ($Sso) { Setze-Env 'SSO_ENABLED' 'true' }
        if ($SsoDomaene) { Setze-Env 'SSO_DOMAINS' $SsoDomaene }
        if ($OhnePasswortLogin) { Setze-Env 'PASSWORT_LOGIN' 'false' }
        Write-Host 'Windows-Anmeldung eingeschaltet. Benutzernamen in der Personenliste müssen den Windows-Anmeldenamen entsprechen.'
    }
    # In dieselbe Datei schreiben, damit die Zugriffsrechte (nur Administratoren/SYSTEM) erhalten bleiben
    $schreiber = New-Object IO.StreamWriter($xmlPfad, $false, (New-Object Text.UTF8Encoding($false)))
    try { $dienstKonfig.Save($schreiber) } finally { $schreiber.Dispose() }
}

# ---------------------------------------------------------------- Dienst starten
Schritt 'Dienst starten'
Start-Service -Name $DienstName
[xml]$konfig = Get-Content (Join-Path $Zielordner 'berichtsheft-service.xml') -Raw -Encoding UTF8
$port = [int](($konfig.service.env | Where-Object { $_.name -eq 'PORT' }).value)
$bereit = $false
for ($i = 0; $i -lt 60 -and -not $bereit; $i++) {
    Start-Sleep -Seconds 2
    $client = New-Object Net.Sockets.TcpClient
    try { $client.Connect('127.0.0.1', $port); $bereit = $true } catch { } finally { $client.Dispose() }
}
if ($bereit) {
    Write-Host "`nUpdate fertig – das Berichtsheft läuft wieder (Port $port)." -ForegroundColor Green
    Write-Host 'Im Browser einmal neu laden (Strg+F5), damit das neue Frontend geladen wird.'
} else {
    Write-Warning "Die Anwendung antwortet noch nicht. Bitte $Zielordner\logs\berichtsheft.log prüfen."
    Write-Warning "Datenbank-Sicherung: $sicherung"
}
