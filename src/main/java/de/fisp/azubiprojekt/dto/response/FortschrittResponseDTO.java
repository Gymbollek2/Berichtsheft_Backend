package de.fisp.azubiprojekt.dto.response;

/** Lernzielfortschritt einer Person. {@code prozent} = erreicht / gesamt * 100 (gerundet). */
public record FortschrittResponseDTO(
        Long personId,
        String personName,
        long gesamt,
        long offen,
        long inBearbeitung,
        long erreicht,
        int prozent) {
}
