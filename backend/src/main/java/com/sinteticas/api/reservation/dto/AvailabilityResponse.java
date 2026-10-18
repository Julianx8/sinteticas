package com.sinteticas.api.reservation.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Disponibilidad de una cancha en un día: una entrada por cada hora en que la cancha abre.
 */
public record AvailabilityResponse(
        UUID courtId,
        LocalDate date,
        List<Slot> slots
) {

    public enum SlotStatus {
        /** Se puede reservar. */
        AVAILABLE,
        /** Ya hay una reserva. */
        BOOKED,
        /** La sede bloqueó ese horario. */
        BLOCKED,
        /** La hora ya pasó. */
        PAST
    }

    public record Slot(
            int hour,
            OffsetDateTime startAt,
            OffsetDateTime endAt,
            BigDecimal price,
            SlotStatus status
    ) {
    }

}
