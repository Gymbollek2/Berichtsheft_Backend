package de.fisp.azubiprojekt.dto.response;

import de.fisp.azubiprojekt.entity.PersonLernziel;
import de.fisp.azubiprojekt.util.StatusEnum;

import java.time.LocalDateTime;

public record PersonLernzielResponseDTO(
        Long id,
        Long personId,
        LernzielResponseDTO lernziel,
        StatusEnum status,
        String statusLabel,
        LocalDateTime updatedAt) {

    public static PersonLernzielResponseDTO from(PersonLernziel pl) {
        return new PersonLernzielResponseDTO(
                pl.getId(),
                pl.getPerson().getId(),
                LernzielResponseDTO.from(pl.getLernziel()),
                pl.getStatus(),
                pl.getStatus().getLabel(),
                pl.getUpdatedAt());
    }
}
