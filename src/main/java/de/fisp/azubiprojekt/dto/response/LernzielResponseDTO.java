package de.fisp.azubiprojekt.dto.response;

import de.fisp.azubiprojekt.entity.Lernziel;
import de.fisp.azubiprojekt.util.KindEnum;

/**
 * @param anzahlUntergeordnet Anzahl direkt untergeordneter Lernziele (0 = Blatt der Hierarchie)
 */
public record LernzielResponseDTO(
        Long id,
        String title,
        String description,
        KindEnum kind,
        String kindLabel,
        Long parentId,
        long anzahlUntergeordnet) {

    public static LernzielResponseDTO from(Lernziel lernziel) {
        return from(lernziel, 0);
    }

    public static LernzielResponseDTO from(Lernziel lernziel, long anzahlUntergeordnet) {
        return new LernzielResponseDTO(
                lernziel.getId(),
                lernziel.getTitle(),
                lernziel.getDescription(),
                lernziel.getKind(),
                lernziel.getKind().getLabel(),
                lernziel.getParentId(),
                anzahlUntergeordnet);
    }
}
