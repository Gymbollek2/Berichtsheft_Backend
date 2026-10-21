package de.fisp.azubiprojekt.dto.request;

import de.fisp.azubiprojekt.util.KindEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Anlegen oder Aendern eines Lernziels. */
public record LernzielRequestDTO(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 2000) String description,
        @NotNull KindEnum kind,
        Long parentId) {
}
