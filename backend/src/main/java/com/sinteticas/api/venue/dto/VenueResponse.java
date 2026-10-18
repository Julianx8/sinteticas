package com.sinteticas.api.venue.dto;

import com.sinteticas.api.venue.Venue;

import java.time.Instant;
import java.util.UUID;

public record VenueResponse(
        UUID id,
        UUID ownerId,
        String name,
        String address,
        String neighborhood,
        String phone,
        String description,
        boolean active,
        Instant createdAt
) {

    public static VenueResponse from(Venue venue) {
        return new VenueResponse(
                venue.getId(),
                venue.getOwnerId(),
                venue.getName(),
                venue.getAddress(),
                venue.getNeighborhood(),
                venue.getPhone(),
                venue.getDescription(),
                venue.isActive(),
                venue.getCreatedAt());
    }

}
