# Changelog

## [Unveröffentlicht]

### Neu
- **Benachrichtigungen** (`/v1/benachrichtigung`): Berichtsheft abgegeben (an alle Ausbilder, auch „erneut
  abgegeben“), abgenommen und Korrekturbedarf (an den Azubi, mit Kommentar), Lernziel erreicht, Willkommen für
  neue Zugänge. Täglich um 7 Uhr und nach dem Start eine Erinnerung an nicht abgegebene Berichtshefte der Vorwoche
  (je Woche höchstens eine). Erledigte Anlässe gelten automatisch als gelesen.

### Geändert
- Ausbilder sehen die Einträge einer Woche erst, wenn der Azubi sie abgegeben hat (`eintraegeSichtbar` in
  `/woche`, nicht abgegebene Wochen fehlen in `/historie`). Zurückgegebene Wochen bleiben sichtbar.
- Erneut eingereichte Wochen liefern kein `geprueftVon`/`geprueftAm` der letzten Rückgabe mehr – die neue
  Prüfung steht aus. Der Kommentar der Rückgabe bleibt erhalten.
- „Abgezeichnet“ heißt jetzt **„Abgenommen“** (Statuslabel, Benachrichtigung, Meldungen). Intern bleiben der
  Status `ABGEZEICHNET` und der Endpunkt `/woche/{id}/abzeichnen`, damit vorhandene Daten weiter gelten.
- Standard-Port **5016** statt 8443 (`PORT`), auch für `install.cmd`. Bestehende Installationen behalten
  bei `update.cmd` ihren Port.

## [1.6.1] – 2026-10-22

### Behoben
- Windows-Anmeldung über den Servernamen schlug mit „HTTP 400“ fehl, über die IP-Adresse klappte sie.
  Ursache: Kerberos-Tickets (enthalten alle AD-Gruppen) sind größer als die 8 KB, die der Webserver
  standardmäßig für Header annimmt. Das Limit liegt jetzt bei 64 KB (`MAX_HEADER_SIZE`).

## [1.6.0] – 2026-10-22

### Neu
- **Windows-Anmeldung (Single Sign-on)** mit `SSO_ENABLED=true`: Der Browser meldet den Windows-Benutzer
  automatisch an (Kerberos, ersatzweise NTLM, über Waffle/SSPI). Zugriff hat nur, wessen Windows-Anmeldename
  (sAMAccountName) als Benutzername in der Personenliste steht; die Rolle kommt wie bisher aus der Person.
  Neue Endpunkte `GET /v1/passport/sso` und `GET /v1/passport/optionen`.
- Optional nur bestimmte Domänen (`SSO_DOMAINS`) und Passwort-Anmeldung abschaltbar (`PASSWORT_LOGIN=false`).
- `install.cmd`/`update.cmd`: Schalter `-Sso`, `-SsoDomaene`, `-OhnePasswortLogin`, `-SsoAus`.

## [1.5.0] – 2026-10-22

### Neu
- Release enthält das Frontend 1.5.0 mit formatierbaren Berichtsheft-Texten (Werkzeugleiste und Markdown).
  Das Backend speichert den Text unverändert; keine Datenbank-Änderung nötig.
- `zertifikat.cmd` im Release: Zertifikat der Firmen-CA auf dem laufenden Server einspielen (mit Prüfung
  von Passwort, Schlüssel, Ablaufdatum und Servernamen) oder das selbstsignierte Zertifikat als .cer
  exportieren, um es auf den Arbeitsplätzen als vertrauenswürdig einzutragen.

## [1.4.0] – 2026-10-22

### Neu
- **Serverbetrieb als Windows-Dienst**: `deploy/build-release.cmd` baut Frontend und Backend zu einem ZIP
  mit eigener Java-Laufzeit und WinSW, `install.cmd` richtet den Dienst „Berichtsheft“ ein (Firewall,
  JWT-Geheimnis, erster Ausbilder), `update.cmd` spielt neue Versionen mit Datenbank-Sicherung ein,
  `uninstall.cmd` entfernt den Dienst. Anleitung: `deploy/SERVER-ANLEITUNG.md`.
- Das Backend liefert das gebaute Frontend aus `./frontend` mit aus (eine Adresse, kein CORS);
  Seiten-Adressen der App funktionieren auch beim Neuladen (`SpaWeiterleitungFilter`).
- Profil `prod` (`application-prod.properties`): `JWT_SECRET` ist Pflicht, keine Demo-Daten,
  H2-Konsole aus, Logdatei unter `logs/`, Komprimierung.
- Erster Ausbilder-Zugang über `data/erster-ausbilder.properties` (wird nach dem Anlegen gelöscht).
- Selbstsigniertes Zertifikat gilt auch für Rechnername, DNS-Namen und IP-Adressen des Servers
  (weitere Namen per `SSL_HOSTNAMES`).

## [1.3.0] – 2026-10-22

### Geändert
- **Rolle „Administrator/in“ entfernt.** Ausbilder dürfen jetzt alles, auch andere Ausbilder anlegen,
  bearbeiten und löschen. Bestehende Admins werden beim Start automatisch zu Ausbildern (`RollenMigration`);
  der letzte Ausbilder kann nicht gelöscht oder herabgestuft werden. Demo-Login `admin` entfällt.
- An Berufsschultagen können keine Lernziele mehr angegeben werden (wie bei Urlaub, Krankheit und Feiertag).

## [1.2.0] – 2026-10-22

### Neu
- **Ausbildungsrahmenplan Fachinformatiker/in Anwendungsentwicklung** (FIAusbV 2020): alle 17
  Berufsbildpositionen (Abschnitte A, B und F) als Richtlernziele, alle 86 Buchstabenpunkte als
  Groblernziele, je ein Beispiel-Feinlernziel. Wird beim Start einmalig eingespielt
  (`lernziele/rahmenplan-fiae.csv`, abschaltbar mit `RAHMENPLAN_IMPORT=false`).
- Tageseinträge im Berichtsheft speichern den Zeitpunkt der letzten Speicherung (`updatedAt`,
  neue Spalte in `berichtsheft`, wird per `ddl-auto=update` angelegt). Das Feld ist in der
  API-Antwort der Einträge enthalten, damit das Frontend die letzte automatische Speicherung anzeigen kann.

## [1.1.1] – 2026-10-21

### Behoben
- Meldungen und Status-Bezeichnungen mit echten Umlauten (z. B. „Zur Überarbeitung“ statt „Zur Ueberarbeitung“).
- Demo-Daten: Feinlernziel „Frontend mit Angular umsetzen“ steht bei Max auf „In Bearbeitung“, passend zum Berichtsheft-Eintrag.

## [1.1.0] – 2026-10-21

### Neu
- **Lernziel-Hierarchie**: Richtlernziel ohne übergeordnetes Ziel, Groblernziel unter Richtlernziel,
  Feinlernziel unter Groblernziel, auch beim CSV-Import. Neuer Endpunkt `/v1/lernziel/parent-options`.
- **Berechneter Status**: Grob- und Richtlernziele ergeben sich aus ihren Unterzielen,
  der Fortschritt zählt nur Feinlernziele.
- **HTTPS**: Backend nur noch auf https://localhost:8443. Ein Entwicklungszertifikat wird beim ersten Start erzeugt.
- Fachregeln fürs Berichtsheft: keine Zukunft, max. 10 h/Tag, Beschreibung Pflicht,
  nur Feinlernziele, Einreichen ab Freitag mit vollständiger Woche.
- Fachregeln für Personen: Passwortregeln, Benutzernamen-Format, Ausbildungsbeginn Pflicht,
  berechnetes Ausbildungsjahr, Schutz des letzten Admins.

### Geändert
- Lernziele mit Unterzielen oder Verwendung in geprüften Wochen können nicht mehr gelöscht werden.
- Standard-Port 8443 statt 8080, CORS-Standard `https://localhost:4200`.

## [1.0.0] – 2026-10-21

Erste vollständige Version des Berichtsheft-Backends.

### Neu
- JWT-Login (`/v1/passport`) mit BCrypt-Passwörtern, „Passwort ändern“ und Token-Invalidierung
- Rollensystem: Azubi, dualer Student, Ausbilder, Admin – serverseitig geprüft
- Personenverwaltung inkl. Login-Konto, automatischem Benutzernamen und Passwort-Reset
- Lernziele als Hierarchie (Richt-/Grob-/Feinlernziel), CSV-Import/-Export per Upload/Download
- Lernfortschritt je Person mit Status Offen / In Bearbeitung / Erreicht und Prozentwert
- Berichtsheft mit Tageseinträgen, Wochenansicht (ISO-Kalenderwoche) und Freigabe-Workflow
  (Entwurf → Eingereicht → Abgezeichnet / Zur Überarbeitung)
- Demo-Daten beim ersten Start, einheitliche JSON-Fehlerantworten
- Unit- und API-Integrationstests, GitHub-Actions-CI, Dockerfile

### Geändert
- Spring Boot 4 mit Java 21, aufgeräumte Abhängigkeiten, REST-konforme Endpunkte
- Konfiguration über Umgebungsvariablen

### Entfernt
- GitLab-OAuth- und LDAP-Reste inkl. der im Klartext gespeicherten Secrets
- GitLab-CI-Konfiguration mit internen Proxy-Adressen
