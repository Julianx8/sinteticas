package com.sinteticas.api.court.dto;

import com.sinteticas.api.court.CourtType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CourtRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull CourtType courtType,
        @Size(max = 2000) String description,
        Boolean active
) {
}
