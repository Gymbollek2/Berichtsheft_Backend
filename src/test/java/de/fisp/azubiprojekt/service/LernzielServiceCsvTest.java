package de.fisp.azubiprojekt.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LernzielServiceCsvTest {

    @Test
    void splitsSimpleLine() {
        assertEquals(List.of("Titel", "Beschreibung", "Groblernziel", ""),
                LernzielService.splitCsvLine("Titel;Beschreibung;Groblernziel;"));
    }

    @Test
    void respectsQuotedFieldsWithSemicolonsAndQuotes() {
        assertEquals(List.of("A; B", "Er sagte \"Hallo\"", "Feinlernziel"),
                LernzielService.splitCsvLine("\"A; B\";\"Er sagte \"\"Hallo\"\"\";Feinlernziel"));
    }
}
