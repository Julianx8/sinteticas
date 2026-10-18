package com.sinteticas.api.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app")
public record SecurityProperties(
        @Valid @NotNull Security security,
        @Valid @NotNull Bootstrap bootstrap
) {

    public record Security(@Valid @NotNull Jwt jwt) {
    }

    public record Jwt(
            @NotBlank @Size(min = 32, message = "JWT_SECRET debe tener al menos 32 caracteres") String secret,
            @NotNull Duration expiration,
            @NotBlank String issuer
    ) {
    }

    public record Bootstrap(@NotNull SuperAdmin superAdmin) {
    }

    public record SuperAdmin(String email, String password, String fullName) {

        public boolean isConfigured() {
            return email != null && !email.isBlank() && password != null && !password.isBlank();
        }

    }

}
