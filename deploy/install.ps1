<#
.SYNOPSIS
    Installiert das Berichtsheft als Windows-Dienst auf diesem Server.

.DESCRIPTION
    Aus dem entpackten Release-Ordner als Administrator ausführen (install.cmd per Doppelklick
    fragt die Administratorrechte selbst an). Das Skript
      1. kopiert die Anwendung in den Zielordner (Standard F:\Program Files\Berichtsheft),
      2. legt den ersten Ausbilder-Zugang an (Passwort wird abgefragt),
      3. erzeugt ein zufälliges JWT-Geheimnis und die Dienst-Konfiguration,
      4. öffnet den Port in der Windows-Firewall,
      5. installiert und startet den Dienst "Berichtsheft" (startet automatisch mit Windows).
    Danach ist die Seite unter https://<Servername>:<Port>/ erreichbar.

.PARAMETER Zielordner
    Installationsordner. Datenbank (data\) und Logs (logs\) liegen ebenfalls dort.

.PARAMETER Port
    HTTPS-Port (Standard 5016; 443 ist möglich, wenn der Port frei ist).

.PARAMETER Hostnamen
    Zusätzliche Namen, unter denen der Server aufgerufen wird (z. B. berichtsheft.firma.local).
    Rechnername und IP-Adressen werden automatisch ins Zertifikat aufgenommen.

.PARAMETER ZertifikatDatei
    Eigenes Zertifikat als .pfx/.p12 (z. B. von der Firmen-CA) – empfohlen, damit Browser nicht warnen.
    Ohne Angabe wird ein selbstsigniertes Zertifikat erzeugt.

.PARAMETER OhneHttps
    Nur HTTP (z. B. wenn ein IIS oder anderer Reverse-Proxy davor HTTPS übernimmt).

.PARAMETER Sso
    Windows-Anmeldung (Single Sign-on): Wer die Seite öffnet, wird mit seinem Windows-Konto angemeldet.
    Zugriff hat nur, wessen Windows-Anmeldename (sAMAccountName) als Benutzername in der Personenliste steht.
    Der Server muss Mitglied der Domäne sein. -AusbilderBenutzer dann auf den eigenen Windows-Anmeldenamen setzen.

.PARAMETER SsoDomaene
    Nur Konten aus dieser Domäne zulassen (Kurzname, z. B. FIRMA; mehrere kommagetrennt).

.PARAMETER OhnePasswortLogin
    Mit -Sso: Anmeldung mit Benutzername/Passwort abschalten.

.PARAMETER DemoDaten
    Demo-Benutzer und Beispieldaten anlegen statt eines eigenen Ausbilder-Zugangs (zum Ausprobieren).

.EXAMPLE
    .\install.cmd
    .\install.cmd -Port 443 -Hostnamen berichtsheft.firma.local -ZertifikatDatei D:\certs\berichtsheft.pfx
#>
[CmdletBinding()]
param(
    [string]$Zielordner = 'F:\Program Files\Berichtsheft',
    [ValidateRange(1, 65535)][int]$Port = 5016,
    [string[]]$Hostnamen = @(),
    [string]$AusbilderBenutzer = 'ausbilder',
    [string]$AusbilderVorname = 'Ausbildung',
    [string]$AusbilderNachname = 'Leitung',
    [securestring]$AusbilderPasswort,
    [string]$ZertifikatDatei,
    [securestring]$ZertifikatPasswort,
    [switch]$OhneHttps,
    [switch]$Sso,
    [string]$SsoDomaene,
    [switch]$OhnePasswortLogin,
    [switch]$DemoDaten,
    [string]$Speicher = '768m'
)

$ErrorActionPreference = 'Stop'
$DienstName = 'Berichtsheft'

# ---------------------------------------------------------------- Administratorrechte
$istAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
        [Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $istAdmin) {
    Write-Host 'Administratorrechte werden benötigt – starte neu als Administrator …'
    $argumente = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-NoExit', '-File', "`"$PSCommandPath`"")
    foreach ($p in $PSBoundParameters.GetEnumerator()) {
        if ($p.Value -is [securestring]) { continue }
        if ($p.Value -is [switch]) { if ($p.Value.IsPresent) { $argumente += "-$($p.Key)" } }
        elseif ($p.Value -is [array]) { $argumente += "-$($p.Key)"; $argumente += ($p.Value -join ',') }
        else { $argumente += "-$($p.Key)"; $argumente += "`"$($p.Value)`"" }
    }
    Start-Process powershell.exe -Verb RunAs -ArgumentList $argumente
    exit
}

function Schritt([string]$text) { Write-Host "`n==> $text" -ForegroundColor Cyan }
function Klartext([securestring]$s) {
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($s)
    try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr) } finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }
}
function Schreibe-Utf8([string]$pfad, [string]$inhalt) {
    [IO.File]::WriteAllText($pfad, $inhalt, (New-Object Text.UTF8Encoding($false)))
}
function Xml([string]$wert) { [Security.SecurityElement]::Escape($wert) }

$quelle = $PSScriptRoot
foreach ($pflicht in 'berichtsheft.jar', 'frontend\index.html', 'berichtsheft-service.exe', 'berichtsheft-service.xml.vorlage') {
    if (-not (Test-Path (Join-Path $quelle $pflicht))) { throw "Im Release fehlt $pflicht – bitte mit deploy\build-release neu bauen." }
}
if (Get-Service -Name $DienstName -ErrorAction SilentlyContinue) {
    throw "Der Dienst '$DienstName' ist bereits installiert. Für eine neue Version update.cmd verwenden, zum Entfernen uninstall.cmd."
}
if ($ZertifikatDatei -and -not (Test-Path $ZertifikatDatei)) { throw "Zertifikat nicht gefunden: $ZertifikatDatei" }
if ($OhnePasswortLogin -and -not $Sso) { throw '-OhnePasswortLogin geht nur zusammen mit -Sso.' }
if ($Sso -and $AusbilderBenutzer -eq 'ausbilder') {
    Write-Warning ("Mit -Sso muss der erste Ausbilder-Zugang dem Windows-Anmeldenamen entsprechen, " +
            "z. B. -AusbilderBenutzer $($env:USERNAME.ToLower()). Sonst nur Anmeldung mit Passwort möglich.")
    if ($OhnePasswortLogin) { throw 'Ohne Passwort-Login bitte -AusbilderBenutzer <Windows-Anmeldename> angeben, sonst kann sich niemand anmelden.' }
}
$laufwerk = [IO.Path]::GetPathRoot($Zielordner)
if (-not $laufwerk -or -not (Test-Path $laufwerk)) {
    throw "Laufwerk '$laufwerk' gibt es auf diesem Server nicht – bitte einen anderen Ordner angeben, z. B. install.cmd -Zielordner 'D:\Berichtsheft'"
}

# ---------------------------------------------------------------- Ausbilder-Zugang abfragen
# Bei einer Neuinstallation mit vorhandener Datenbank (z. B. nach uninstall.cmd) gibt es die Zugänge schon.
$datenbankVorhanden = Test-Path (Join-Path $Zielordner 'data\berichtsheft.mv.db')
if ($datenbankVorhanden) { Write-Host "Vorhandene Datenbank in $Zielordner\data wird weiterverwendet." }
$ersterAusbilder = -not $DemoDaten -and -not $datenbankVorhanden
if ($ersterAusbilder) {
    if ($AusbilderBenutzer -notmatch '^[a-z0-9._-]{3,50}$') {
        throw 'Benutzername: 3–50 Zeichen, nur Kleinbuchstaben, Ziffern, Punkt, - und _'
    }
    while ($true) {
        if (-not $AusbilderPasswort) {
            Write-Host "`nErster Ausbilder-Zugang: Benutzername '$AusbilderBenutzer'"
            $AusbilderPasswort = Read-Host 'Passwort (mind. 8 Zeichen, Buchstaben und Ziffern)' -AsSecureString
            $wiederholung = Read-Host 'Passwort wiederholen' -AsSecureString
            if ((Klartext $AusbilderPasswort) -cne (Klartext $wiederholung)) {
                Write-Warning 'Die Passwörter stimmen nicht überein.'; $AusbilderPasswort = $null; continue
            }
        }
        if ((Klartext $AusbilderPasswort) -match '^(?=.*[A-Za-zÄÖÜäöüß])(?=.*\d).{8,100}$') { break }
        Write-Warning 'Das Passwort braucht mindestens 8 Zeichen mit Buchstaben und Ziffern.'; $AusbilderPasswort = $null
    }
}
if ($ZertifikatDatei -and -not $ZertifikatPasswort) {
    $ZertifikatPasswort = Read-Host "Passwort des Zertifikats $([IO.Path]::GetFileName($ZertifikatDatei))" -AsSecureString
}

# ---------------------------------------------------------------- Dateien kopieren
Schritt "Anwendung nach $Zielordner kopieren"
Get-ChildItem $quelle -Recurse -File | Unblock-File -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $Zielordner, (Join-Path $Zielordner 'data'), (Join-Path $Zielordner 'logs') | Out-Null
$quelleVoll = (Resolve-Path $quelle).Path.TrimEnd('\')
$zielVoll = (Resolve-Path $Zielordner).Path.TrimEnd('\')
if ($quelleVoll -ne $zielVoll) {
    foreach ($eintrag in Get-ChildItem $quelle | Where-Object { $_.Name -notin 'data', 'logs', 'backup' }) {
        $zielEintrag = Join-Path $Zielordner $eintrag.Name
        # Ordner (frontend, jre) vorher entfernen, sonst legt Copy-Item sie verschachtelt an
        if ($eintrag.PSIsContainer -and (Test-Path $zielEintrag)) { Remove-Item $zielEintrag -Recurse -Force }
        Copy-Item $eintrag.FullName $Zielordner -Recurse -Force
    }
}

# ---------------------------------------------------------------- Java
$jreJava = Join-Path $Zielordner 'jre\bin\java.exe'
if (Test-Path $jreJava) {
    $java = '%BASE%\jre\bin\java.exe'
    Write-Host 'Mitgelieferte Java-Laufzeit wird verwendet.'
} else {
    $kandidat = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { (Get-Command java -ErrorAction SilentlyContinue).Source }
    if (-not $kandidat -or -not (Test-Path $kandidat)) { throw 'Keine Java-Laufzeit gefunden. Bitte Java 21 installieren oder ein Release mit jre\ bauen.' }
    # java -version schreibt nach stderr – das darf hier nicht als Fehler abbrechen
    $ErrorActionPreference = 'Continue'
    $versionText = (& $kandidat -version 2>&1 | Out-String)
    $ErrorActionPreference = 'Stop'
    if ($versionText -notmatch 'version "(\d+)' -or [int]$Matches[1] -lt 21) { throw "Java 21 oder neuer wird benötigt, gefunden: $versionText" }
    $java = $kandidat
    Write-Host "Java: $java"
}

# ---------------------------------------------------------------- Konfiguration
Schritt 'Dienst konfigurieren'
$fqdn = $env:COMPUTERNAME.ToLower()
try { $fqdn = [Net.Dns]::GetHostEntry('').HostName.ToLower() } catch { }
$namen = @(@($Hostnamen | ForEach-Object { $_ -split ',' } | ForEach-Object { $_.Trim().ToLower() } | Where-Object { $_ }) +
        @($fqdn, $env:COMPUTERNAME.ToLower()) | Select-Object -Unique)
$anzeigeHost = $namen[0]

$bytes = New-Object byte[] 48
[Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
$jwtSecret = [Convert]::ToBase64String($bytes)

$keystoreZeilen = ''
if ($ZertifikatDatei -and -not $OhneHttps) {
    $sslOrdner = Join-Path $Zielordner 'data\ssl'
    New-Item -ItemType Directory -Force -Path $sslOrdner | Out-Null
    Copy-Item $ZertifikatDatei (Join-Path $sslOrdner 'server.pfx') -Force
    $keystoreZeilen = @(
        '  <env name="SSL_KEYSTORE" value="file:./data/ssl/server.pfx"/>'
        "  <env name=`"SSL_KEYSTORE_PASSWORD`" value=`"$(Xml (Klartext $ZertifikatPasswort))`"/>"
        '  <env name="SSL_KEYSTORE_TYPE" value="PKCS12"/>'
    ) -join "`r`n"
}

$anmeldungZeilen = @()
if ($Sso) {
    $anmeldungZeilen += '  <env name="SSO_ENABLED" value="true"/>'
    if ($SsoDomaene) { $anmeldungZeilen += "  <env name=`"SSO_DOMAINS`" value=`"$(Xml $SsoDomaene)`"/>" }
    if ($OhnePasswortLogin) { $anmeldungZeilen += '  <env name="PASSWORT_LOGIN" value="false"/>' }
}

$xml = (Get-Content (Join-Path $Zielordner 'berichtsheft-service.xml.vorlage') -Raw -Encoding UTF8)
$xml = $xml.Replace('{{ANMELDUNG_ZEILEN}}', ($anmeldungZeilen -join "`r`n"))
$xml = $xml.Replace('{{JAVA}}', (Xml $java)).Replace('{{SPEICHER}}', $Speicher).Replace('{{PORT}}', "$Port")
$xml = $xml.Replace('{{HOST}}', (Xml $anzeigeHost)).Replace('{{JWT_SECRET}}', $jwtSecret)
$xml = $xml.Replace('{{SSL_ENABLED}}', $(if ($OhneHttps) { 'false' } else { 'true' }))
$xml = $xml.Replace('{{SSL_HOSTNAMES}}', (Xml ($namen -join ','))).Replace('{{SSL_KEYSTORE_ZEILEN}}', $keystoreZeilen)
$xml = $xml.Replace('{{DEMO_DATA}}', $(if ($DemoDaten) { 'true' } else { 'false' }))
if ($OhneHttps) { $xml = $xml.Replace("https://$anzeigeHost", "http://$anzeigeHost") }
$xmlPfad = Join-Path $Zielordner 'berichtsheft-service.xml'
Schreibe-Utf8 $xmlPfad $xml

if ($ersterAusbilder) {
    $esc = { param($v) $v -replace '\\', '\\' }
    Schreibe-Utf8 (Join-Path $Zielordner 'data\erster-ausbilder.properties') (@(
        "benutzername=$(& $esc $AusbilderBenutzer)"
        "passwort=$(& $esc (Klartext $AusbilderPasswort))"
        "vorname=$(& $esc $AusbilderVorname)"
        "nachname=$(& $esc $AusbilderNachname)"
    ) -join "`n")
}

# Konfiguration (JWT-Geheimnis) und Datenbank nur für Administratoren und den Dienst (SYSTEM) lesbar
& icacls $xmlPfad /inheritance:r /grant:r '*S-1-5-32-544:(F)' '*S-1-5-18:(F)' | Out-Null
& icacls (Join-Path $Zielordner 'data') /inheritance:r /grant:r '*S-1-5-32-544:(OI)(CI)(F)' '*S-1-5-18:(OI)(CI)(F)' | Out-Null

# ---------------------------------------------------------------- Firewall
Schritt "Firewall: Port $Port öffnen"
Get-NetFirewallRule -DisplayName 'Berichtsheft*' -ErrorAction SilentlyContinue | Remove-NetFirewallRule
New-NetFirewallRule -DisplayName "Berichtsheft (TCP $Port)" -Direction Inbound -Protocol TCP -LocalPort $Port `
        -Action Allow -Profile Domain, Private | Out-Null
Write-Host 'Freigegeben für Domänen- und private Netzwerke (nicht öffentlich).'

# ---------------------------------------------------------------- Dienst
Schritt "Dienst '$DienstName' installieren und starten"
$dienstExe = Join-Path $Zielordner 'berichtsheft-service.exe'
& $dienstExe install
if ($LASTEXITCODE -ne 0) { throw 'Dienst konnte nicht installiert werden.' }
& $dienstExe start
if ($LASTEXITCODE -ne 0) { throw "Dienst konnte nicht gestartet werden – siehe $Zielordner\logs" }

Write-Host 'Warte, bis die Anwendung bereit ist (bis zu 2 Minuten) …'
$bereit = $false
for ($i = 0; $i -lt 60 -and -not $bereit; $i++) {
    Start-Sleep -Seconds 2
    $client = New-Object Net.Sockets.TcpClient
    try { $client.Connect('127.0.0.1', $Port); $bereit = $true } catch { } finally { $client.Dispose() }
}

$schema = if ($OhneHttps) { 'http' } else { 'https' }
$url = "${schema}://${anzeigeHost}:$Port/"
if ($bereit) {
    Write-Host "`nFertig! Das Berichtsheft läuft unter:  $url" -ForegroundColor Green
} else {
    Write-Warning "Die Anwendung antwortet noch nicht. Bitte $Zielordner\logs\berichtsheft.log prüfen. Adresse: $url"
}
if ($datenbankVorhanden) {
    Write-Host 'Anmelden mit den bisherigen Zugängen.'
} elseif ($DemoDaten) {
    Write-Host 'Demo-Zugänge: ausbilder/ausbilder123, azubi/azubi123, azubi2/azubi123'
} else {
    Write-Host "Anmelden mit '$AusbilderBenutzer' und dem eben vergebenen Passwort – danach im Profil ändern."
}
if (-not $ZertifikatDatei -and -not $OhneHttps) {
    Write-Host 'Hinweis: Es wird ein selbstsigniertes Zertifikat verwendet – Browser zeigen beim ersten Aufruf eine Warnung.'
    Write-Host '         Für den Dauerbetrieb ein Firmen-Zertifikat verwenden (siehe SERVER-ANLEITUNG.md).'
}
