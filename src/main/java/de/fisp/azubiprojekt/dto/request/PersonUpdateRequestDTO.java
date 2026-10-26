package de.fisp.azubiprojekt.dto.request;

import de.fisp.azubiprojekt.util.AnredeEnum;
import de.fisp.azubiprojekt.util.RoleEnum;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record PersonUpdateRequestDTO(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Size(max = 100) String middleName,
        AnredeEnum anrede,
        @NotNull RoleEnum role,
        @NotBlank @Pattern(regexp = ValidationPatterns.USERNAME, message = ValidationPatterns.USERNAME_MESSAGE) String username,
        @Size(max = 150) String beruf,
        LocalDate dateOfTrainingStart,
        @Past LocalDate dateOfBirth) {
}
