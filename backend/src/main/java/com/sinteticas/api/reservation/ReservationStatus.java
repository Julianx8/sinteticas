package com.sinteticas.api.reservation;

/**
 * Ciclo de vida: CONFIRMED → (CANCELLED | COMPLETED | NO_SHOW).
 * Debe coincidir con ck_reservations_status en V5.
 */
public enum ReservationStatus {
    /** Activa: ocupa el horario. */
    CONFIRMED,
    /** Cancelada por el cliente o por la sede: libera el horario. */
    CANCELLED,
    /** Se jugó. */
    COMPLETED,
    /** El cliente no llegó. */
    NO_SHOW
}
