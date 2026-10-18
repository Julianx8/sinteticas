-- Reservas de canchas.
-- Estados: CONFIRMED (activa), CANCELLED, COMPLETED (se jugó), NO_SHOW (el cliente no llegó).
-- El pago es en la sede, por eso una reserva nace CONFIRMED.

CREATE TABLE reservations (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    court_id            UUID           NOT NULL REFERENCES courts (id),
    customer_id         UUID           NOT NULL REFERENCES users (id),
    start_at            TIMESTAMPTZ    NOT NULL,
    end_at              TIMESTAMPTZ    NOT NULL,
    -- Precio congelado al reservar: si el dueño cambia el horario/precio, la reserva no cambia
    total_price         NUMERIC(12, 2) NOT NULL CONSTRAINT ck_reservations_price CHECK (total_price >= 0),
    status              VARCHAR(20)    NOT NULL
        CONSTRAINT ck_reservations_status CHECK (status IN ('CONFIRMED', 'CANCELLED', 'COMPLETED', 'NO_SHOW')),
    notes               VARCHAR(500),
    cancelled_at        TIMESTAMPTZ,
    cancelled_by        UUID REFERENCES users (id),
    cancellation_reason VARCHAR(255),
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_reservations_range CHECK (end_at > start_at),

    -- LA REGLA MÁS IMPORTANTE DEL SISTEMA: dos reservas no canceladas de la misma cancha no pueden
    -- cruzarse en el tiempo. La garantiza PostgreSQL, así que se cumple aunque lleguen dos peticiones
    -- en el mismo milisegundo (una validación solo en Java no lo garantiza).
    -- tstzrange es [inicio, fin): una reserva 18-19 y otra 19-20 no se cruzan.
    CONSTRAINT ex_reservations_no_overlap EXCLUDE USING gist (
        court_id WITH =,
        tstzrange(start_at, end_at) WITH &&
    ) WHERE (status <> 'CANCELLED')
);

CREATE INDEX ix_reservations_customer_start ON reservations (customer_id, start_at DESC);
CREATE INDEX ix_reservations_court_start ON reservations (court_id, start_at);
