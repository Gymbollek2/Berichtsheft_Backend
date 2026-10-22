package de.fisp.azubiprojekt.config;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Bestehende Datenbanken mit ENUM-Spalte (alte Werte) muessen neue Benachrichtigungstypen speichern koennen. */
class BenachrichtigungTypMigrationTest {

    @Test
    void stelltAlteEnumSpalteAufTextUm() {
        JdbcTemplate jdbc = new JdbcTemplate(
                new DriverManagerDataSource("jdbc:h2:mem:typ-migration;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE benachrichtigung (id BIGINT PRIMARY KEY, typ ENUM('BERICHT_ABGEGEBEN','WILLKOMMEN'))");

        // So scheitert eine Datenbank, die vor dem neuen Typ angelegt wurde
        assertThatThrownBy(() -> jdbc.update("INSERT INTO benachrichtigung VALUES (1, 'BERICHT_WIEDER_GEOEFFNET')"))
                .isInstanceOf(Exception.class);

        BenachrichtigungTypMigration migration = new BenachrichtigungTypMigration(jdbc);
        assertThat(migration.umstellen()).isTrue();
        jdbc.update("INSERT INTO benachrichtigung VALUES (1, 'BERICHT_WIEDER_GEOEFFNET')");
        assertThat(jdbc.queryForObject("SELECT typ FROM benachrichtigung WHERE id = 1", String.class))
                .isEqualTo("BERICHT_WIEDER_GEOEFFNET");

        // Beim naechsten Start passiert nichts mehr
        assertThat(migration.umstellen()).isFalse();
    }
}
