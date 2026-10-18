package com.sinteticas.api;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Reloj para tests: por defecto da la hora real, pero se puede fijar o adelantar
 * (p. ej. para probar cancelaciones tardías o marcar reservas que ya empezaron).
 * Cada test que lo modifique debe llamar a {@link #reset()} al final.
 */
public class MutableClock extends Clock {

    private final ZoneId zone;
    private volatile Instant fixed;

    public MutableClock(ZoneId zone) {
        this.zone = zone;
    }

    public void setInstant(Instant instant) {
        this.fixed = instant;
    }

    public void advance(Duration duration) {
        this.fixed = instant().plus(duration);
    }

    public void reset() {
        this.fixed = null;
    }

    @Override
    public Instant instant() {
        Instant current = fixed;
        return current != null ? current : Instant.now();
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return Clock.fixed(instant(), zone);
    }

}
