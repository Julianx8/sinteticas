package com.sinteticas.api.court;

import com.sinteticas.api.auth.AuthenticatedUser;
import com.sinteticas.api.common.persistence.AuditableEntity;
import com.sinteticas.api.venue.Venue;
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

import java.util.UUID;

@Entity
@Table(name = "courts")
public class Court extends AuditableEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id", nullable = false, updatable = false)
    private Venue venue;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "court_type", nullable = false, length = 20)
    private CourtType courtType;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private boolean active = true;

    protected Court() {
    }

    public Court(Venue venue) {
        this.venue = venue;
    }

    /** Se puede reservar si la cancha y su sede están activas. */
    public boolean isBookable() {
        return active && venue.isActive();
    }

    public boolean canBeManagedBy(AuthenticatedUser user) {
        return venue.canBeManagedBy(user);
    }

    public UUID getId() {
        return id;
    }

    public Venue getVenue() {
        return venue;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name.trim();
    }

    public CourtType getCourtType() {
        return courtType;
    }

    public void setCourtType(CourtType courtType) {
        this.courtType = courtType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

}
