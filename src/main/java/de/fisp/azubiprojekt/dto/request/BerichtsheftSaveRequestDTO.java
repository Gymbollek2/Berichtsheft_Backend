package de.fisp.azubiprojekt.dto.request;

import de.fisp.azubiprojekt.util.EintragArtEnum;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;

/** Speichert (anlegen oder aktualisieren) den Tageseintrag des angemeldeten Azubis. */
public record BerichtsheftSaveRequestDTO(
        @NotNull LocalDate date,
        @DecimalMin("0.0") @DecimalMax("24.0") double hours,
        @NotNull EintragArtEnum art,
        @Size(max = 4000) String description,
        List<Long> lernzielIds) {
}
