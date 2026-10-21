package de.fisp.azubiprojekt.dto.response;

import java.time.LocalDate;

/** Ein Tag innerhalb eines Wochenberichts. {@code eintrag} ist {@code null}, wenn noch nichts erfasst wurde. */
public record WochenTagDTO(LocalDate date, String wochentag, BerichtsheftEintragDTO eintrag) {
}
