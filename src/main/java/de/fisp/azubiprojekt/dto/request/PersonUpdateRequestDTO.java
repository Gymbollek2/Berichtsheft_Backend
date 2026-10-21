package de.fisp.azubiprojekt.dto.request;

import de.fisp.azubiprojekt.util.RoleEnum;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record PersonUpdateRequestDTO(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotNull RoleEnum role,
        @NotBlank String username,
        String beruf,
        @Min(0) @Max(5) int yearOfTraining,
        LocalDate dateOfTrainingStart,
        @Past LocalDate dateOfBirth) {
}
