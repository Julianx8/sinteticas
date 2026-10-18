package com.sinteticas.api.court.dto;

import com.sinteticas.api.court.Court;
import com.sinteticas.api.court.CourtType;

import java.util.UUID;

public record CourtResponse(
        UUID id,
        UUID venueId,
        String venueName,
        String name,
        CourtType courtType,
        String description,
        boolean active
) {

    public static CourtResponse from(Court court) {
        return new CourtResponse(
                court.getId(),
                court.getVenue().getId(),
                court.getVenue().getName(),
                court.getName(),
                court.getCourtType(),
                court.getDescription(),
                court.isActive());
    }

}
