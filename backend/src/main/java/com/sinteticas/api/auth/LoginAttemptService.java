package com.sinteticas.api.auth;

import com.sinteticas.api.common.error.RateLimitedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Frena a quien intenta adivinar contraseñas: cuenta los intentos FALLIDOS de login en una ventana de tiempo,
 * por email y por IP. Al superar el límite responde 429 hasta que la ventana se libere.
 * <ul>
 *   <li>Por email: protege una cuenta concreta aunque el atacante cambie de IP.</li>
 *   <li>Por IP: frena a quien prueba muchos emails distintos desde el mismo lugar.</li>
 * </ul>
 * Un login correcto borra los fallos de ese email.
 * <p>
 * Los contadores viven en memoria: sirve con una sola instancia del backend (nuestro caso).
 * Con varias instancias habría que llevarlos a Redis o a la base de datos.
 */
@Service
public class LoginAttemptService {

    private final Map<String, Deque<Instant>> failuresByEmail = new ConcurrentHashMap<>();
    private final Map<String, Deque<Instant>> failuresByIp = new ConcurrentHashMap<>();
    private final SecurityProperties.LoginThrottle config;
    private final Clock clock;

    public LoginAttemptService(SecurityProperties properties, Clock clock) {
        this.config = properties.security().loginThrottle();
        this.clock = clock;
    }

    /** Lanza 429 si el email o la IP superaron el límite de fallos. Se llama ANTES de verificar la contraseña. */
    public void checkAllowed(String email, String ip) {
        Instant now = clock.instant();
        check(failuresByEmail, normalize(email), config.maxFailuresPerEmail(), now);
        check(failuresByIp, ip, config.maxFailuresPerIp(), now);
    }

    public void recordFailure(String email, String ip) {
        Instant now = clock.instant();
        record(failuresByEmail, normalize(email), now);
        record(failuresByIp, ip, now);
    }

    public void recordSuccess(String email) {
        failuresByEmail.remove(normalize(email));
    }

    /** Cada 10 minutos borra los contadores sin fallos recientes, para que la memoria no crezca sin límite. */
    @Scheduled(fixedDelay = 10, timeUnit = TimeUnit.MINUTES)
    public void evictExpired() {
        Instant now = clock.instant();
        for (Map<String, Deque<Instant>> failures : List.of(failuresByEmail, failuresByIp)) {
            failures.entrySet().removeIf(entry -> {
                synchronized (entry.getValue()) {
                    prune(entry.getValue(), now);
                    return entry.getValue().isEmpty();
                }
            });
        }
    }

    private void check(Map<String, Deque<Instant>> failures, String key, int max, Instant now) {
        Deque<Instant> attempts = failures.get(key);
        if (attempts == null) {
            return;
        }
        synchronized (attempts) {
            prune(attempts, now);
            if (attempts.size() >= max) {
                // Se libera cuando el fallo más antiguo de la ventana caduca
                Duration retryAfter = Duration.between(now, attempts.peekFirst().plus(config.window()));
                throw new RateLimitedException("TOO_MANY_LOGIN_ATTEMPTS",
                        "Demasiados intentos fallidos. Intenta de nuevo en " + minutes(retryAfter) + " minuto(s)",
                        retryAfter);
            }
        }
    }

    private void record(Map<String, Deque<Instant>> failures, String key, Instant now) {
        Deque<Instant> attempts = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (attempts) {
            prune(attempts, now);
            attempts.addLast(now);
        }
    }

    private void prune(Deque<Instant> attempts, Instant now) {
        Instant limit = now.minus(config.window());
        while (!attempts.isEmpty() && !attempts.peekFirst().isAfter(limit)) {
            attempts.removeFirst();
        }
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static long minutes(Duration duration) {
        return Math.max(1, (duration.toSeconds() + 59) / 60);
    }

}
