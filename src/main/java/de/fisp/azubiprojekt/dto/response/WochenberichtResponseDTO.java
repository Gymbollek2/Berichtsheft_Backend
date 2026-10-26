package de.fisp.azubiprojekt.dto.response;

import de.fisp.azubiprojekt.util.BerichtStatusEnum;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Vollstaendige Ansicht einer Kalenderwoche (Montag bis Sonntag) eines Azubis.
 * {@code eintraegeSichtbar} ist false, wenn ein Ausbilder eine noch nicht abgegebene Woche oeffnet –
 * dann sind {@code tage} ohne Eintraege und {@code gesamtStunden} ist 0.
 */
public record WochenberichtResponseDTO(
        Long id,
        Long personId,
        String personName,
        /** Rolle passend zur Anrede, z. B. "Auszubildende" – fuer die Unterschriftszeile im Ausdruck */
        String rolle,
        String beruf,
        int ausbildungsjahr,
        LocalDate weekStart,
        LocalDate weekEnd,
        int kalenderwoche,
        int jahr,
        BerichtStatusEnum status,
        String statusLabel,
        boolean editierbar,
        String kommentar,
        String geprueftVon,
        LocalDateTime eingereichtAm,
        LocalDateTime geprueftAm,
        double gesamtStunden,
        boolean eintraegeSichtbar,
        List<WochenTagDTO> tage) {
}
