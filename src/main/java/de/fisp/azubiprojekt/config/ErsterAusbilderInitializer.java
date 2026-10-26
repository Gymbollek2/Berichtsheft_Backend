package de.fisp.azubiprojekt.config;

import de.fisp.azubiprojekt.dao.PersonDAO;
import de.fisp.azubiprojekt.dao.UserDAO;
import de.fisp.azubiprojekt.dto.request.ValidationPatterns;
import de.fisp.azubiprojekt.entity.Person;
import de.fisp.azubiprojekt.entity.Users;
import de.fisp.azubiprojekt.util.AnredeEnum;
import de.fisp.azubiprojekt.util.RoleEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * Legt auf einem Server ohne Demo-Daten den ersten Ausbilder-Zugang an.
 * <p>
 * Das Installationsskript schreibt dazu die Datei {@code data/erster-ausbilder.properties}
 * ({@code benutzername}, {@code passwort}, optional {@code vorname} und {@code nachname}).
 * Gibt es noch keinen Ausbilder, wird er angelegt. Die Datei wird danach in jedem Fall geloescht,
 * damit das Passwort nicht auf der Platte liegen bleibt. Das Passwort sollte nach der ersten
 * Anmeldung im Profil geaendert werden.
 */
@Component
@Order(1)
public class ErsterAusbilderInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ErsterAusbilderInitializer.class);

    private final PersonDAO personDAO;
    private final UserDAO userDAO;
    private final PasswordEncoder passwordEncoder;
    private final Path datei;

    public ErsterAusbilderInitializer(PersonDAO personDAO, UserDAO userDAO, PasswordEncoder passwordEncoder,
                                      @Value("${app.erster-ausbilder.datei:./data/erster-ausbilder.properties}") String datei) {
        this.personDAO = personDAO;
        this.userDAO = userDAO;
        this.passwordEncoder = passwordEncoder;
        this.datei = Path.of(datei).toAbsolutePath();
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        if (!Files.exists(datei)) {
            return;
        }
        try {
            anlegen(lesen());
        } finally {
            Files.deleteIfExists(datei);
        }
    }

    private Properties lesen() throws IOException {
        Properties props = new Properties();
        try (Reader reader = Files.newBufferedReader(datei, StandardCharsets.UTF_8)) {
            props.load(reader);
        }
        return props;
    }

    private void anlegen(Properties props) {
        if (!personDAO.findByRole(RoleEnum.ROLE_TRAINER).isEmpty()) {
            log.info("Es gibt bereits einen Ausbilder – {} wird ignoriert und gelöscht", datei.getFileName());
            return;
        }
        String benutzername = props.getProperty("benutzername", "").trim().toLowerCase(Locale.ROOT);
        String passwort = props.getProperty("passwort", "");
        if (!benutzername.matches(ValidationPatterns.USERNAME)) {
            log.error("Erster Ausbilder nicht angelegt: Benutzername {}", ValidationPatterns.USERNAME_MESSAGE);
            return;
        }
        if (!passwort.matches(ValidationPatterns.PASSWORD)) {
            log.error("Erster Ausbilder nicht angelegt: Passwort {}", ValidationPatterns.PASSWORD_MESSAGE);
            return;
        }
        if (userDAO.existsByUsernameIgnoreCase(benutzername)) {
            log.error("Erster Ausbilder nicht angelegt: Benutzername '{}' ist bereits vergeben", benutzername);
            return;
        }
        Person person = new Person(props.getProperty("vorname", "Ausbildung").trim(),
                props.getProperty("nachname", "Leitung").trim(), RoleEnum.ROLE_TRAINER, 0, null, null);
        person.setAnrede(AnredeEnum.vermuten(person.getFirstName()));
        person.setUsers(new Users(benutzername, passwordEncoder.encode(passwort), person));
        personDAO.save(person);
        log.info("Erster Ausbilder-Zugang '{}' angelegt – bitte das Passwort nach der ersten Anmeldung ändern", benutzername);
    }
}
