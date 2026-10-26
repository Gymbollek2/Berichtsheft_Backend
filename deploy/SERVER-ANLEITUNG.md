# Berichtsheft auf einem Windows-Server betreiben

Backend und Frontend laufen zusammen als **ein Windows-Dienst** („Berichtsheft“). Das Backend liefert
das Frontend mit aus, die Seite ist danach im Netzwerk erreichbar unter

```
https://<Servername>:5016/
```

Der Dienst startet automatisch mit Windows und nach einem Absturz neu. Installieren, Aktualisieren,
Einstellungen ändern, Verschieben und Deinstallieren erledigt **ein Setup mit Assistent** (PowerShell-Fenster
im Design der Webanwendung).

---

## 1. Setup bauen (auf dem Entwicklungsrechner)

Voraussetzungen: **JDK 21** (`JAVA_HOME` gesetzt), **Node.js 20+**, beim ersten Mal Internetzugang,
beide Projekte nebeneinander ausgecheckt (`…\Berichtsheft_Backend` und `…\Berichtsheft_Frontend`).

```bat
cd Berichtsheft_Backend
deploy\build-setup.cmd
```

Ergebnis: **`release\Berichtsheft-Setup-<Version>.zip`** (ca. 130 MB). Gebaut wird nur, was sich geändert
hat – ohne Änderungen dauert das wenige Sekunden:

| Was | Wann |
|---|---|
| Frontend (`ng build`) | nur nach Änderungen im Frontend |
| Backend (Maven, ohne Tests) | nur nach Änderungen im Backend – parallel zum Frontend |
| Java-Laufzeit (jlink), WinSW | einmalig, danach aus `release\_cache` |

Optionen: `-MitTests` (vorher alle Tests), `-Neu` (alles neu bauen), `-FrontendPfad D:\…\Berichtsheft_Frontend`.

**Neue Version veröffentlichen:** `deploy\build-setup.cmd -Release minor` (oder `patch` / `major`). Das Skript
erhöht vor dem Bauen die Version (Ausgangspunkt ist die höhere aus `pom.xml` und `package.json`), trägt sie in
`pom.xml`, `package.json` und `package-lock.json` ein und macht in beiden CHANGELOGs aus `[Unveröffentlicht]`
den Abschnitt `[<Version>] – <Datum>` mit einem neuen, leeren `[Unveröffentlicht]` darüber. Neue Einträge
also vorher unter `[Unveröffentlicht]` sammeln. Ohne `-Release` bleibt die Version gleich (Probe-Builds).

Inhalt des ZIP:

| Inhalt | Zweck |
|---|---|
| `Setup.cmd`, `setup\` | der Assistent (PowerShell) |
| `app\berichtsheft.jar` | Backend (Spring Boot) |
| `app\frontend\` | gebautes Angular-Frontend |
| `app\jre\` | eigene Java-Laufzeit – auf dem Server muss **kein Java** installiert sein |
| `app\berichtsheft-service.exe` | [WinSW](https://github.com/winsw/winsw) – startet die Anwendung als Dienst |
| `info\` | Version und beide CHANGELOGs (zeigen beim Aktualisieren, was neu ist) |

> Kann WinSW nicht heruntergeladen werden (Proxy), die Datei
> `https://github.com/winsw/winsw/releases/download/v2.12.0/WinSW-x64.exe` von Hand laden,
> als `release\_cache\WinSW-x64.exe` ablegen und erneut bauen.

## 2. Setup auf dem Server ausführen

Das ZIP auf den Server kopieren, **in einen beliebigen Ordner entpacken** (z. B. `C:\Temp`) und
**`Setup.cmd`** doppelklicken (fragt nach Administratorrechten). Das Setup sucht zuerst selbst nach einer
vorhandenen Installation – über den Windows-Dienst und auf allen lokalen Laufwerken.

### Noch nicht installiert → Neuinstallation in vier Schritten

1. **Speicherort** – Vorschlag `F:\Program Files\Berichtsheft` (ohne Laufwerk F: unter `C:\Program Files`).
   Dort liegen auch Datenbank (`data`), Protokolle (`logs`) und Sicherungen (`backup`).
2. **Adresse, Port und Zertifikat** – Servernamen (der volle Name zuerst, z. B. `scapa02e.f-i-sp.de`),
   Port (Standard 5016; ein belegter Port wird erkannt), Zertifikat: selbstsigniert oder eigene `.pfx`
   (wird vorher geprüft: Passwort, privater Schlüssel, Ablaufdatum, Servernamen).
3. **Anmeldung** – Windows-Anmeldung An/Aus, optional nur bestimmte Domänen, Passwort-Anmeldung als
   Ausweg; **erster Ausbilder-Zugang** (Benutzername, Name, Passwort). Liegt im Ordner schon eine
   Datenbank, gelten die bisherigen Zugänge.
4. **Zusammenfassung** → *Installieren*.

Das Setup kopiert die Anwendung, erzeugt das JWT-Geheimnis und die Dienst-Konfiguration, öffnet den Port
in der Firewall (Domänen- und private Netzwerke), richtet den Dienst ein, startet ihn und wartet, bis die
Seite antwortet. Am Ende steht die Adresse zum Anklicken.

### Schon installiert → Änderungen ansehen und aktualisieren

Das Setup zeigt

- den **aktuellen Speicherort** – mit *Ort ändern …* wird beim Aktualisieren alles (auch Datenbank und
  Sicherungen) in einen neuen, leeren Ordner verschoben; der alte Ordner wird danach gelöscht,
- installierte und neue **Version**,
- die **Windows-Anmeldung** mit dem aktuellen Stand (*Derzeit: an/aus*) – mit *An* / *Aus* umschalten,
- die **Änderungen** gegenüber der installierten Version (aus den CHANGELOGs),
- optional *weitere Einstellungen ändern* (Servernamen, Port, Zertifikat, Domänen, Passwort-Anmeldung).

Mit *Aktualisieren*: Dienst anhalten → Datenbank nach `backup\<Datum>` sichern → (verschieben) → neue
Version einspielen → Dienst starten. Datenbank, Logs, Port und Zertifikat bleiben erhalten.

Werden mehrere Installationen gefunden, fragt das Setup, welche gemeint ist. Ältere Installationen
(aus den früheren ZIP-Releases mit `install.cmd`) werden genauso erkannt und dabei aufgeräumt.

### Verwaltung ohne neue Version

Bei jeder Installation legt das Setup **`Verwaltung.cmd`** in den Installationsordner und trägt das
Berichtsheft unter **Apps / Programme und Features** ein. Darüber lassen sich jederzeit die
Windows-Anmeldung umschalten, Einstellungen ändern, der Speicherort wechseln oder das Berichtsheft
**deinstallieren** (wahlweise mit oder ohne Daten). Dasselbe geht auch mit jedem Setup
(Knopf *Deinstallieren …* unten links).

Jeder Lauf schreibt ein Protokoll nach `logs\setup-<Datum>.log`.

## 3. Zertifikat

Ohne eigenes Zertifikat erzeugt die Anwendung beim ersten Start ein **selbstsigniertes** Zertifikat
für die angegebenen Servernamen, die IP-Adressen und `localhost`. Das funktioniert sofort, Browser
zeigen aber „Nicht sicher“.

Ob ein Browser die Seite als **sicher** anzeigt, entscheidet **der Rechner, auf dem der Browser läuft**:
Das Zertifikat muss von einer Stelle stammen, der dieser Rechner vertraut, und den Namen enthalten,
unter dem die Seite aufgerufen wird. Zwei Wege:

**a) Zertifikat der Firmen-CA (empfohlen).** Bei der IT ein Webserver-Zertifikat anfragen:

- Name(n): der Name, unter dem alle die Seite aufrufen, z. B. `scapa02e.f-i-sp.de` (plus ggf. Kurzname/Alias)
- Format: **.pfx mit privatem Schlüssel** und Zwischenzertifikaten, dazu das Passwort

Einspielen: Setup (oder `Verwaltung.cmd`) starten → *weitere Einstellungen ändern* → *Eigenes
Zertifikat* → Datei und Passwort → *Prüfen* → weiter. Firmenrechner vertrauen der Firmen-CA bereits.

**b) Selbstsigniertes Zertifikat verteilen** (wenn die IT keins ausstellt): Die Seite im Browser öffnen,
Schloss-Symbol → Zertifikat → *Details* → *In Datei kopieren* (`.cer`). Die Datei auf **jedem Arbeitsplatz**
(nicht auf dem Server) unter **Vertrauenswürdige Stammzertifizierungsstellen** eintragen – per
Gruppenrichtlinie (IT) oder von Hand: Doppelklick → *Zertifikat installieren* → *Lokaler Computer*.
Danach Browser komplett neu starten. Werden die Servernamen geändert, entsteht ein neues Zertifikat,
das erneut verteilt werden muss.

In beiden Fällen die Seite **unter dem Namen aus dem Zertifikat** aufrufen – nicht per IP-Adresse.

## 4. Windows-Anmeldung (Single Sign-on)

Mit eingeschalteter Windows-Anmeldung wird jeder, der die Seite öffnet, automatisch mit seinem
Windows-Konto angemeldet – ohne Passworteingabe (Kerberos, ersatzweise NTLM). Zugriff hat nur, wessen
**Windows-Anmeldename (sAMAccountName)** unter **Personen** als **Benutzername** eingetragen ist.
Alle anderen sehen die Seite „Kein Zugriff“ und kommen an keine andere Seite und keine Daten.

**Vorher:** Benutzernamen in der Personenliste auf die Windows-Anmeldenamen umstellen (*Personen →
bearbeiten → Benutzername*), mindestens den eigenen. Den Anmeldenamen zeigt auf dem jeweiligen PC
`whoami` (Teil hinter dem `\`), oder die Seite „Kein Zugriff“ zeigt ihn an.

**Ein-/Ausschalten:** Setup oder `Verwaltung.cmd` starten → bei *Windows-Anmeldung* auf *An* bzw. *Aus* → *Weiter*.

**Voraussetzungen (ggf. mit der IT klären):**

| Was | Warum / wie |
|---|---|
| Server ist **Mitglied der Domäne** | Nur dann kann er Windows-Anmeldungen prüfen. Der Dienst läuft als „Lokales System“ – nichts weiter einzurichten. |
| Seite über den **vollen Servernamen** aufrufen, z. B. `https://scapa02e.f-i-sp.de:5016/` | Für den Rechnernamen gibt es den Kerberos-Eintrag (SPN) automatisch. Bei einem **DNS-Alias** (z. B. `berichtsheft.firma.local`) muss die IT ihn anlegen: `setspn -S HTTP/berichtsheft.firma.local SERVERNAME` |
| Seite ist im Browser als **Intranet** eingestuft | Edge und Chrome schicken die Windows-Anmeldung nur an Intranet-Seiten. Per Gruppenrichtlinie „Liste der Site-zu-Zonen-Zuweisungen“: `https://scapa02e.f-i-sp.de` → Zone `1` (Lokales Intranet). Alternativ die Richtlinie `AuthServerAllowlist` für Edge/Chrome. Firefox: `network.negotiate-auth.trusted-uris`. |
| Zertifikat ohne Warnung | siehe Abschnitt 3 – bei Zertifikatsfehlern schicken Browser keine Windows-Anmeldung. |

**Prüfen:** Seite öffnen – man landet ohne Anmeldemaske auf der Übersicht. Erscheint stattdessen
„Dein Windows-Konto konnte nicht automatisch ermittelt werden“, fehlt meist die Intranet-Einstufung.
Im Log steht jede abgelehnte Anmeldung mit Konto („Windows-Anmeldung abgelehnt: …“).

## 5. Betrieb

| Aufgabe | So geht’s |
|---|---|
| Dienst starten/stoppen | `services.msc` → „Berichtsheft (Azubi-Portal)“ oder `"<Ordner>\berichtsheft-service.exe" restart` |
| Logs | `<Ordner>\logs\berichtsheft.log` (Anwendung), `berichtsheft-service.*.log` (Dienst), `setup-*.log` (Setup) |
| Datensicherung | Ordner `<Ordner>\data` sichern (Dienst vorher stoppen); jedes Update legt zusätzlich `backup\<Datum>` an |
| Einstellungen, Speicherort, Deinstallieren | `<Ordner>\Verwaltung.cmd` oder „Apps / Programme und Features“ |
| Speicher für Java | in `berichtsheft-service.xml` bei `-Xmx768m`, danach Dienst neu starten |

Die Datenbank ist eine H2-Datei (`data\berichtsheft.mv.db`). Konfiguration und Datenbank sind nur für
Administratoren und den Dienst (SYSTEM) lesbar, weil sie das JWT-Geheimnis bzw. Passwort-Hashes enthalten.

## 6. Fehlersuche

| Problem | Lösung |
|---|---|
| Nach Doppelklick auf `Setup.cmd` passiert nichts | Die Administrator-Abfrage bestätigen; das Fenster erscheint nach einigen Sekunden. Sonst in einer PowerShell `powershell -ExecutionPolicy Bypass -File setup\setup.ps1` starten, um Fehlermeldungen zu sehen. |
| Seite vom eigenen PC nicht erreichbar, auf dem Server schon | Firewall: Ist das Netzwerkprofil des Servers „Öffentlich“? Dann Regel „Berichtsheft (TCP 5016)“ in `wf.msc` auch für „Öffentlich“ freigeben. |
| Setup meldet „Die Anwendung antwortet nicht“ | `logs\berichtsheft.log` ansehen. Häufig: Port belegt → Setup erneut ausführen, *Einstellungen ändern*, anderen Port wählen. |
| Erster Ausbilder kann sich nicht anmelden | In `logs\berichtsheft.log` nach „Erster Ausbilder“ suchen. Passwort zurücksetzen geht über einen weiteren Ausbilder oder Neuinstallation mit leerem `data`-Ordner. |
| Nach Update alte Oberfläche | Im Browser Strg+F5. |
| „Die Windows-Anmeldung ist fehlgeschlagen (HTTP 400)“ nur beim Aufruf per Name, per IP klappt es | Das Kerberos-Ticket ist größer als das Header-Limit (64 KB). Bei sehr vielen AD-Gruppen in `berichtsheft-service.xml` `<env name="MAX_HEADER_SIZE" value="128KB"/>` ergänzen und Dienst neu starten. |
| „Kein Zugriff“, obwohl die Person angelegt ist | Benutzername in der Personenliste muss genau dem Windows-Anmeldenamen entsprechen, der auf der Seite angezeigt wird (ohne Domäne). |
| Windows-Anmeldung klappt nicht, Browser fragt nach Benutzer/Passwort | Seite ist nicht als Intranet eingestuft oder per IP/Alias ohne SPN aufgerufen – siehe Abschnitt 4. |
| Ausgesperrt (ohne Passwort-Login, eigener Benutzername falsch) | Setup bzw. `Verwaltung.cmd` → *weitere Einstellungen ändern* → Passwort-Anmeldung erlauben, mit Passwort anmelden, Benutzernamen korrigieren. |

## 7. E-Mail-Versand über Microsoft 365 (Microsoft Graph)

Das Portal verschickt E-Mails (Abnahme/Rückgabe, Erinnerungen) entweder per **SMTP** (Standard, lokal an Mailhog)
oder über **Microsoft Graph**. Bei Graph meldet sich die Anwendung selbst per OAuth2 an – ohne Benutzerpasswort.
Umgeschaltet wird mit `MAIL_VERSANDART` (`smtp` oder `graph`).

### 7.1 Postfach anlegen (Exchange Admin Center)
1. <https://admin.exchange.microsoft.com> → *Empfänger* → *Postfächer* → **Freigegebenes Postfach hinzufügen**
   (braucht keine Lizenz), z. B. `Ausbildung-Fellbach@AzubiPortal.de`, Anzeigename z. B. „Azubi Portal“.
   Der Anzeigename ist der Absendername in den Mails.
2. Die Domain der Adresse muss im Mandanten als Domain eingerichtet sein.

### 7.2 App registrieren (Microsoft Entra Admin Center)
1. <https://entra.microsoft.com> → *Identität* → *Anwendungen* → **App-Registrierungen** → *Neue Registrierung*:
   Name „Berichtsheft E-Mail“, *Nur Konten in diesem Organisationsverzeichnis*, **keine** Umleitungs-URI.
2. Auf der Übersicht notieren: **Anwendungs-ID (Client)** → `MAIL_GRAPH_CLIENT_ID`,
   **Verzeichnis-ID (Mandant)** → `MAIL_GRAPH_TENANT_ID`.
3. *Zertifikate & Geheimnisse* → *Neuer geheimer Clientschlüssel* → Ablauf wählen (max. 24 Monate).
   Den **Wert** sofort kopieren (wird nur einmal angezeigt) → `MAIL_GRAPH_CLIENT_SECRET`.
   Ablaufdatum in den Kalender eintragen – danach schlägt der Versand fehl (siehe Fehlersuche).

### 7.3 Senderecht nur für dieses eine Postfach erteilen (Exchange Online PowerShell)
`Mail.Send` als Anwendungsberechtigung in Entra würde Senden **aus jedem Postfach** der Firma erlauben.
Deshalb wird das Recht über *RBAC für Anwendungen* in Exchange Online auf das Portal-Postfach begrenzt
(in Entra unter *API-Berechtigungen* also **kein** `Mail.Send` mit Administratorzustimmung eintragen):

```powershell
Install-Module ExchangeOnlineManagement   # einmalig
Connect-ExchangeOnline

# Objekt-ID findet man in Entra unter "Unternehmensanwendungen" → "Berichtsheft E-Mail" (NICHT die der App-Registrierung)
New-ServicePrincipal -AppId <Client-ID> -ObjectId <Objekt-ID der Unternehmensanwendung> -DisplayName "Berichtsheft E-Mail"

New-ManagementScope -Name "Berichtsheft-Postfach" `
    -RecipientRestrictionFilter "PrimarySmtpAddress -eq 'Ausbildung-Fellbach@AzubiPortal.de'"

New-ManagementRoleAssignment -App <Client-ID> -Role "Application Mail.Send" -CustomResourceScope "Berichtsheft-Postfach"

# Prüfen: InScope muss True sein
Test-ServicePrincipalAuthorization -Identity <Client-ID> -Resource Ausbildung-Fellbach@AzubiPortal.de
```

Neue Rechte können bis zu ca. 2 Stunden brauchen, bis sie greifen.

### 7.4 Server einstellen
In `berichtsheft-service.xml` ergänzen und den Dienst neu starten:

```xml
<env name="MAIL_VERSANDART" value="graph"/>
<env name="MAIL_GRAPH_TENANT_ID" value="<Verzeichnis-ID>"/>
<env name="MAIL_GRAPH_CLIENT_ID" value="<Anwendungs-ID>"/>
<env name="MAIL_GRAPH_CLIENT_SECRET" value="<Wert des Clientschlüssels>"/>
<env name="MAIL_ABSENDER" value="Ausbildung-Fellbach@AzubiPortal.de"/>
<env name="MAIL_MUSTER_AZUBI" value="{vorname}.{nachname}@firma.de"/>
<env name="MAIL_MUSTER_AUSBILDER" value="{vorname}.{nachname}@firma.de"/>
<env name="MAIL_PORTAL_URL" value="https://<servername>:5016"/>
<!-- Zum Ausprobieren erst alles an eine Adresse umleiten, danach Zeile entfernen -->
<env name="MAIL_UMLEITEN_AN" value="admin@firma.de"/>
```

Der Server braucht ausgehend HTTPS (Port 443) zu `login.microsoftonline.com` und `graph.microsoft.com`.
Muss ein Proxy benutzt werden, in den Java-Argumenten `-Dhttps.proxyHost=… -Dhttps.proxyPort=…` ergänzen.

**Prüfen:** Im Log steht nach dem Start (Erinnerungen) bzw. nach einer Abnahme
„E-Mail … per Microsoft Graph verschickt“. Die Mail liegt außerdem unter *Gesendete Elemente* des Postfachs.

### 7.5 Fehlersuche E-Mail

| Meldung im Log | Ursache / Lösung |
|---|---|
| Dienst startet nicht: „… es fehlt: app.mail.graph.…“ | Umgebungsvariablen aus 7.4 fehlen oder sind falsch geschrieben. |
| „Anmeldung bei Microsoft Entra ID fehlgeschlagen … AADSTS7000215“ | Falscher oder abgelaufener Clientschlüssel – den **Wert** (nicht die geheime ID) eintragen bzw. neuen Schlüssel erstellen. |
| „… AADSTS700016“ / „AADSTS90002“ | Client-ID bzw. Mandanten-ID falsch. |
| „Microsoft Graph hat die Mail abgelehnt (HTTP 403): Access is denied“ | Senderecht fehlt oder ist noch nicht aktiv – 7.3 prüfen (`Test-ServicePrincipalAuthorization`), ggf. bis zu 2 h warten. |
| „… (HTTP 404) … ErrorInvalidUser“ / „MailboxNotEnabledForRESTAPI“ | Postfach `MAIL_ABSENDER` existiert nicht in Exchange Online. |
| Verbindungsfehler / Zeitüberschreitung | Firewall oder Proxy (7.4). |

## 8. E-Mail-Versand über einen eigenen Exchange-Server (SMTP + IMAP)

Für einen Exchange-Server im eigenen Netz (ohne Microsoft 365) gibt es die Versandart `exchange`:
Die Anwendung meldet sich mit Benutzername und Passwort eines Postfachs per **SMTP** an und verschickt darüber
(Client-Submission, Port 587 mit STARTTLS). Danach legt sie per **IMAP** eine Kopie in den Ordner
„Gesendete Elemente“ – das macht Exchange bei SMTP-Versand nicht selbst. IMAP dient nur dieser Kopie,
verschickt wird immer per SMTP. Scheitert nur die Kopie, ist die Mail trotzdem raus (Warnung im Log).

**Bei der IT klären:**

| Was | Warum |
|---|---|
| Postfach für den Versand (z. B. `ausbildung@firma.de`) mit Passwort | Damit meldet sich die Anwendung an; es ist auch der Absender. |
| Client-Submission (SMTP-Authentifizierung) ist erlaubt, Port 587 | Bei Exchange der Empfangsconnector „Client Frontend“. |
| IMAP4-Dienst läuft, Port 993 | Nur für die Kopie in „Gesendete Elemente“ (sonst `MAIL_EXCHANGE_GESENDET_SPEICHERN=false`). |
| Zertifikat des Exchange-Servers | Stammt es von der Firmen-CA, kennt Java es nicht: `MAIL_EXCHANGE_SSL_TRUST=mail.firma.de` setzen. |

**Server einstellen** – in `berichtsheft-service.xml` ergänzen und den Dienst neu starten:

```xml
<env name="MAIL_VERSANDART" value="exchange"/>
<env name="MAIL_EXCHANGE_SMTP_HOST" value="mail.firma.de"/>
<env name="MAIL_EXCHANGE_BENUTZERNAME" value="ausbildung@firma.de"/>
<env name="MAIL_EXCHANGE_PASSWORT" value="<Passwort des Postfachs>"/>
<env name="MAIL_ABSENDER" value="ausbildung@firma.de"/>
<!-- nur wenn abweichend: -->
<env name="MAIL_EXCHANGE_IMAP_HOST" value="imap.firma.de"/>
<env name="MAIL_EXCHANGE_GESENDET_ORDNER" value="Sent Items"/>
<env name="MAIL_EXCHANGE_SSL_TRUST" value="mail.firma.de"/>
<env name="MAIL_EXCHANGE_EHLO_NAME" value="scapa02e.f-i-sp.de"/>
```

Alle Einstellungen mit Standardwerten stehen in `application.properties` unter „Exchange-Server“
(`app.mail.exchange.*`: Ports 587/993, STARTTLS bzw. SSL, Zeitlimit, Ordnername). Lokal zum Ausprobieren
können sie dort auch direkt eingetragen werden – das Passwort aber nur als Umgebungsvariable.

**Prüfen:** Im Log steht „E-Mail … per Exchange verschickt“, die Mail liegt in „Gesendete Elemente“ des Postfachs.

| Meldung im Log | Ursache / Lösung |
|---|---|
| Dienst startet nicht: „… es fehlt: app.mail.exchange.…“ | Umgebungsvariablen oben fehlen oder sind falsch geschrieben. |
| „535 5.7.3 Authentication unsuccessful“ | Benutzername/Passwort falsch oder SMTP-Authentifizierung für das Postfach gesperrt. |
| „STARTTLS is required but host does not support STARTTLS“ | Falscher Port – 587 verwenden oder bei Port 465 `MAIL_EXCHANGE_SSL=true`. |
| „PKIX path building failed“ | Zertifikat unbekannt: `MAIL_EXCHANGE_SSL_TRUST=<Servername>`. |
| „5.7.60 … not have permissions to send as this sender“ | `MAIL_ABSENDER` ist nicht das angemeldete Postfach und es fehlt das Senden-als-Recht. |
| „… Kopie in „Gesendete Elemente“ per IMAP ist fehlgeschlagen“ | IMAP-Dienst aus/Port gesperrt oder Ordnername falsch (englisch „Sent Items“). Die Mail ist trotzdem verschickt. |
