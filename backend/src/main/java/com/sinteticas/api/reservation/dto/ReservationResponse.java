package com.sinteticas.api.reservation.dto;

import com.sinteticas.api.common.config.BusinessTime;
import com.sinteticas.api.court.Court;
import com.sinteticas.api.reservation.Reservation;
import com.sinteticas.api.reservation.ReservationStatus;
import com.sinteticas.api.user.User;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Reserva con los datos que necesitan tanto el cliente (dónde y cuándo juega)
 * como la sede (quién reservó). Las fechas van en hora de Colombia.
 */
public record ReservationResponse(
        UUID id,
        ReservationStatus status,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        int durationHours,
        BigDecimal totalPrice,
        String notes,
        CourtInfo court,
        CustomerInfo customer,
        OffsetDateTime cancelledAt,
        String cancellationReason,
        OffsetDateTime createdAt
) {

    public record CourtInfo(UUID id, String name, UUID venueId, String venueName, String venueAddress) {
    }

    public record CustomerInfo(UUID id, String fullName, String email) {
    }

    /** Debe llamarse dentro de la transacción (accede a relaciones LAZY). */
    public static ReservationResponse from(Reservation reservation, BusinessTime time) {
        Court court = reservation.getCourt();
        User customer = reservation.getCustomer();
        return new ReservationResponse(
                reservation.getId(),
                reservation.getStatus(),
                time.toOffset(reservation.getStartAt()),
                time.toOffset(reservation.getEndAt()),
                reservation.durationHours(),
                reservation.getTotalPrice(),
                reservation.getNotes(),
                new CourtInfo(court.getId(), court.getName(), court.getVenue().getId(),
                        court.getVenue().getName(), court.getVenue().getAddress()),
                new CustomerInfo(customer.getId(), customer.getFullName(), customer.getEmail()),
                time.toOffset(reservation.getCancelledAt()),
                reservation.getCancellationReason(),
                time.toOffset(reservation.getCreatedAt()));
    }

}
