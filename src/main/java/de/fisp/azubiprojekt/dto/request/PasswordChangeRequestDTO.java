package de.fisp.azubiprojekt.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordChangeRequestDTO(
        @NotBlank String oldPassword,
        @NotBlank @Size(min = 8, message = "muss mindestens 8 Zeichen lang sein") String newPassword) {
}
