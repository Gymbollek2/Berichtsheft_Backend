# Berichtsheft auf einem Windows-Server betreiben

Backend und Frontend laufen zusammen als **ein Windows-Dienst** („Berichtsheft“). Das Backend liefert
das Frontend mit aus, die Seite ist danach im Netzwerk erreichbar unter

```
https://<Servername>:8443/
```

Der Dienst startet automatisch mit Windows und nach einem Absturz neu.

---

## 1. Release bauen (auf dem Entwicklungsrechner)

Voraussetzungen: **JDK 21** (`JAVA_HOME` gesetzt), **Node.js 20+**, Internetzugang, beide Projekte
nebeneinander ausgecheckt (`…\Berichtsheft_Backend` und `…\Berichtsheft_Frontend`).

```bat
cd Berichtsheft_Backend
deploy\build-release.cmd
```

Ergebnis: `release\berichtsheft-<Version>.zip` mit

| Inhalt | Zweck |
|---|---|
| `berichtsheft.jar` | Backend (Spring Boot) |
| `frontend\` | gebautes Angular-Frontend |
| `jre\` | eigene Java-Laufzeit – auf dem Server muss **kein Java** installiert sein |
| `berichtsheft-service.exe` | [WinSW](https://github.com/winsw/winsw) – startet die Anwendung als Dienst |
| `install.cmd`, `update.cmd`, `uninstall.cmd` | Einrichten, Aktualisieren, Entfernen |
| `zertifikat.cmd` | Zertifikat der Firmen-CA einspielen oder das aktuelle Zertifikat zum Verteilen exportieren (siehe 3.) |

Optionen: `-FrontendPfad D:\…\Berichtsheft_Frontend` (anderer Ort), `-OhneTests`, `-OhneJava`
(kleineres Paket, dann braucht der Server Java 21).

> Kann WinSW nicht heruntergeladen werden (Proxy), die Datei
> `https://github.com/winsw/winsw/releases/download/v2.12.0/WinSW-x64.exe` von Hand laden,
> als `release\WinSW-x64.exe` ablegen und erneut bauen.

## 2. Installieren (auf dem Server)

1. ZIP auf den Server kopieren und entpacken, z. B. nach `C:\Temp\berichtsheft-1.4.0`.
2. `install.cmd` doppelklicken (fragt nach Administratorrechten).
3. Passwort für den ersten Ausbilder-Zugang `ausbilder` eingeben.

Das Skript kopiert alles nach **`F:\Program Files\Berichtsheft`** (anderer Ort: `-Zielordner`), erzeugt ein zufälliges JWT-Geheimnis, öffnet den
Port in der Firewall (Domänen- und private Netzwerke), installiert den Dienst und startet ihn.
Am Ende steht die Adresse, z. B. `https://srv-ausbildung.firma.local:8443/`.

Nach der ersten Anmeldung das Passwort unter **Profil** ändern und weitere Ausbilder und Azubis unter
**Personen** anlegen. Die Lernziele des Ausbildungsrahmenplans sind bereits eingespielt.

### Optionen

```powershell
.\install.cmd -Port 443 `
              -Hostnamen berichtsheft.firma.local `
              -ZertifikatDatei D:\Zertifikate\berichtsheft.pfx `
              -Zielordner D:\Apps\Berichtsheft `
              -AusbilderBenutzer petra.pruefer -AusbilderVorname Petra -AusbilderNachname Pruefer
```

| Option | Bedeutung |
|---|---|
| `-Port` | HTTPS-Port (Standard 8443). Mit 443 entfällt die Portangabe in der Adresse. |
| `-Hostnamen` | weitere Namen/DNS-Aliase, unter denen der Server aufgerufen wird |
| `-ZertifikatDatei` | eigenes Zertifikat (.pfx), siehe unten – Passwort wird abgefragt |
| `-OhneHttps` | nur HTTP, wenn ein IIS/Reverse-Proxy davor HTTPS übernimmt |
| `-DemoDaten` | Demo-Zugänge statt eigenem Ausbilder (nur zum Ausprobieren) |
| `-Speicher` | maximaler Arbeitsspeicher für Java (Standard `768m`) |

## 3. Zertifikat

Ohne `-ZertifikatDatei` erzeugt die Anwendung beim ersten Start ein **selbstsigniertes** Zertifikat
für den Rechnernamen, den vollen DNS-Namen, die IP-Adressen und `localhost`. Das funktioniert sofort,
Browser zeigen aber eine Warnung („Verbindung nicht privat“).

Ob ein Browser die Seite als **sicher** anzeigt, hängt nur am Zertifikat: Es muss von einer Stelle
stammen, der der Arbeitsplatz vertraut, und den Namen enthalten, unter dem die Seite aufgerufen wird.
Dafür gibt es zwei Wege, beide mit `zertifikat.cmd` (im Installationsordner, als Administrator):

**a) Zertifikat der Firmen-CA (empfohlen).** Bei der IT ein Server-Zertifikat anfragen:

- Name(n): der Name, unter dem alle die Seite aufrufen, z. B. `srv-ausbildung.firma.local`
  (alle Namen, die `zertifikat.cmd -Exportieren` unter „Ausgestellt für“ zeigt, sind sinnvoll)
- Format: **.pfx mit privatem Schlüssel** und Zwischenzertifikaten, dazu das Passwort

Einspielen – der Dienst wird kurz neu gestartet, Daten und Anmeldungen bleiben:

```powershell
.\zertifikat.cmd -ZertifikatDatei D:\Zertifikate\berichtsheft.pfx
```

Das Skript prüft vorher Passwort, privaten Schlüssel, Ablaufdatum und ob die Servernamen im Zertifikat
stehen. Der vorherige Stand wird unter `data\ssl\*.alt` gesichert. Firmenrechner vertrauen der
Firmen-CA bereits, es ist also nichts weiter zu tun.

**b) Selbstsigniertes Zertifikat verteilen** (wenn die IT kein Zertifikat ausstellt):

```powershell
.\zertifikat.cmd -Exportieren
```

erzeugt `berichtsheft-zertifikat.cer`. Diese Datei per Gruppenrichtlinie (IT) oder von Hand auf jedem
Arbeitsplatz unter **Vertrauenswürdige Stammzertifizierungsstellen** eintragen (Doppelklick →
Zertifikat installieren → Lokaler Computer). Danach Browser neu starten. Wird das Zertifikat neu
erzeugt, muss es erneut verteilt werden.

In beiden Fällen die Seite **unter dem Namen aus dem Zertifikat** aufrufen, also z. B.
`https://srv-ausbildung.firma.local:8443/` und nicht per IP-Adresse oder Kurzname, sofern der nicht
ebenfalls im Zertifikat steht.

Anderer Servername beim selbstsignierten Zertifikat: `F:\Program Files\Berichtsheft\data\ssl\berichtsheft-dev.p12`
löschen und den Dienst neu starten – das Zertifikat wird mit den aktuellen Namen neu erzeugt.

## 4. Windows-Anmeldung (Single Sign-on)

Mit eingeschalteter Windows-Anmeldung wird jeder, der die Seite öffnet, automatisch mit seinem
Windows-Konto angemeldet – ohne Passworteingabe (Kerberos, ersatzweise NTLM). Zugriff hat nur, wessen
**Windows-Anmeldename (sAMAccountName)** unter **Personen** als **Benutzername** eingetragen ist.
Alle anderen sehen die Seite „Kein Zugriff“ und kommen an keine andere Seite und keine Daten.

**Vorher:** Benutzernamen in der Personenliste auf die Windows-Anmeldenamen umstellen (*Personen →
bearbeiten → Benutzername*), mindestens den eigenen. Den Anmeldenamen zeigt auf dem jeweiligen PC
`whoami` (Teil hinter dem `\`), oder die Seite „Kein Zugriff“ zeigt ihn an.

**Einschalten** (beim Update oder jederzeit erneut aus dem Release-Ordner):

```powershell
.\update.cmd -Sso                          # einschalten, Passwort-Anmeldung bleibt als Ausweg
.\update.cmd -Sso -SsoDomaene FIRMA        # nur Konten der Domäne FIRMA zulassen
.\update.cmd -Sso -OhnePasswortLogin       # nur noch Windows-Anmeldung
.\update.cmd -SsoAus                       # wieder ausschalten
```

Bei einer Neuinstallation: `install.cmd -Sso -AusbilderBenutzer <eigener Windows-Anmeldename>`.

**Voraussetzungen (ggf. mit der IT klären):**

| Was | Warum / wie |
|---|---|
| Server ist **Mitglied der Domäne** | Nur dann kann er Windows-Anmeldungen prüfen. Der Dienst läuft als „Lokales System“ – nichts weiter einzurichten. |
| Seite über den **vollen Servernamen** aufrufen, z. B. `https://srv-ausbildung.firma.local:8443/` | Für den Rechnernamen gibt es den Kerberos-Eintrag (SPN) automatisch. Bei einem **DNS-Alias** (z. B. `berichtsheft.firma.local`) muss die IT ihn anlegen: `setspn -S HTTP/berichtsheft.firma.local SERVERNAME` |
| Seite ist im Browser als **Intranet** eingestuft | Edge und Chrome schicken die Windows-Anmeldung nur an Intranet-Seiten. Per Gruppenrichtlinie „Liste der Site-zu-Zonen-Zuweisungen“: `https://srv-ausbildung.firma.local` → Zone `1` (Lokales Intranet). Alternativ die Richtlinie `AuthServerAllowlist` für Edge/Chrome. Firefox: `network.negotiate-auth.trusted-uris`. |
| Zertifikat ohne Warnung | siehe Abschnitt 3 – bei Zertifikatsfehlern schicken Browser keine Windows-Anmeldung. |

**Prüfen:** Seite öffnen – man landet ohne Anmeldemaske auf der Übersicht. Erscheint stattdessen
„Dein Windows-Konto konnte nicht automatisch ermittelt werden“, fehlt meist die Intranet-Einstufung.
Im Log steht jede abgelehnte Anmeldung mit Konto („Windows-Anmeldung abgelehnt: …“).

## 5. Neue Version einspielen

1. Neues Release bauen (Schritt 1) und auf dem Server in einen **eigenen** Ordner entpacken.
2. Dort `update.cmd` doppelklicken.

Der Dienst wird angehalten, die Datenbank nach `F:\Program Files\Berichtsheft\backup\<Datum>` gesichert, Anwendung
und Frontend ersetzt und der Dienst wieder gestartet. Datenbank, Logs, Port und Zertifikat bleiben.

## 6. Betrieb

| Aufgabe | So geht’s |
|---|---|
| Dienst starten/stoppen | `services.msc` → „Berichtsheft (Azubi-Portal)“ oder `"F:\Program Files\Berichtsheft\berichtsheft-service.exe" restart` |
| Logs | `F:\Program Files\Berichtsheft\logs\berichtsheft.log` (Anwendung) und `berichtsheft-service.*.log` (Dienst) |
| Datensicherung | Ordner `F:\Program Files\Berichtsheft\data` sichern (bei laufendem Dienst: vorher stoppen oder `update.cmd`-Backups nutzen) |
| Einstellungen ändern | `F:\Program Files\Berichtsheft\berichtsheft-service.xml` (Port, Zertifikat, …), danach Dienst neu starten |
| Entfernen | `uninstall.cmd` (Daten bleiben) bzw. `uninstall.cmd -AllesLoeschen` |

Die Datenbank ist eine H2-Datei (`data\berichtsheft.mv.db`). Konfiguration und Datenbank sind nur für
Administratoren und den Dienst (SYSTEM) lesbar, weil sie das JWT-Geheimnis bzw. Passwort-Hashes enthalten.

## 7. Fehlersuche

| Problem | Lösung |
|---|---|
| Seite vom eigenen PC nicht erreichbar, auf dem Server schon | Firewall: Ist das Netzwerkprofil des Servers „Öffentlich“? Dann Regel „Berichtsheft (TCP 8443)“ in `wf.msc` auch für „Öffentlich“ freigeben. |
| Dienst startet nicht | `logs\berichtsheft.log` ansehen. Häufig: Port belegt (`netstat -ano | findstr :8443`) → `uninstall.cmd`, dann `install.cmd -Port 9443`. |
| „JWT_SECRET muss gesetzt sein“ | Dienst wurde ohne `install.cmd` eingerichtet – `JWT_SECRET` in `berichtsheft-service.xml` setzen. |
| Erster Ausbilder kann sich nicht anmelden | In `logs\berichtsheft.log` nach „Erster Ausbilder“ suchen. Passwort zurücksetzen geht über einen weiteren Ausbilder oder Neuinstallation mit leerem `data`-Ordner. |
| Nach Update alte Oberfläche | Im Browser Strg+F5. |
| „Die Windows-Anmeldung ist fehlgeschlagen (HTTP 400)“ nur beim Aufruf per Name, per IP klappt es | Das Kerberos-Ticket ist größer als das Header-Limit (ab Version 1.6.1: 64 KB). Bei sehr vielen AD-Gruppen in `berichtsheft-service.xml` `<env name="MAX_HEADER_SIZE" value="128KB"/>` ergänzen und Dienst neu starten. |
| „Kein Zugriff“, obwohl die Person angelegt ist | Benutzername in der Personenliste muss genau dem Windows-Anmeldenamen entsprechen, der auf der Seite angezeigt wird (ohne Domäne). |
| Windows-Anmeldung klappt nicht, Browser fragt nach Benutzer/Passwort | Seite ist nicht als Intranet eingestuft oder per IP/Alias ohne SPN aufgerufen – siehe Abschnitt 4. |
| Ausgesperrt (ohne Passwort-Login, eigener Benutzername falsch) | Im Release-Ordner `update.cmd -SsoAus`, mit Passwort anmelden, Benutzernamen korrigieren, danach wieder `update.cmd -Sso -OhnePasswortLogin`. |
