package com.sinteticas.api.reservation.dto;

import jakarta.validation.constraints.Size;

public record CancelReservationRequest(
        @Size(max = 255) String reason
) {
}
