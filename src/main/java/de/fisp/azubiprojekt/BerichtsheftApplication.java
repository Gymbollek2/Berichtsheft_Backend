package de.fisp.azubiprojekt;

import de.fisp.azubiprojekt.ssl.DevCertificate;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
// Fuer die taeglichen Erinnerungen an nicht abgegebene Berichtshefte
@EnableScheduling
public class BerichtsheftApplication {

    public static void main(String[] args) {
        // Lokal: TLS-Zertifikat fuer https://localhost:5016 erzeugen, falls noch keins vorhanden ist
        DevCertificate.ensureDevKeystore();
        SpringApplication.run(BerichtsheftApplication.class, args);
    }
}
