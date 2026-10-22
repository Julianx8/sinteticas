package com.sinteticas.api.auth;

import com.sinteticas.api.common.error.RateLimitedException;
import com.sinteticas.api.common.web.SlidingWindowCounter;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static com.sinteticas.api.common.web.SlidingWindowCounter.normalizeEmail;

/**
 * Frena a quien intenta adivinar contraseñas: cuenta los intentos FALLIDOS de login en una ventana de tiempo,
 * por email y por IP. Al superar el límite responde 429 hasta que la ventana se libere.
 * <ul>
 *   <li>Por email: protege una cuenta concreta aunque el atacante cambie de IP.</li>
 *   <li>Por IP: frena a quien prueba muchos emails distintos desde el mismo lugar.</li>
 * </ul>
 * Un login correcto borra los fallos de ese email.
 */
@Service
public class LoginAttemptService {

    private final SlidingWindowCounter failuresByEmail;
    private final SlidingWindowCounter failuresByIp;
    private final SecurityProperties.LoginThrottle config;
    private final Clock clock;

    public LoginAttemptService(SecurityProperties properties, Clock clock) {
        this.config = properties.security().loginThrottle();
        this.clock = clock;
        this.failuresByEmail = new SlidingWindowCounter(config.window());
        this.failuresByIp = new SlidingWindowCounter(config.window());
    }

    /** Lanza 429 si el email o la IP superaron el límite de fallos. Se llama ANTES de verificar la contraseña. */
    public void checkAllowed(String email, String ip) {
        Instant now = clock.instant();
        failuresByEmail.retryAfterIfAtLimit(normalizeEmail(email), config.maxFailuresPerEmail(), now)
                .or(() -> failuresByIp.retryAfterIfAtLimit(ip, config.maxFailuresPerIp(), now))
                .ifPresent(LoginAttemptService::reject);
    }

    public void recordFailure(String email, String ip) {
        Instant now = clock.instant();
        failuresByEmail.record(normalizeEmail(email), now);
        failuresByIp.record(ip, now);
    }

    public void recordSuccess(String email) {
        failuresByEmail.reset(normalizeEmail(email));
    }

    /** Cada 10 minutos borra los contadores sin fallos recientes. */
    @Scheduled(fixedDelay = 10, timeUnit = TimeUnit.MINUTES)
    public void evictExpired() {
        Instant now = clock.instant();
        failuresByEmail.evictExpired(now);
        failuresByIp.evictExpired(now);
    }

    private static void reject(Duration retryAfter) {
        throw new RateLimitedException("TOO_MANY_LOGIN_ATTEMPTS",
                "Demasiados intentos fallidos. Intenta de nuevo en "
                        + SlidingWindowCounter.minutes(retryAfter) + " minuto(s)",
                retryAfter);
    }

}
