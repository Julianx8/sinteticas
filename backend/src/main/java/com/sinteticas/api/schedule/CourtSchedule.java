package com.sinteticas.api.schedule;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.UUID;

/**
 * Franja horaria semanal de una cancha con su precio por hora (hora local de Colombia).
 */
@Entity
@Table(name = "court_schedules")
public class CourtSchedule {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "court_id", nullable = false, updatable = false)
    private UUID courtId;

    /** ISO-8601: 1 = lunes ... 7 = domingo (igual que {@link DayOfWeek#getValue()}). */
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "day_of_week", nullable = false)
    private int dayOfWeek;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "start_hour", nullable = false)
    private int startHour;

    /** Exclusiva: 24 = hasta la medianoche. */
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "end_hour", nullable = false)
    private int endHour;

    @Column(name = "price_per_hour", nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerHour;

    protected CourtSchedule() {
    }

    public CourtSchedule(UUID courtId, int dayOfWeek, int startHour, int endHour, BigDecimal pricePerHour) {
        this.courtId = courtId;
        this.dayOfWeek = dayOfWeek;
        this.startHour = startHour;
        this.endHour = endHour;
        this.pricePerHour = pricePerHour;
    }

    public boolean covers(int hour) {
        return hour >= startHour && hour < endHour;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCourtId() {
        return courtId;
    }

    public int getDayOfWeek() {
        return dayOfWeek;
    }

    public int getStartHour() {
        return startHour;
    }

    public int getEndHour() {
        return endHour;
    }

    public BigDecimal getPricePerHour() {
        return pricePerHour;
    }

}
