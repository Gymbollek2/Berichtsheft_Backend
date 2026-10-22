package de.fisp.azubiprojekt.dto.response;

import java.util.List;

/** Inhalt der Glocke: Anzahl ungelesener Nachrichten und die neuesten Nachrichten. */
public record BenachrichtigungenResponseDTO(long ungelesen, List<BenachrichtigungDTO> eintraege) {
}
