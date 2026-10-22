<#
.SYNOPSIS
    Baut Frontend und Backend und packt alles für den Server zusammen (ZIP).

.DESCRIPTION
    Auf dem Entwicklungsrechner ausführen (braucht Node.js, npm, JDK 21 und Internet).
    Ergebnis: release\berichtsheft-<Version>.zip mit
      berichtsheft.jar            Backend (Spring Boot)
      frontend\                   gebautes Angular-Frontend, wird vom Backend ausgeliefert
      jre\                        eigene Java-Laufzeit (der Server braucht kein Java)
      berichtsheft-service.exe    WinSW – startet die Anwendung als Windows-Dienst
      install / update / uninstall-Skripte und SERVER-ANLEITUNG.md

.PARAMETER FrontendPfad
    Ordner des Frontend-Projekts (Standard: ..\Berichtsheft_Frontend neben diesem Projekt).

.PARAMETER OhneJava
    Keine eigene Java-Laufzeit beilegen – dann muss auf dem Server Java 21 installiert sein.

.PARAMETER OhneTests
    Backend-Tests beim Bauen überspringen.

.EXAMPLE
    .\deploy\build-release.cmd
    .\deploy\build-release.cmd -FrontendPfad D:\Projekte\Berichtsheft_Frontend -OhneTests
#>
[CmdletBinding()]
param(
    [string]$FrontendPfad,
    [switch]$OhneJava,
    [switch]$OhneTests
)

$ErrorActionPreference = 'Stop'
$WinSwUrl = 'https://github.com/winsw/winsw/releases/download/v2.12.0/WinSW-x64.exe'

function Schritt([string]$text) { Write-Host "`n==> $text" -ForegroundColor Cyan }
function Pruefe-Befehl([string]$name, [string]$hinweis) {
    if (-not (Get-Command $name -ErrorAction SilentlyContinue)) { throw "$name nicht gefunden. $hinweis" }
}
function Ausfuehren([string]$exe, [string[]]$argumente) {
    & $exe @argumente
    if ($LASTEXITCODE -ne 0) { throw "$exe $($argumente -join ' ') ist fehlgeschlagen (Exit-Code $LASTEXITCODE)" }
}

$backend = Split-Path -Parent $PSScriptRoot
if (-not $FrontendPfad) { $FrontendPfad = Join-Path (Split-Path -Parent $backend) 'Berichtsheft_Frontend' }
$angular = Join-Path $FrontendPfad 'azubi-portal'
if (-not (Test-Path (Join-Path $angular 'package.json'))) {
    throw "Frontend nicht gefunden: $angular (Pfad mit -FrontendPfad angeben)"
}

$version = ([xml](Get-Content (Join-Path $backend 'pom.xml') -Raw -Encoding UTF8)).project.version
$name = "berichtsheft-$version"
$release = Join-Path $backend 'release'
$ziel = Join-Path $release $name
Write-Host "Baue $name"
Write-Host "  Backend : $backend"
Write-Host "  Frontend: $angular"

# ---------------------------------------------------------------- Voraussetzungen
Pruefe-Befehl 'npm' 'Bitte Node.js 20 oder neuer installieren: https://nodejs.org'
$javaHome = $env:JAVA_HOME
if (-not $javaHome) {
    $javaExe = (Get-Command java -ErrorAction SilentlyContinue).Source
    if ($javaExe) { $javaHome = Split-Path -Parent (Split-Path -Parent $javaExe) }
}
if (-not $javaHome -or -not (Test-Path (Join-Path $javaHome 'bin\javac.exe'))) {
    throw 'Kein JDK gefunden. Bitte JDK 21 installieren und JAVA_HOME setzen (z. B. Eclipse Temurin 21).'
}
$env:JAVA_HOME = $javaHome

# ---------------------------------------------------------------- Frontend
Schritt 'Frontend bauen (npm ci, ng build)'
Push-Location $angular
try {
    Ausfuehren 'npm' @('ci', '--no-audit', '--no-fund')
    Ausfuehren 'npm' @('run', 'build')
} finally { Pop-Location }
$frontendDist = Join-Path $angular 'dist\azubi-portal\browser'
if (-not (Test-Path (Join-Path $frontendDist 'index.html'))) { throw "Frontend-Build fehlt: $frontendDist\index.html" }

# ---------------------------------------------------------------- Backend
Schritt 'Backend bauen (Maven)'
Push-Location $backend
try {
    $mvnArgs = @('-B', 'clean', 'package')
    if ($OhneTests) { $mvnArgs += '-DskipTests' }
    Ausfuehren (Join-Path $backend 'mvnw.cmd') $mvnArgs
} finally { Pop-Location }
$jar = Get-ChildItem (Join-Path $backend 'target') -Filter '*.jar' |
        Where-Object { $_.Name -notlike '*-plain.jar' -and $_.Name -notlike '*-sources.jar' } |
        Select-Object -First 1
if (-not $jar) { throw 'Backend-JAR nicht gefunden (target\*.jar)' }

# ---------------------------------------------------------------- Zusammenstellen
Schritt "Release zusammenstellen: $ziel"
if (Test-Path $ziel) { Remove-Item $ziel -Recurse -Force }
New-Item -ItemType Directory -Path $ziel | Out-Null
Copy-Item $jar.FullName (Join-Path $ziel 'berichtsheft.jar')
Copy-Item $frontendDist (Join-Path $ziel 'frontend') -Recurse
foreach ($datei in 'install.ps1', 'install.cmd', 'update.ps1', 'update.cmd', 'uninstall.ps1', 'uninstall.cmd', 'zertifikat.ps1', 'zertifikat.cmd',
                   'berichtsheft-service.xml.vorlage', 'SERVER-ANLEITUNG.md') {
    Copy-Item (Join-Path $PSScriptRoot $datei) $ziel
}
Set-Content -Path (Join-Path $ziel 'VERSION.txt') -Value "$name, gebaut am $(Get-Date -Format 'dd.MM.yyyy HH:mm')" -Encoding UTF8

# WinSW (Windows-Dienst-Hülle)
$winsw = Join-Path $ziel 'berichtsheft-service.exe'
$cache = Join-Path $release 'WinSW-x64.exe'
if (-not (Test-Path $cache)) {
    Schritt 'WinSW herunterladen'
    try {
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        Invoke-WebRequest -Uri $WinSwUrl -OutFile $cache -UseBasicParsing
    } catch {
        Write-Warning "WinSW konnte nicht heruntergeladen werden ($($_.Exception.Message))."
        Write-Warning "Bitte $WinSwUrl manuell herunterladen und als $cache ablegen, dann erneut bauen."
    }
}
if (Test-Path $cache) { Copy-Item $cache $winsw }

# Eigene Java-Laufzeit (jlink) – nur die benötigten Module, ca. 60 MB
if (-not $OhneJava) {
    Schritt 'Java-Laufzeit erzeugen (jlink)'
    $jlink = Join-Path $javaHome 'bin\jlink.exe'
    if (-not (Test-Path $jlink)) { throw "jlink nicht gefunden ($jlink). JDK statt JRE verwenden oder -OhneJava angeben." }
    $vorhanden = & (Join-Path $javaHome 'bin\java.exe') --list-modules | ForEach-Object { ($_ -split '@')[0] }
    $gewuenscht = 'java.se', 'jdk.unsupported', 'jdk.crypto.ec', 'jdk.crypto.cryptoki', 'jdk.zipfs', 'jdk.management',
                  'jdk.localedata', 'jdk.naming.dns', 'jdk.charsets', 'jdk.net', 'jdk.httpserver'
    $module = ($gewuenscht | Where-Object { $vorhanden -contains $_ }) -join ','
    Ausfuehren $jlink @('--add-modules', $module, '--strip-debug', '--no-man-pages', '--no-header-files',
                        '--compress=zip-6', '--output', (Join-Path $ziel 'jre'))
}

# ---------------------------------------------------------------- ZIP
Schritt 'ZIP erstellen'
$zip = Join-Path $release "$name.zip"
if (Test-Path $zip) { Remove-Item $zip -Force }
Compress-Archive -Path (Join-Path $ziel '*') -DestinationPath $zip -CompressionLevel Optimal

Write-Host "`nFertig: $zip" -ForegroundColor Green
if (-not (Test-Path $winsw)) { Write-Warning 'Im Release fehlt berichtsheft-service.exe (WinSW) – siehe Warnung oben.' }
Write-Host 'Nächster Schritt: ZIP auf den Server kopieren, entpacken und install.cmd als Administrator ausführen.'
Write-Host 'Details: SERVER-ANLEITUNG.md'
