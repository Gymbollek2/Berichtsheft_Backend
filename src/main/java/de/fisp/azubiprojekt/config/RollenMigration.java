package de.fisp.azubiprojekt.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Die Rolle "Administrator" gibt es nicht mehr – Ausbilder duerfen jetzt alles.
 * Bestehende Admins werden beim Start einmalig zu Ausbildern. Laeuft per SQL und vor allen
 * anderen Startaufgaben, weil {@code ROLE_ADMIN} nicht mehr in {@code RoleEnum} steht und Hibernate
 * solche Zeilen sonst nicht laden koennte. Alte Admin-Tokens werden dadurch ungueltig (Rolle im Token),
 * die Betroffenen melden sich einfach neu an.
 * <p>
 * H2 legt die Spalte als ENUM mit den damaligen Werten an. In einer neuen Datenbank fehlt {@code ROLE_ADMIN}
 * dort, ein direkter Vergleich waere ein Fehler – deshalb wird als Text verglichen.
 */
@Component
@Order(0)
public class RollenMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RollenMigration.class);

    private final JdbcTemplate jdbcTemplate;

    public RollenMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            int geaendert = jdbcTemplate.update(
                    "UPDATE person SET role = 'ROLE_TRAINER' WHERE CAST(role AS VARCHAR(50)) = 'ROLE_ADMIN'");
            if (geaendert > 0) {
                log.info("Rolle Administrator entfernt: {} Person(en) sind jetzt Ausbilder", geaendert);
            }
        } catch (DataAccessException e) {
            // Darf den Start nie verhindern – schlimmstenfalls muss ein alter Admin von Hand umgestellt werden
            log.warn("Umstellung alter Admin-Konten auf Ausbilder fehlgeschlagen: {}", e.getMessage());
        }
    }
}
