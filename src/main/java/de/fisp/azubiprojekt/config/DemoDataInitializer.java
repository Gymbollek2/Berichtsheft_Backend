package de.fisp.azubiprojekt.config;

import de.fisp.azubiprojekt.dao.BerichtsheftDAO;
import de.fisp.azubiprojekt.dao.LernzielDAO;
import de.fisp.azubiprojekt.dao.PersonDAO;
import de.fisp.azubiprojekt.dao.PersonLernzielDAO;
import de.fisp.azubiprojekt.dao.UserDAO;
import de.fisp.azubiprojekt.dao.WochenberichtDAO;
import de.fisp.azubiprojekt.entity.*;
import de.fisp.azubiprojekt.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Legt beim ersten Start (leere Datenbank) Demo-Daten an, damit die Anwendung sofort ausprobiert werden kann.
 * Abschalten mit {@code app.demo-data.enabled=false} bzw. Umgebungsvariable {@code DEMO_DATA=false}.
 */
@Component
@ConditionalOnProperty(name = "app.demo-data.enabled", havingValue = "true", matchIfMissing = true)
public class DemoDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);
    private static final String BERUF = "Fachinformatiker/in Anwendungsentwicklung";

    private final PersonDAO personDAO;
    private final UserDAO userDAO;
    private final LernzielDAO lernzielDAO;
    private final PersonLernzielDAO personLernzielDAO;
    private final BerichtsheftDAO berichtsheftDAO;
    private final WochenberichtDAO wochenberichtDAO;
    private final PasswordEncoder passwordEncoder;

    public DemoDataInitializer(PersonDAO personDAO, UserDAO userDAO, LernzielDAO lernzielDAO,
                               PersonLernzielDAO personLernzielDAO, BerichtsheftDAO berichtsheftDAO,
                               WochenberichtDAO wochenberichtDAO, PasswordEncoder passwordEncoder) {
        this.personDAO = personDAO;
        this.userDAO = userDAO;
        this.lernzielDAO = lernzielDAO;
        this.personLernzielDAO = personLernzielDAO;
        this.berichtsheftDAO = berichtsheftDAO;
        this.wochenberichtDAO = wochenberichtDAO;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userDAO.count() > 0) {
            return;
        }
        log.info("Leere Datenbank erkannt – lege Demo-Daten an");

        createPerson("Anna", "Admin", RoleEnum.ROLE_ADMIN, "admin", "admin123", null, 0, null);
        Person ausbilder = createPerson("Petra", "Pruefer", RoleEnum.ROLE_TRAINER, "ausbilder", "ausbilder123", null, 0, null);
        Person max = createPerson("Max", "Mustermann", RoleEnum.ROLE_TRAINEE, "azubi", "azubi123", BERUF, 2,
                LocalDate.now().minusYears(1).withMonth(9).withDayOfMonth(1));
        Person erika = createPerson("Erika", "Musterfrau", RoleEnum.ROLE_TRAINEE, "azubi2", "azubi123", BERUF, 1,
                LocalDate.now().withMonth(9).withDayOfMonth(1));

        // Lernziel-Hierarchie (Auszug Ausbildungsrahmenplan)
        Lernziel software = lz("Software entwickeln", "Anwendungen nach Kundenanforderungen planen, umsetzen und testen", KindEnum.KIND_GENERAL, null);
        Lernziel java = lz("Programmieren mit Java", "Grundlagen und fortgeschrittene Konzepte der Sprache Java", KindEnum.KIND_BROAD, software);
        Lernziel oop = lz("Objektorientierte Konzepte anwenden", "Klassen, Vererbung, Interfaces und Polymorphie sicher einsetzen", KindEnum.KIND_SPECIFIC, java);
        Lernziel junit = lz("Unit-Tests mit JUnit schreiben", "Testfaelle entwerfen und automatisiert ausfuehren", KindEnum.KIND_SPECIFIC, java);
        Lernziel web = lz("Webanwendungen entwickeln", "Client-Server-Architekturen verstehen und umsetzen", KindEnum.KIND_BROAD, software);
        Lernziel rest = lz("REST-Schnittstellen entwickeln", "APIs mit Spring Boot entwerfen und dokumentieren", KindEnum.KIND_SPECIFIC, web);
        Lernziel angular = lz("Frontend mit Angular umsetzen", "Komponenten, Services und Routing einsetzen", KindEnum.KIND_SPECIFIC, web);
        Lernziel betrieb = lz("Betrieb und Organisation", "Aufbau, Ablaeufe und Regeln des Ausbildungsbetriebs", KindEnum.KIND_GENERAL, null);
        Lernziel sicherheit = lz("IT-Sicherheit und Datenschutz", "Schutzbedarf erkennen und Massnahmen umsetzen", KindEnum.KIND_BROAD, betrieb);
        Lernziel dsgvo = lz("Datenschutz (DSGVO) beachten", "Personenbezogene Daten rechtskonform verarbeiten", KindEnum.KIND_SPECIFIC, sicherheit);
        Lernziel projekt = lz("Projektarbeit", "Arbeitsablaeufe planen und im Team umsetzen", KindEnum.KIND_BROAD, betrieb);
        Lernziel scrum = lz("Agile Methoden (Scrum) anwenden", "Sprints planen, Daily Scrum und Retrospektiven durchfuehren", KindEnum.KIND_SPECIFIC, projekt);

        List<Lernziel> alle = List.of(software, java, oop, junit, web, rest, angular, betrieb, sicherheit, dsgvo, projekt, scrum);
        for (Person azubi : List.of(max, erika)) {
            for (Lernziel l : alle) {
                personLernzielDAO.save(new PersonLernziel(azubi, l, StatusEnum.STATUS_UNFINISHED));
            }
        }
        setStatus(max, oop, StatusEnum.STATUS_DONE);
        setStatus(max, junit, StatusEnum.STATUS_DONE);
        setStatus(max, rest, StatusEnum.STATUS_IN_PROGRESS);
        setStatus(max, scrum, StatusEnum.STATUS_IN_PROGRESS);

        // Max: letzte Woche eingereicht (wartet auf Pruefung), vorletzte Woche abgezeichnet, aktuelle Woche Entwurf
        LocalDate dieseWoche = LocalDate.now().with(DayOfWeek.MONDAY);
        LocalDate letzteWoche = dieseWoche.minusWeeks(1);
        LocalDate vorletzteWoche = dieseWoche.minusWeeks(2);

        woche(max, vorletzteWoche, List.of(
                "Einarbeitung in das Azubi-Portal, Entwicklungsumgebung eingerichtet",
                "Java-Grundlagen wiederholt: Klassen, Interfaces, Vererbung",
                "Berufsschule: Netzwerktechnik und Wirtschaft",
                "Erste Unit-Tests mit JUnit 5 fuer den PersonService geschrieben",
                "Code-Review mit dem Team, Feedback eingearbeitet"), List.of(oop, junit));
        Wochenbericht abgezeichnet = new Wochenbericht(max, vorletzteWoche);
        abgezeichnet.setStatus(BerichtStatusEnum.ABGEZEICHNET);
        abgezeichnet.setEingereichtAm(LocalDateTime.now().minusDays(9));
        abgezeichnet.setGeprueftAm(LocalDateTime.now().minusDays(8));
        abgezeichnet.setGeprueftVon(ausbilder.getFullName());
        abgezeichnet.setKommentar("Sehr ausfuehrlich, weiter so!");
        wochenberichtDAO.save(abgezeichnet);

        woche(max, letzteWoche, List.of(
                "REST-Endpunkte fuer Lernziele mit Spring Boot umgesetzt",
                "Fehlerbehandlung und Validierung im Backend ergaenzt",
                "Berufsschule: Datenbanken (SQL, Normalisierung)",
                "Sprint Planning und Daily Scrum, Aufgaben geschaetzt",
                "Integrationstests fuer die API geschrieben"), List.of(rest, scrum));
        Wochenbericht eingereicht = new Wochenbericht(max, letzteWoche);
        eingereicht.setStatus(BerichtStatusEnum.EINGEREICHT);
        eingereicht.setEingereichtAm(LocalDateTime.now().minusDays(2));
        wochenberichtDAO.save(eingereicht);

        berichtsheftDAO.save(eintrag(max, dieseWoche, EintragArtEnum.BETRIEB, 8,
                "Angular-Komponente fuer die Wochenansicht begonnen", List.of(angular)));

        log.info("Demo-Logins: admin/admin123 (Admin), ausbilder/ausbilder123 (Ausbilder), azubi/azubi123 und azubi2/azubi123 (Azubis)");
    }

    private Person createPerson(String first, String last, RoleEnum role, String username, String password,
                                String beruf, int jahr, LocalDate start) {
        Person p = new Person(first, last, role, jahr, start, null);
        p.setBeruf(beruf);
        p.setUsers(new Users(username, passwordEncoder.encode(password), p));
        return personDAO.save(p);
    }

    private Lernziel lz(String title, String description, KindEnum kind, Lernziel parent) {
        return lernzielDAO.save(new Lernziel(title, description, kind, parent == null ? null : parent.getId()));
    }

    private void setStatus(Person person, Lernziel lernziel, StatusEnum status) {
        personLernzielDAO.findByPersonAndLernziel(person, lernziel).ifPresent(pl -> {
            pl.setStatus(status);
            personLernzielDAO.save(pl);
        });
    }

    private void woche(Person person, LocalDate montag, List<String> texte, List<Lernziel> lernziele) {
        for (int i = 0; i < texte.size(); i++) {
            EintragArtEnum art = texte.get(i).startsWith("Berufsschule") ? EintragArtEnum.SCHULE : EintragArtEnum.BETRIEB;
            berichtsheftDAO.save(eintrag(person, montag.plusDays(i), art, art == EintragArtEnum.SCHULE ? 6 : 8,
                    texte.get(i), art == EintragArtEnum.SCHULE ? List.of() : lernziele));
        }
    }

    private Berichtsheft eintrag(Person person, LocalDate date, EintragArtEnum art, double hours, String text, List<Lernziel> lernziele) {
        Berichtsheft b = new Berichtsheft(date, hours, person, text);
        b.setArt(art);
        b.setQualifications(lernziele);
        return b;
    }
}
