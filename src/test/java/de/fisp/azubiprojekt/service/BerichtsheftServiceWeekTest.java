package de.fisp.azubiprojekt.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BerichtsheftServiceWeekTest {

    @Test
    void weekStartIsAlwaysMonday() {
        assertEquals(LocalDate.of(2026, 10, 19), BerichtsheftService.weekStart(LocalDate.of(2026, 10, 21)));
        assertEquals(LocalDate.of(2026, 10, 19), BerichtsheftService.weekStart(LocalDate.of(2026, 10, 25)));
        assertEquals(LocalDate.of(2026, 10, 19), BerichtsheftService.weekStart(LocalDate.of(2026, 10, 19)));
    }

    @Test
    void isoCalendarWeekAcrossYearBoundary() {
        // 29.12.2025 (Montag) gehoert zur KW 1 des Jahres 2026
        LocalDate montag = LocalDate.of(2025, 12, 29);
        assertEquals(1, BerichtsheftService.kalenderwoche(montag));
        assertEquals(2026, BerichtsheftService.wochenJahr(montag));
    }
}
