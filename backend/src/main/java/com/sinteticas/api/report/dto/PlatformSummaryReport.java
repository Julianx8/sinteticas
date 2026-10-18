package com.sinteticas.api.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Resumen de toda la plataforma para el SUPER_ADMIN.
 * Los conteos de usuarios, sedes y canchas activas son a hoy; el resto corresponde al rango.
 */
public record PlatformSummaryReport(
        LocalDate from,
        LocalDate to,
        long totalUsers,
        long newUsers,
        long activeVenues,
        long activeCourts,
        Metrics reservations,
        List<MonthMetrics> months,
        List<TopVenue> topVenues
) {

    /** {@code month} en formato YYYY-MM. Solo aparecen meses con reservas. */
    public record MonthMetrics(String month, long reservations, long cancelled, BigDecimal realizedRevenue,
                               BigDecimal projectedRevenue) {
    }

    public record TopVenue(UUID venueId, String venueName, long reservations, BigDecimal realizedRevenue) {
    }

}
