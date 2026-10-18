package com.sinteticas.api.report.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Serie diaria de una sede: un punto por cada día del rango, también los días sin reservas
 * (así el frontend puede graficar directamente sin rellenar huecos).
 */
public record VenueDailyReport(
        UUID venueId,
        LocalDate from,
        LocalDate to,
        List<DayMetrics> days
) {

    public record DayMetrics(LocalDate date, Metrics metrics) {
    }

}
