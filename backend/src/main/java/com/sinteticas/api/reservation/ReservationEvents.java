package com.sinteticas.api.reservation;

import com.sinteticas.api.reservation.dto.ReservationResponse;

import java.util.UUID;

/**
 * Eventos que publica {@link ReservationService}. Llevan una copia de los datos (no entidades JPA)
 * porque se procesan después de la transacción, en otro hilo.
 */
public final class ReservationEvents {

    private ReservationEvents() {
    }

    public record Created(ReservationResponse reservation, UUID venueOwnerId) {
    }

    /** {@code byVenue} = la canceló la sede (si no, el cliente). */
    public record Cancelled(ReservationResponse reservation, UUID venueOwnerId, boolean byVenue) {
    }

}
