package com.sinteticas.api.auth;

import com.sinteticas.api.common.config.AppProperties;
import com.sinteticas.api.common.error.ApiException;
import com.sinteticas.api.notification.MailService;
import com.sinteticas.api.user.User;
import com.sinteticas.api.user.UserRepository;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * "Olvidé mi contraseña": envía un enlace de un solo uso que vence en 1 hora.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    @Validated
    @ConfigurationProperties(prefix = "app.password-reset")
    public record Properties(@NotNull Duration expiration, @Min(1) int maxRequestsPerHour) {
    }

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final MailService mailService;
    private final Properties properties;
    private final String frontendUrl;
    private final Clock clock;

    public PasswordResetService(PasswordResetTokenRepository tokenRepository, UserRepository userRepository,
                                PasswordEncoder passwordEncoder, RefreshTokenService refreshTokenService,
                                MailService mailService, Properties properties, AppProperties appProperties,
                                Clock clock) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.mailService = mailService;
        this.properties = properties;
        this.frontendUrl = appProperties.frontendUrl();
        this.clock = clock;
    }

    /**
     * Siempre termina sin error, exista o no el email: así nadie puede averiguar qué emails están registrados.
     * Solo se envía el correo si la cuenta existe, está activa y no superó el límite de solicitudes por hora.
     */
    @Transactional
    public void requestReset(String email) {
        User user = userRepository.findByEmail(email.trim()).filter(User::isEnabled).orElse(null);
        if (user == null) {
            return;
        }
        Instant now = clock.instant();
        if (tokenRepository.countCreatedSince(user.getId(), now.minus(Duration.ofHours(1)))
                >= properties.maxRequestsPerHour()) {
            log.info("Límite de solicitudes de restablecimiento alcanzado para {}", user.getEmail());
            return;
        }

        tokenRepository.invalidateAllForUser(user.getId(), now);
        String token = SecureTokens.generate();
        tokenRepository.save(new PasswordResetToken(user, SecureTokens.sha256(token), now.plus(properties.expiration())));

        String link = UriComponentsBuilder.fromUriString(frontendUrl)
                .path("/restablecer-contrasena")
                .queryParam("token", token)
                .toUriString();
        mailService.send(user.getEmail(), "Restablece tu contraseña de Sintéticas",
                "Hola " + user.getFullName() + ",\n\n"
                        + "Recibimos una solicitud para restablecer tu contraseña. Abre este enlace para elegir una nueva:\n\n"
                        + link + "\n\n"
                        + "El enlace sirve una sola vez y vence en " + properties.expiration().toMinutes() + " minutos.\n"
                        + "Si no fuiste tú, ignora este correo: tu contraseña no cambia.\n\n"
                        + "— Sintéticas");
    }

    /** Cambia la contraseña con el token del correo y cierra todas las sesiones abiertas. */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        Instant now = clock.instant();
        PasswordResetToken resetToken = tokenRepository.findByHashForUpdate(SecureTokens.sha256(token))
                .filter(t -> t.isUsable(now))
                .orElseThrow(() -> ApiException.badRequest("INVALID_RESET_TOKEN",
                        "El enlace no es válido o ya venció. Solicita uno nuevo"));

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        resetToken.markUsed(now);
        refreshTokenService.revokeAllForUser(user.getId());
    }

}
