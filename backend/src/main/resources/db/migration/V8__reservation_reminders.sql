-- Recordatorio por correo antes del partido.
-- reminder_sent_at: cuándo se envió (NULL = pendiente). Evita enviar dos veces el mismo recordatorio,
-- aunque la tarea que los envía se ejecute varias veces o en varias instancias.
ALTER TABLE reservations ADD COLUMN reminder_sent_at TIMESTAMPTZ;

-- La tarea busca seguido "confirmadas, sin recordatorio, que empiezan pronto": índice solo de esas filas.
CREATE INDEX ix_reservations_pending_reminder ON reservations (start_at)
    WHERE status = 'CONFIRMED' AND reminder_sent_at IS NULL;
