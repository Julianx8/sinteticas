package com.sinteticas.api.reservation;

import com.sinteticas.api.common.error.ApiException;
import com.sinteticas.api.common.persistence.AuditableEntity;
import com.sinteticas.api.court.Court;
import com.sinteticas.api.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reservations")
public class Reservation extends AuditableEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "court_id", nullable = false, updatable = false)
    private Court court;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, updatable = false)
    private User customer;

    @Column(name = "start_at", nullable = false, updatable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false, updatable = false)
    private Instant endAt;

    @Column(name = "total_price", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status = ReservationStatus.CONFIRMED;

    @Column(length = 500)
    private String notes;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    protected Reservation() {
    }

    public Reservation(Court court, User customer, Instant startAt, Instant endAt, BigDecimal totalPrice,
                       String notes) {
        this.court = court;
        this.customer = customer;
        this.startAt = startAt;
        this.endAt = endAt;
        this.totalPrice = totalPrice;
        this.notes = notes;
    }

    public void cancel(UUID byUserId, String reason, Instant now) {
        requireConfirmed();
        this.status = ReservationStatus.CANCELLED;
        this.cancelledAt = now;
        this.cancelledBy = byUserId;
        this.cancellationReason = reason;
    }

    /** Registra el resultado (COMPLETED o NO_SHOW); solo cuando la reserva ya empezó. */
    public void markOutcome(ReservationStatus outcome, Instant now) {
        if (outcome != ReservationStatus.COMPLETED && outcome != ReservationStatus.NO_SHOW) {
            throw new IllegalArgumentException("Resultado inválido: " + outcome);
        }
        requireConfirmed();
        if (now.isBefore(startAt)) {
            throw ApiException.businessRule("RESERVATION_NOT_STARTED",
                    "Solo se puede registrar el resultado cuando la reserva ya empezó");
        }
        this.status = outcome;
    }

    public boolean hasStarted(Instant now) {
        return !now.isBefore(startAt);
    }

    public boolean isCustomer(UUID userId) {
        return customer.getId().equals(userId);
    }

    public int durationHours() {
        return (int) Duration.between(startAt, endAt).toHours();
    }

    private void requireConfirmed() {
        if (status != ReservationStatus.CONFIRMED) {
            throw ApiException.businessRule("RESERVATION_NOT_ACTIVE",
                    "La reserva ya no está activa (estado: " + status + ")");
        }
    }

    public UUID getId() {
        return id;
    }

    public Court getCourt() {
        return court;
    }

    public User getCustomer() {
        return customer;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

}
