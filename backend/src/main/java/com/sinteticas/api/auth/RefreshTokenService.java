package com.sinteticas.api.auth;

import com.sinteticas.api.common.error.ApiException;
import com.sinteticas.api.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Emite, rota y revoca refresh tokens (ver V6__refresh_tokens.sql).
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository repository;
    private final Duration expiration;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repository, SecurityProperties properties, Clock clock) {
        this.repository = repository;
        this.expiration = properties.security().refresh().expiration();
        this.clock = clock;
    }

    /** Datos del cliente que inició la sesión, para auditoría. */
    public record ClientInfo(String ip, String userAgent) {

        public ClientInfo {
            userAgent = userAgent == null ? null : userAgent.substring(0, Math.min(255, userAgent.length()));
        }

    }

    /** Token en claro (se entrega una sola vez al cliente) y su vencimiento. */
    public record Issued(String value, Instant expiresAt) {
    }

    public record Rotation(User user, Issued token) {
    }

    public Duration expiration() {
        return expiration;
    }

    /** Nueva sesión (nueva familia) tras un login o registro. */
    @Transactional
    public Issued issue(User user, ClientInfo client) {
        return create(user, UUID.randomUUID(), client).issued();
    }

    /**
     * Cambia un refresh token válido por uno nuevo de la misma familia.
     * {@code noRollbackFor}: cuando se detecta reutilización o cuenta desactivada, la revocación de la familia
     * debe guardarse aunque la respuesta sea un error.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public Rotation rotate(String rawToken, ClientInfo client) {
        Instant now = clock.instant();
        RefreshToken current = repository.findByHashForUpdate(hash(rawToken))
                .orElseThrow(RefreshTokenService::invalidToken);

        if (current.isRevoked()) {
            // Un token ya usado vuelve a aparecer: alguien más lo tiene. Se cierra toda la sesión.
            repository.revokeFamily(current.getFamilyId(), now);
            log.warn("Reutilización de refresh token detectada para el usuario {}: sesión revocada",
                    current.getUser().getId());
            throw new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_REUSED",
                    "La sesión fue cerrada por seguridad. Inicia sesión de nuevo");
        }
        if (current.isExpired(now)) {
            throw invalidToken();
        }
        User user = current.getUser();
        if (!user.isEnabled()) {
            repository.revokeFamily(current.getFamilyId(), now);
            throw ApiException.forbidden("ACCOUNT_DISABLED", "La cuenta está desactivada");
        }

        Created next = create(user, current.getFamilyId(), client);
        current.rotateTo(next.entity().getId(), now);
        return new Rotation(user, next.issued());
    }

    /** Logout: cierra la sesión a la que pertenece el token. Si el token no existe no hace nada. */
    @Transactional
    public void revoke(String rawToken) {
        repository.findByHashForUpdate(hash(rawToken))
                .ifPresent(token -> repository.revokeFamily(token.getFamilyId(), clock.instant()));
    }

    /** Cierra todas las sesiones del usuario (cuenta desactivada, cambio de contraseña, "cerrar todo"). */
    @Transactional
    public void revokeAllForUser(UUID userId) {
        repository.revokeAllForUser(userId, clock.instant());
    }

    /** Una vez al día borra los tokens vencidos hace más de un día. */
    @Scheduled(fixedDelay = 24, timeUnit = TimeUnit.HOURS, initialDelay = 1)
    @Transactional
    public void purgeExpired() {
        int deleted = repository.deleteExpiredBefore(clock.instant().minus(Duration.ofDays(1)));
        if (deleted > 0) {
            log.info("Refresh tokens vencidos eliminados: {}", deleted);
        }
    }

    private record Created(RefreshToken entity, Issued issued) {
    }

    private Created create(User user, UUID familyId, ClientInfo client) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = clock.instant().plus(expiration);

        RefreshToken entity = repository.save(
                new RefreshToken(user, familyId, hash(raw), expiresAt, client.ip(), client.userAgent()));
        return new Created(entity, new Issued(raw, expiresAt));
    }

    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    private static ApiException invalidToken() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN",
                "La sesión no es válida o venció. Inicia sesión de nuevo");
    }

}
