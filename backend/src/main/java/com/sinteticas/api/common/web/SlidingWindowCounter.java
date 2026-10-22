package com.sinteticas.api.common.web;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cuenta eventos por clave (email, IP…) dentro de una ventana de tiempo que se desliza.
 * Lo usan los límites de intentos (login, "olvidé mi contraseña").
 * <p>
 * Vive en memoria: sirve con una sola instancia del backend (nuestro caso).
 * Con varias instancias habría que llevarlo a Redis o a la base de datos.
 */
public class SlidingWindowCounter {

    private final Map<String, Deque<Instant>> events = new ConcurrentHashMap<>();
    private final Duration window;

    public SlidingWindowCounter(Duration window) {
        this.window = window;
    }

    /**
     * Si la clave ya llegó a {@code max} eventos en la ventana, devuelve cuánto falta para que se libere un cupo
     * (cuando caduque el evento más antiguo). Si no, vacío.
     */
    public Optional<Duration> retryAfterIfAtLimit(String key, int max, Instant now) {
        Deque<Instant> attempts = events.get(key);
        if (attempts == null) {
            return Optional.empty();
        }
        synchronized (attempts) {
            prune(attempts, now);
            if (attempts.size() < max) {
                return Optional.empty();
            }
            return Optional.of(Duration.between(now, attempts.peekFirst().plus(window)));
        }
    }

    public void record(String key, Instant now) {
        Deque<Instant> attempts = events.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (attempts) {
            prune(attempts, now);
            attempts.addLast(now);
        }
    }

    public void reset(String key) {
        events.remove(key);
    }

    /** Borra las claves sin eventos recientes, para que la memoria no crezca sin límite. */
    public void evictExpired(Instant now) {
        events.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                prune(entry.getValue(), now);
                return entry.getValue().isEmpty();
            }
        });
    }

    /** Minutos para mostrar al usuario, redondeados hacia arriba y como mínimo 1. */
    public static long minutes(Duration duration) {
        return Math.max(1, (duration.toSeconds() + 59) / 60);
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void prune(Deque<Instant> attempts, Instant now) {
        Instant limit = now.minus(window);
        while (!attempts.isEmpty() && !attempts.peekFirst().isAfter(limit)) {
            attempts.removeFirst();
        }
    }

}
