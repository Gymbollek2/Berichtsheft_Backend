package de.fisp.azubiprojekt.dto.response;

import java.time.Instant;

/** Antwort auf einen erfolgreichen Login. Das Token wird als "Authorization: Bearer &lt;token&gt;" mitgeschickt. */
public record LoginResponseDTO(String accessToken, Instant expiresAt, PersonResponseDTO user) {
}
