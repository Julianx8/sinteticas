-- Sedes (establecimientos) y sus canchas.
-- Una sede pertenece a un usuario con rol ADMIN (el dueño). No se borran: se desactivan (active = false)
-- para no perder el historial de reservas.

CREATE TABLE venues (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id     UUID         NOT NULL REFERENCES users (id),
    name         VARCHAR(150) NOT NULL,
    address      VARCHAR(255) NOT NULL,
    neighborhood VARCHAR(100),
    phone        VARCHAR(30),
    description  TEXT,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_venues_owner_id ON venues (owner_id);
CREATE INDEX ix_venues_active ON venues (active);

CREATE TABLE courts (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    venue_id    UUID         NOT NULL REFERENCES venues (id),
    name        VARCHAR(100) NOT NULL,
    court_type  VARCHAR(20)  NOT NULL
        CONSTRAINT ck_courts_type CHECK (court_type IN ('FUTBOL_5', 'FUTBOL_6', 'FUTBOL_7', 'FUTBOL_8', 'FUTBOL_11')),
    description TEXT,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Dos canchas de la misma sede no pueden llamarse igual ("Cancha 1" y "cancha 1" cuentan como iguales)
CREATE UNIQUE INDEX ux_courts_venue_name ON courts (venue_id, lower(name));
