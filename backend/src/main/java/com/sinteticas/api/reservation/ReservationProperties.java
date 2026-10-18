package com.sinteticas.api.reservation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Reglas de negocio configurables de las reservas (prefijo {@code app.reservations}).
 */
@Validated
@ConfigurationProperties(prefix = "app.reservations")
public record ReservationProperties(
        @Min(1) @Max(24) int maxHours,
        @Min(1) int maxDaysAhead,
        @NotNull Duration customerCancellationNotice,
        @Min(1) int maxActivePerCustomer
) {
}
