package com.sinteticas.api.report.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Demanda por día de la semana y hora (hora de Colombia): sirve para un mapa de calor
 * y para decidir precios por franja.
 *
 * @param cells     solo las combinaciones con al menos una hora reservada; dayOfWeek 1 = lunes … 7 = domingo
 * @param peakHours las celdas con más horas reservadas (máximo 5), de mayor a menor
 */
public record VenueDemandReport(
        UUID venueId,
        LocalDate from,
        LocalDate to,
        List<Cell> cells,
        List<Cell> peakHours
) {

    public record Cell(int dayOfWeek, int hour, long bookedHours) {
    }

}
