package de.fisp.azubiprojekt.dto.request;

import de.fisp.azubiprojekt.util.StatusEnum;
import jakarta.validation.constraints.NotNull;

public record PersonLernzielStatusRequestDTO(@NotNull StatusEnum status) {
}
