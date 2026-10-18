package com.sinteticas.api.report;

import com.sinteticas.api.reservation.ReservationStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Acumula reservas agregadas (por estado) y calcula las métricas derivadas.
 * Todas las fórmulas de los reportes viven aquí, para que sean las mismas en cualquier vista.
 * <ul>
 *   <li>Horas reservadas = CONFIRMED + COMPLETED + NO_SHOW (las canceladas no ocupan la cancha).</li>
 *   <li>Ocupación = horas reservadas ÷ horas disponibles (horario de la cancha menos bloqueos).</li>
 *   <li>Ingreso realizado = COMPLETED · ingreso proyectado = CONFIRMED (aún por jugar).
 *       NO_SHOW no suma: el pago es en la sede.</li>
 *   <li>Tasa de cancelación = CANCELLED ÷ todas las reservas.</li>
 *   <li>Tasa de inasistencia = NO_SHOW ÷ (COMPLETED + NO_SHOW), es decir, sobre las que ya tienen resultado.</li>
 * </ul>
 */
public class ReservationStats {

    private long confirmed;
    private long completed;
    private long cancelled;
    private long noShow;
    private BigDecimal bookedHours = BigDecimal.ZERO;
    private BigDecimal realizedRevenue = BigDecimal.ZERO;
    private BigDecimal projectedRevenue = BigDecimal.ZERO;
    /** null = no se calculó (p. ej. el resumen de plataforma): la ocupación tampoco se calcula. */
    private Long availableHours;

    public void add(String status, long reservations, BigDecimal hours, BigDecimal amount) {
        add(ReservationStatus.valueOf(status), reservations, hours, amount);
    }

    public void add(ReservationStatus status, long reservations, BigDecimal hours, BigDecimal amount) {
        switch (status) {
            case CONFIRMED -> {
                confirmed += reservations;
                projectedRevenue = projectedRevenue.add(amount);
            }
            case COMPLETED -> {
                completed += reservations;
                realizedRevenue = realizedRevenue.add(amount);
            }
            case CANCELLED -> cancelled += reservations;
            case NO_SHOW -> noShow += reservations;
        }
        if (status != ReservationStatus.CANCELLED) {
            bookedHours = bookedHours.add(hours);
        }
    }

    public void addAvailableHours(long hours) {
        availableHours = (availableHours == null ? 0 : availableHours) + hours;
    }

    public long total() {
        return confirmed + completed + cancelled + noShow;
    }

    public long confirmed() {
        return confirmed;
    }

    public long completed() {
        return completed;
    }

    public long cancelled() {
        return cancelled;
    }

    public long noShow() {
        return noShow;
    }

    public long bookedHours() {
        return bookedHours.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    public Long availableHours() {
        return availableHours;
    }

    public BigDecimal realizedRevenue() {
        return realizedRevenue;
    }

    public BigDecimal projectedRevenue() {
        return projectedRevenue;
    }

    public BigDecimal occupancyRate() {
        return availableHours == null ? null : ratio(bookedHours(), availableHours);
    }

    public BigDecimal cancellationRate() {
        return ratio(cancelled, total());
    }

    public BigDecimal noShowRate() {
        return ratio(noShow, completed + noShow);
    }

    /** Proporción entre 0 y 1 con 4 decimales (0.1234 = 12,34 %); 0 cuando no hay base. */
    static BigDecimal ratio(long part, long whole) {
        if (whole == 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(part).divide(BigDecimal.valueOf(whole), 4, RoundingMode.HALF_UP);
    }

}
