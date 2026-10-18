package com.sinteticas.api.reservation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * {@code startAt} en ISO-8601 con zona y en punto, p. ej. {@code 2026-10-20T18:00:00-05:00}.
 */
public record CreateReservationRequest(
        @NotNull UUID courtId,
        @NotNull OffsetDateTime startAt,
        @NotNull @Min(1) Integer durationHours,
        @Size(max = 500) String notes
) {
}
