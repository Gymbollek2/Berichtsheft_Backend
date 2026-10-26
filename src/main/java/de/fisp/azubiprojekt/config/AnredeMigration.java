package de.fisp.azubiprojekt.config;

import de.fisp.azubiprojekt.dao.PersonDAO;
import de.fisp.azubiprojekt.entity.Person;
import de.fisp.azubiprojekt.util.AnredeEnum;
import de.fisp.azubiprojekt.util.Bezeichnung;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Im Portal wird nicht mehr gegendert. Beim Start wird einmalig nachgezogen, was davor angelegt wurde:
 * <ul>
 *     <li>Personen ohne Anrede bekommen eine – vermutet aus dem Vornamen ({@link AnredeEnum#vermuten}),
 *     jede Entscheidung steht im Log und laesst sich unter Personen korrigieren.</li>
 *     <li>Berufe passen zur Anrede: "Fachinformatiker/in …" wird zu "Fachinformatikerin …" bzw.
 *     "Fachinformatiker …", eine Frau mit Studiengang "Informatiker" bekommt "Informatikerin".</li>
 *     <li>In den Lernzielen des Rahmenplans wird aus "Kunden und Kundinnen" einfach "Kunden".</li>
 * </ul>
 * Laeuft nach dem Anlegen der Demo-Daten und dem Rahmenplan-Import, danach ist nichts mehr zu tun.
 */
@Component
@Order(4)
public class AnredeMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AnredeMigration.class);

    private final PersonDAO personDAO;
    private final JdbcTemplate jdbcTemplate;

    public AnredeMigration(PersonDAO personDAO, JdbcTemplate jdbcTemplate) {
        this.personDAO = personDAO;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int anreden = 0;
        int berufe = 0;
        for (Person person : personDAO.findAll()) {
            if (person.getAnrede() == null) {
                person.setAnrede(AnredeEnum.vermuten(person.getFirstName()));
                log.info("Anrede für {} aus dem Vornamen vermutet: {} (änderbar unter Personen)",
                        person.getFullName(), person.getAnrede().getLabel());
                anreden++;
            }
            String beruf = Bezeichnung.fuer(person.getBeruf(), person.getAnrede());
            if (beruf != null && !beruf.equals(person.getBeruf())) {
                person.setBeruf(beruf);
                berufe++;
            }
        }
        if (anreden + berufe > 0) {
            log.info("Ohne Gendern: {} Anrede(n) ergänzt, {} Beruf(e) angepasst", anreden, berufe);
        }
        lernzieleOhneGendern();
    }

    private void lernzieleOhneGendern() {
        try {
            int geaendert = jdbcTemplate.update("UPDATE lernziel SET "
                    + "title = REPLACE(title, 'Kunden und Kundinnen', 'Kunden'), "
                    + "description = REPLACE(description, 'Kunden und Kundinnen', 'Kunden') "
                    + "WHERE title LIKE '%Kundinnen%' OR description LIKE '%Kundinnen%'");
            if (geaendert > 0) {
                log.info("Ohne Gendern: {} Lernziel(e) angepasst (\"Kunden und Kundinnen\" -> \"Kunden\")", geaendert);
            }
        } catch (DataAccessException e) {
            // Darf den Start nie verhindern – schlimmstenfalls bleiben die alten Texte stehen
            log.warn("Lernziel-Texte konnten nicht angepasst werden: {}", e.getMessage());
        }
    }
}
