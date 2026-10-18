package com.sinteticas.api.schedule;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Periodo en que una cancha no se puede reservar (mantenimiento, torneo, festivo...).
 */
@Entity
@Table(name = "schedule_blocks")
public class ScheduleBlock {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "court_id", nullable = false, updatable = false)
    private UUID courtId;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(nullable = false)
    private String reason;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ScheduleBlock() {
    }

    public ScheduleBlock(UUID courtId, Instant startAt, Instant endAt, String reason, UUID createdBy) {
        this.courtId = courtId;
        this.startAt = startAt;
        this.endAt = endAt;
        this.reason = reason;
        this.createdBy = createdBy;
    }

    public boolean overlaps(Instant from, Instant to) {
        return startAt.isBefore(to) && endAt.isAfter(from);
    }

    public UUID getId() {
        return id;
    }

    public UUID getCourtId() {
        return courtId;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public String getReason() {
        return reason;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

}
