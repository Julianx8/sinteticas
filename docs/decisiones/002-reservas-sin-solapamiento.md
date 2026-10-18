# ADR 002 — Evitar reservas cruzadas con una restricción de PostgreSQL

**Estado:** aceptada

## Contexto

La regla más importante del sistema: **dos personas no pueden reservar la misma cancha a la misma hora.**

Validarlo solo en Java ("¿existe ya una reserva en ese horario? si no, la creo") tiene una condición de carrera:
si dos peticiones llegan casi al mismo tiempo, ambas consultan, ambas ven el horario libre y ambas insertan.

## Decisión

Se usa una restricción `EXCLUDE` de PostgreSQL con un índice GiST (extensión `btree_gist`):

```sql
CONSTRAINT ex_reservations_no_overlap EXCLUDE USING gist (
    court_id WITH =,
    tstzrange(start_at, end_at) WITH &&
) WHERE (status <> 'CANCELLED')
```

Significa: *no puede haber dos filas con el mismo `court_id` cuyos rangos de tiempo se crucen (`&&`), salvo las canceladas.*
El rango es `[inicio, fin)`, así que una reserva de 18:00 a 19:00 y otra de 19:00 a 20:00 sí pueden coexistir.

En el backend:

1. Se hace un chequeo previo en Java para devolver un mensaje claro en el caso normal.
2. Si aun así la base de datos rechaza el INSERT (porque otra petición ganó la carrera), se captura la violación
   de `ex_reservations_no_overlap` y se responde `409 RESERVATION_SLOT_TAKEN`.

## Verificación

`ReservationIntegrationTest.concurrentReservationsForSameSlotOnlyOneWins` lanza 8 peticiones simultáneas por el
mismo horario contra un PostgreSQL real (Testcontainers): exactamente una recibe `201` y las otras siete `409`.

## Consecuencias

- ✅ La garantía no depende de que todo el código haga la validación correctamente: la base de datos la impone.
- ✅ No hace falta bloquear filas ni usar transacciones serializables.
- ⚠️ Depende de PostgreSQL (por eso los tests usan PostgreSQL real y no H2).
