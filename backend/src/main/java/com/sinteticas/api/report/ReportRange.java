package com.sinteticas.api.report;

import com.sinteticas.api.common.config.BusinessTime;
import com.sinteticas.api.common.error.ApiException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Rango de un reporte: días locales (hora de Colombia) de {@code from} a {@code to}, ambos inclusive.
 */
public record ReportRange(LocalDate from, LocalDate to, Instant start, Instant end) {

    /** Máximo un año por consulta: suficiente para comparar meses y evita consultas enormes. */
    public static final int MAX_DAYS = 366;

    public static ReportRange of(LocalDate from, LocalDate to, BusinessTime time) {
        if (to.isBefore(from)) {
            throw ApiException.badRequest("INVALID_RANGE", "La fecha final debe ser igual o posterior a la inicial");
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days > MAX_DAYS) {
            throw ApiException.badRequest("RANGE_TOO_LARGE",
                    "El rango de un reporte no puede superar " + MAX_DAYS + " días");
        }
        return new ReportRange(from, to, time.atHour(from, 0), time.atHour(to, 24));
    }

    public List<LocalDate> dates() {
        return from.datesUntil(to.plusDays(1)).toList();
    }

}
