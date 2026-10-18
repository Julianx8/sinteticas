package com.sinteticas.api.schedule.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * Franja horaria: {@code dayOfWeek} 1 = lunes ... 7 = domingo; horas enteras,
 * {@code endHour} exclusiva (24 = medianoche).
 */
public record ScheduleSlotRequest(
        @NotNull @Min(1) @Max(7) Integer dayOfWeek,
        @NotNull @Min(0) @Max(23) Integer startHour,
        @NotNull @Min(1) @Max(24) Integer endHour,
        @NotNull @PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal pricePerHour
) {
}
