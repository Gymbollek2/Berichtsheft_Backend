package de.fisp.azubiprojekt.dto.response;

import de.fisp.azubiprojekt.entity.Berichtsheft;
import de.fisp.azubiprojekt.util.EintragArtEnum;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record BerichtsheftEintragDTO(
        Long id,
        LocalDate date,
        double hours,
        EintragArtEnum art,
        String artLabel,
        String description,
        List<LernzielResponseDTO> lernziele,
        LocalDateTime updatedAt) {

    public static BerichtsheftEintragDTO from(Berichtsheft b) {
        return new BerichtsheftEintragDTO(
                b.getId(),
                b.getDate(),
                b.getHours(),
                b.getArt(),
                b.getArt().getLabel(),
                b.getDescription(),
                b.getQualifications().stream().map(LernzielResponseDTO::from).toList(),
                b.getUpdatedAt());
    }
}
