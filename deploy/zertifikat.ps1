<#
.SYNOPSIS
    Tauscht das HTTPS-Zertifikat des installierten Berichtshefts oder exportiert es zum Verteilen.

.DESCRIPTION
    Damit Browser die Seite ohne Warnung als sicher anzeigen, muss das Zertifikat von einer Stelle
    stammen, der die Rechner vertrauen. Zwei Wege:

    1. Zertifikat der Firmen-CA (empfohlen): .pfx mit privatem Schlüssel von der IT besorgen und
       einspielen:  zertifikat.cmd -ZertifikatDatei D:\Zertifikate\berichtsheft.pfx
       Das Skript prüft das Zertifikat, hält den Dienst an, trägt es ein und startet den Dienst neu.
       Datenbank, Port und Anmeldungen bleiben erhalten.

    2. Selbstsigniertes Zertifikat verteilen: zertifikat.cmd -Exportieren
       legt berichtsheft-zertifikat.cer an. Dieses Zertifikat per Gruppenrichtlinie (oder von Hand)
       auf den Arbeitsplätzen unter "Vertrauenswürdige Stammzertifizierungsstellen" eintragen.

.EXAMPLE
    .\zertifikat.cmd -ZertifikatDatei D:\Zertifikate\berichtsheft.pfx
    .\zertifikat.cmd -Exportieren
#>
[CmdletBinding(DefaultParameterSetName = 'Einspielen')]
param(
    [string]$Zielordner = 'F:\Program Files\Berichtsheft',
    [Parameter(ParameterSetName = 'Einspielen', Mandatory = $true)][string]$ZertifikatDatei,
    [Parameter(ParameterSetName = 'Einspielen')][securestring]$ZertifikatPasswort,
    [Parameter(ParameterSetName = 'Exportieren', Mandatory = $true)][switch]$Exportieren,
    [Parameter(ParameterSetName = 'Exportieren')][string]$ExportDatei
)

$ErrorActionPreference = 'Stop'
$DienstName = 'Berichtsheft'

$istAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
        [Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $istAdmin) {
    Write-Host 'Administratorrechte werden benötigt – starte neu als Administrator …'
    $argumente = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-NoExit', '-File', "`"$PSCommandPath`"")
    foreach ($p in $PSBoundParameters.GetEnumerator()) {
        if ($p.Value -is [securestring]) { continue }
        if ($p.Value -is [switch]) { if ($p.Value.IsPresent) { $argumente += "-$($p.Key)" } }
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

$xmlPfad = Join-Path $Zielordner 'berichtsheft-service.xml'
if (-not (Test-Path $xmlPfad)) { throw "Keine Installation in $Zielordner gefunden (berichtsheft-service.xml fehlt)." }
[xml]$konfig = Get-Content $xmlPfad -Raw -Encoding UTF8
function Env([string]$name) { ($konfig.service.env | Where-Object { $_.name -eq $name }).value }

# Namen, unter denen der Server aufgerufen wird (von install.ps1 eingetragen)
$hostnamen = @((Env 'SSL_HOSTNAMES') -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ })
$port = [int](Env 'PORT')

if ($Exportieren) {
    # ------------------------------------------------------------ aktuelles Zertifikat exportieren
    $keystore = Env 'SSL_KEYSTORE'
    $pfad = if ($keystore) { Join-Path $Zielordner ($keystore -replace '^file:(\./)?', '') } else { Join-Path $Zielordner 'data\ssl\berichtsheft-dev.p12' }
    $passwort = Env 'SSL_KEYSTORE_PASSWORD'
    if (-not $passwort) { $passwort = 'berichtsheft-dev' }
    if (-not (Test-Path $pfad)) { throw "Zertifikat nicht gefunden: $pfad – läuft der Dienst schon einmal gestartet?" }

    $sammlung = New-Object Security.Cryptography.X509Certificates.X509Certificate2Collection
    $sammlung.Import($pfad, $passwort, 'DefaultKeySet')
    $zert = $sammlung | Where-Object HasPrivateKey | Select-Object -First 1
    if (-not $ExportDatei) { $ExportDatei = Join-Path $Zielordner 'berichtsheft-zertifikat.cer' }
    [IO.File]::WriteAllBytes($ExportDatei, $zert.Export('Cert'))

    Write-Host "`nExportiert: $ExportDatei" -ForegroundColor Green
    Write-Host "Ausgestellt für: $(($zert.DnsNameList | ForEach-Object Unicode) -join ', ')"
    Write-Host "Gültig bis:      $($zert.NotAfter.ToString('dd.MM.yyyy'))"
    Write-Host "Fingerabdruck:   $($zert.Thumbprint)"
    if ($zert.Subject -eq $zert.Issuer) {
        Write-Host @"

Diese Datei auf den Arbeitsplätzen als vertrauenswürdig eintragen:
  - für alle Firmenrechner: Gruppenrichtlinie (IT) → Computerkonfiguration → Richtlinien → Windows-Einstellungen →
    Sicherheitseinstellungen → Richtlinien für öffentliche Schlüssel → Vertrauenswürdige Stammzertifizierungsstellen → Importieren
  - einzelner PC: Doppelklick auf die .cer → Zertifikat installieren → Lokaler Computer →
    Speicher "Vertrauenswürdige Stammzertifizierungsstellen"
Danach Browser neu starten. Die Seite immer unter einem der oben genannten Namen aufrufen.
Achtung: Wird das Zertifikat neu erzeugt (data\ssl gelöscht), muss es erneut verteilt werden.
"@
    } else {
        Write-Host "`nDas Zertifikat stammt von $($zert.Issuer) – vertrauen die Rechner dieser CA, ist nichts weiter zu tun."
    }
    return
}

# ---------------------------------------------------------------- neues Zertifikat prüfen
Schritt 'Zertifikat prüfen'
if (-not (Test-Path $ZertifikatDatei)) { throw "Zertifikat nicht gefunden: $ZertifikatDatei" }
if (-not $ZertifikatPasswort) {
    $ZertifikatPasswort = Read-Host "Passwort des Zertifikats $([IO.Path]::GetFileName($ZertifikatDatei))" -AsSecureString
}
$passwort = Klartext $ZertifikatPasswort
$sammlung = New-Object Security.Cryptography.X509Certificates.X509Certificate2Collection
try {
    $sammlung.Import((Resolve-Path $ZertifikatDatei).Path, $passwort, 'DefaultKeySet')
} catch {
    throw "Zertifikat konnte nicht geöffnet werden – Passwort falsch oder keine .pfx-Datei? ($($_.Exception.Message))"
}
$zert = $sammlung | Where-Object HasPrivateKey | Select-Object -First 1
if (-not $zert) { throw 'Die Datei enthält keinen privaten Schlüssel. Bitte bei der IT eine .pfx MIT privatem Schlüssel anfordern.' }
if ($zert.NotAfter -lt (Get-Date)) { throw "Das Zertifikat ist am $($zert.NotAfter.ToString('dd.MM.yyyy')) abgelaufen." }

$zertNamen = @($zert.DnsNameList | ForEach-Object { $_.Unicode.ToLower() })
Write-Host "Ausgestellt für: $($zertNamen -join ', ')"
Write-Host "Aussteller:      $($zert.Issuer)"
Write-Host "Gültig bis:      $($zert.NotAfter.ToString('dd.MM.yyyy'))"
$passend = @($hostnamen | Where-Object { $n = $_; $zertNamen | Where-Object { $_ -eq $n -or ($_ -like '`*.*' -and $n -like $_) } })
if (-not $passend) {
    Write-Warning "Keiner der Servernamen ($($hostnamen -join ', ')) steht im Zertifikat. Browser warnen dann weiterhin."
    if ((Read-Host 'Trotzdem einspielen? (ja/nein)') -ne 'ja') { return }
} else {
    Write-Host "Passt zu: $($passend -join ', ')" -ForegroundColor Green
}

$kette = New-Object Security.Cryptography.X509Certificates.X509Chain
$kette.ChainPolicy.RevocationMode = 'NoCheck'
foreach ($c in $sammlung) { [void]$kette.ChainPolicy.ExtraStore.Add($c) }
if (-not $kette.Build($zert)) {
    Write-Warning "Dieser Server vertraut dem Aussteller nicht ($((($kette.ChainStatus | ForEach-Object StatusInformation) -join ' ').Trim()))."
    Write-Warning 'Bei einem Zertifikat der Firmen-CA ist das ungewöhnlich – ggf. mit der IT klären.'
}

# ---------------------------------------------------------------- einspielen
Schritt 'Dienst anhalten'
$dienst = Get-Service -Name $DienstName -ErrorAction SilentlyContinue
if ($dienst -and $dienst.Status -ne 'Stopped') {
    Stop-Service -Name $DienstName -Force
    $dienst.WaitForStatus('Stopped', [TimeSpan]::FromSeconds(60))
}

Schritt 'Zertifikat eintragen'
$sslOrdner = Join-Path $Zielordner 'data\ssl'
New-Item -ItemType Directory -Force -Path $sslOrdner | Out-Null
$ziel = Join-Path $sslOrdner 'server.pfx'
# Bisherigen Stand sichern – in data\ssl, weil die Konfiguration das JWT-Geheimnis enthält (nur Admins lesbar)
$stempel = Get-Date -Format 'yyyy-MM-dd_HH-mm'
Copy-Item $xmlPfad (Join-Path $sslOrdner "berichtsheft-service-$stempel.xml.alt")
if (Test-Path $ziel) { Copy-Item $ziel (Join-Path $sslOrdner "server-$stempel.pfx.alt") }
Copy-Item $ZertifikatDatei $ziel -Force

foreach ($name in 'SSL_KEYSTORE', 'SSL_KEYSTORE_PASSWORD', 'SSL_KEYSTORE_TYPE') {
    $konfig.service.env | Where-Object { $_.name -eq $name } | ForEach-Object { [void]$konfig.service.RemoveChild($_) }
}
$vorDemo = $konfig.service.env | Where-Object { $_.name -eq 'DEMO_DATA' } | Select-Object -First 1
foreach ($eintrag in @(@('SSL_KEYSTORE', 'file:./data/ssl/server.pfx'), @('SSL_KEYSTORE_PASSWORD', $passwort), @('SSL_KEYSTORE_TYPE', 'PKCS12'))) {
    $e = $konfig.CreateElement('env')
    $e.SetAttribute('name', $eintrag[0])
    $e.SetAttribute('value', $eintrag[1])
    if ($vorDemo) { [void]$konfig.service.InsertBefore($e, $vorDemo) } else { [void]$konfig.service.AppendChild($e) }
}
# In dieselbe Datei schreiben, damit die Zugriffsrechte (nur Administratoren/SYSTEM) erhalten bleiben
$schreiber = New-Object IO.StreamWriter($xmlPfad, $false, (New-Object Text.UTF8Encoding($false)))
try { $konfig.Save($schreiber) } finally { $schreiber.Dispose() }
Write-Host "Eingetragen in $xmlPfad"

Schritt 'Dienst starten'
Start-Service -Name $DienstName
$bereit = $false
for ($i = 0; $i -lt 60 -and -not $bereit; $i++) {
    Start-Sleep -Seconds 2
    $client = New-Object Net.Sockets.TcpClient
    try { $client.Connect('127.0.0.1', $port); $bereit = $true } catch { } finally { $client.Dispose() }
}
$adresse = if ($passend) { $passend[0] } else { $hostnamen[0] }
if ($bereit) {
    Write-Host "`nFertig – das Berichtsheft läuft mit dem neuen Zertifikat:  https://${adresse}:$port/" -ForegroundColor Green
    Write-Host 'Im Browser die Seite unter genau diesem Namen aufrufen (nicht per IP-Adresse).'
} else {
    Write-Warning "Die Anwendung antwortet nicht. Bitte $Zielordner\logs\berichtsheft.log prüfen."
    Write-Warning "Zurück zum alten Stand: $sslOrdner\berichtsheft-service-$stempel.xml.alt als $xmlPfad zurückkopieren und den Dienst neu starten."
}
