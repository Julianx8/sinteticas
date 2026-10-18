package com.sinteticas.api.common.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.ZoneId;
import java.util.List;

/**
 * Propiedades propias de la aplicación (prefijo {@code app.} en application.yml).
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotNull ZoneId zoneId,
        @NotNull Cors cors,
        @NotBlank String frontendUrl
) {

    public record Cors(@NotEmpty List<String> allowedOrigins) {
    }

}
