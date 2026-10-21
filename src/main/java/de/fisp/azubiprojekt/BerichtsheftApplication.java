package de.fisp.azubiprojekt;

import de.fisp.azubiprojekt.ssl.DevCertificate;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BerichtsheftApplication {

    public static void main(String[] args) {
        // Lokal: TLS-Zertifikat fuer https://localhost:8443 erzeugen, falls noch keins vorhanden ist
        DevCertificate.ensureDevKeystore();
        SpringApplication.run(BerichtsheftApplication.class, args);
    }
}
