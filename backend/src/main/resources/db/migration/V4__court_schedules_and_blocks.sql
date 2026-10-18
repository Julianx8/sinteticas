-- Horario semanal y precios de cada cancha, y bloqueos puntuales (mantenimiento, torneos, festivos...).

-- btree_gist permite combinar "=" sobre UUID/enteros con "&&" (solapamiento de rangos) en una restricción EXCLUDE
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Una fila = una franja horaria de un día de la semana con su precio por hora.
-- Un mismo día puede tener varias franjas con precios distintos (p. ej. 08-18 a $60.000 y 18-23 a $90.000).
-- Horas enteras en hora local de Colombia; end_hour = 24 significa "hasta la medianoche".
CREATE TABLE court_schedules (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    court_id       UUID          NOT NULL REFERENCES courts (id) ON DELETE CASCADE,
    day_of_week    SMALLINT      NOT NULL CONSTRAINT ck_schedules_day CHECK (day_of_week BETWEEN 1 AND 7), -- ISO: 1 = lunes, 7 = domingo
    start_hour     SMALLINT      NOT NULL CONSTRAINT ck_schedules_start CHECK (start_hour BETWEEN 0 AND 23),
    end_hour       SMALLINT      NOT NULL CONSTRAINT ck_schedules_end CHECK (end_hour BETWEEN 1 AND 24),
    price_per_hour NUMERIC(10, 2) NOT NULL CONSTRAINT ck_schedules_price CHECK (price_per_hour >= 0),
    CONSTRAINT ck_schedules_range CHECK (end_hour > start_hour),
    -- Las franjas de un mismo día no pueden solaparse
    CONSTRAINT ex_schedules_no_overlap EXCLUDE USING gist (
        court_id WITH =,
        day_of_week WITH =,
        int4range(start_hour, end_hour) WITH &&
    )
);

CREATE TABLE schedule_blocks (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    court_id   UUID         NOT NULL REFERENCES courts (id) ON DELETE CASCADE,
    start_at   TIMESTAMPTZ  NOT NULL,
    end_at     TIMESTAMPTZ  NOT NULL,
    reason     VARCHAR(255) NOT NULL,
    created_by UUID         NOT NULL REFERENCES users (id),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_blocks_range CHECK (end_at > start_at)
);

CREATE INDEX ix_blocks_court_time ON schedule_blocks USING gist (court_id, tstzrange(start_at, end_at));
