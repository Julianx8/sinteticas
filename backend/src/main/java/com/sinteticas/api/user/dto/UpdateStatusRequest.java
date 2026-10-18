package com.sinteticas.api.user.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(
        @NotNull Boolean enabled
) {
}
