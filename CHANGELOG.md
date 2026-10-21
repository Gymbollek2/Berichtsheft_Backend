# Changelog

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
