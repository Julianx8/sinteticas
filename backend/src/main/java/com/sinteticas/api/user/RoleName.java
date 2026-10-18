package com.sinteticas.api.user;

/**
 * Roles del sistema (filas sembradas en V1__initial_schema.sql).
 */
public enum RoleName {
    /** Persona que reserva canchas. */
    CUSTOMER,
    /** Dueño o administrador de una o varias sedes. */
    ADMIN,
    /** Administrador de toda la plataforma. */
    SUPER_ADMIN
}
