package com.sinteticas.api.common.config;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Hora "del negocio": los horarios de las canchas se definen en hora local de Colombia,
 * pero en la base de datos todo se guarda como instantes UTC (TIMESTAMPTZ).
 * Centraliza las conversiones para que nadie use la zona horaria del servidor por accidente.
 */
@Component
public class BusinessTime {

    private final Clock clock;
    private final ZoneId zone;

    public BusinessTime(Clock clock, AppProperties appProperties) {
        this.clock = clock;
        this.zone = appProperties.zoneId();
    }

    public Instant now() {
        return clock.instant();
    }

    public LocalDate today() {
        return clock.instant().atZone(zone).toLocalDate();
    }

    public ZoneId zone() {
        return zone;
    }

    public ZonedDateTime toZoned(Instant instant) {
        return instant.atZone(zone);
    }

    /** Para las respuestas JSON: "2026-10-20T18:00:00-05:00" es más claro para el frontend que UTC. */
    public OffsetDateTime toOffset(Instant instant) {
        return instant == null ? null : instant.atZone(zone).toOffsetDateTime();
    }

    /** Instante en que empieza la hora {@code hour} (0-24) del día {@code date} en hora local. */
    public Instant atHour(LocalDate date, int hour) {
        return date.atStartOfDay(zone).plusHours(hour).toInstant();
    }

}
