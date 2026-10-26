package de.fisp.azubiprojekt.dto.request;

import de.fisp.azubiprojekt.util.RoleEnum;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

/**
 * Anlegen einer Person inkl. Login. Ist {@code username} leer, wird "vorname.nachname" erzeugt.
 * Das Ausbildungsjahr wird aus dem Ausbildungsbeginn berechnet.
 */
public record PersonCreateRequestDTO(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Size(max = 100) String middleName,
        @NotNull RoleEnum role,
        @Pattern(regexp = "^$|" + ValidationPatterns.USERNAME, message = ValidationPatterns.USERNAME_MESSAGE) String username,
        @NotBlank @Pattern(regexp = ValidationPatterns.PASSWORD, message = ValidationPatterns.PASSWORD_MESSAGE) String password,
        @Size(max = 150) String beruf,
        LocalDate dateOfTrainingStart,
        @Past LocalDate dateOfBirth) {
}
