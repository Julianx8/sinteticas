package com.sinteticas.api.report.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Resumen de una sede en un rango: totales y desglose por cancha.
 */
public record VenueSummaryReport(
        UUID venueId,
        String venueName,
        LocalDate from,
        LocalDate to,
        Metrics totals,
        List<CourtMetrics> courts
) {

    public record CourtMetrics(UUID courtId, String courtName, boolean active, Metrics metrics) {
    }

}
