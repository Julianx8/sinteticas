package com.sinteticas.api.venue.dto;

import com.sinteticas.api.court.CourtType;
import com.sinteticas.api.venue.Venue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @param courtTypes      tipos de las canchas activas de la sede, ordenados (vacío si no tiene)
 * @param minPricePerHour precio por hora más bajo de sus horarios (null si ninguna cancha tiene horario)
 */
public record VenueResponse(
        UUID id,
        UUID ownerId,
        String name,
        String address,
        String neighborhood,
        String phone,
        String description,
        boolean active,
        Instant createdAt,
        List<CourtType> courtTypes,
        BigDecimal minPricePerHour
) {

    /** Resumen de las canchas de una sede, calculado por {@code VenueCatalog}. */
    public record CourtSummary(List<CourtType> courtTypes, BigDecimal minPricePerHour) {
        public static final CourtSummary EMPTY = new CourtSummary(List.of(), null);
    }

    public static VenueResponse from(Venue venue, CourtSummary summary) {
        return new VenueResponse(
                venue.getId(),
                venue.getOwnerId(),
                venue.getName(),
                venue.getAddress(),
                venue.getNeighborhood(),
                venue.getPhone(),
                venue.getDescription(),
                venue.isActive(),
                venue.getCreatedAt(),
                summary.courtTypes(),
                summary.minPricePerHour());
    }

}
