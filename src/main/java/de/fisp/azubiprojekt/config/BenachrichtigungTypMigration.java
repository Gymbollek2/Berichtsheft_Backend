package de.fisp.azubiprojekt.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * H2 legt {@code benachrichtigung.typ} als ENUM mit den Werten an, die es beim Anlegen der Tabelle gab. Neue
 * Nachrichtentypen (z. B. {@code BERICHT_WIEDER_GEOEFFNET}) koennten dann nicht gespeichert werden, und
 * {@code ddl-auto=update} aendert bestehende Spalten nicht. Deshalb wird die Spalte beim Start einmalig auf Text
 * umgestellt; der Typ wird ohnehin als Name gespeichert. Danach passiert hier nichts mehr.
 */
@Component
@Order(1)
public class BenachrichtigungTypMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BenachrichtigungTypMigration.class);

    private final JdbcTemplate jdbcTemplate;

    public BenachrichtigungTypMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        umstellen();
    }

    /** true, wenn die Spalte umgestellt wurde. */
    boolean umstellen() {
        try {
            List<String> typ = jdbcTemplate.queryForList(
                    "SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS "
                            + "WHERE UPPER(TABLE_NAME) = 'BENACHRICHTIGUNG' AND UPPER(COLUMN_NAME) = 'TYP'",
                    String.class);
            if (typ.isEmpty() || !"ENUM".equalsIgnoreCase(typ.get(0))) {
                return false;
            }
            jdbcTemplate.execute("ALTER TABLE benachrichtigung ALTER COLUMN typ SET DATA TYPE VARCHAR(40)");
            log.info("Spalte benachrichtigung.typ auf Text umgestellt (fuer neue Benachrichtigungstypen)");
            return true;
        } catch (DataAccessException e) {
            // Darf den Start nie verhindern – schlimmstenfalls lassen sich neue Nachrichtentypen nicht speichern
            log.warn("Umstellung von benachrichtigung.typ fehlgeschlagen: {}", e.getMessage());
            return false;
        }
    }
}
