package com.sinteticas.api.notification;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Correo saliente (prefijo {@code app.mail}). El servidor SMTP se configura en {@code spring.mail.*}.
 */
@Validated
@ConfigurationProperties(prefix = "app.mail")
public record MailProperties(
        boolean enabled,
        @NotBlank String from
) {
}
