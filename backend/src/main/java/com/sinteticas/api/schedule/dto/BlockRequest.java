package com.sinteticas.api.schedule.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/**
 * Fechas en ISO-8601 con zona, p. ej. {@code 2026-12-24T00:00:00-05:00}.
 */
public record BlockRequest(
        @NotNull OffsetDateTime startAt,
        @NotNull OffsetDateTime endAt,
        @NotBlank @Size(max = 255) String reason
) {
}
