package de.fisp.azubiprojekt.dto.response;

import de.fisp.azubiprojekt.entity.PersonLernziel;
import de.fisp.azubiprojekt.util.StatusEnum;

import java.time.LocalDateTime;

/**
 * @param berechnet {@code true}, wenn das Lernziel untergeordnete Lernziele hat und sein Status
 *                  daher automatisch aus diesen berechnet wird (nicht direkt aenderbar)
 */
public record PersonLernzielResponseDTO(
        Long id,
        Long personId,
        LernzielResponseDTO lernziel,
        StatusEnum status,
        String statusLabel,
        boolean berechnet,
        LocalDateTime updatedAt) {

    public static PersonLernzielResponseDTO from(PersonLernziel pl, long anzahlUntergeordnet) {
        return new PersonLernzielResponseDTO(
                pl.getId(),
                pl.getPerson().getId(),
                LernzielResponseDTO.from(pl.getLernziel(), anzahlUntergeordnet),
                pl.getStatus(),
                pl.getStatus().getLabel(),
                anzahlUntergeordnet > 0,
                pl.getUpdatedAt());
    }
}
