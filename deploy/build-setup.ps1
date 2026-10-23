<#
.SYNOPSIS
    Baut das Setup für den Server: release\Berichtsheft-Setup-<Version>.zip

.DESCRIPTION
    Auf dem Entwicklungsrechner ausführen (braucht Node.js und JDK 21; beim ersten Mal Internet für WinSW).
    Schnell, weil nur gebaut wird, was sich geändert hat:
      - Frontend (ng build) nur, wenn sich im Frontend etwas geändert hat
      - Backend (Maven, ohne Tests) nur, wenn sich im Backend etwas geändert hat – parallel zum Frontend
      - Java-Laufzeit (jlink) und WinSW werden einmal erzeugt bzw. geladen und in release\_cache aufgehoben
      - das ZIP wird ohne erneute Komprimierung gepackt (JAR und Java-Laufzeit sind bereits komprimiert)

    Auf dem Server: ZIP entpacken, Setup.cmd doppelklicken. Das Setup sucht selbst nach einer vorhandenen
    Installation und führt durch die Neuinstallation oder zeigt die Änderungen und aktualisiert.

.PARAMETER FrontendPfad
    Ordner des Frontend-Projekts (Standard: ..\Berichtsheft_Frontend neben diesem Projekt).

.PARAMETER MitTests
    Vorher die Tests von Backend und Frontend ausführen (dauert deutlich länger).

.PARAMETER Neu
    Alles neu bauen (Maven clean, Frontend, Java-Laufzeit) statt nur das Geänderte.

.EXAMPLE
    .\deploy\build-setup.cmd
    .\deploy\build-setup.cmd -MitTests
#>
[CmdletBinding()]
param(
    [string]$FrontendPfad,
    [switch]$MitTests,
    [switch]$Neu
)

$ErrorActionPreference = 'Stop'
$WinSwUrl = 'https://github.com/winsw/winsw/releases/download/v2.12.0/WinSW-x64.exe'
$uhr = [Diagnostics.Stopwatch]::StartNew()

function Schritt([string]$text) { Write-Host ("{0,5:N1}s  {1}" -f $uhr.Elapsed.TotalSeconds, $text) -ForegroundColor Cyan }
function Ausfuehren([string]$exe, [string[]]$argumente) {
    & $exe @argumente
    if ($LASTEXITCODE -ne 0) { throw "$exe $($argumente -join ' ') ist fehlgeschlagen (Exit-Code $LASTEXITCODE)" }
}
# Jüngste Änderung unter den angegebenen Dateien/Ordnern
function Stand([string[]]$pfade) {
    $neueste = [datetime]::MinValue
    foreach ($p in $pfade | Where-Object { Test-Path $_ }) {
        $dateien = if ((Get-Item $p).PSIsContainer) { [IO.Directory]::EnumerateFiles($p, '*', 'AllDirectories') } else { @($p) }
        foreach ($d in $dateien) {
            $t = [IO.File]::GetLastWriteTimeUtc($d)
            if ($t -gt $neueste) { $neueste = $t }
        }
    }
    return $neueste
}

$backend = Split-Path -Parent $PSScriptRoot
if (-not $FrontendPfad) { $FrontendPfad = Join-Path (Split-Path -Parent $backend) 'Berichtsheft_Frontend' }
$FrontendPfad = (Resolve-Path $FrontendPfad).Path
$angular = Join-Path $FrontendPfad 'azubi-portal'
if (-not (Test-Path (Join-Path $angular 'package.json'))) { throw "Frontend nicht gefunden: $angular (Pfad mit -FrontendPfad angeben)" }

$version = ([xml](Get-Content (Join-Path $backend 'pom.xml') -Raw -Encoding UTF8)).project.version
$release = Join-Path $backend 'release'
$cache = Join-Path $release '_cache'
$zip = Join-Path $release "Berichtsheft-Setup-$version.zip"
New-Item -ItemType Directory -Force -Path $cache | Out-Null

$javaHome = $env:JAVA_HOME
if (-not $javaHome) {
    $javaExe = (Get-Command java -ErrorAction SilentlyContinue).Source
    if ($javaExe) { $javaHome = Split-Path -Parent (Split-Path -Parent ([IO.Path]::GetFullPath($javaExe))) }
}
if (-not $javaHome -or -not (Test-Path (Join-Path $javaHome 'bin\jlink.exe'))) {
    throw 'Kein JDK gefunden. Bitte JDK 21 installieren und JAVA_HOME setzen (z. B. Eclipse Temurin 21).'
}
$env:JAVA_HOME = $javaHome
Write-Host "Berichtsheft-Setup $version"

# ---------------------------------------------------------------- Was ist zu bauen?
$frontendDist = Join-Path $angular 'dist\azubi-portal\browser'
$frontendIndex = Join-Path $frontendDist 'index.html'
$frontendStand = Stand @((Join-Path $angular 'src'), (Join-Path $angular 'public'), (Join-Path $angular 'package.json'),
                         (Join-Path $angular 'package-lock.json'), (Join-Path $angular 'angular.json'))
$frontendBauen = $Neu -or -not (Test-Path $frontendIndex) -or $frontendStand -gt [IO.File]::GetLastWriteTimeUtc($frontendIndex)

$jarSuche = { Get-ChildItem (Join-Path $backend 'target') -Filter '*.jar' -ErrorAction SilentlyContinue |
              Where-Object { $_.Name -notlike '*-plain.jar' -and $_.Name -notlike '*-sources.jar' } | Select-Object -First 1 }
$jar = & $jarSuche
$backendStand = Stand @((Join-Path $backend 'src\main'), (Join-Path $backend 'pom.xml'))
$backendBauen = $Neu -or -not $jar -or $jar.Name -notlike "*$version*" -or $backendStand -gt $jar.LastWriteTimeUtc

# ---------------------------------------------------------------- Frontend und Backend (parallel)
$frontendProzess = $null
$frontendLog = Join-Path $cache 'frontend-build.log'
if ($frontendBauen) {
    if (-not (Test-Path (Join-Path $angular 'node_modules'))) {
        Schritt 'Frontend: Pakete installieren (npm ci, nur beim ersten Mal)'
        Push-Location $angular; try { Ausfuehren 'npm.cmd' @('ci', '--no-audit', '--no-fund') } finally { Pop-Location }
    }
    if ($MitTests) {
        Schritt 'Frontend: Tests'
        Push-Location $angular; try { Ausfuehren 'npx.cmd' @('ng', 'test', '--watch=false') } finally { Pop-Location }
    }
    Schritt 'Frontend bauen (im Hintergrund)'
    $frontendProzess = Start-Process cmd.exe -ArgumentList '/c', "npx ng build > `"$frontendLog`" 2>&1" `
            -WorkingDirectory $angular -WindowStyle Hidden -PassThru
} else {
    Schritt 'Frontend unverändert – vorhandener Build wird verwendet'
}

if ($backendBauen) {
    Schritt $(if ($MitTests) { 'Backend bauen und testen (Maven)' } else { 'Backend bauen (Maven, ohne Tests)' })
    $mvnArgs = @('-q', '-B')
    if ($Neu) { $mvnArgs += 'clean' }
    $mvnArgs += 'package'
    if (-not $MitTests) { $mvnArgs += '-DskipTests' }
    Push-Location $backend; try { Ausfuehren (Join-Path $backend 'mvnw.cmd') $mvnArgs } finally { Pop-Location }
    $jar = & $jarSuche
    if (-not $jar) { throw 'Backend-JAR nicht gefunden (target\*.jar)' }
} else {
    Schritt 'Backend unverändert – vorhandenes JAR wird verwendet'
}

if ($frontendProzess) {
    $frontendProzess.WaitForExit()
    if ($frontendProzess.ExitCode -ne 0) {
        Get-Content $frontendLog -Tail 30
        throw "Frontend-Build fehlgeschlagen (Protokoll: $frontendLog)"
    }
    Schritt 'Frontend fertig'
}
if (-not (Test-Path $frontendIndex)) { throw "Frontend-Build fehlt: $frontendIndex" }

# ---------------------------------------------------------------- Java-Laufzeit und WinSW (zwischengespeichert)
$jre = Join-Path $cache 'jre'
$jreKennung = Join-Path $cache 'jre-version.txt'
$javaVersion = (Get-Content (Join-Path $javaHome 'release') -ErrorAction SilentlyContinue | Select-String 'JAVA_VERSION=') -replace '.*=', ''
if ($Neu -or -not (Test-Path (Join-Path $jre 'bin\java.exe')) -or (Get-Content $jreKennung -ErrorAction SilentlyContinue) -ne $javaVersion) {
    Schritt 'Java-Laufzeit erzeugen (jlink, nur bei neuem JDK)'
    if (Test-Path $jre) { Remove-Item $jre -Recurse -Force }
    $vorhanden = & (Join-Path $javaHome 'bin\java.exe') --list-modules | ForEach-Object { ($_ -split '@')[0] }
    $gewuenscht = 'java.se', 'jdk.unsupported', 'jdk.crypto.ec', 'jdk.crypto.cryptoki', 'jdk.zipfs', 'jdk.management',
                  'jdk.localedata', 'jdk.naming.dns', 'jdk.charsets', 'jdk.net', 'jdk.httpserver'
    $module = ($gewuenscht | Where-Object { $vorhanden -contains $_ }) -join ','
    Ausfuehren (Join-Path $javaHome 'bin\jlink.exe') @('--add-modules', $module, '--strip-debug', '--no-man-pages',
            '--no-header-files', '--compress=zip-6', '--output', $jre)
    Set-Content $jreKennung $javaVersion
}

$winsw = Join-Path $cache 'WinSW-x64.exe'
if (-not (Test-Path $winsw)) {
    $alt = Join-Path $release 'WinSW-x64.exe'
    if (Test-Path $alt) {
        Move-Item $alt $winsw
    } else {
        Schritt 'WinSW herunterladen (nur beim ersten Mal)'
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        Invoke-WebRequest -Uri $WinSwUrl -OutFile $winsw -UseBasicParsing
    }
}

# ---------------------------------------------------------------- ZIP packen
Schritt "ZIP packen: $zip"
Add-Type -AssemblyName System.IO.Compression, System.IO.Compression.FileSystem
if (Test-Path $zip) { Remove-Item $zip -Force }
$archiv = [IO.Compression.ZipFile]::Open($zip, 'Create')
try {
    $ohne = [IO.Compression.CompressionLevel]::NoCompression
    $klein = [IO.Compression.CompressionLevel]::Fastest
    function Datei([string]$quelle, [string]$name, $stufe = $ohne) {
        [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archiv, $quelle, $name, $stufe)
    }
    function Ordner([string]$quelle, [string]$ziel, $stufe = $ohne) {
        $basis = $quelle.TrimEnd('\').Length + 1
        foreach ($d in [IO.Directory]::EnumerateFiles($quelle, '*', 'AllDirectories')) {
            Datei $d ($ziel + '/' + $d.Substring($basis).Replace('\', '/')) $stufe
        }
    }
    function Text([string]$name, [string]$inhalt) {
        $eintrag = $archiv.CreateEntry($name, $klein)
        $schreiber = New-Object IO.StreamWriter($eintrag.Open(), (New-Object Text.UTF8Encoding($false)))
        try { $schreiber.Write($inhalt) } finally { $schreiber.Dispose() }
    }
    $setup = Join-Path $PSScriptRoot 'setup'
    Datei (Join-Path $setup 'Setup.cmd') 'Setup.cmd' $klein
    Datei (Join-Path $setup 'setup.ps1') 'setup/setup.ps1' $klein
    Datei (Join-Path $setup 'setup-funktionen.ps1') 'setup/setup-funktionen.ps1' $klein
    Datei $jar.FullName 'app/berichtsheft.jar'
    Datei $winsw 'app/berichtsheft-service.exe'
    Ordner $frontendDist 'app/frontend' $klein
    Ordner $jre 'app/jre' $klein
    Text 'info/version.txt' "berichtsheft-$version, gebaut am $(Get-Date -Format 'dd.MM.yyyy HH:mm')"
    Datei (Join-Path $backend 'CHANGELOG.md') 'info/CHANGELOG-Backend.md' $klein
    Datei (Join-Path $FrontendPfad 'CHANGELOG.md') 'info/CHANGELOG-Frontend.md' $klein
} finally { $archiv.Dispose() }

$groesse = [math]::Round((Get-Item $zip).Length / 1MB)
Schritt "Fertig: $zip ($groesse MB)"
Write-Host 'Auf dem Server: ZIP entpacken und Setup.cmd doppelklicken.'
