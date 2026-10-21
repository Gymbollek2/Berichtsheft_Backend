package de.fisp.azubiprojekt.dto.request;

import de.fisp.azubiprojekt.util.RoleEnum;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

/**
 * Anlegen einer Person inkl. Login. Ist {@code username} leer, wird "vorname.nachname" erzeugt.
 */
public record PersonCreateRequestDTO(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotNull RoleEnum role,
        String username,
        @NotBlank @Size(min = 8, message = "muss mindestens 8 Zeichen lang sein") String password,
        String beruf,
        @Min(0) @Max(5) int yearOfTraining,
        LocalDate dateOfTrainingStart,
        @Past LocalDate dateOfBirth) {
}
