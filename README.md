# Berichtsheft – Backend

REST-Backend für das **digitale Berichtsheft (Azubi-Portal)**: Azubis führen ihr Berichtsheft wochenweise online, ordnen ihre Tätigkeiten Lernzielen zu und reichen Wochen zur Prüfung ein. Ausbilder zeichnen ab oder geben mit Kommentar zurück und verfolgen den Lernfortschritt.

Das passende Frontend liegt in [`Berichtsheft_Frontend`](https://github.com/Gymbollek2/Berichtsheft_Frontend).

**Technik:** Java 21 · Spring Boot 4 · Spring Security (JWT) · Spring Data JPA · H2-Datenbank · Maven

---

## Inhalt

1. [Funktionen](#funktionen)
2. [Fachregeln](#fachregeln) und [Rollen](#rollen-und-rechte)
3. [Lokal starten – Schritt für Schritt](#lokal-starten--schritt-für-schritt)
4. [Demo-Zugänge](#demo-zugänge)
5. [Konfiguration und HTTPS](#konfiguration)
6. [REST-API](#rest-api)
7. [Tests](#tests)
8. [Versionen und Releases](#versionen-und-releases)
9. [Projektstruktur](#projektstruktur)
10. [Auf einem Server betreiben (Windows-Dienst)](#auf-einem-server-betreiben-windows-dienst)
11. [Docker](#docker)
12. [Fehlerbehebung](#fehlerbehebung)

---

## Funktionen

- **Nur HTTPS** auf Port 8443. Für die lokale Entwicklung erzeugt das Backend beim ersten Start automatisch ein Zertifikat.
- **Login mit JWT**: Benutzername und Passwort (BCrypt-gehasht) ergeben ein Token. Nach einer Passwortänderung werden alle alten Tokens ungültig.
- **Benutzerrollen**: Azubi, dualer Student und Ausbilder, jeweils mit eigenen Rechten (siehe unten). Ausbilder dürfen alles.
- **Personenverwaltung**: Personen mit Login anlegen, bearbeiten, löschen und Passwörter zurücksetzen. Der Benutzername wird automatisch als `vorname.nachname` erzeugt.
- **Lernziele**: feste Hierarchie Richtlernziel → Groblernziel → Feinlernziel (siehe [Fachregeln](#fachregeln)). Neue Lernziele werden automatisch allen Azubis zugewiesen. **CSV-Import und -Export** sind möglich.
- **Lernfortschritt**: Der Ausbilder setzt den Status der Feinlernziele (*Offen*, *In Bearbeitung*, *Erreicht*). Grob- und Richtlernziele werden daraus berechnet. Wird ein Feinlernziel im Berichtsheft angegeben, wechselt es automatisch auf *In Bearbeitung*.
- **Berichtsheft**:
  - Tageseinträge mit Art (Betrieb, Berufsschule, Urlaub, Krank …), Stunden, Tätigkeiten und Lernzielen
  - Wochenansicht mit Kalenderwoche und Stundensumme
  - Workflow: **Entwurf → Eingereicht → Abgezeichnet** bzw. **Zur Überarbeitung** (mit Pflichtkommentar)
  - Eingereichte und abgezeichnete Wochen sind gesperrt.
- **Demo-Daten** beim ersten Start, damit man sofort loslegen kann.
- Einheitliche **Fehlermeldungen** als JSON (`status`, `message`, `timestamp`).

## Fachregeln

**Lernziele**

| Art | Übergeordnetes Lernziel |
|---|---|
| Richtlernziel | keines (oberste Ebene) |
| Groblernziel | genau ein Richtlernziel (Pflicht) |
| Feinlernziel | genau ein Groblernziel (Pflicht) |

- `GET /v1/lernziel/parent-options?kind=…` liefert nur die passenden übergeordneten Lernziele.
- Titel sind je Ebene eindeutig.
- Die Art lässt sich nur ändern, solange keine Unterziele existieren.
- Löschen ist nur ohne Unterziele möglich. Ebenfalls gesperrt ist es, wenn das Lernziel in eingereichten oder abgezeichneten Wochen verwendet wird.
- Status übergeordneter Lernziele: alle Unterziele erreicht → *Erreicht*, mindestens eines begonnen → *In Bearbeitung*, sonst *Offen*. Der Fortschritt in Prozent zählt nur Lernziele ohne Unterziele.

**Berichtsheft**

- Keine Einträge für zukünftige Tage und keine vor dem Ausbildungsbeginn.
- Höchstens 10 Stunden pro Tag (Arbeitszeitgesetz).
- Betrieb und Berufsschule brauchen Stunden und eine Beschreibung.
- Tätigkeiten werden nur zugeordneten **Feinlernzielen** zugewiesen. An Urlaubs-, Krankheits- und Feiertagen gibt es keine Lernziele.
- Einreichen ist ab dem Freitag der Woche möglich und nur, wenn Montag bis Freitag jeweils einen Eintrag haben.
- Eingereichte und abgezeichnete Wochen sind gesperrt. Zurückgegebene Wochen sind wieder bearbeitbar.

**Personen**

- Passwörter: mindestens 8 Zeichen mit Buchstaben und Ziffern.
- Benutzernamen: Kleinbuchstaben, Ziffern, `.`, `-`, `_`.
- Azubis brauchen einen Ausbildungsbeginn. Das Ausbildungsjahr wird daraus berechnet.
- Niemand kann sich selbst löschen oder die eigene Rolle ändern. Der letzte Ausbilder bleibt immer erhalten.
- Azubis mit Berichtsheft-Einträgen können nicht zu Ausbildern gemacht werden.

## Rollen und Rechte

| Aktion | Azubi / Student | Ausbilder |
|---|:---:|:---:|
| Eigenes Berichtsheft schreiben und einreichen | ✅ | – |
| Eigene Lernziele und eigenen Fortschritt sehen | ✅ | ✅ |
| Berichtshefte aller Azubis lesen | – | ✅ |
| Wochen abzeichnen oder zurückgeben | – | ✅ |
| Lernziel-Status setzen | – | ✅ |
| Lernziele anlegen, ändern, löschen, CSV | – | ✅ |
| Azubis, Studierende und Ausbilder verwalten | – | ✅ |

Die frühere Rolle „Administrator/in“ gibt es nicht mehr. Bestehende Admins werden beim Start automatisch
zu Ausbildern (`RollenMigration`) und melden sich einmal neu an.

Azubis können über die API **nur ihre eigenen Daten** abrufen. Das wird serverseitig geprüft, nicht nur im Frontend.

---

## Lokal starten – Schritt für Schritt

### 1. Voraussetzungen

| Was | Version | Prüfen mit |
|---|---|---|
| **JDK** | 21 oder neuer (z. B. Temurin 21/24) | `java -version` |
| Maven | optional, der Maven-Wrapper (`mvnw`) bzw. IntelliJ bringt es mit | `mvn -v` |
| Git | beliebig | `git --version` |

> **Tipp für Windows:** Ein JDK bekommst du z. B. über <https://adoptium.net> (Temurin 21, „JDK“, Installer mit „Set JAVA_HOME“ anhaken). IntelliJ kann ein JDK auch direkt herunterladen: *File → Project Structure → SDK → Download JDK*.

### 2. Projekt holen

```bash
git clone https://github.com/Gymbollek2/Berichtsheft_Backend.git
cd Berichtsheft_Backend
```

### 3a. Starten mit IntelliJ IDEA (empfohlen)

1. *File → Open…* → den Ordner `Berichtsheft_Backend` wählen. IntelliJ erkennt das Maven-Projekt und lädt die Abhängigkeiten. Das dauert beim ersten Mal ein paar Minuten.
2. Unter *File → Project Structure → Project* ein **SDK 21 oder neuer** einstellen.
3. Die Klasse `src/main/java/de/fisp/azubiprojekt/BerichtsheftApplication.java` öffnen und auf den grünen ▶-Pfeil neben `main` klicken.
4. In der Konsole erscheint `Started BerichtsheftApplication …` und darüber die **Demo-Logins**. Beim allerersten Start steht dort außerdem `Selbstsigniertes Entwicklungszertifikat erzeugt`.

### 3b. Starten über die Kommandozeile

```bash
# Windows
mvnw.cmd spring-boot:run

# macOS / Linux
./mvnw spring-boot:run

# alternativ mit installiertem Maven
mvn spring-boot:run
```

### 4. Prüfen, ob es läuft

- Öffne <https://localhost:8443/v1/lernziel>. Der Browser warnt einmal vor dem selbstsignierten Zertifikat. Unter *Erweitert → Weiter zu localhost* bestätigen.
  Die Antwort ist **401** mit `{"status":401,"message":"Nicht angemeldet …"}`. Das ist richtig, weil ohne Login nichts abrufbar ist.
- Die Datenbank-Oberfläche liegt unter <https://localhost:8443/h2-console>
  (JDBC-URL `jdbc:h2:file:./data/berichtsheft`, User `sa`, Passwort leer).
- HTTP (`http://localhost:8080`) gibt es bewusst nicht mehr.

Anschließend das **Frontend** starten, siehe [Frontend-README](https://github.com/Gymbollek2/Berichtsheft_Frontend#lokal-starten--schritt-für-schritt).

### Datenbank zurücksetzen

Die Daten liegen in `./data/berichtsheft.mv.db`, das Entwicklungszertifikat in `./data/ssl`. Backend stoppen, den Ordner `data` löschen und neu starten. Danach werden Demo-Daten und Zertifikat neu angelegt.

> **Update von Version 1.0:** Die neuen Hierarchieregeln gelten auch für bestehende Daten. Am einfachsten löschst du einmal den Ordner `data`.

## Demo-Zugänge

Werden beim **ersten Start mit leerer Datenbank** automatisch angelegt:

| Benutzername | Passwort | Rolle | Hinweis |
|---|---|---|---|
| `ausbilder` | `ausbilder123` | Ausbilder | darf alles; eine Woche von Max wartet auf Prüfung |
| `azubi` | `azubi123` | Azubi (Max Mustermann) | mit Beispiel-Wochen |
| `azubi2` | `azubi123` | Azubi (Erika Musterfrau) | ohne Einträge |

> ⚠️ Nur für die lokale Entwicklung. Für einen echten Betrieb `DEMO_DATA=false` setzen, siehe unten.

### Vertrauenswürdiges Zertifikat (optional, ohne Browser-Warnung)

Mit [mkcert](https://github.com/FiloSottile/mkcert) (Windows: `winget install FiloSottile.mkcert`) erzeugst du ein Zertifikat, dem dein Rechner vertraut:

```bash
mkcert -install
mkcert -pkcs12 -p12-file data/ssl/berichtsheft-dev.p12 localhost 127.0.0.1
```

mkcert verwendet als Passwort `changeit`. Deshalb vor dem Start `SSL_KEYSTORE_PASSWORD=changeit` setzen, in IntelliJ unter *Run Configuration → Environment variables*.

---

## Konfiguration

Alle Werte stehen in `src/main/resources/application.properties` und lassen sich per **Umgebungsvariable** überschreiben:

| Variable | Standard | Bedeutung |
|---|---|---|
| `PORT` | `8443` | HTTPS-Port |
| `SSL_KEYSTORE` | `file:./data/ssl/berichtsheft-dev.p12` (wird erzeugt) | Eigenes Zertifikat als PKCS12, z. B. `file:/etc/berichtsheft/server.p12` |
| `SSL_KEYSTORE_PASSWORD` | `berichtsheft-dev` | Passwort des Keystores |
| `SSL_KEYSTORE_TYPE` | `PKCS12` | `PKCS12` oder `JKS` |
| `SSL_ENABLED` | `true` | nur für Sonderfälle (z. B. TLS am Reverse Proxy) auf `false` setzen |
| `DB_URL` | `jdbc:h2:file:./data/berichtsheft;AUTO_SERVER=TRUE` | JDBC-URL der Datenbank |
| `DB_USER` / `DB_PASSWORD` | `sa` / leer | Datenbank-Zugang |
| `JWT_SECRET` | Entwicklungsschlüssel | **In Produktion unbedingt setzen** (langer Zufallswert) |
| `JWT_EXPIRATION_HOURS` | `12` | Gültigkeit eines Logins |
| `CORS_ORIGINS` | `https://localhost:4200` | Erlaubte Frontend-Adressen (kommagetrennt) |
| `DEMO_DATA` | `true` (Profil `prod`: `false`) | Demo-Daten bei leerer DB anlegen |
| `SSL_HOSTNAMES` | – | weitere Namen für das selbstsignierte Zertifikat (kommagetrennt); Rechnername und IPs kommen automatisch dazu |
| `FRONTEND_DIR` | `file:./frontend/` | Ordner mit dem gebauten Frontend, das unter derselben Adresse ausgeliefert wird |
| `RAHMENPLAN_IMPORT` | `true` | Ausbildungsrahmenplan FIAE einmalig einspielen |
| `SSO_ENABLED` | `false` | Windows-Anmeldung (Kerberos/NTLM): Zugriff nur, wenn der Windows-Anmeldename (sAMAccountName) als Benutzername in der Personenliste steht. Server muss Domänenmitglied sein, siehe `deploy/SERVER-ANLEITUNG.md` |
| `SSO_DOMAINS` | – | nur Konten dieser Domänen zulassen (Kurzname, kommagetrennt) |
| `PASSWORT_LOGIN` | `true` | Anmeldung mit Benutzername/Passwort zusätzlich erlauben |
| `MAX_HEADER_SIZE` | `64KB` | maximale Header-Größe – Kerberos-Tickets mit vielen AD-Gruppen sind groß |

Mit `--spring.profiles.active=prod` gelten zusätzlich die Werte aus `application-prod.properties`:
`JWT_SECRET` ist Pflicht, keine Demo-Daten, H2-Konsole aus, Logdatei unter `logs/`.

> **Hinweis:** Die frühere GitLab-OAuth-Anbindung (Client-ID/-Secret von `gitlab.fisp.dev`) und die LDAP-Reste wurden entfernt. Die Zugangsdaten standen im Klartext im Code und dürfen nicht in ein öffentliches Repository. Falls ihr den GitLab-Login wieder braucht, solltet ihr das alte Client-Secret in GitLab **neu erzeugen** und das neue nur per Umgebungsvariable übergeben.

---

## REST-API

Alle Endpunkte außer dem Login brauchen den Header `Authorization: Bearer <accessToken>`.

### Anmeldung – `/v1/passport`

| Methode | Pfad | Beschreibung |
|---|---|---|
| GET | `/optionen` | `{ sso, passwortLogin }` – welche Anmeldearten angeboten werden |
| POST | `/login` | `{ "username", "password" }` → `{ accessToken, expiresAt, user }` |
| GET | `/sso` | Windows-Anmeldung (`WWW-Authenticate: Negotiate`) → wie `/login`; 403 mit `konto`, wenn das Windows-Konto nicht in der Personenliste steht |
| GET | `/me` | eigene Daten |
| POST | `/change-password` | `{ oldPassword, newPassword }` → neues Token |

### Personen – `/v1/person` (Ausbilder)

| Methode | Pfad | Beschreibung |
|---|---|---|
| GET | `/` `?role=ROLE_TRAINEE` | alle Personen (optional nach Rolle) |
| GET | `/azubis` | alle Azubis und Studierenden |
| GET | `/{id}` | eine Person (Azubis nur sich selbst) |
| POST | `/` | anlegen (`firstName, lastName, role, password, username?, beruf?, yearOfTraining, dateOfTrainingStart?, dateOfBirth?`) |
| PUT | `/{id}` | ändern |
| DELETE | `/{id}` | löschen (inkl. Berichtsheft und Fortschritt) |
| POST | `/{id}/reset-password` | `{ newPassword }` |

### Lernziele – `/v1/lernziel`

| Methode | Pfad | Beschreibung |
|---|---|---|
| GET | `/` `?kind=KIND_BROAD` | alle Lernziele |
| GET | `/{id}` | ein Lernziel (inkl. Anzahl Unterziele) |
| GET | `/parent-options?kind=KIND_SPECIFIC` | erlaubte übergeordnete Lernziele für eine Art |
| POST / PUT / DELETE | `/` bzw. `/{id}` | verwalten (Ausbilder): `{ title, description?, kind, parentId? }` |
| GET | `/export` | CSV-Download |
| POST | `/import` | CSV-Upload (multipart, Feld `file`) |

**CSV-Format** (Semikolon, UTF-8, Kopfzeile optional):

```csv
titel;beschreibung;art;uebergeordnetes_lernziel
Software entwickeln;Anwendungen planen und umsetzen;Richtlernziel;
Programmieren mit Java;;Groblernziel;Software entwickeln
Unit-Tests schreiben;JUnit 5;Feinlernziel;Programmieren mit Java
```

Für `art` gehen `Richtlernziel`, `Groblernziel` und `Feinlernziel`, die Kurzformen `Richt`, `Grob` und `Fein` oder die Enum-Namen `KIND_GENERAL`, `KIND_BROAD` und `KIND_SPECIFIC`.

### Lernfortschritt – `/v1/personLernziel`

| Methode | Pfad | Beschreibung |
|---|---|---|
| GET | `/me` `?status=…` | eigene Lernziele mit Status |
| GET | `/me/fortschritt` | eigener Fortschritt in Prozent |
| GET | `/person/{id}` | Lernziele einer Person |
| GET | `/person/{id}/fortschritt` | Fortschritt einer Person |
| GET | `/fortschritt` | Fortschritt aller Azubis (Ausbilder) |
| PUT | `/{id}/status` | `{ "status": "STATUS_DONE" }` (Ausbilder, nur Lernziele ohne Unterziele) |

### Berichtsheft – `/v1/berichtsheft`

| Methode | Pfad | Beschreibung |
|---|---|---|
| GET | `/woche?datum=2026-10-21&personId=` | Woche (Mo–So) mit allen Tagen |
| GET | `/historie?personId=` | alle Wochen einer Person |
| PUT | `/eintrag` | Tageseintrag speichern: `{ date, hours, art, description, lernzielIds }` |
| DELETE | `/eintrag/{id}` | Tageseintrag löschen |
| POST | `/woche/einreichen` | `{ "datum": "2026-10-21" }` |
| GET | `/offen` | eingereichte Wochen (Ausbilder) |
| POST | `/woche/{id}/abzeichnen` | `{ kommentar? }` (Ausbilder) |
| POST | `/woche/{id}/ablehnen` | `{ kommentar }`, Kommentar ist Pflicht (Ausbilder) |

---

## Tests

```bash
mvnw.cmd test        # Windows
./mvnw test          # macOS / Linux
```

- **Unit-Tests**: Lernziel-Hierarchie, CSV-Parser, Kalenderwochen, Benutzernamen, Ausbildungsdaten
- **API-Integrationstest** (`BerichtsheftApiTest`): Er startet die komplette Anwendung mit In-Memory-Datenbank und prüft Login, Rechte, Personenregeln, Lernziel-Hierarchie, berechneten Status, Berichtsheft-Regeln, CSV und den kompletten Workflow (erfassen → einreichen → zurückgeben → nachbessern → abzeichnen).

Die Tests laufen zusätzlich bei jedem Push automatisch per **GitHub Actions** (Tab *Actions* im Repository).

## Versionen und Releases

Tags und GitHub-Releases werden automatisch vom Workflow `.github/workflows/release.yml` erstellt:

1. `<version>` in der `pom.xml` erhöhen (z. B. `1.1.0`).
2. Im `CHANGELOG.md` einen Abschnitt `## [1.1.0] – Datum` ergänzen.
3. Committen und auf `main` pushen. Wenn Build und Tests grün sind, entstehen Tag `v1.1.0` und ein Release mit der JAR-Datei.

## Projektstruktur

```
src/main/java/de/fisp/azubiprojekt
├── BerichtsheftApplication.java   Startklasse
├── config/        Security, JWT-Filter, Frontend-Auslieferung, Start-Aufgaben (Demo-Daten, Rahmenplan, erster Ausbilder)
├── controller/    REST-Endpunkte
├── dao/           Spring-Data-Repositories
├── dto/           Request-/Response-Objekte (Java Records)
├── entity/        JPA-Entities (Person, Users, Lernziel, PersonLernziel, Berichtsheft, Wochenbericht)
├── exceptions/    Fehlerklassen und globaler Exception-Handler
├── service/       Geschäftslogik
└── util/          Enums (Rollen, Lernziel-Arten, Status …)
```

## Auf einem Server betreiben (Windows-Dienst)

Backend und Frontend laufen zusammen als **ein Windows-Dienst**: Das Backend liefert das gebaute
Frontend aus dem Ordner `frontend/` mit aus, die Seite ist unter `https://<Server>:8443/` erreichbar.

```bat
deploy\build-release.cmd        :: auf dem Entwicklungsrechner -> release\berichtsheft-<Version>.zip
install.cmd                     :: auf dem Server im entpackten ZIP (als Administrator)
```

Das Release enthält eine eigene Java-Laufzeit, auf dem Server muss nichts installiert sein. Das
Installationsskript legt den ersten Ausbilder-Zugang an, erzeugt das JWT-Geheimnis, öffnet die Firewall
und richtet den Dienst mit [WinSW](https://github.com/winsw/winsw) ein. Updates mit `update.cmd`, Zertifikat ohne Browser-Warnung mit `zertifikat.cmd`.
Alle Schritte, Optionen und Zertifikate: **[deploy/SERVER-ANLEITUNG.md](deploy/SERVER-ANLEITUNG.md)**.

## Docker

```bash
docker build -t berichtsheft-backend .
docker run -p 8443:8443 -e JWT_SECRET=bitte-aendern -v berichtsheft-data:/app/data berichtsheft-backend
```

## Fehlerbehebung

| Problem | Lösung |
|---|---|
| `release version 21 not supported` | Es läuft ein älteres JDK. JDK 21+ installieren und in IntelliJ als Projekt-SDK einstellen. |
| `Port 8443 already in use` | Anderes Programm beenden oder mit `PORT=8444` starten. Dann im Frontend `proxy.conf.json` anpassen. |
| `keytool fehlgeschlagen` beim Start | Es läuft nur ein JRE ohne `keytool`. Ein vollständiges JDK 21 verwenden oder ein eigenes Zertifikat per `SSL_KEYSTORE` angeben. |
| Browser meldet „Verbindung nicht sicher“ | Erwartet beim selbstsignierten Zertifikat: einmal bestätigen oder mkcert verwenden (siehe oben). |
| Login klappt nicht mehr nach Code-Änderungen | Ordner `data` löschen, damit die Demo-Daten neu angelegt werden. |
| `mvnw` lädt nicht | IntelliJ verwenden (bringt Maven mit) oder Maven installieren und `mvn spring-boot:run` nutzen. |
