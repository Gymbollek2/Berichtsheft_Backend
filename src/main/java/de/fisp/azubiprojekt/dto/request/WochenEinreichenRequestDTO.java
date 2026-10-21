package de.fisp.azubiprojekt.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** Ein beliebiges Datum innerhalb der einzureichenden Woche. */
public record WochenEinreichenRequestDTO(@NotNull LocalDate datum) {
}
