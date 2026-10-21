package de.fisp.azubiprojekt.dto.request;

import jakarta.validation.constraints.NotBlank;

public record LoginRequestDTO(@NotBlank String username, @NotBlank String password) {
}
