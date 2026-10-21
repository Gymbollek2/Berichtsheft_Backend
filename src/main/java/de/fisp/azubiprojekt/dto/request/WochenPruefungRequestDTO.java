package de.fisp.azubiprojekt.dto.request;

import jakarta.validation.constraints.Size;

/** Kommentar des Ausbilders beim Abzeichnen (optional) oder Zurueckgeben (Pflicht). */
public record WochenPruefungRequestDTO(@Size(max = 2000) String kommentar) {
}
