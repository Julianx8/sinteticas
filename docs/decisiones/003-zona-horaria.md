# ADR 003 — Manejo de fechas y zona horaria

**Estado:** aceptada

## Contexto

Las canchas abren "de 8 a 23" en hora de Colombia, pero el servidor, la base de datos o el navegador pueden
estar en otra zona horaria. Un error aquí corre las reservas horas enteras.

## Decisión

- En la base de datos todas las fechas son `TIMESTAMPTZ` y Hibernate escribe en UTC (`hibernate.jdbc.time_zone: UTC`).
- En Java las entidades usan `Instant` (un instante absoluto, sin zona).
- La zona del negocio es `America/Bogota` (`app.zone-id`). Toda conversión pasa por el componente `BusinessTime`;
  nadie usa la zona del servidor.
- Los horarios semanales se guardan como horas enteras locales (`start_hour`, `end_hour`), porque así los piensa el dueño.
- La API recibe fechas ISO-8601 **con zona** (`2026-10-20T18:00:00-05:00` o `2026-10-20T23:00:00Z`) y responde
  siempre en hora de Colombia.
- El reloj (`Clock`) se inyecta, así que los tests pueden fijar la hora (`MutableClock`).

## Consecuencias

- ✅ El resultado es el mismo sin importar dónde corra el servidor.
- ✅ Colombia no tiene horario de verano, así que un día siempre tiene 24 horas.
- ✅ Los festivos de Colombia se calculan solos (`ColombianHolidays`) y usan el horario y los precios del domingo.
  Para cerrar un festivo concreto, la sede crea un bloqueo.
