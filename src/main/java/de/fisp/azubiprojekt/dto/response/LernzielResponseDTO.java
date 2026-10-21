package de.fisp.azubiprojekt.dto.response;

import de.fisp.azubiprojekt.entity.Lernziel;
import de.fisp.azubiprojekt.util.KindEnum;

public record LernzielResponseDTO(
        Long id,
        String title,
        String description,
        KindEnum kind,
        String kindLabel,
        Long parentId) {

    public static LernzielResponseDTO from(Lernziel lernziel) {
        return new LernzielResponseDTO(
                lernziel.getId(),
                lernziel.getTitle(),
                lernziel.getDescription(),
                lernziel.getKind(),
                lernziel.getKind().getLabel(),
                lernziel.getParentId());
    }
}
