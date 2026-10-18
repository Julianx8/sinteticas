package com.sinteticas.api.venue;

import com.sinteticas.api.auth.AuthenticatedUser;
import com.sinteticas.api.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "venues")
public class Venue extends AuditableEntity {

    @Id
    @GeneratedValue
    private UUID id;

    // Solo el id: para las reglas de propiedad no hace falta cargar el usuario completo
    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false)
    private String address;

    @Column(length = 100)
    private String neighborhood;

    @Column(length = 30)
    private String phone;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private boolean active = true;

    protected Venue() {
    }

    public Venue(UUID ownerId) {
        this.ownerId = ownerId;
    }

    /** El dueño de la sede o un SUPER_ADMIN pueden administrarla. */
    public boolean canBeManagedBy(AuthenticatedUser user) {
        return user.isSuperAdmin() || ownerId.equals(user.id());
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name.trim();
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address.trim();
    }

    public String getNeighborhood() {
        return neighborhood;
    }

    public void setNeighborhood(String neighborhood) {
        this.neighborhood = neighborhood;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
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
