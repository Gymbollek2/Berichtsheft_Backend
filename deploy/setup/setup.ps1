<#
.SYNOPSIS
    Berichtsheft-Setup mit Assistent: installieren, aktualisieren, Einstellungen ändern, verschieben, deinstallieren.

.DESCRIPTION
    Wird über Setup.cmd (im entpackten Setup-ZIP) bzw. Verwaltung.cmd (im Installationsordner) gestartet.
    Sucht zuerst nach einer vorhandenen Installation (Windows-Dienst, alle lokalen Laufwerke):
      - keine gefunden: Neuinstallation in vier Schritten
      - gefunden: Speicherort, Versionen und Änderungen anzeigen, dann aktualisieren (auf Wunsch an einen
        anderen Ort und mit geänderten Einstellungen) – oder deinstallieren
    Ohne mitgelieferte Anwendung (app\ fehlt, also aus dem Installationsordner gestartet) nur Verwaltung.
#>
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Windows.Forms, System.Drawing

# Paket: <Ordner>\Setup.cmd, <Ordner>\setup\setup.ps1, <Ordner>\app\…, <Ordner>\info\…
$PaketOrdner = Split-Path -Parent $PSScriptRoot
if (-not (Test-Path (Join-Path $PaketOrdner 'app\berichtsheft.jar'))) { $PaketOrdner = $null }

function Neu-Starten([string]$skript) {
    Start-Process powershell.exe -Verb RunAs -ArgumentList @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-WindowStyle', 'Hidden', '-File', "`"$skript`"")
}

try {
    # Die Verwaltung liegt im Installationsordner. Damit Verschieben und Deinstallieren ihn löschen können,
    # läuft sie als Kopie aus dem Temp-Ordner.
    $temp = [IO.Path]::GetTempPath()
    if (-not $PaketOrdner -and -not $PSScriptRoot.StartsWith($temp, [StringComparison]::OrdinalIgnoreCase)) {
        $kopie = Join-Path $temp "berichtsheft-verwaltung-$([guid]::NewGuid().ToString('N').Substring(0, 8))"
        New-Item -ItemType Directory -Path $kopie | Out-Null
        Copy-Item (Join-Path $PSScriptRoot '*.ps1') $kopie
        Neu-Starten (Join-Path $kopie 'setup.ps1')
        return
    }
    $istAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
            [Security.Principal.WindowsBuiltInRole]::Administrator)
    if (-not $istAdmin) {
        Neu-Starten $PSCommandPath
        return
    }
    if ($PaketOrdner) { Get-ChildItem $PaketOrdner -Recurse -File | Unblock-File -ErrorAction SilentlyContinue }
    . (Join-Path $PSScriptRoot 'setup-funktionen.ps1')
} catch {
    [Windows.Forms.MessageBox]::Show("Das Setup konnte nicht starten: $($_.Exception.Message)", 'Berichtsheft', 'OK', 'Error') | Out-Null
    return
}

# ====================================================================== Zustand

$S = @{
    Info                 = $(if ($PaketOrdner) { Join-Path $PaketOrdner 'info' })
    NeuVersion           = $(if ($PaketOrdner) { (Get-Content (Join-Path $PaketOrdner 'info\version.txt') -Raw -Encoding UTF8).Trim() })
    Gefunden             = @()
    Inst                 = $null
    E                    = $null
    Modus                = 'Installieren'   # Installieren | Aktualisieren | Deinstallieren
    EinstellungenAendern = $false
    DatenLoeschen        = $false
    Beschaeftigt         = $false
    Verlauf              = New-Object Collections.Stack
    Seite                = $null
    Breite               = 660
}

# Farben und Schrift wie in der Webanwendung (Frontend src/styles.css)
function Farbe([string]$hex) { [Drawing.ColorTranslator]::FromHtml($hex) }
$Navy = Farbe '#10024d'      # --navy-2: Kopf, Hauptknöpfe, Links
$Navy3 = Farbe '#1c1466'     # --navy-3: Hover
$Tinte = Farbe '#1d1b3a'     # --ink: Text
$Grau = Farbe '#5d5b74'      # --muted
$Linie = Farbe '#e4e3ec'     # --line
$LinieStark = Farbe '#cfcde0'
$Papier = Farbe '#f5f5f8'    # --paper: Hintergrund
$Flaeche = [Drawing.Color]::White
$Akzent = Farbe '#a3126f'    # --accent
$Gruen = Farbe '#2f6b4f'     # --ok
$Orange = Farbe '#84590a'    # --warn
$Rot = Farbe '#9b1c2c'       # --bad
$Schrift = if ((New-Object Drawing.Text.InstalledFontCollection).Families.Name -contains 'IBM Plex Sans') { 'IBM Plex Sans' } else { 'Segoe UI' }

# ====================================================================== Fenster

[Windows.Forms.Application]::EnableVisualStyles()
$Form = New-Object Windows.Forms.Form
$Form.Text = if ($PaketOrdner) { "Berichtsheft $(Version-Nummer $S.NeuVersion) – Einrichtung" } else { 'Berichtsheft – Verwaltung' }
$Form.Font = New-Object Drawing.Font($Schrift, 9.5)
$Form.ForeColor = $Tinte
$Form.BackColor = $Papier
$Form.AutoScaleMode = 'Font'
$Form.ClientSize = New-Object Drawing.Size(760, 660)
$Form.FormBorderStyle = 'FixedDialog'
$Form.MaximizeBox = $false
$Form.StartPosition = 'CenterScreen'
try { $Form.Icon = [Drawing.Icon]::ExtractAssociatedIcon((Get-Command powershell.exe).Source) } catch { }

# Kopf in Nachtblau mit dem Marken-Verlauf als feinem Streifen darunter
$Kopf = New-Object Windows.Forms.Panel -Property @{ Dock = 'Top'; Height = 74; BackColor = $Navy }
$Marke = New-Object Windows.Forms.Label -Property @{ Text = 'AZUBI-PORTAL  ·  BERICHTSHEFT'; AutoSize = $true; Location = '24,10'; ForeColor = (Farbe '#b9b6d6') }
$Marke.Font = New-Object Drawing.Font($Schrift, 7.5, [Drawing.FontStyle]::Bold)
$Titel = New-Object Windows.Forms.Label -Property @{ AutoSize = $true; Location = '22,26'; ForeColor = [Drawing.Color]::White }
$Titel.Font = New-Object Drawing.Font($Schrift, 14, [Drawing.FontStyle]::Bold)
$Untertitel = New-Object Windows.Forms.Label -Property @{
    Dock = 'Right'; AutoSize = $false; Width = 320; TextAlign = 'MiddleRight'; Padding = '0,12,24,0'; ForeColor = (Farbe '#d6d4ea')
}
$Kopf.Controls.AddRange(@($Untertitel, $Marke, $Titel))
$Streifen = New-Object Windows.Forms.Panel -Property @{ Dock = 'Top'; Height = 4 }
$Streifen.Add_Paint({
    $r = $Streifen.ClientRectangle
    if ($r.Width -le 0) { return }
    $pinsel = New-Object Drawing.Drawing2D.LinearGradientBrush($r, [Drawing.Color]::Black, [Drawing.Color]::Black, 0.0)
    $verlauf = New-Object Drawing.Drawing2D.ColorBlend
    $verlauf.Colors = @((Farbe '#ffc400'), (Farbe '#ee0000'), (Farbe '#dd00a0'), (Farbe '#7a2bd6'))
    $verlauf.Positions = @(0.0, 0.35, 0.7, 1.0)
    $pinsel.InterpolationColors = $verlauf
    $_.Graphics.FillRectangle($pinsel, $r)
    $pinsel.Dispose()
})

# Inhalt auf einer weißen Fläche mit feiner Linie (wie .panel in der Webanwendung)
$Rahmen = New-Object Windows.Forms.Panel -Property @{ Dock = 'Fill'; Padding = '16,16,16,8'; BackColor = $Papier }
$Karte = New-Object Windows.Forms.Panel -Property @{ Dock = 'Fill'; BackColor = $Flaeche; Padding = '1,1,1,1' }
$Karte.Add_Paint({
    $stift = New-Object Drawing.Pen($Linie)
    $_.Graphics.DrawRectangle($stift, 0, 0, $Karte.Width - 1, $Karte.Height - 1)
    $stift.Dispose()
})
$Inhalt = New-Object Windows.Forms.FlowLayoutPanel -Property @{
    Dock = 'Fill'; FlowDirection = 'TopDown'; WrapContents = $false; AutoScroll = $true; Padding = '22,18,16,8'; BackColor = $Flaeche
}
$Karte.Controls.Add($Inhalt)
$Rahmen.Controls.Add($Karte)

function Knopf-Stil($b, [bool]$haupt) {
    $b.FlatStyle = 'Flat'
    $b.Cursor = 'Hand'
    $b.Height = 32
    $b.FlatAppearance.BorderSize = 1
    if ($haupt) {
        $b.BackColor = $Navy; $b.ForeColor = [Drawing.Color]::White
        $b.FlatAppearance.BorderColor = $Navy
        $b.FlatAppearance.MouseOverBackColor = $Navy3
        $b.Font = New-Object Drawing.Font($Schrift, 9.5, [Drawing.FontStyle]::Bold)
    } else {
        $b.BackColor = $Flaeche; $b.ForeColor = $Navy
        $b.FlatAppearance.BorderColor = $LinieStark
        $b.FlatAppearance.MouseOverBackColor = $Papier
    }
}

# Fußleiste: links Deinstallieren, rechts Zurück / Weiter / Abbrechen – rechtsbündig, damit nichts aus dem Bild rutscht
$Fuss = New-Object Windows.Forms.Panel -Property @{ Dock = 'Bottom'; Height = 56; BackColor = $Papier; Padding = '16,8,16,12' }
$Rechts = New-Object Windows.Forms.FlowLayoutPanel -Property @{ Dock = 'Right'; FlowDirection = 'RightToLeft'; AutoSize = $true; WrapContents = $false }
$Abbrechen = New-Object Windows.Forms.Button -Property @{ Text = 'Abbrechen'; Width = 100; Margin = '8,0,0,0' }
$Weiter = New-Object Windows.Forms.Button -Property @{ Text = 'Weiter'; Width = 130; Margin = '8,0,0,0' }
$Zurueck = New-Object Windows.Forms.Button -Property @{ Text = 'Zurück'; Width = 100; Margin = '0,0,0,0' }
$Rechts.Controls.AddRange(@($Abbrechen, $Weiter, $Zurueck))
$Extra = New-Object Windows.Forms.Button -Property @{ AutoSize = $true; Dock = 'Left'; Visible = $false }
Knopf-Stil $Abbrechen $false; Knopf-Stil $Zurueck $false; Knopf-Stil $Weiter $true; Knopf-Stil $Extra $false
$Extra.ForeColor = $Rot
$Fuss.Controls.AddRange(@($Rechts, $Extra))
$Form.Controls.AddRange(@($Rahmen, $Streifen, $Kopf, $Fuss))
$Form.AcceptButton = $Weiter

# ====================================================================== Bausteine für Seiten

function Absatz([string]$text, [switch]$Fett, $Farbe = $Tinte, [int]$Abstand = 6) {
    $l = New-Object Windows.Forms.Label -Property @{ Text = $text; AutoSize = $true; ForeColor = $Farbe }
    $l.MaximumSize = New-Object Drawing.Size($S.Breite, 0)
    $l.Margin = New-Object Windows.Forms.Padding(0, 0, 0, $Abstand)
    if ($Fett) { $l.Font = New-Object Drawing.Font($Form.Font, [Drawing.FontStyle]::Bold) }
    $Inhalt.Controls.Add($l)
    return $l
}

function Hinweis([string]$text) { return (Absatz $text -Farbe $Grau -Abstand 10) }

function Eingabe([string]$beschriftung, [string]$wert, [int]$breite = 360, [switch]$Passwort) {
    Absatz $beschriftung -Abstand 2 | Out-Null
    $t = New-Object Windows.Forms.TextBox -Property @{ Text = $wert; Width = $breite; UseSystemPasswordChar = [bool]$Passwort; BorderStyle = 'FixedSingle' }
    $t.Margin = New-Object Windows.Forms.Padding(0, 0, 0, 10)
    $Inhalt.Controls.Add($t)
    return $t
}

function Haken([string]$text, [bool]$an) {
    $c = New-Object Windows.Forms.CheckBox -Property @{ Text = $text; Checked = $an; AutoSize = $true }
    $c.MaximumSize = New-Object Drawing.Size($S.Breite, 0)
    $c.Margin = New-Object Windows.Forms.Padding(0, 4, 0, 6)
    $Inhalt.Controls.Add($c)
    return $c
}

function Auswahl([string]$text, [bool]$an, $ziel = $Inhalt) {
    $r = New-Object Windows.Forms.RadioButton -Property @{ Text = $text; Checked = $an; AutoSize = $true }
    $r.MaximumSize = New-Object Drawing.Size(($S.Breite - 20), 0)
    $r.Margin = New-Object Windows.Forms.Padding(0, 2, 0, 4)
    $ziel.Controls.Add($r)
    return $r
}

function Knopf([string]$text, [scriptblock]$klick) {
    $b = New-Object Windows.Forms.Button -Property @{ Text = $text; AutoSize = $true }
    Knopf-Stil $b $false
    $b.Height = 27
    $b.Margin = New-Object Windows.Forms.Padding(0, -2, 0, 0)
    $b.Add_Click($klick)
    return $b
}

<#
    Umschalter An | Aus (zwei Knöpfe, der gewählte in Nachtblau). Mit -Derzeit wird daneben der Stand der
    vorhandenen Installation angezeigt und ob er geändert wird. Liefert die Zeile; der Wert steht in .Tag.An
#>
function Schalter([bool]$an, $Derzeit = $null) {
    $zeile = New-Object Windows.Forms.FlowLayoutPanel -Property @{ AutoSize = $true; WrapContents = $false; Margin = '0,2,0,8' }
    $zustand = [pscustomobject]@{ An = $an; Derzeit = $Derzeit; Knopf = @{}; Hinweis = $null }
    $zeile.Tag = $zustand
    foreach ($wert in $true, $false) {
        $k = New-Object Windows.Forms.Button -Property @{ Text = $(if ($wert) { 'An' } else { 'Aus' }); Width = 64; Height = 28; Margin = '0,0,0,0'; Tag = $wert }
        $k.FlatStyle = 'Flat'; $k.Cursor = 'Hand'; $k.FlatAppearance.BorderColor = $Navy
        $k.Add_Click({ $z = $this.Parent.Tag; $z.An = [bool]$this.Tag; Schalter-Zeichnen $z })
        $zustand.Knopf[$wert] = $k
        $zeile.Controls.Add($k)
    }
    if ($null -ne $Derzeit) {
        $zustand.Hinweis = New-Object Windows.Forms.Label -Property @{ AutoSize = $true; Margin = '12,6,0,0' }
        $zeile.Controls.Add($zustand.Hinweis)
    }
    Schalter-Zeichnen $zustand
    $Inhalt.Controls.Add($zeile)
    return $zeile
}

function Schalter-Zeichnen($z) {
    foreach ($wert in $true, $false) {
        $k = $z.Knopf[$wert]
        $gewaehlt = $z.An -eq $wert
        $k.BackColor = if ($gewaehlt) { $Navy } else { $Flaeche }
        $k.ForeColor = if ($gewaehlt) { [Drawing.Color]::White } else { $Navy }
        $k.Font = New-Object Drawing.Font($Schrift, 9.5, $(if ($gewaehlt) { [Drawing.FontStyle]::Bold } else { [Drawing.FontStyle]::Regular }))
    }
    if ($z.Hinweis) {
        $derzeit = if ($z.Derzeit) { 'an' } else { 'aus' }
        if ($z.An -eq $z.Derzeit) {
            $z.Hinweis.Text = "Derzeit: $derzeit"
            $z.Hinweis.ForeColor = $Grau
        } else {
            $z.Hinweis.Text = "Derzeit: $derzeit  →  wird $(if ($z.An) { 'eingeschaltet' } else { 'ausgeschaltet' })"
            $z.Hinweis.ForeColor = $Akzent
        }
    }
    if ($z.Geaendert) { & $z.Geaendert }
}

function Zeile([object[]]$controls, $ziel = $Inhalt) {
    $z = New-Object Windows.Forms.FlowLayoutPanel -Property @{ AutoSize = $true; WrapContents = $false }
    $z.Margin = New-Object Windows.Forms.Padding(0, 0, 0, 6)
    foreach ($c in $controls) { $z.Controls.Add($c) }
    $ziel.Controls.Add($z)
    return $z
}

function Textfeld([string]$text, [int]$hoehe) {
    $t = New-Object Windows.Forms.TextBox -Property @{
        Multiline = $true; ReadOnly = $true; ScrollBars = 'Vertical'; Width = $S.Breite; Height = $hoehe
        BackColor = $Papier; BorderStyle = 'FixedSingle'; Text = (($text -replace "`r`n", "`n") -replace "`n", "`r`n")
    }
    $t.Margin = New-Object Windows.Forms.Padding(0, 0, 0, 10)
    $Inhalt.Controls.Add($t)
    return $t
}

function Abstand([int]$hoehe = 8) {
    $Inhalt.Controls.Add((New-Object Windows.Forms.Panel -Property @{ Height = $hoehe; Width = 1; Margin = '0,0,0,0' }))
}

function Melden-Fehler([string]$text) {
    [Windows.Forms.MessageBox]::Show($Form, $text, 'Bitte prüfen', 'OK', 'Warning') | Out-Null
}

function Ordner-Waehlen([string]$vorschlag) {
    $dialog = New-Object Windows.Forms.FolderBrowserDialog
    $dialog.Description = 'Ordner für das Berichtsheft wählen (ein Unterordner „Berichtsheft“ wird angehängt)'
    if (Test-Path $vorschlag) { $dialog.SelectedPath = $vorschlag }
    if ($dialog.ShowDialog($Form) -ne 'OK') { return $null }
    $pfad = $dialog.SelectedPath.TrimEnd('\')
    if ((Split-Path -Leaf $pfad) -eq 'Berichtsheft') { return $pfad }
    return (Join-Path $pfad 'Berichtsheft')
}

function Ordner-Fehler([string]$pfad) {
    if (-not $pfad -or -not [IO.Path]::IsPathRooted($pfad) -or $pfad.StartsWith('\\')) {
        return 'Bitte einen vollständigen Pfad auf einem lokalen Laufwerk angeben, z. B. D:\Berichtsheft.'
    }
    $wurzel = [IO.Path]::GetPathRoot($pfad)
    if (-not (Test-Path $wurzel)) { return "Das Laufwerk $wurzel gibt es auf diesem Server nicht." }
    if ($pfad.StartsWith($env:SystemRoot, [StringComparison]::OrdinalIgnoreCase) -or $pfad.TrimEnd('\') -eq $wurzel.TrimEnd('\')) {
        return 'Bitte einen eigenen Ordner wählen (nicht das Windows-Verzeichnis oder die Laufwerkswurzel).'
    }
    return $null
}

# ====================================================================== Navigation

<#
    Jede Seite: Titel, Untertitel, Aufbauen (füllt $Inhalt), optional Pruefen (Fehlermeldung oder $null),
    Weiter (Name der nächsten Seite oder $null), WeiterText ($null = kein Weiter-Knopf), KeinZurueck.
#>
$Seiten = @{}

function Zeige([string]$name, [switch]$OhneVerlauf) {
    if ($S.Seite -and -not $OhneVerlauf) { $S.Verlauf.Push($S.Seite) }
    $S.Seite = $name
    $seite = $Seiten[$name]
    $Inhalt.SuspendLayout()
    foreach ($c in @($Inhalt.Controls)) { $c.Dispose() }
    $Inhalt.Controls.Clear()
    $Extra.Visible = $false
    $Titel.Text = & $seite.Titel
    $Untertitel.Text = if ($seite.Untertitel) { & $seite.Untertitel } else { '' }
    $text = if ($seite.ContainsKey('WeiterText')) { & $seite.WeiterText } else { 'Weiter' }
    $Weiter.Text = if ($text) { $text } else { '' }
    $Weiter.Visible = [bool]$text
    & $seite.Aufbauen
    $Inhalt.ResumeLayout()
    Knoepfe $true
    if ($seite.Danach) { & $seite.Danach }
}

function Knoepfe([bool]$aktiv) {
    $seite = $Seiten[$S.Seite]
    $Weiter.Enabled = $aktiv
    $Zurueck.Visible = $S.Verlauf.Count -gt 0 -and -not $seite.KeinZurueck
    $Zurueck.Enabled = $aktiv
    $Abbrechen.Enabled = $aktiv
    $Abbrechen.Text = if ($S.Seite -eq 'Fertig') { 'Schließen' } else { 'Abbrechen' }
    $Extra.Enabled = $aktiv
}

$Weiter.Add_Click({
    $seite = $Seiten[$S.Seite]
    if ($seite.Pruefen) {
        $fehler = & $seite.Pruefen
        if ($fehler) { Melden-Fehler $fehler; return }
    }
    $naechste = & $seite.Weiter
    if ($naechste) { Zeige $naechste }
})
$Zurueck.Add_Click({ if ($S.Verlauf.Count) { Zeige $S.Verlauf.Pop() -OhneVerlauf } })
$Abbrechen.Add_Click({ $Form.Close() })
$Form.Add_FormClosing({ if ($S.Beschaeftigt) { $_.Cancel = $true } })

# ====================================================================== Suche

$Seiten.Suche = @{
    Titel      = { 'Vorhandene Installation suchen' }
    Untertitel = { 'Windows-Dienst und lokale Laufwerke werden durchsucht.' }
    WeiterText = { $null }
    Aufbauen   = {
        Absatz 'Einen Moment – es wird geprüft, ob das Berichtsheft auf diesem Server schon installiert ist.' | Out-Null
        Abstand
        $balken = New-Object Windows.Forms.ProgressBar -Property @{ Style = 'Marquee'; Width = 360 }
        $Inhalt.Controls.Add($balken)
        $script:SuchStatus = Hinweis ''
    }
    Danach     = {
        Knoepfe $false
        $S.Gefunden = @(Installationen-Suchen { param($t) $script:SuchStatus.Text = $t; [Windows.Forms.Application]::DoEvents() })
        $S.Verlauf.Clear()
        if ($S.Gefunden.Count) {
            Zeige 'Gefunden' -OhneVerlauf
        } elseif ($PaketOrdner) {
            $S.Modus = 'Installieren'
            $S.E = Einstellungen-Neu
            Zeige 'Willkommen' -OhneVerlauf
        } else {
            [Windows.Forms.MessageBox]::Show($Form, "Auf diesem Server wurde keine Berichtsheft-Installation gefunden.`n`n" +
                    'Zum Installieren bitte Setup.cmd aus dem Setup-ZIP verwenden.', 'Berichtsheft', 'OK', 'Information') | Out-Null
            $Form.Close()
        }
    }
}

# ====================================================================== Neuinstallation

$Seiten.Willkommen = @{
    Titel      = { 'Berichtsheft installieren' }
    Untertitel = { 'Willkommen' }
    Aufbauen   = {
        Absatz 'Auf diesem Server wurde keine Berichtsheft-Installation gefunden.' -Fett -Abstand 12 | Out-Null
        Absatz "Der Assistent installiert Version $(Version-Kurz $S.NeuVersion) – in wenigen Schritten:" | Out-Null
        Absatz "  1.  Speicherort wählen`n  2.  Adresse, Port und Zertifikat`n  3.  Anmeldung und erster Ausbilder-Zugang`n  4.  Installieren" -Abstand 12 | Out-Null
        Absatz ('Das Berichtsheft läuft danach als Windows-Dienst „Berichtsheft“ und startet automatisch mit Windows. ' +
                'Eine Java-Laufzeit ist dabei – auf dem Server muss nichts zusätzlich installiert werden.') | Out-Null
        Hinweis 'Für spätere Updates einfach ein neues Setup ausführen – es findet diese Installation selbst.' | Out-Null
    }
    Weiter     = { 'Ordner' }
}

$Seiten.Ordner = @{
    Titel      = { 'Speicherort' }
    Untertitel = { 'Schritt 1 von 4' }
    Aufbauen   = {
        Absatz 'In diesen Ordner wird das Berichtsheft installiert:' | Out-Null
        $script:OrdnerFeld = New-Object Windows.Forms.TextBox -Property @{ Text = $S.E.Ordner; Width = 480; Margin = '0,0,8,0' }
        $waehlen = Knopf 'Durchsuchen …' { $p = Ordner-Waehlen $script:OrdnerFeld.Text; if ($p) { $script:OrdnerFeld.Text = $p } }
        Zeile @($script:OrdnerFeld, $waehlen) | Out-Null
        Abstand
        Hinweis ('Dort liegen auch die Datenbank (data), die Protokolle (logs) und die Sicherungen bei Updates (backup). ' +
                 'Den Ort kannst du später jederzeit mit dem Setup ändern.') | Out-Null
        $script:OrdnerInfo = Absatz '' -Farbe $Navy
        $aktualisieren = {
            try {
                $pfad = $script:OrdnerFeld.Text.Trim()
                $laufwerk = New-Object IO.DriveInfo([IO.Path]::GetPathRoot($pfad))
                $text = if ($laufwerk.IsReady) { "Freier Speicher auf $($laufwerk.Name): $([math]::Floor($laufwerk.AvailableFreeSpace / 1GB)) GB (benötigt: ca. 0,5 GB)" } else { '' }
                if (Test-Path (Join-Path $pfad 'data\berichtsheft.mv.db')) { $text += "`nIn diesem Ordner liegt bereits eine Datenbank – sie wird weiterverwendet." }
                $script:OrdnerInfo.Text = $text
            } catch { $script:OrdnerInfo.Text = '' }
        }
        $script:OrdnerFeld.Add_TextChanged($aktualisieren)
        & $aktualisieren
    }
    Pruefen    = {
        $pfad = $script:OrdnerFeld.Text.Trim()
        $fehler = Ordner-Fehler $pfad
        if ($fehler) { return $fehler }
        $S.E.Ordner = [IO.Path]::GetFullPath($pfad).TrimEnd('\')
        return $null
    }
    Weiter     = { 'Adresse' }
}

# ====================================================================== Vorhandene Installation

function Ort-Anzeigen {
    $script:OrtLabel.Text = $S.Inst.Ordner
    $verschieben = $script:ZielOrdner -ne $S.Inst.Ordner
    $script:NeuerOrt.Text = if ($verschieben) { "Neuer Speicherort: $script:ZielOrdner`nAlles wird dorthin verschoben – auch Datenbank und Sicherungen." } else { '' }
    $script:NeuerOrt.Visible = $verschieben
    $script:OrtZurueck.Visible = $verschieben
}

function Installation-Waehlen($inst) {
    # Beim Zurückblättern bleiben die bisherigen Eingaben (Ort, SSO …) erhalten
    if ($S.Inst -ne $inst -or -not $S.E) {
        $S.Inst = $inst
        $S.E = Einstellungen-Aus $inst
    }
    $script:ZielOrdner = $S.E.Ordner
    Ort-Anzeigen
    $z = $script:SsoSchalter.Tag
    $z.An = $S.E.Sso
    $z.Derzeit = $inst.Sso
    Schalter-Zeichnen $z
    $dienst = if (-not $inst.DienstAktiv) { 'nicht eingerichtet' } else {
        $d = Get-Service $DienstName -ErrorAction SilentlyContinue
        if ($d.Status -eq 'Running') { 'läuft' } else { 'angehalten' }
    }
    $script:VersionLabel.Text = "Installierte Version:  $(Version-Kurz $inst.VersionText)" +
            $(if ($PaketOrdner) { "`nNeue Version:              $(Version-Kurz $S.NeuVersion)" })
    $script:AdresseLabel.Text = "Adresse:  $($inst.Adresse)     Dienst: $dienst"
    $script:VersionHinweis.Text = ''
    if ($PaketOrdner) {
        $neu = Version-Nummer $S.NeuVersion
        $alt = Version-Nummer $inst.VersionText
        if ($S.NeuVersion -eq $inst.VersionText) {
            $script:VersionHinweis.Text = 'Diese Version ist bereits installiert. Mit „Weiter“ wird sie erneut eingespielt.'
        } elseif ($neu -and $alt -and $neu -lt $alt) {
            $script:VersionHinweis.Text = "Achtung: Das Setup enthält eine ÄLTERE Version ($neu) als die installierte ($alt)."
        }
        $script:AenderungenFeld.Text = ((Aenderungen-Beschreiben $inst $S.Info) -replace "`r`n", "`n") -replace "`n", "`r`n"
    }
}

$Seiten.Gefunden = @{
    Titel      = { if ($PaketOrdner) { 'Berichtsheft aktualisieren' } else { 'Berichtsheft verwalten' } }
    Untertitel = { if ($PaketOrdner) { 'Eine Installation wurde gefunden.' } else { 'Einstellungen, Speicherort oder Deinstallation' } }
    Aufbauen   = {
        if ($S.Gefunden.Count -gt 1) {
            Absatz 'Es wurden mehrere Installationen gefunden. Welche soll bearbeitet werden?' -Abstand 2 | Out-Null
            $script:InstAuswahl = New-Object Windows.Forms.ComboBox -Property @{ DropDownStyle = 'DropDownList'; Width = $S.Breite; Margin = '0,0,0,12' }
            foreach ($i in $S.Gefunden) {
                [void]$script:InstAuswahl.Items.Add("$($i.Ordner)   –   $(Version-Kurz $i.VersionText)$(if ($i.DienstAktiv) { '   (Dienst läuft hier)' })")
            }
            $Inhalt.Controls.Add($script:InstAuswahl)
        }
        Absatz 'Aktueller Speicherort' -Fett -Abstand 2 | Out-Null
        $script:OrtLabel = New-Object Windows.Forms.Label -Property @{ AutoSize = $true; Margin = '0,4,8,0'; MaximumSize = '470,0' }
        $aendern = Knopf 'Ort ändern …' {
            $neu = Ordner-Waehlen $script:ZielOrdner
            if (-not $neu -or $neu -eq $script:ZielOrdner) { return }
            $fehler = Ordner-Fehler $neu
            if (-not $fehler -and $neu.StartsWith($S.Inst.Ordner + '\', [StringComparison]::OrdinalIgnoreCase)) { $fehler = 'Der neue Ort darf nicht innerhalb des bisherigen Ordners liegen.' }
            if (-not $fehler -and (Test-Path $neu) -and (Get-ChildItem $neu -Force | Select-Object -First 1)) { $fehler = "Der Ordner $neu ist nicht leer. Bitte einen leeren oder neuen Ordner wählen." }
            if ($fehler) { Melden-Fehler $fehler; return }
            $script:ZielOrdner = [IO.Path]::GetFullPath($neu).TrimEnd('\')
            Ort-Anzeigen
        }
        Zeile @($script:OrtLabel, $aendern) | Out-Null
        $script:NeuerOrt = Absatz '' -Farbe $Orange -Abstand 0
        $script:OrtZurueck = New-Object Windows.Forms.LinkLabel -Property @{ Text = 'Am bisherigen Ort lassen'; AutoSize = $true; Margin = '0,0,0,8' }
        $script:OrtZurueck.Add_LinkClicked({ $script:ZielOrdner = $S.Inst.Ordner; Ort-Anzeigen })
        $Inhalt.Controls.Add($script:OrtZurueck)

        $script:VersionLabel = Absatz '' -Abstand 2
        $script:AdresseLabel = Absatz '' -Abstand 4
        $script:VersionHinweis = Absatz '' -Fett -Farbe $Orange
        Absatz 'Windows-Anmeldung (Single Sign-on)' -Fett -Abstand 2 | Out-Null
        $script:SsoSchalter = Schalter $false $false
        if ($PaketOrdner) {
            Absatz 'Änderungen in der neuen Version' -Fett -Abstand 2 | Out-Null
            $script:AenderungenFeld = Textfeld '' 150
        }
        $script:EinstellungenHaken = Haken 'Außerdem weitere Einstellungen ändern (Adresse, Port, Zertifikat, Anmeldung)' (-not $PaketOrdner)

        $auswahl = [Math]::Max(0, [array]::IndexOf($S.Gefunden, $S.Inst))
        Installation-Waehlen $S.Gefunden[$auswahl]
        if ($S.Gefunden.Count -gt 1) {
            $script:InstAuswahl.SelectedIndex = $auswahl
            $script:InstAuswahl.Add_SelectedIndexChanged({ Installation-Waehlen $S.Gefunden[$script:InstAuswahl.SelectedIndex] })
        }
        $Extra.Text = 'Deinstallieren …'
        $Extra.Visible = $true
    }
    Pruefen    = {
        $S.Modus = 'Aktualisieren'
        $S.E.Ordner = $script:ZielOrdner
        $S.E.Sso = $script:SsoSchalter.Tag.An
        $S.EinstellungenAendern = $script:EinstellungenHaken.Checked
        if (-not $PaketOrdner -and $script:ZielOrdner -eq $S.Inst.Ordner -and -not $S.EinstellungenAendern -and $S.E.Sso -eq $S.Inst.Sso) {
            return 'Es ist nichts zu tun: Bitte einen neuen Speicherort wählen, die Windows-Anmeldung umschalten oder „Einstellungen ändern“ ankreuzen.'
        }
        return $null
    }
    Weiter     = { if ($S.EinstellungenAendern) { 'Adresse' } else { 'Zusammenfassung' } }
}

$Extra.Add_Click({
    if ($S.Seite -ne 'Gefunden') { return }
    $antwort = [Windows.Forms.MessageBox]::Show($Form,
        "Das Berichtsheft in $($S.Inst.Ordner) wird deinstalliert (Dienst, Firewall-Regel und Programmdateien).`n`n" +
        "Sollen auch die Daten gelöscht werden – Datenbank, Protokolle und Sicherungen?`n`n" +
        "Ja = alles löschen (nicht rückgängig zu machen)`nNein = Daten behalten (für eine spätere Neuinstallation)",
        'Deinstallieren', 'YesNoCancel', 'Warning', 'Button2')
    if ($antwort -eq 'Cancel') { return }
    if ($antwort -eq 'Yes' -and [Windows.Forms.MessageBox]::Show($Form, 'Wirklich ALLE Daten unwiderruflich löschen?', 'Deinstallieren',
            'YesNo', 'Warning', 'Button2') -ne 'Yes') { return }
    $S.Modus = 'Deinstallieren'
    $S.DatenLoeschen = $antwort -eq 'Yes'
    Zeige 'Fortschritt'
})

# ====================================================================== Adresse, Port, Zertifikat

function Namen-Liste { @($script:NamenFeld.Text -split '[,; ]' | ForEach-Object { $_.Trim().ToLower() } | Where-Object { $_ } | Select-Object -Unique) }

function Port-Pruefen {
    $p = [int]$script:PortFeld.Value
    $eigener = $S.Modus -ne 'Installieren' -and $p -eq $S.Inst.Port
    if (-not $eigener -and (Port-Belegt $p)) {
        $script:PortStatus.Text = '⚠ Dieser Port wird schon von einem anderen Programm verwendet.'
        $script:PortStatus.ForeColor = $Rot
    } else {
        $name = @(Namen-Liste)[0]
        if (-not $name) { $name = Server-Rechnername }
        $script:PortStatus.Text = "Adresse: https://${name}:$p/"
        $script:PortStatus.ForeColor = $Grau
    }
}

function Pfx-Pruefen([bool]$namenErgaenzen) {
    if (-not (Test-Path $script:PfxFeld.Text)) {
        $script:PfxErgebnis.Text = 'Datei nicht gefunden.'
        $script:PfxErgebnis.ForeColor = $Rot
        return $false
    }
    $r = Zertifikat-Pruefen $script:PfxFeld.Text $script:PfxPasswort.Text (Namen-Liste)
    $script:PfxErgebnis.Text = $r.Meldung
    $script:PfxErgebnis.ForeColor = if ($r.Ok) { $Gruen } else { $Rot }
    $script:PfxOk = $r.Ok
    if ($namenErgaenzen -and $r.Ok -and $r.Namen.Count -and -not (Namen-Liste | Where-Object { $r.Namen -contains $_ })) {
        # Erster Name im Zertifikat als Adresse vorschlagen
        $script:NamenFeld.Text = (@($r.Namen) + @(Namen-Liste) | Select-Object -Unique) -join ', '
    }
    return $r.Ok
}

$Seiten.Adresse = @{
    Titel      = { 'Adresse, Port und Zertifikat' }
    Untertitel = { if ($S.Modus -eq 'Installieren') { 'Schritt 2 von 4' } else { 'Einstellungen' } }
    Aufbauen   = {
        $neu = $S.Modus -eq 'Installieren'
        $script:NamenFeld = Eingabe 'Servernamen (kommagetrennt) – unter dem ersten rufen alle die Seite auf:' ($S.E.Hostnamen -join ', ') $S.Breite
        Absatz 'Port (HTTPS):' -Abstand 2 | Out-Null
        $script:PortFeld = New-Object Windows.Forms.NumericUpDown -Property @{ Minimum = 1; Maximum = 65535; Value = $S.E.Port; Width = 90; Margin = '0,0,10,0' }
        $script:PortStatus = New-Object Windows.Forms.Label -Property @{ AutoSize = $true; Margin = '0,4,0,0' }
        Zeile @($script:PortFeld, $script:PortStatus) | Out-Null
        Hinweis 'Mit Port 443 entfällt die Portangabe in der Adresse (nur, wenn kein anderes Programm ihn nutzt).' | Out-Null
        $script:PortFeld.Add_ValueChanged({ Port-Pruefen })
        $script:NamenFeld.Add_TextChanged({ Port-Pruefen })

        Absatz 'Zertifikat' -Fett -Abstand 4 | Out-Null
        $script:ZertBehalten = $null
        if (-not $neu) {
            $art = if ($S.Inst.EigenesZertifikat) { 'eigenes Zertifikat' } else { 'selbstsigniert' }
            $script:ZertBehalten = Auswahl "Bisheriges behalten ($art)" ($S.E.Zertifikat -eq 'Behalten')
        }
        $script:ZertSelbst = Auswahl 'Selbstsigniert – sofort einsatzbereit, Browser zeigen aber „Nicht sicher“' ($S.E.Zertifikat -eq 'Selbstsigniert')
        $script:ZertDatei = Auswahl 'Eigenes Zertifikat (.pfx, z. B. von der Firmen-CA) – empfohlen' ($S.E.Zertifikat -eq 'Datei')

        $bereich = New-Object Windows.Forms.FlowLayoutPanel -Property @{ FlowDirection = 'TopDown'; AutoSize = $true; WrapContents = $false; Margin = '18,0,0,0' }
        $script:PfxFeld = New-Object Windows.Forms.TextBox -Property @{ Width = 420; Margin = '0,0,8,0'; Text = $S.E.PfxDatei }
        $suchen = Knopf 'Durchsuchen …' {
            $d = New-Object Windows.Forms.OpenFileDialog -Property @{ Filter = 'Zertifikat mit Schlüssel (*.pfx;*.p12)|*.pfx;*.p12|Alle Dateien|*.*' }
            if ($d.ShowDialog($Form) -eq 'OK') { $script:PfxFeld.Text = $d.FileName }
        }
        Zeile @($script:PfxFeld, $suchen) $bereich | Out-Null
        $bereich.Controls.Add((New-Object Windows.Forms.Label -Property @{ Text = 'Passwort der .pfx-Datei:'; AutoSize = $true; Margin = '0,0,0,2' }))
        $script:PfxPasswort = New-Object Windows.Forms.TextBox -Property @{ Width = 220; UseSystemPasswordChar = $true; Margin = '0,0,8,0'; Text = $S.E.PfxPasswort }
        $pruefen = Knopf 'Prüfen' { Pfx-Pruefen $true | Out-Null }
        Zeile @($script:PfxPasswort, $pruefen) $bereich | Out-Null
        $script:PfxErgebnis = New-Object Windows.Forms.Label -Property @{ AutoSize = $true; MaximumSize = "$($S.Breite - 30),0" }
        $bereich.Controls.Add($script:PfxErgebnis)
        $Inhalt.Controls.Add($bereich)
        $script:PfxBereich = $bereich
        $script:PfxOk = $false
        $script:PfxFeld.Add_TextChanged({ $script:PfxOk = $false })
        $script:PfxPasswort.Add_TextChanged({ $script:PfxOk = $false })
        foreach ($r in @($script:ZertBehalten, $script:ZertSelbst, $script:ZertDatei) | Where-Object { $_ }) {
            $r.Add_CheckedChanged({ $script:PfxBereich.Enabled = $script:ZertDatei.Checked })
        }
        $bereich.Enabled = $script:ZertDatei.Checked
        Port-Pruefen
    }
    Pruefen    = {
        $liste = @(Namen-Liste)
        if (-not $liste.Count) { return 'Bitte mindestens einen Servernamen angeben.' }
        $p = [int]$script:PortFeld.Value
        $eigener = $S.Modus -ne 'Installieren' -and $p -eq $S.Inst.Port
        if (-not $eigener -and (Port-Belegt $p)) { return "Port $p wird schon von einem anderen Programm verwendet. Bitte einen anderen Port wählen." }
        if ($script:ZertDatei.Checked -and -not $script:PfxOk -and -not (Pfx-Pruefen $false)) {
            return "Das Zertifikat ist nicht verwendbar:`n`n$($script:PfxErgebnis.Text)"
        }
        $namenGeaendert = ($liste -join ',') -ne ($S.E.Hostnamen -join ',')
        $S.E.Hostnamen = $liste
        $S.E.Port = $p
        $S.E.PfxDatei = $script:PfxFeld.Text
        $S.E.PfxPasswort = $script:PfxPasswort.Text
        if ($script:ZertDatei.Checked) {
            $S.E.Zertifikat = 'Datei'
        } elseif ($script:ZertSelbst.Checked) {
            $S.E.Zertifikat = 'Selbstsigniert'
        } elseif ($namenGeaendert -and -not $S.Inst.EigenesZertifikat) {
            $S.E.Zertifikat = 'Selbstsigniert' # mit den neuen Namen neu erzeugen; ein eigenes bleibt
        } else {
            $S.E.Zertifikat = 'Behalten'
        }
        return $null
    }
    Weiter     = { 'Anmeldung' }
}

# ====================================================================== Anmeldung

$Seiten.Anmeldung = @{
    Titel      = { 'Anmeldung' }
    Untertitel = { if ($S.Modus -eq 'Installieren') { 'Schritt 3 von 4' } else { 'Einstellungen' } }
    Aufbauen   = {
        $domaene = Server-Domaene
        Absatz 'Windows-Anmeldung (Single Sign-on)' -Fett -Abstand 2 | Out-Null
        Absatz 'Wer die Seite öffnet, wird automatisch mit seinem Windows-Konto angemeldet.' -Abstand 2 | Out-Null
        $derzeit = if ($S.Modus -eq 'Installieren') { $null } else { $S.Inst.Sso }
        $script:SsoSchalterA = Schalter $S.E.Sso $derzeit
        Hinweis $(if ($domaene) { "Der Server ist Mitglied der Domäne $domaene. Zugriff hat nur, wessen Windows-Anmeldename als Benutzername in der Personenliste steht." }
                  else { 'Dieser Server ist in keiner Domäne – die Windows-Anmeldung funktioniert nur auf Domänenmitgliedern.' }) | Out-Null
        $script:DomaenenFeld = Eingabe 'Nur diese Domänen zulassen (Kurzname, kommagetrennt; leer = alle):' $S.E.SsoDomaenen 220
        $script:PasswortHaken = Haken 'Anmeldung mit Benutzername und Passwort zusätzlich erlauben' $S.E.PasswortLogin
        $z = $script:SsoSchalterA.Tag
        $z | Add-Member Geaendert { $script:DomaenenFeld.Enabled = $script:PasswortHaken.Enabled = $script:SsoSchalterA.Tag.An }
        Schalter-Zeichnen $z

        $script:ErsterAusbilder = $S.Modus -eq 'Installieren' -and -not (Test-Path (Join-Path $S.E.Ordner 'data\berichtsheft.mv.db'))
        Abstand 6
        if ($script:ErsterAusbilder) {
            Absatz 'Erster Ausbilder-Zugang' -Fett -Abstand 2 | Out-Null
            Hinweis ('Mit diesem Zugang meldest du dich zuerst an und legst danach weitere Ausbilder und Azubis an. ' +
                     'Bei Windows-Anmeldung muss der Benutzername dein Windows-Anmeldename sein.') | Out-Null
            $script:BenutzerFeld = Eingabe 'Benutzername:' $S.E.AusbilderBenutzer 220
            $script:VornameFeld = Eingabe 'Vorname:' $S.E.AusbilderVorname 220
            $script:NachnameFeld = Eingabe 'Nachname:' $S.E.AusbilderNachname 220
            $script:PasswortFeld = Eingabe 'Passwort (mind. 8 Zeichen, Buchstaben und Ziffern):' '' 220 -Passwort
            $script:WiederholungFeld = Eingabe 'Passwort wiederholen:' '' 220 -Passwort
        } elseif ($S.Modus -eq 'Installieren') {
            Absatz 'Im Ordner liegt bereits eine Datenbank – es gelten die bisherigen Zugänge.' -Fett | Out-Null
        }
    }
    Pruefen    = {
        if ($script:ErsterAusbilder) {
            $name = $script:BenutzerFeld.Text.Trim().ToLower()
            if ($name -notmatch '^[a-z0-9._-]{3,50}$') { return 'Benutzername: 3–50 Zeichen, nur Kleinbuchstaben, Ziffern, Punkt, - und _.' }
            if (-not $script:VornameFeld.Text.Trim() -or -not $script:NachnameFeld.Text.Trim()) { return 'Bitte Vor- und Nachnamen angeben.' }
            if ($script:PasswortFeld.Text -notmatch '^(?=.*[A-Za-zÄÖÜäöüß])(?=.*\d).{8,100}$') { return 'Das Passwort braucht mindestens 8 Zeichen mit Buchstaben und Ziffern.' }
            if ($script:PasswortFeld.Text -cne $script:WiederholungFeld.Text) { return 'Die Passwörter stimmen nicht überein.' }
            if ($script:SsoSchalterA.Tag.An -and -not $script:PasswortHaken.Checked -and $name -ne $env:USERNAME.ToLower()) {
                $antwort = [Windows.Forms.MessageBox]::Show($Form,
                    "Ohne Passwort-Anmeldung kann sich nur anmelden, wessen Windows-Anmeldename in der Personenliste steht.`n`n" +
                    "Der erste Zugang heißt `„$name`“, du bist als `„$($env:USERNAME.ToLower())`“ angemeldet. Trotzdem fortfahren?",
                    'Anmeldung', 'YesNo', 'Warning')
                if ($antwort -ne 'Yes') { return 'Bitte den Benutzernamen prüfen oder die Passwort-Anmeldung erlauben.' }
            }
            $S.E.AusbilderBenutzer = $name
            $S.E.AusbilderVorname = $script:VornameFeld.Text.Trim()
            $S.E.AusbilderNachname = $script:NachnameFeld.Text.Trim()
            $S.E.AusbilderPasswort = $script:PasswortFeld.Text
        }
        $S.E.ErsterAusbilder = $script:ErsterAusbilder
        $S.E.Sso = $script:SsoSchalterA.Tag.An
        $S.E.SsoDomaenen = $script:DomaenenFeld.Text.Trim()
        $S.E.PasswortLogin = -not $script:SsoSchalterA.Tag.An -or $script:PasswortHaken.Checked
        return $null
    }
    Weiter     = { 'Zusammenfassung' }
}

# ====================================================================== Zusammenfassung

function Zusammenfassung-Text {
    $e = $S.E
    $zert = switch ($e.Zertifikat) {
        'Datei' { "eigenes Zertifikat ($(Split-Path -Leaf $e.PfxDatei))" }
        'Selbstsigniert' { 'selbstsigniert (wird beim Start erzeugt)' }
        default { 'unverändert' }
    }
    $anmeldung = if ($e.Sso) {
        'Windows-Anmeldung' + $(if ($e.SsoDomaenen) { " (nur $($e.SsoDomaenen))" }) + $(if ($e.PasswortLogin) { ' + Benutzername/Passwort' } else { ' (ohne Passwort-Anmeldung)' })
    } else { 'Benutzername und Passwort' }
    $t = New-Object Text.StringBuilder
    if ($S.Modus -eq 'Installieren') {
        [void]$t.AppendLine("Version:          $(Version-Kurz $S.NeuVersion)")
        [void]$t.AppendLine("Speicherort:      $($e.Ordner)")
        [void]$t.AppendLine("Adresse:          $(Einstellungen-Adresse $e)")
        [void]$t.AppendLine("Weitere Namen:    $((@($e.Hostnamen) | Select-Object -Skip 1) -join ', ')")
        [void]$t.AppendLine("Zertifikat:       $zert")
        [void]$t.AppendLine("Anmeldung:        $anmeldung")
        if ($e.ErsterAusbilder) { [void]$t.AppendLine("Erster Zugang:    $($e.AusbilderBenutzer) ($($e.AusbilderVorname) $($e.AusbilderNachname))") }
        [void]$t.AppendLine()
        [void]$t.AppendLine('Außerdem: Windows-Dienst „Berichtsheft“ (startet automatisch), Firewall-Freigabe')
        [void]$t.AppendLine("für Port $($e.Port) (Domänen- und private Netzwerke), Eintrag unter `„Apps / Programme und Features`“.")
        return $t.ToString()
    }
    $inst = $S.Inst
    $verschieben = $e.Ordner -ne $inst.Ordner
    if ($PaketOrdner) { [void]$t.AppendLine("Version:          $(Version-Kurz $inst.VersionText)  →  $(Version-Kurz $S.NeuVersion)") }
    if ($verschieben) {
        [void]$t.AppendLine("Speicherort:      $($inst.Ordner)")
        [void]$t.AppendLine("              →  $($e.Ordner)   (inkl. Datenbank; alter Ordner wird danach gelöscht)")
    } else {
        [void]$t.AppendLine("Speicherort:      $($inst.Ordner) (unverändert)")
    }
    if ($S.EinstellungenAendern) {
        [void]$t.AppendLine("Adresse:          $(Einstellungen-Adresse $e)$(if ($e.Port -ne $inst.Port) { "   (Port bisher $($inst.Port))" })")
        [void]$t.AppendLine("Zertifikat:       $zert")
        [void]$t.AppendLine("Anmeldung:        $anmeldung")
    } else {
        [void]$t.AppendLine("Einstellungen:    unverändert ($($inst.Adresse))")
        if ($e.Sso -ne $inst.Sso) {
            [void]$t.AppendLine("Windows-Anmeldung: $(if ($inst.Sso) { 'an' } else { 'aus' })  →  $(if ($e.Sso) { 'an' } else { 'aus' })")
        }
    }
    [void]$t.AppendLine()
    [void]$t.AppendLine('Ablauf: Dienst anhalten → Datenbank sichern (backup) → ' + $(if ($verschieben) { 'verschieben → ' }) +
            $(if ($PaketOrdner) { 'neue Version einspielen → ' }) + 'Dienst starten.')
    return $t.ToString()
}

$Seiten.Zusammenfassung = @{
    Titel      = { 'Zusammenfassung' }
    Untertitel = { if ($S.Modus -eq 'Installieren') { 'Schritt 4 von 4' } else { 'Bitte prüfen' } }
    WeiterText = { if ($S.Modus -eq 'Installieren') { 'Installieren' } elseif ($PaketOrdner) { 'Aktualisieren' } else { 'Übernehmen' } }
    Aufbauen   = {
        Absatz $(if ($S.Modus -eq 'Installieren') { 'Folgendes wird installiert:' } else { 'Folgendes wird gemacht:' }) -Abstand 8 | Out-Null
        $feld = Textfeld (Zusammenfassung-Text) 300
        $feld.Font = New-Object Drawing.Font('Consolas', 9.5) # feste Zeichenbreite, damit die Spalten stehen
        Hinweis $(if ($S.Modus -eq 'Installieren') { 'Mit „Installieren“ geht es los. Das dauert etwa eine Minute.' }
                  else { 'Der Dienst wird dafür kurz angehalten (etwa eine Minute). Vorher wird die Datenbank gesichert.' }) | Out-Null
    }
    Weiter     = { 'Fortschritt' }
}

# ====================================================================== Ausführen

$Seiten.Fortschritt = @{
    Titel       = { switch ($S.Modus) { 'Installieren' { 'Installation läuft …' } 'Deinstallieren' { 'Deinstallation läuft …' } default { 'Aktualisierung läuft …' } } }
    Untertitel  = { 'Bitte warten und das Fenster nicht schließen.' }
    WeiterText  = { $null }
    KeinZurueck = $true
    Aufbauen    = {
        $script:Balken = New-Object Windows.Forms.ProgressBar -Property @{ Width = $S.Breite; Height = 20; Margin = '0,4,0,12' }
        $Inhalt.Controls.Add($script:Balken)
        $script:Meldungen = New-Object Windows.Forms.ListBox -Property @{ Width = $S.Breite; Height = 300; IntegralHeight = $false; HorizontalScrollbar = $true }
        $Inhalt.Controls.Add($script:Meldungen)
    }
    Danach      = {
        Knoepfe $false
        $S.Beschaeftigt = $true
        $script:Melden = { param($t) [void]$script:Meldungen.Items.Add($t); $script:Meldungen.TopIndex = $script:Meldungen.Items.Count - 1; [Windows.Forms.Application]::DoEvents() }
        $script:Fortschritt = { param($p) $script:Balken.Value = [Math]::Max(0, [Math]::Min(100, $p)); [Windows.Forms.Application]::DoEvents() }
        $script:Fehler = $null
        $script:Sicherung = $null
        try {
            switch ($S.Modus) {
                'Installieren' { Berichtsheft-Installieren $PaketOrdner $S.E }
                'Aktualisieren' { $script:Sicherung = Berichtsheft-Aktualisieren $PaketOrdner $S.Inst $S.E }
                default { Berichtsheft-Deinstallieren $S.Inst $S.DatenLoeschen }
            }
        } catch {
            $script:Fehler = $_
        }
        # Protokoll speichern
        $ordner = if ($S.Modus -eq 'Deinstallieren' -and $S.DatenLoeschen) { $env:TEMP } elseif ($S.E) { Join-Path $S.E.Ordner 'logs' } else { Join-Path $S.Inst.Ordner 'logs' }
        if (-not (Test-Path $ordner)) { $ordner = $env:TEMP }
        $script:LogDatei = Join-Path $ordner "setup-$(Get-Date -Format 'yyyy-MM-dd_HH-mm-ss').log"
        $text = $script:Protokoll.ToString() + $(if ($script:Fehler) { "`r`nFEHLER: $($script:Fehler.Exception.Message)`r`n$($script:Fehler.ScriptStackTrace)`r`n" })
        try { [IO.File]::WriteAllText($script:LogDatei, $text, [Text.Encoding]::UTF8) } catch { $script:LogDatei = $null }
        $S.Beschaeftigt = $false
        $S.Verlauf.Clear()
        Zeige 'Fertig' -OhneVerlauf
    }
}

$Seiten.Fertig = @{
    Titel       = {
        if ($script:Fehler) { 'Fehler' } else { switch ($S.Modus) { 'Installieren' { 'Installation abgeschlossen' } 'Deinstallieren' { 'Deinstallation abgeschlossen' } default { 'Aktualisierung abgeschlossen' } } }
    }
    WeiterText  = { $null }
    KeinZurueck = $true
    Aufbauen    = {
        if ($script:Fehler) {
            Absatz 'Das hat leider nicht geklappt:' -Fett -Farbe ($Rot) | Out-Null
            Textfeld $script:Fehler.Exception.Message 120 | Out-Null
            if ($script:Sicherung) { Absatz "Die Datenbank wurde vorher gesichert: $script:Sicherung" | Out-Null }
            Hinweis 'Die Einzelheiten stehen im Protokoll. Nach dem Beheben der Ursache das Setup einfach erneut ausführen – es findet die Installation wieder.' | Out-Null
        } elseif ($S.Modus -eq 'Deinstallieren') {
            Absatz 'Das Berichtsheft wurde deinstalliert.' -Fett -Abstand 10 | Out-Null
            Absatz $(if ($S.DatenLoeschen) { 'Alle Daten wurden gelöscht.' } else {
                "Die Datenbank liegt weiter in $(Join-Path $S.Inst.Ordner 'data'). Eine spätere Installation in denselben Ordner verwendet sie wieder." }) | Out-Null
        } else {
            $adresse = Einstellungen-Adresse $S.E
            Absatz $(if ($S.Modus -eq 'Installieren') { 'Das Berichtsheft ist installiert und läuft.' } else { 'Das Berichtsheft ist aktualisiert und läuft.' }) -Fett -Abstand 12 | Out-Null
            Absatz 'Adresse:' -Abstand 2 | Out-Null
            $link = New-Object Windows.Forms.LinkLabel -Property @{ Text = $adresse; AutoSize = $true; Margin = '0,0,0,12' }
            $link.Font = New-Object Drawing.Font('Segoe UI', 11)
            $link.Add_LinkClicked({ Start-Process $this.Text })
            $Inhalt.Controls.Add($link)
            if ($S.Modus -eq 'Installieren') {
                Absatz $(if ($S.E.ErsterAusbilder) { "Anmelden mit `„$($S.E.AusbilderBenutzer)`“ und dem eben vergebenen Passwort; danach unter Personen weitere Zugänge anlegen." }
                         else { 'Anmelden mit den bisherigen Zugängen.' }) | Out-Null
                if ($S.E.Zertifikat -eq 'Selbstsigniert') {
                    Hinweis 'Mit dem selbstsignierten Zertifikat zeigen Browser zunächst „Nicht sicher“. Abhilfe: ein Zertifikat der Firmen-CA (Verwaltung.cmd → Einstellungen ändern).' | Out-Null
                }
            } else {
                Absatz 'Im Browser einmal neu laden (Strg+F5), damit die neue Oberfläche geladen wird.' | Out-Null
                if ($script:Sicherung) { Hinweis "Datenbank-Sicherung: $script:Sicherung" | Out-Null }
            }
            Hinweis "Verwaltung (Einstellungen, Speicherort, Deinstallation): $(Join-Path $S.E.Ordner 'Verwaltung.cmd') oder `„Apps / Programme und Features`“." | Out-Null
        }
        if ($script:LogDatei) {
            $log = New-Object Windows.Forms.LinkLabel -Property @{ Text = 'Protokoll anzeigen'; AutoSize = $true; Margin = '0,6,0,0'; Tag = $script:LogDatei }
            $log.Add_LinkClicked({ Start-Process notepad.exe -ArgumentList "`"$($this.Tag)`"" })
            $Inhalt.Controls.Add($log)
        }
    }
}

# ====================================================================== Start

$Form.Add_Shown({ Zeige 'Suche' })
if ($env:BERICHTSHEFT_SETUP_TEST) { return } # automatischer Test steuert das Fenster selbst
try {
    [void]$Form.ShowDialog()
} catch {
    [Windows.Forms.MessageBox]::Show("Unerwarteter Fehler: $($_.Exception.Message)`n`n$($_.ScriptStackTrace)", 'Berichtsheft', 'OK', 'Error') | Out-Null
}
