package de.fisp.azubiprojekt.dto.response;

import de.fisp.azubiprojekt.util.BerichtStatusEnum;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Kurzfassung eines Wochenberichts fuer Listen (Pruefungs-Warteschlange, Historie). */
public record WochenberichtSummaryDTO(
        Long id,
        Long personId,
        String personName,
        LocalDate weekStart,
        int kalenderwoche,
        int jahr,
        BerichtStatusEnum status,
        String statusLabel,
        double gesamtStunden,
        int anzahlEintraege,
        LocalDateTime eingereichtAm,
        LocalDateTime geprueftAm) {
}
