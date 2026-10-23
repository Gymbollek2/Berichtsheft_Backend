<#
    Arbeitsschritte des Berichtsheft-Setups (ohne Fenster): Installation suchen, installieren,
    aktualisieren (auch an einen anderen Ort), deinstallieren, Änderungen aus den CHANGELOGs ermitteln.
    Wird von setup.ps1 geladen. Meldungen gehen an $script:Melden, der Fortschritt (0–100) an $script:Fortschritt.
#>

$script:DienstName = 'Berichtsheft'
$script:KonfigDatei = 'berichtsheft-service.xml'
$script:DienstExe = 'berichtsheft-service.exe'
$script:RegEigener = 'HKLM:\SOFTWARE\Berichtsheft'
$script:RegProgramme = 'HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\Berichtsheft'
$script:Melden = { param($text) Write-Host $text }
$script:Fortschritt = { param($prozent) }
$script:Protokoll = New-Object Text.StringBuilder

# Dateien früherer ZIP-Releases (Skripte) und des Setups, die beim Aktualisieren ersetzt werden
$script:Veraltet = 'install.ps1', 'install.cmd', 'update.ps1', 'update.cmd', 'uninstall.ps1', 'uninstall.cmd',
                   'zertifikat.ps1', 'zertifikat.cmd', 'berichtsheft-service.xml.vorlage', 'SERVER-ANLEITUNG.md',
                   'Berichtsheft-Verwaltung.exe'

function Melde([string]$text) {
    [void]$script:Protokoll.AppendLine("$(Get-Date -Format 'HH:mm:ss')  $text")
    & $script:Melden $text | Out-Null
}

function Schritt([int]$prozent, [string]$text) {
    & $script:Fortschritt $prozent | Out-Null
    Melde "» $text"
}

# ====================================================================== Versionen

function Version-Nummer([string]$text) {
    if ($text -match '(\d+\.\d+(\.\d+)?)') { return [version]$Matches[1] }
    return $null
}

function Version-Datum([string]$text) {
    if ($text -match '(\d{2}\.\d{2}\.\d{4} \d{2}:\d{2})') {
        return [datetime]::ParseExact($Matches[1], 'dd.MM.yyyy HH:mm', [Globalization.CultureInfo]::InvariantCulture)
    }
    return $null
}

function Version-Kurz([string]$text) {
    $v = Version-Nummer $text
    if (-not $v) { return 'unbekannt' }
    $d = Version-Datum $text
    if ($d) { return "$v (gebaut am $($d.ToString('dd.MM.yyyy HH:mm')))" }
    return "$v"
}

# ====================================================================== Server

function Server-Rechnername { $env:COMPUTERNAME.ToLower() }

function Server-Domaene {
    try { return [Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().DomainName } catch { return '' }
}

function Server-Namen {
    $voll = Server-Rechnername
    try { $voll = [Net.Dns]::GetHostEntry('').HostName.ToLower() } catch { }
    return @(@($voll, (Server-Rechnername)) | Select-Object -Unique)
}

function Server-VorschlagOrdner {
    if (Test-Path 'F:\') { return 'F:\Program Files\Berichtsheft' }
    return (Join-Path $env:ProgramFiles 'Berichtsheft')
}

function Port-Belegt([int]$port) {
    return [bool]([Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners() |
            Where-Object Port -eq $port)
}

# ====================================================================== Installationen

function Dienst-Ordner {
    $schluessel = "HKLM:\SYSTEM\CurrentControlSet\Services\$script:DienstName"
    $pfad = (Get-ItemProperty $schluessel -Name ImagePath -ErrorAction SilentlyContinue).ImagePath
    if (-not $pfad) { return $null }
    if ($pfad -match '^"([^"]+)"' -or $pfad -match '^(\S+)') {
        return (Split-Path -Parent ([Environment]::ExpandEnvironmentVariables($Matches[1]))).TrimEnd('\')
    }
    return $null
}

function Installation-Laden([string]$ordner, [bool]$dienstAktiv) {
    $xmlPfad = Join-Path $ordner $script:KonfigDatei
    $konfig = New-Object Xml.XmlDocument
    $konfig.PreserveWhitespace = $true
    $konfig.Load($xmlPfad)
    if ($konfig.SelectSingleNode('/service/id').InnerText.Trim() -ne $script:DienstName) { return $null }
    $versionDatei = Join-Path $ordner 'VERSION.txt'
    $inst = [pscustomobject]@{
        Ordner      = [IO.Path]::GetFullPath($ordner).TrimEnd('\')
        DienstAktiv = $dienstAktiv
        Konfig      = $konfig
        VersionText = $(if (Test-Path $versionDatei) { (Get-Content $versionDatei -Raw -Encoding UTF8).Trim() } else { '' })
    }
    $env = @{}
    foreach ($e in $konfig.SelectNodes('/service/env')) { $env[$e.GetAttribute('name')] = $e.GetAttribute('value') }
    $inst | Add-Member Env $env
    $inst | Add-Member Port $(if ($env.PORT) { [int]$env.PORT } else { 5016 })
    $inst | Add-Member Https ($env.SSL_ENABLED -ne 'false')
    $inst | Add-Member EigenesZertifikat ([bool]$env.SSL_KEYSTORE)
    $inst | Add-Member Sso ($env.SSO_ENABLED -eq 'true')
    $inst | Add-Member PasswortLogin ($env.PASSWORT_LOGIN -ne 'false')
    $inst | Add-Member SsoDomaenen ([string]$env.SSO_DOMAINS)
    $inst | Add-Member Hostnamen @(([string]$env.SSL_HOSTNAMES) -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ })
    $hostname = if ($inst.Hostnamen.Count) { $inst.Hostnamen[0] } else { Server-Rechnername }
    $inst | Add-Member Adresse "$(if ($inst.Https) { 'https' } else { 'http' })://${hostname}:$($inst.Port)/"
    return $inst
}

<#
    Sucht Installationen: über den Windows-Dienst, den Registry-Eintrag des Setups und auf allen lokalen
    Festplatten (bis zu vier Ordnerebenen tief). Die Installation des laufenden Dienstes steht vorne.
#>
function Installationen-Suchen([scriptblock]$status) {
    $gefunden = @{}
    $dienstOrdner = Dienst-Ordner
    $pruefen = {
        param($ordner)
        if (-not $ordner -or -not [IO.File]::Exists((Join-Path $ordner $script:KonfigDatei))) { return }
        $voll = [IO.Path]::GetFullPath($ordner).TrimEnd('\')
        if ($gefunden.ContainsKey($voll)) { return }
        try {
            $inst = Installation-Laden $voll ($voll -eq $dienstOrdner)
            if ($inst) { $gefunden[$voll] = $inst }
        } catch { }
    }
    if ($status) { & $status 'Windows-Dienst prüfen …' }
    & $pruefen $dienstOrdner
    & $pruefen (Get-ItemProperty $script:RegEigener -Name Ordner -ErrorAction SilentlyContinue).Ordner

    $auslassen = @('Windows', '$Recycle.Bin', 'System Volume Information', 'Recovery', 'PerfLogs', '$WinREAgent',
                   'node_modules', '.git', 'WinSxS', 'Microsoft', 'Packages', 'AppData', 'jre', 'frontend', 'logs', 'backup')
    foreach ($laufwerk in [IO.DriveInfo]::GetDrives() | Where-Object { $_.DriveType -eq 'Fixed' -and $_.IsReady }) {
        if ($status) { & $status "Laufwerk $($laufwerk.Name) durchsuchen …" }
        $offen = New-Object 'Collections.Generic.Queue[object]'
        $offen.Enqueue(@($laufwerk.RootDirectory.FullName, 0))
        while ($offen.Count -gt 0) {
            $eintrag = $offen.Dequeue()
            & $pruefen $eintrag[0]
            if ($eintrag[1] -ge 4) { continue }
            try { $kinder = [IO.Directory]::GetDirectories($eintrag[0]) } catch { continue }
            foreach ($kind in $kinder) {
                try {
                    $info = New-Object IO.DirectoryInfo $kind
                    if (($info.Attributes -band [IO.FileAttributes]::ReparsePoint) -or $auslassen -contains $info.Name) { continue }
                } catch { continue }
                $offen.Enqueue(@($kind, ($eintrag[1] + 1)))
            }
        }
    }
    return @($gefunden.Values | Sort-Object @{ Expression = 'DienstAktiv'; Descending = $true }, Ordner)
}

# ====================================================================== Einstellungen

function Einstellungen-Neu {
    return [pscustomobject]@{
        Ordner            = Server-VorschlagOrdner
        Port              = 5016
        Hostnamen         = @(Server-Namen)
        Https             = $true
        Zertifikat        = 'Selbstsigniert'   # Behalten | Selbstsigniert | Datei
        PfxDatei          = ''
        PfxPasswort       = ''
        Sso               = [bool](Server-Domaene)
        SsoDomaenen       = ''
        PasswortLogin     = $true
        ErsterAusbilder   = $false
        AusbilderBenutzer = $env:USERNAME.ToLower()
        AusbilderVorname  = 'Ausbildung'
        AusbilderNachname = 'Leitung'
        AusbilderPasswort = ''
        Speicher          = '768m'
    }
}

function Einstellungen-Aus($inst) {
    $e = Einstellungen-Neu
    $e.Ordner = $inst.Ordner
    $e.Port = $inst.Port
    if ($inst.Hostnamen.Count) { $e.Hostnamen = @($inst.Hostnamen) }
    $e.Https = $inst.Https
    $e.Zertifikat = 'Behalten'
    $e.Sso = $inst.Sso
    $e.SsoDomaenen = $inst.SsoDomaenen
    $e.PasswortLogin = $inst.PasswortLogin
    return $e
}

function Einstellungen-Adresse($e) {
    $hostname = if ($e.Hostnamen.Count) { $e.Hostnamen[0] } else { Server-Rechnername }
    return "$(if ($e.Https) { 'https' } else { 'http' })://${hostname}:$($e.Port)/"
}

# ====================================================================== Zertifikat prüfen

function Zertifikat-Pruefen([string]$datei, [string]$passwort, [string[]]$hostnamen) {
    $ergebnis = [pscustomobject]@{ Ok = $false; Meldung = ''; Namen = @() }
    $sammlung = New-Object Security.Cryptography.X509Certificates.X509Certificate2Collection
    try {
        $sammlung.Import($datei, $passwort, [Security.Cryptography.X509Certificates.X509KeyStorageFlags]32) # EphemeralKeySet
    } catch {
        $ergebnis.Meldung = "Die Datei lässt sich nicht öffnen – Passwort falsch oder keine .pfx-Datei?`n$($_.Exception.InnerException.Message)"
        return $ergebnis
    }
    $zert = $sammlung | Where-Object HasPrivateKey | Select-Object -First 1
    if (-not $zert) {
        $ergebnis.Meldung = 'Die Datei enthält keinen privaten Schlüssel. Bitte eine .pfx MIT privatem Schlüssel verwenden.'
        return $ergebnis
    }
    $ergebnis.Namen = @($zert.DnsNameList | ForEach-Object { $_.Unicode.ToLower() } | Select-Object -Unique)
    if ($zert.NotAfter -lt (Get-Date)) {
        $ergebnis.Meldung = "Das Zertifikat ist am $($zert.NotAfter.ToString('dd.MM.yyyy')) abgelaufen."
        return $ergebnis
    }
    $passend = @($hostnamen | Where-Object { $n = $_.ToLower(); $ergebnis.Namen | Where-Object { $_ -eq $n -or ($_ -like '`*.*' -and $n -like $_) } })
    $ergebnis.Ok = $true
    $ergebnis.Meldung = "Ausgestellt für: $($ergebnis.Namen -join ', ')`nAussteller: $($zert.GetNameInfo('SimpleName', $true))`nGültig bis: $($zert.NotAfter.ToString('dd.MM.yyyy'))"
    if (-not $passend) { $ergebnis.Meldung += "`n`nAchtung: Keiner der Servernamen steht im Zertifikat – Browser warnen dann weiterhin." }
    return $ergebnis
}

# ====================================================================== Änderungen (CHANGELOG)

function Changelog-Punkte([string]$markdown) {
    $punkte = New-Object Collections.Generic.List[object]
    $version = $null; $datum = $null; $abschnitt = 'Änderungen'; $aktuell = $null
    foreach ($zeile in ($markdown -replace "`r", '') -split "`n") {
        if ($zeile -match '^## \[([^\]]+)\](?:\s*[–-]\s*(\d{4}-\d{2}-\d{2}))?') {
            $version = if ($Matches[1] -match '^\d') { $Matches[1] } else { $null } # [Unveröffentlicht] ist immer neu
            $datum = if ($Matches[2]) { [datetime]::ParseExact($Matches[2], 'yyyy-MM-dd', $null) } else { $null }
            $aktuell = $null
        } elseif ($zeile.StartsWith('### ')) {
            $abschnitt = $zeile.Substring(4).Trim(); $aktuell = $null
        } elseif ($zeile.StartsWith('- ')) {
            $aktuell = [pscustomobject]@{ Version = $version; Datum = $datum; Abschnitt = $abschnitt; Text = $zeile.Substring(2).Trim() }
            $punkte.Add($aktuell)
        } elseif ($aktuell -and $zeile.StartsWith('  ') -and $zeile.Trim()) {
            $aktuell.Text += ' ' + $zeile.Trim()
        } else {
            $aktuell = $null
        }
    }
    return $punkte
}

<#
    Was bringt die neue Version mit? Das Setup legt die CHANGELOGs jeder Installation bei; neu ist jeder Punkt,
    der dort noch nicht stand. Ältere Installationen haben keine – dann zählen die Abschnitte, die neuer als die
    installierte Version (Server) bzw. als ihr Build-Datum (Oberfläche, eigene Versionsnummern) sind.
#>
function Aenderungen-Beschreiben($inst, [string]$infoOrdner) {
    $text = New-Object Text.StringBuilder
    foreach ($teil in @(@('SERVER', 'CHANGELOG-Backend.md', $false), @('OBERFLÄCHE', 'CHANGELOG-Frontend.md', $true))) {
        $neuDatei = Join-Path $infoOrdner $teil[1]
        if (-not (Test-Path $neuDatei)) { continue }
        $neu = Changelog-Punkte (Get-Content $neuDatei -Raw -Encoding UTF8)
        $altDatei = Join-Path $inst.Ordner $teil[1]
        if (Test-Path $altDatei) {
            $bekannt = @{}
            foreach ($p in Changelog-Punkte (Get-Content $altDatei -Raw -Encoding UTF8)) { $bekannt[($p.Text -replace '\s+', ' ')] = $true }
            $punkte = @($neu | Where-Object { -not $bekannt.ContainsKey(($_.Text -replace '\s+', ' ')) })
        } else {
            $v = Version-Nummer $inst.VersionText; $d = Version-Datum $inst.VersionText
            $punkte = @($neu | Where-Object {
                -not $_.Version -or
                (-not $teil[2] -and $v -and [version]$_.Version -gt $v) -or
                ($teil[2] -and $d -and $_.Datum -and $_.Datum.Date -gt $d.Date) })
        }
        if (-not $punkte.Count) { continue }
        [void]$text.AppendLine($teil[0])
        foreach ($gruppe in $punkte | Group-Object Abschnitt) {
            [void]$text.AppendLine("  $($gruppe.Name)")
            foreach ($p in $gruppe.Group) {
                [void]$text.AppendLine("    •  " + ($p.Text -replace '\*\*', '' -replace '`([^`]*)`', '$1'))
            }
        }
        [void]$text.AppendLine()
    }
    if ($text.Length -eq 0) { return 'Keine inhaltlichen Änderungen gegenüber der installierten Version.' }
    return $text.ToString().TrimEnd()
}

# ====================================================================== Neuinstallation

function Berichtsheft-Installieren([string]$paketOrdner, $e) {
    $ordner = $e.Ordner
    Schritt 2 "Ordner anlegen: $ordner"
    New-Item -ItemType Directory -Force -Path (Join-Path $ordner 'data'), (Join-Path $ordner 'logs') | Out-Null
    $datenbankVorhanden = Test-Path (Join-Path $ordner 'data\berichtsheft.mv.db')

    Schritt 10 'Anwendung kopieren'
    Anwendung-Kopieren $paketOrdner $ordner

    Schritt 60 'Dienst konfigurieren'
    $konfig = Konfig-Neu $e
    Zertifikat-Eintragen $konfig $e $ordner
    Konfig-Speichern $konfig $ordner
    if ($e.ErsterAusbilder -and -not $datenbankVorhanden) { Erster-Ausbilder $ordner $e }
    Rechte-Setzen $ordner

    Schritt 70 "Firewall: Port $($e.Port) freigeben"
    Firewall-Freigeben $e.Port

    Schritt 78 'Windows-Dienst einrichten'
    $verwaist = Dienst-Ordner
    if ($verwaist) { Dienst-Abmelden $verwaist } # Dienst einer gelöschten Installation
    WinSw $ordner 'install'
    Registrieren $paketOrdner $ordner (Einstellungen-Adresse $e)

    Schritt 84 'Dienst starten'
    Dienst-Starten $ordner $e.Port
    Schritt 100 'Fertig'
}

# ====================================================================== Aktualisieren

function Berichtsheft-Aktualisieren([string]$paketOrdner, $inst, $e) {
    $alt = $inst.Ordner
    $neu = [IO.Path]::GetFullPath($e.Ordner).TrimEnd('\')
    $verschieben = $alt -ne $neu

    Schritt 2 'Dienst anhalten'
    Dienst-Anhalten
    Schritt 8 'Datenbank sichern'
    $sicherung = Datenbank-Sichern $alt

    if ($verschieben) {
        Schritt 12 'Dienst am alten Ort abmelden'
        Dienst-Abmelden $alt
        Schritt 15 "Alles nach $neu kopieren (auch Datenbank und Sicherungen)"
        Ordner-Kopieren $alt $neu
        if ($sicherung) { $sicherung = $neu + $sicherung.Substring($alt.Length) }
    }

    if ($paketOrdner) {
        Schritt 35 'Neue Version einspielen'
        foreach ($name in 'frontend', 'jre') {
            $pfad = Join-Path $neu $name
            if (Test-Path $pfad) { Remove-Item $pfad -Recurse -Force }
        }
        foreach ($datei in $script:Veraltet) { Remove-Item (Join-Path $neu $datei) -Force -ErrorAction SilentlyContinue }
        Anwendung-Kopieren $paketOrdner $neu
    }

    Schritt 70 'Einstellungen übernehmen'
    $konfig = (Installation-Laden $neu $false).Konfig
    Anmeldung-Setzen $konfig $e
    Setze-Env $konfig 'PORT' "$($e.Port)"
    Setze-Env $konfig 'SSL_ENABLED' $(if ($e.Https) { 'true' } else { 'false' })
    Setze-Env $konfig 'SSL_HOSTNAMES' ($e.Hostnamen -join ',')
    Zertifikat-Eintragen $konfig $e $neu
    Konfig-Speichern $konfig $neu
    Rechte-Setzen $neu

    if ($e.Port -ne $inst.Port -or $verschieben -or -not $inst.DienstAktiv) {
        Schritt 76 "Firewall: Port $($e.Port) freigeben"
        Firewall-Freigeben $e.Port
    }
    if ($verschieben -or -not $inst.DienstAktiv) {
        Schritt 80 'Windows-Dienst einrichten'
        $anderer = Dienst-Ordner
        if ($anderer) { Dienst-Abmelden $anderer }
        WinSw $neu 'install'
    }
    Registrieren $paketOrdner $neu (Einstellungen-Adresse $e)

    Schritt 84 'Dienst starten'
    Dienst-Starten $neu $e.Port

    if ($verschieben) {
        Schritt 97 "Alten Ordner entfernen: $alt"
        try { Remove-Item $alt -Recurse -Force } catch { Melde "Der alte Ordner konnte nicht ganz gelöscht werden ($($_.Exception.Message)). Bitte später von Hand löschen." }
    }
    Schritt 100 'Fertig'
    return $sicherung
}

# ====================================================================== Deinstallieren

function Berichtsheft-Deinstallieren($inst, [bool]$datenLoeschen) {
    Schritt 5 'Dienst anhalten'
    Dienst-Anhalten
    Schritt 25 'Dienst entfernen'
    Dienst-Abmelden $inst.Ordner
    Schritt 45 'Firewall-Regel entfernen'
    Get-NetFirewallRule -DisplayName 'Berichtsheft*' -ErrorAction SilentlyContinue | Remove-NetFirewallRule
    Registry-Entfernen $script:RegEigener; Registry-Entfernen $script:RegProgramme

    Schritt 60 $(if ($datenLoeschen) { 'Alles löschen' } else { 'Programmdateien löschen (Datenbank bleibt)' })
    Set-Location $env:TEMP
    foreach ($eintrag in Get-ChildItem $inst.Ordner -Force) {
        if (-not $datenLoeschen -and @('data', 'logs', 'backup') -contains $eintrag.Name) { continue }
        try { Remove-Item $eintrag.FullName -Recurse -Force } catch { Melde "Nicht gelöscht: $($eintrag.FullName) ($($_.Exception.Message))" }
    }
    if ($datenLoeschen) {
        try { Remove-Item $inst.Ordner -Recurse -Force } catch { Melde "Ordner nicht gelöscht: $($_.Exception.Message)" }
    } else {
        Melde "Die Datenbank bleibt in $(Join-Path $inst.Ordner 'data') erhalten."
    }
    Schritt 100 'Fertig'
}

# ====================================================================== Bausteine

function Anwendung-Kopieren([string]$paketOrdner, [string]$ziel) {
    Ordner-Kopieren (Join-Path $paketOrdner 'app') $ziel
    $info = Join-Path $paketOrdner 'info'
    Copy-Item (Join-Path $info 'version.txt') (Join-Path $ziel 'VERSION.txt') -Force
    Copy-Item (Join-Path $info 'CHANGELOG-Backend.md'), (Join-Path $info 'CHANGELOG-Frontend.md') $ziel -Force
    Melde "Version $(Version-Kurz (Get-Content (Join-Path $info 'version.txt') -Raw)) eingespielt."
}

function Konfig-Neu($e) {
    $bytes = New-Object byte[] 48
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $konfig = New-Object Xml.XmlDocument
    $konfig.PreserveWhitespace = $true
    $konfig.LoadXml(@"
<?xml version="1.0" encoding="UTF-8"?>
<!--
  Konfiguration des Windows-Dienstes (WinSW), angelegt vom Berichtsheft-Setup.
  Ändern am besten mit dem Setup (Verwaltung.cmd); nach Änderungen von Hand den Dienst neu starten.
  Achtung: enthält das JWT-Geheimnis – nur für Administratoren lesbar.
-->
<service>
  <id>$script:DienstName</id>
  <name>Berichtsheft (Azubi-Portal)</name>
  <description>Digitales Berichtsheft – Backend und Frontend</description>
  <executable>%BASE%\jre\bin\java.exe</executable>
  <arguments>-Xms256m -Xmx$($e.Speicher) -Dfile.encoding=UTF-8 -jar "%BASE%\berichtsheft.jar" --spring.profiles.active=prod</arguments>
  <workingdirectory>%BASE%</workingdirectory>
  <startmode>Automatic</startmode>
  <onfailure action="restart" delay="10 sec"/>
  <onfailure action="restart" delay="30 sec"/>
  <resetfailure>1 hour</resetfailure>
  <stoptimeout>30 sec</stoptimeout>
  <logpath>%BASE%\logs</logpath>
  <log mode="roll-by-size">
    <sizeThreshold>10240</sizeThreshold>
    <keepFiles>5</keepFiles>
  </log>
</service>
"@)
    Setze-Env $konfig 'PORT' "$($e.Port)"
    Setze-Env $konfig 'JWT_SECRET' ([Convert]::ToBase64String($bytes))
    Setze-Env $konfig 'SSL_ENABLED' $(if ($e.Https) { 'true' } else { 'false' })
    Setze-Env $konfig 'SSL_HOSTNAMES' ($e.Hostnamen -join ',')
    Setze-Env $konfig 'DEMO_DATA' 'false'
    Anmeldung-Setzen $konfig $e
    return $konfig
}

function Anmeldung-Setzen($konfig, $e) {
    Setze-Env $konfig 'SSO_ENABLED' $(if ($e.Sso) { 'true' } else { 'false' })
    Setze-Env $konfig 'SSO_DOMAINS' $(if ($e.Sso -and $e.SsoDomaenen.Trim()) { $e.SsoDomaenen.Trim() } else { $null })
    Setze-Env $konfig 'PASSWORT_LOGIN' $(if ($e.Sso -and -not $e.PasswortLogin) { 'false' } else { $null })
}

function Zertifikat-Eintragen($konfig, $e, [string]$ordner) {
    $ssl = Join-Path $ordner 'data\ssl'
    if ($e.Zertifikat -eq 'Datei') {
        New-Item -ItemType Directory -Force -Path $ssl | Out-Null
        $ziel = Join-Path $ssl 'server.pfx'
        if (Test-Path $ziel) { Copy-Item $ziel (Join-Path $ssl "server-$(Get-Date -Format 'yyyy-MM-dd_HH-mm').pfx.alt") -Force }
        Copy-Item $e.PfxDatei $ziel -Force
        Setze-Env $konfig 'SSL_KEYSTORE' 'file:./data/ssl/server.pfx'
        Setze-Env $konfig 'SSL_KEYSTORE_PASSWORD' $e.PfxPasswort
        Setze-Env $konfig 'SSL_KEYSTORE_TYPE' 'PKCS12'
        Melde 'Eigenes Zertifikat eingetragen.'
    } elseif ($e.Zertifikat -eq 'Selbstsigniert') {
        foreach ($name in 'SSL_KEYSTORE', 'SSL_KEYSTORE_PASSWORD', 'SSL_KEYSTORE_TYPE') { Setze-Env $konfig $name $null }
        # Mit den aktuellen Servernamen neu erzeugen lassen
        Remove-Item (Join-Path $ssl 'berichtsheft-dev.p12') -Force -ErrorAction SilentlyContinue
    }
}

function Setze-Env($konfig, [string]$name, $wert) {
    $service = $konfig.SelectSingleNode('/service')
    $vorhanden = @($service.SelectNodes('env') | Where-Object { $_.GetAttribute('name') -eq $name })
    if ($null -eq $wert) {
        foreach ($x in $vorhanden) { [void]$service.RemoveChild($x) }
        return
    }
    if ($vorhanden.Count) {
        $vorhanden[0].SetAttribute('value', [string]$wert)
        foreach ($x in $vorhanden | Select-Object -Skip 1) { [void]$service.RemoveChild($x) }
        return
    }
    $element = $konfig.CreateElement('env')
    $element.SetAttribute('name', $name)
    $element.SetAttribute('value', [string]$wert)
    [void]$service.AppendChild($konfig.CreateWhitespace('  '))
    [void]$service.AppendChild($element)
    [void]$service.AppendChild($konfig.CreateWhitespace("`r`n"))
}

function Konfig-Speichern($konfig, [string]$ordner) {
    # In die vorhandene Datei schreiben, damit die Zugriffsrechte (nur Administratoren/SYSTEM) bleiben
    $schreiber = New-Object IO.StreamWriter((Join-Path $ordner $script:KonfigDatei), $false, (New-Object Text.UTF8Encoding($false)))
    try { $konfig.Save($schreiber) } finally { $schreiber.Dispose() }
}

function Erster-Ausbilder([string]$ordner, $e) {
    $esc = { param($v) $v -replace '\\', '\\' }
    $inhalt = @(
        "benutzername=$(& $esc $e.AusbilderBenutzer)"
        "passwort=$(& $esc $e.AusbilderPasswort)"
        "vorname=$(& $esc $e.AusbilderVorname)"
        "nachname=$(& $esc $e.AusbilderNachname)"
    ) -join "`n"
    [IO.File]::WriteAllText((Join-Path $ordner 'data\erster-ausbilder.properties'), $inhalt, (New-Object Text.UTF8Encoding($false)))
}

# Konfiguration (JWT-Geheimnis, Zertifikat-Passwort) und Datenbank nur für Administratoren und SYSTEM
function Rechte-Setzen([string]$ordner) {
    Ausfuehren 'icacls.exe' @((Join-Path $ordner $script:KonfigDatei), '/inheritance:r', '/grant:r', '*S-1-5-32-544:(F)', '*S-1-5-18:(F)') | Out-Null
    Ausfuehren 'icacls.exe' @((Join-Path $ordner 'data'), '/inheritance:r', '/grant:r', '*S-1-5-32-544:(OI)(CI)(F)', '*S-1-5-18:(OI)(CI)(F)') | Out-Null
}

function Firewall-Freigeben([int]$port) {
    Get-NetFirewallRule -DisplayName 'Berichtsheft*' -ErrorAction SilentlyContinue | Remove-NetFirewallRule
    New-NetFirewallRule -DisplayName "Berichtsheft (TCP $port)" -Direction Inbound -Protocol TCP -LocalPort $port `
            -Action Allow -Profile Domain, Private | Out-Null
    Melde 'Freigegeben für Domänen- und private Netzwerke (nicht öffentlich).'
}

# Setup-Skripte in den Installationsordner (Verwaltung.cmd) und Eintrag unter „Apps / Programme und Features“
function Registrieren([string]$paketOrdner, [string]$ordner, [string]$adresse) {
    $quelle = $PSScriptRoot
    $ziel = Join-Path $ordner 'setup'
    if ($quelle.TrimEnd('\') -ne $ziel.TrimEnd('\')) {
        New-Item -ItemType Directory -Force -Path $ziel | Out-Null
        Copy-Item (Join-Path $quelle '*.ps1') $ziel -Force
    }
    [IO.File]::WriteAllText((Join-Path $ordner 'Verwaltung.cmd'), (
        "@echo off`r`nrem Berichtsheft verwalten: Einstellungen, Speicherort, Deinstallieren`r`n" +
        "powershell.exe -NoProfile -ExecutionPolicy Bypass -File `"%~dp0setup\setup.ps1`"`r`n"), [Text.Encoding]::ASCII)

    $version = (Get-Content (Join-Path $ordner 'VERSION.txt') -Raw -ErrorAction SilentlyContinue)
    $aufruf = "powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$ordner\setup\setup.ps1`""
    Registry-Schreiben $script:RegEigener @{ Ordner = $ordner; Version = ([string]$version).Trim() }
    Registry-Schreiben $script:RegProgramme @{
        DisplayName = 'Berichtsheft (Azubi-Portal)'; DisplayVersion = "$(Version-Nummer $version)"; Publisher = 'Azubi-Portal'
        InstallLocation = $ordner; URLInfoAbout = $adresse; UninstallString = $aufruf; ModifyPath = $aufruf
        DisplayIcon = "$ordner\jre\bin\java.exe"; NoRepair = 1; EstimatedSize = 200 * 1024
    }
}

# Mit reg.exe statt den PowerShell-Registry-Befehlen – die werden von manchen Sicherheitsrichtlinien abgewiesen
function Registry-Schreiben([string]$pfad, [hashtable]$werte) {
    $schluessel = $pfad -replace '^HKLM:', 'HKLM'
    foreach ($w in $werte.GetEnumerator()) {
        $art = if ($w.Value -is [int]) { 'REG_DWORD' } else { 'REG_SZ' }
        Ausfuehren 'reg.exe' @('add', $schluessel, '/v', $w.Key, '/t', $art, '/d', "$($w.Value)", '/f', '/reg:64') | Out-Null
    }
}

function Registry-Entfernen([string]$pfad) {
    Ausfuehren 'reg.exe' @('delete', ($pfad -replace '^HKLM:', 'HKLM'), '/f', '/reg:64') -OhnePruefung | Out-Null
}

function Dienst-Anhalten {
    $dienst = Get-Service -Name $script:DienstName -ErrorAction SilentlyContinue
    if (-not $dienst) { Melde 'Kein Dienst installiert.'; return }
    if ($dienst.Status -ne 'Stopped') {
        Stop-Service -Name $script:DienstName -Force
        $dienst.WaitForStatus('Stopped', [TimeSpan]::FromSeconds(90))
    }
    Warte 2 # Datenbankdatei freigeben lassen
    Melde 'Dienst angehalten.'
}

function Dienst-Abmelden([string]$ordner) {
    if (-not (Get-Service -Name $script:DienstName -ErrorAction SilentlyContinue)) { return }
    if ((Test-Path (Join-Path $ordner $script:DienstExe)) -and (Test-Path (Join-Path $ordner $script:KonfigDatei))) {
        WinSw $ordner 'uninstall'
    } else {
        Ausfuehren 'sc.exe' @('delete', $script:DienstName) | Out-Null
    }
    # Warten, bis Windows den Dienst wirklich entfernt hat
    for ($i = 0; $i -lt 30 -and (Get-Service -Name $script:DienstName -ErrorAction SilentlyContinue); $i++) { Warte 0.5 }
}

function Dienst-Starten([string]$ordner, [int]$port) {
    WinSw $ordner 'start'
    Melde 'Warte, bis die Anwendung antwortet (bis zu 2 Minuten) …'
    $ende = (Get-Date).AddMinutes(2)
    $p = 84
    while ((Get-Date) -lt $ende) {
        $client = New-Object Net.Sockets.TcpClient
        try {
            $client.Connect('127.0.0.1', $port)
            Melde 'Die Anwendung läuft.'
            return
        } catch {
            Warte 2
            & $script:Fortschritt ([Math]::Min(96, ++$p)) | Out-Null
        } finally { $client.Dispose() }
    }
    throw "Die Anwendung antwortet nicht auf Port $port. Bitte $(Join-Path $ordner 'logs\berichtsheft.log') prüfen."
}

function Datenbank-Sichern([string]$ordner) {
    $dateien = @(Get-ChildItem (Join-Path $ordner 'data') -Filter '*.db' -ErrorAction SilentlyContinue)
    if (-not $dateien.Count) { Melde 'Keine Datenbank gefunden – nichts zu sichern.'; return $null }
    $ziel = Join-Path $ordner ("backup\" + (Get-Date -Format 'yyyy-MM-dd_HH-mm-ss'))
    New-Item -ItemType Directory -Force -Path $ziel | Out-Null
    foreach ($datei in $dateien) { Copy-Item $datei.FullName $ziel }
    Melde "Gesichert nach $ziel"
    return $ziel
}

function Ordner-Kopieren([string]$quelle, [string]$ziel) {
    if ($ziel.StartsWith($quelle.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Der neue Ort darf nicht innerhalb des bisherigen Ordners liegen.'
    }
    # robocopy übernimmt Zugriffsrechte und kommt mit langen Pfaden zurecht; Codes ab 8 sind Fehler
    $code = Ausfuehren 'robocopy.exe' @($quelle, $ziel, '/E', '/COPY:DATS', '/R:2', '/W:2', '/MT:8', '/NFL', '/NDL', '/NP', '/NJH') -OhnePruefung
    if ($code -ge 8) { throw "Kopieren nach $ziel fehlgeschlagen (robocopy $code)." }
}

function WinSw([string]$ordner, [string]$befehl) {
    Ausfuehren (Join-Path $ordner $script:DienstExe) @($befehl) -Ordner $ordner | Out-Null
}

function Ausfuehren([string]$exe, [string[]]$argumente, [switch]$OhnePruefung, [string]$Ordner = $env:SystemRoot) {
    $info = New-Object Diagnostics.ProcessStartInfo $exe
    $info.Arguments = ($argumente | ForEach-Object { if ($_ -match '[\s"]') { '"' + ($_ -replace '"', '\"') + '"' } else { $_ } }) -join ' '
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $info.WorkingDirectory = $Ordner
    $p = [Diagnostics.Process]::Start($info)
    $fehler = $p.StandardError.ReadToEndAsync()
    $ausgabe = $p.StandardOutput.ReadToEndAsync()
    while (-not $p.HasExited) { Warte 0.1 }
    $p.WaitForExit()
    $text = ($ausgabe.Result + $fehler.Result).Trim()
    foreach ($zeile in $text -split "`n" | Where-Object { $_.Trim() }) { [void]$script:Protokoll.AppendLine("           $($zeile.TrimEnd())") }
    if (-not $OhnePruefung -and $p.ExitCode -ne 0) {
        throw "$([IO.Path]::GetFileName($exe)) $($argumente[0]) ist fehlgeschlagen (Code $($p.ExitCode)).`n$text"
    }
    return $p.ExitCode
}

# Warten, ohne das Fenster einfrieren zu lassen
function Warte([double]$sekunden) {
    $ende = (Get-Date).AddSeconds($sekunden)
    while ((Get-Date) -lt $ende) {
        if ('System.Windows.Forms.Application' -as [type]) { [Windows.Forms.Application]::DoEvents() }
        Start-Sleep -Milliseconds 50
    }
}
