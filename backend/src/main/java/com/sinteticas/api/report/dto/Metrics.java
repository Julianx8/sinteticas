package com.sinteticas.api.report.dto;

import com.sinteticas.api.report.ReservationStats;

import java.math.BigDecimal;

/**
 * Bloque de métricas común a todos los reportes. Las tasas van de 0 a 1 con 4 decimales
 * (0.2500 = 25 %). Las fórmulas están documentadas en {@link ReservationStats}.
 * {@code availableHours} y {@code occupancyRate} se omiten cuando no aplican (resumen de plataforma).
 */
public record Metrics(
        long reservations,
        long confirmed,
        long completed,
        long cancelled,
        long noShow,
        long bookedHours,
        Long availableHours,
        BigDecimal occupancyRate,
        BigDecimal realizedRevenue,
        BigDecimal projectedRevenue,
        BigDecimal cancellationRate,
        BigDecimal noShowRate
) {

    public static Metrics from(ReservationStats stats) {
        return new Metrics(
                stats.total(),
                stats.confirmed(),
                stats.completed(),
                stats.cancelled(),
                stats.noShow(),
                stats.bookedHours(),
                stats.availableHours(),
                stats.occupancyRate(),
                stats.realizedRevenue(),
                stats.projectedRevenue(),
                stats.cancellationRate(),
                stats.noShowRate());
    }

}
