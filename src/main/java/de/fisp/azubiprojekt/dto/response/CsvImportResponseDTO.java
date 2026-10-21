package de.fisp.azubiprojekt.dto.response;

import java.util.List;

public record CsvImportResponseDTO(int importiert, List<String> fehler) {
}
