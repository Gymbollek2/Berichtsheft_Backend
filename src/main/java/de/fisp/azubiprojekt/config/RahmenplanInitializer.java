package de.fisp.azubiprojekt.config;

import de.fisp.azubiprojekt.dao.LernzielDAO;
import de.fisp.azubiprojekt.dto.response.CsvImportResponseDTO;
import de.fisp.azubiprojekt.service.LernzielService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

/**
 * Spielt beim Start einmalig die Richt- und Groblernziele des Ausbildungsrahmenplans
 * Fachinformatiker/in Anwendungsentwicklung (FIAusbV 2020) samt je einem Beispiel-Feinlernziel ein.
 * <p>
 * Quelle: {@code src/main/resources/lernziele/rahmenplan-fiae.csv} (gleiches Format wie der CSV-Import).
 * Laeuft nach den Demo-Daten, damit auch die Demo-Azubis alle Lernziele zugewiesen bekommen.
 * Ist das erste Richtlernziel schon vorhanden, passiert nichts – geloeschte oder umbenannte Lernziele
 * werden also nicht erneut angelegt. Abschalten mit {@code RAHMENPLAN_IMPORT=false}.
 */
@Component
@Order(3)
@ConditionalOnProperty(name = "app.rahmenplan-import.enabled", havingValue = "true", matchIfMissing = true)
public class RahmenplanInitializer implements ApplicationRunner {

    static final String CSV = "lernziele/rahmenplan-fiae.csv";
    /** Erstes Richtlernziel der Datei – daran erkennen wir, dass der Rahmenplan schon eingespielt wurde. */
    static final String MARKER = "A01 Planen, Vorbereiten und Durchführen von Arbeitsaufgaben in Abstimmung "
            + "mit den kundenspezifischen Geschäfts- und Leistungsprozessen";

    private static final Logger log = LoggerFactory.getLogger(RahmenplanInitializer.class);

    private final LernzielDAO lernzielDAO;
    private final LernzielService lernzielService;

    public RahmenplanInitializer(LernzielDAO lernzielDAO, LernzielService lernzielService) {
        this.lernzielDAO = lernzielDAO;
        this.lernzielService = lernzielService;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (lernzielDAO.findFirstByTitleIgnoreCase(MARKER).isPresent()) {
            return;
        }
        try (InputStream in = new ClassPathResource(CSV).getInputStream()) {
            CsvImportResponseDTO result = lernzielService.importCsv(in);
            log.info("Ausbildungsrahmenplan Fachinformatiker/in Anwendungsentwicklung eingespielt: {} Lernziele", result.importiert());
            result.fehler().forEach(f -> log.warn("Rahmenplan-Import: {}", f));
        }
    }
}
