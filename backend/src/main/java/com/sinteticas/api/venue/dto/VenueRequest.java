package com.sinteticas.api.venue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Datos para crear o actualizar una sede. {@code active} es opcional (por defecto true al crear).
 */
public record VenueRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 255) String address,
        @Size(max = 100) String neighborhood,
        @Size(max = 30) @Pattern(regexp = "^[0-9 +()-]*$", message = "Teléfono inválido") String phone,
        @Size(max = 2000) String description,
        Boolean active
) {
}
