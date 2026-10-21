package de.fisp.azubiprojekt.dto.response;

import de.fisp.azubiprojekt.util.BerichtStatusEnum;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Vollstaendige Ansicht einer Kalenderwoche (Montag bis Sonntag) eines Azubis. */
public record WochenberichtResponseDTO(
        Long id,
        Long personId,
        String personName,
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
        List<WochenTagDTO> tage) {
}
