# Berichtsheft – Backend

REST-Backend für das **digitale Berichtsheft (Azubi-Portal)**: Azubis führen ihr Berichtsheft wochenweise online, ordnen ihre Tätigkeiten Lernzielen zu und reichen Wochen zur Prüfung ein. Ausbilder zeichnen ab oder geben mit Kommentar zurück und verfolgen den Lernfortschritt.

Das passende Frontend liegt in [`Berichtsheft_Frontend`](https://github.com/Gymbollek2/Berichtsheft_Frontend).

**Technik:** Java 21 · Spring Boot 4 · Spring Security (JWT) · Spring Data JPA · H2-Datenbank · Maven

---

## Inhalt

1. [Funktionen](#funktionen)
2. [Rollen und Rechte](#rollen-und-rechte)
3. [Lokal starten – Schritt für Schritt](#lokal-starten--schritt-für-schritt)
4. [Demo-Zugänge](#demo-zugänge)
5. [Konfiguration](#konfiguration)
6. [REST-API](#rest-api)
7. [Tests](#tests)
8. [Versionen und Releases](#versionen-und-releases)
9. [Projektstruktur](#projektstruktur)
10. [Docker](#docker)
11. [Fehlerbehebung](#fehlerbehebung)

---

## Funktionen

- **Login mit JWT**: Benutzername und Passwort (BCrypt-gehasht) ergeben ein Token. Nach einer Passwortänderung werden alle alten Tokens ungültig.
- **Benutzerrollen**: Azubi, dualer Student, Ausbilder und Admin, jeweils mit eigenen Rechten (siehe unten).
- **Personenverwaltung**: Personen mit Login anlegen, bearbeiten, löschen und Passwörter zurücksetzen. Der Benutzername wird automatisch als `vorname.nachname` erzeugt.
- **Lernziele**: Hierarchie aus Richt-, Grob- und Feinlernzielen. Neue Lernziele werden automatisch allen Azubis zugewiesen. **CSV-Import und -Export** sind möglich.
- **Lernfortschritt**: Jedes Lernziel hat pro Azubi einen Status (*Offen*, *In Bearbeitung*, *Erreicht*). Wird ein Lernziel im Berichtsheft angegeben, wechselt es automatisch auf *In Bearbeitung*.
- **Berichtsheft**:
  - Tageseinträge mit Art (Betrieb, Berufsschule, Urlaub, Krank …), Stunden, Tätigkeiten und Lernzielen
  - Wochenansicht mit Kalenderwoche und Stundensumme
  - Workflow: **Entwurf → Eingereicht → Abgezeichnet** bzw. **Zur Überarbeitung** (mit Pflichtkommentar)
  - Eingereichte und abgezeichnete Wochen sind gesperrt.
- **Demo-Daten** beim ersten Start, damit man sofort loslegen kann.
- Einheitliche **Fehlermeldungen** als JSON (`status`, `message`, `timestamp`).

## Rollen und Rechte

| Aktion | Azubi / Student | Ausbilder | Admin |
|---|:---:|:---:|:---:|
| Eigenes Berichtsheft schreiben und einreichen | ✅ | – | – |
| Eigene Lernziele und eigenen Fortschritt sehen | ✅ | ✅ | ✅ |
| Berichtshefte aller Azubis lesen | – | ✅ | ✅ |
| Wochen abzeichnen oder zurückgeben | – | ✅ | ✅ |
| Lernziel-Status setzen | – | ✅ | ✅ |
| Lernziele anlegen, ändern, löschen, CSV | – | ✅ | ✅ |
| Azubis und Studierende verwalten | – | ✅ | ✅ |
| Ausbilder und Admins verwalten | – | – | ✅ |

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
4. In der Konsole erscheint `Started BerichtsheftApplication …` und darüber die **Demo-Logins**.

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

- <http://localhost:8080/v1/lernziel> sollte mit **401** und `{"status":401,"message":"Nicht angemeldet …"}` antworten. Das ist richtig, weil ohne Login nichts abrufbar ist.
- Die Datenbank-Oberfläche liegt unter <http://localhost:8080/h2-console>
  (JDBC-URL `jdbc:h2:file:./data/berichtsheft`, User `sa`, Passwort leer).

Anschließend das **Frontend** starten, siehe [Frontend-README](https://github.com/Gymbollek2/Berichtsheft_Frontend#lokal-starten--schritt-für-schritt).

### Datenbank zurücksetzen

Die Daten liegen in `./data/berichtsheft.mv.db`. Backend stoppen, den Ordner `data` löschen und neu starten. Danach werden die Demo-Daten neu angelegt.

## Demo-Zugänge

Werden beim **ersten Start mit leerer Datenbank** automatisch angelegt:

| Benutzername | Passwort | Rolle | Hinweis |
|---|---|---|---|
| `admin` | `admin123` | Admin | darf alles |
| `ausbilder` | `ausbilder123` | Ausbilder | eine Woche von Max wartet auf Prüfung |
| `azubi` | `azubi123` | Azubi (Max Mustermann) | mit Beispiel-Wochen |
| `azubi2` | `azubi123` | Azubi (Erika Musterfrau) | ohne Einträge |

> ⚠️ Nur für die lokale Entwicklung. Für einen echten Betrieb `DEMO_DATA=false` setzen, siehe unten.

---

## Konfiguration

Alle Werte stehen in `src/main/resources/application.properties` und lassen sich per **Umgebungsvariable** überschreiben:

| Variable | Standard | Bedeutung |
|---|---|---|
| `PORT` | `8080` | HTTP-Port |
| `DB_URL` | `jdbc:h2:file:./data/berichtsheft;AUTO_SERVER=TRUE` | JDBC-URL der Datenbank |
| `DB_USER` / `DB_PASSWORD` | `sa` / leer | Datenbank-Zugang |
| `JWT_SECRET` | Entwicklungsschlüssel | **In Produktion unbedingt setzen** (langer Zufallswert) |
| `JWT_EXPIRATION_HOURS` | `12` | Gültigkeit eines Logins |
| `CORS_ORIGINS` | `http://localhost:4200` | Erlaubte Frontend-Adressen (kommagetrennt) |
| `DEMO_DATA` | `true` | Demo-Daten bei leerer DB anlegen |

> **Hinweis:** Die frühere GitLab-OAuth-Anbindung (Client-ID/-Secret von `gitlab.fisp.dev`) und die LDAP-Reste wurden entfernt. Die Zugangsdaten standen im Klartext im Code und dürfen nicht in ein öffentliches Repository. Falls ihr den GitLab-Login wieder braucht, solltet ihr das alte Client-Secret in GitLab **neu erzeugen** und das neue nur per Umgebungsvariable übergeben.

---

## REST-API

Alle Endpunkte außer dem Login brauchen den Header `Authorization: Bearer <accessToken>`.

### Anmeldung – `/v1/passport`

| Methode | Pfad | Beschreibung |
|---|---|---|
| POST | `/login` | `{ "username", "password" }` → `{ accessToken, expiresAt, user }` |
| GET | `/me` | eigene Daten |
| POST | `/change-password` | `{ oldPassword, newPassword }` → neues Token |

### Personen – `/v1/person` (Ausbilder/Admin)

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
| GET | `/{id}` | ein Lernziel |
| POST / PUT / DELETE | `/` bzw. `/{id}` | verwalten (Ausbilder/Admin): `{ title, description?, kind, parentId? }` |
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
| GET | `/fortschritt` | Fortschritt aller Azubis (Ausbilder/Admin) |
| PUT | `/{id}/status` | `{ "status": "STATUS_DONE" }` (Ausbilder/Admin) |

### Berichtsheft – `/v1/berichtsheft`

| Methode | Pfad | Beschreibung |
|---|---|---|
| GET | `/woche?datum=2026-10-21&personId=` | Woche (Mo–So) mit allen Tagen |
| GET | `/historie?personId=` | alle Wochen einer Person |
| PUT | `/eintrag` | Tageseintrag speichern: `{ date, hours, art, description, lernzielIds }` |
| DELETE | `/eintrag/{id}` | Tageseintrag löschen |
| POST | `/woche/einreichen` | `{ "datum": "2026-10-21" }` |
| GET | `/offen` | eingereichte Wochen (Ausbilder/Admin) |
| POST | `/woche/{id}/abzeichnen` | `{ kommentar? }` (Ausbilder/Admin) |
| POST | `/woche/{id}/ablehnen` | `{ kommentar }`, Kommentar ist Pflicht (Ausbilder/Admin) |

---

## Tests

```bash
mvnw.cmd test        # Windows
./mvnw test          # macOS / Linux
```

- **Unit-Tests**: Lernziel-Arten, CSV-Parser, Kalenderwochen-Logik, Benutzernamen-Erzeugung
- **API-Integrationstest** (`BerichtsheftApiTest`): Er startet die komplette Anwendung mit In-Memory-Datenbank und prüft Login, Rechte, Personen anlegen, CSV und den kompletten Berichtsheft-Workflow (erfassen → einreichen → zurückgeben → nachbessern → abzeichnen).

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
├── config/        Security, JWT-Filter, Passwort-Encoder, Demo-Daten
├── controller/    REST-Endpunkte
├── dao/           Spring-Data-Repositories
├── dto/           Request-/Response-Objekte (Java Records)
├── entity/        JPA-Entities (Person, Users, Lernziel, PersonLernziel, Berichtsheft, Wochenbericht)
├── exceptions/    Fehlerklassen und globaler Exception-Handler
├── service/       Geschäftslogik
└── util/          Enums (Rollen, Lernziel-Arten, Status …)
```

## Docker

```bash
docker build -t berichtsheft-backend .
docker run -p 8080:8080 -e JWT_SECRET=bitte-aendern -v berichtsheft-data:/app/data berichtsheft-backend
```

## Fehlerbehebung

| Problem | Lösung |
|---|---|
| `release version 21 not supported` | Es läuft ein älteres JDK. JDK 21+ installieren und in IntelliJ als Projekt-SDK einstellen. |
| `Port 8080 already in use` | Anderes Programm beenden oder mit `PORT=8081` starten. Dann im Frontend `proxy.conf.json` anpassen. |
| Login klappt nicht mehr nach Code-Änderungen | Ordner `data` löschen, damit die Demo-Daten neu angelegt werden. |
| `mvnw` lädt nicht | IntelliJ verwenden (bringt Maven mit) oder Maven installieren und `mvn spring-boot:run` nutzen. |
