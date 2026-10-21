# Changelog

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
