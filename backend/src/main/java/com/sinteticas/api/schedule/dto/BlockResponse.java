package com.sinteticas.api.schedule.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record BlockResponse(
        UUID id,
        UUID courtId,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        String reason
) {
}
