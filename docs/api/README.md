# API REST — Sintéticas

Base: `http://localhost:8080/api/v1` · Documentación interactiva: **http://localhost:8080/swagger-ui.html**

En Swagger, usa el botón **Authorize** y pega el `accessToken` que devuelve `/auth/login`.

## Autenticación

Los endpoints protegidos requieren el header:

```
Authorization: Bearer <accessToken>
```

Login y registro devuelven dos tokens:

| Token | Dura | Para qué |
| --- | --- | --- |
| `accessToken` | 15 minutos (`JWT_EXPIRATION`) | Va en el header `Authorization` de cada petición |
| `refreshToken` | 14 días (`JWT_REFRESH_EXPIRATION`) | Se envía a `POST /auth/refresh` para obtener un par nuevo |

Flujo recomendado para el frontend: si una petición responde `401`, llamar a `/auth/refresh` **una vez** con el
refresh token guardado, reemplazar ambos tokens y repetir la petición. Si el refresh también falla, ir al login.
Cada refresh token sirve una sola vez: reutilizar uno viejo cierra la sesión (`REFRESH_TOKEN_REUSED`).

Los roles se leen de nuevo en cada renovación: si un SUPER_ADMIN cambia los roles de alguien, aplican en máximo 15 minutos.
Tras 5 intentos de login fallidos en 15 minutos, `/auth/login` responde `429` con el header `Retry-After`.

## Roles

| Rol | Quién es | Qué puede hacer |
| --- | --- | --- |
| `CUSTOMER` | Cualquier persona registrada | Reservar, ver y cancelar sus reservas |
| `ADMIN` | Dueño de una o varias sedes | Todo lo anterior + administrar **sus** sedes, canchas, horarios, bloqueos y reservas |
| `SUPER_ADMIN` | Administrador de la plataforma | Gestionar usuarios y roles; administrar cualquier sede |

El registro público siempre crea `CUSTOMER`. Para que un dueño de cancha sea `ADMIN`, un SUPER_ADMIN usa `PUT /admin/users/{id}/roles`.

## Endpoints

🌐 = público (no necesita token) · 🔒 = cualquier usuario autenticado · 🏟️ = ADMIN o SUPER_ADMIN · 👑 = solo SUPER_ADMIN

### Autenticación y cuenta

| Método | Ruta | | Descripción |
| --- | --- | --- | --- |
| POST | `/auth/register` | 🌐 | Registrar cliente → tokens |
| POST | `/auth/login` | 🌐 | Iniciar sesión → tokens |
| POST | `/auth/refresh` | 🌐 | Renovar tokens `{"refreshToken": "…"}` |
| POST | `/auth/logout` | 🌐 | Cerrar la sesión de ese refresh token |
| POST | `/auth/password/forgot` | 🌐 | Enviar enlace para restablecer contraseña `{"email": "…"}` (siempre 202) |
| POST | `/auth/password/reset` | 🌐 | Contraseña nueva con el token del enlace `{"token": "…", "newPassword": "…"}` |
| GET | `/users/me` | 🔒 | Mis datos |
| PATCH | `/users/me` | 🔒 | Cambiar mi nombre |
| PUT | `/users/me/password` | 🔒 | Cambiar mi contraseña (cierra todas las sesiones) |
| DELETE | `/users/me/sessions` | 🔒 | Cerrar todas mis sesiones |

### Administración de usuarios

| Método | Ruta | | Descripción |
| --- | --- | --- | --- |
| GET | `/admin/users?q=&role=` | 👑 | Buscar usuarios por nombre/email y rol (paginado) |
| GET | `/admin/users/{id}` | 👑 | Ver usuario |
| GET | `/admin/users/{id}/venues` | 👑 | Sedes de las que es dueño |
| PUT | `/admin/users/{id}/roles` | 👑 | Reemplazar roles `{"roles": ["CUSTOMER", "ADMIN"]}` |
| PUT | `/admin/users/{id}/status` | 👑 | Activar/desactivar `{"enabled": false}` |

### Sedes y canchas

| Método | Ruta | | Descripción |
| --- | --- | --- | --- |
| GET | `/venues?q=&neighborhood=&courtType=` | 🌐 | Catálogo de sedes activas (paginado) |
| GET | `/venues/mine` | 🏟️ | Mis sedes (incluye inactivas) |
| GET | `/venues/{id}` | 🌐 | Detalle de sede |
| POST | `/venues` | 🏟️ | Crear sede (quien la crea es el dueño) |
| PUT | `/venues/{id}` | 🏟️ | Actualizar sede (`active: false` la desactiva) |
| GET | `/venues/{id}/courts` | 🌐 | Canchas de la sede |
| POST | `/venues/{id}/courts` | 🏟️ | Crear cancha |
| GET | `/courts/{id}` | 🌐 | Detalle de cancha |
| PUT | `/courts/{id}` | 🏟️ | Actualizar cancha |

Tipos de cancha: `FUTBOL_5`, `FUTBOL_6`, `FUTBOL_7`, `FUTBOL_8`, `FUTBOL_11`.

### Horarios y bloqueos

| Método | Ruta | | Descripción |
| --- | --- | --- | --- |
| GET | `/courts/{id}/schedules` | 🌐 | Horario semanal con precios |
| PUT | `/courts/{id}/schedules` | 🏟️ | Reemplazar horario completo |
| GET | `/courts/{id}/blocks?from=2026-12-01&to=2026-12-31` | 🏟️ | Bloqueos en un rango |
| POST | `/courts/{id}/blocks` | 🏟️ | Bloquear un periodo |
| DELETE | `/courts/{id}/blocks/{blockId}` | 🏟️ | Quitar bloqueo |

Ejemplo de horario (lunes con precio de día y de noche; `dayOfWeek`: 1 = lunes … 7 = domingo; `endHour` 24 = medianoche):

```json
{
  "slots": [
    { "dayOfWeek": 1, "startHour": 8,  "endHour": 18, "pricePerHour": 60000 },
    { "dayOfWeek": 1, "startHour": 18, "endHour": 24, "pricePerHour": 90000 }
  ]
}
```

### Reservas

| Método | Ruta | | Descripción |
| --- | --- | --- | --- |
| GET | `/courts/{id}/availability?date=2026-10-20` | 🌐 | Horas del día con precio y estado |
| POST | `/reservations` | 🔒 | Reservar |
| GET | `/reservations/mine?status=&upcoming=true` | 🔒 | Mis reservas (paginado) |
| GET | `/reservations/{id}` | 🔒 | Detalle (el cliente o la sede) |
| POST | `/reservations/{id}/cancel` | 🔒 | Cancelar `{"reason": "..."}` (opcional) |
| POST | `/reservations/{id}/complete` | 🏟️ | Marcar que se jugó |
| POST | `/reservations/{id}/no-show` | 🏟️ | Marcar que el cliente no llegó |
| GET | `/venues/{id}/reservations?from=&to=&status=&courtId=` | 🏟️ | Agenda de la sede |

Crear una reserva:

```json
POST /reservations
{ "courtId": "…", "startAt": "2026-10-20T18:00:00-05:00", "durationHours": 2, "notes": "Partido de la oficina" }
```

Estados de la disponibilidad: `AVAILABLE`, `BOOKED`, `BLOCKED`, `PAST`. Si la fecha es festivo, la respuesta trae
`holidayName` (por ejemplo `"Día de Todos los Santos"`).

### Festivos

| Método | Ruta | | Descripción |
| --- | --- | --- | --- |
| GET | `/holidays?year=2026` | 🌐 | Festivos de Colombia del año: `[{"date": "2026-01-01", "name": "Año Nuevo"}, …]` |

Los festivos se calculan solos (Ley Emiliani: fijos, trasladables al lunes y los que dependen de la Pascua).
**En un festivo la cancha usa el horario y los precios del domingo** (`app.holidays.sunday-schedule: true`).
Para cerrar un festivo concreto, la sede crea un bloqueo.

Estados de una reserva: `CONFIRMED` → `CANCELLED` | `COMPLETED` | `NO_SHOW`.

### Reglas de reserva (configurables en `application.yml`, sección `app.reservations`)

- Empiezan en punto y duran horas enteras, máximo **3 horas**, y terminan a más tardar a la medianoche.
- Solo dentro del horario de la cancha, sin bloqueos y sin cruzarse con otra reserva.
- Hasta **30 días** de anticipación; nunca en el pasado.
- Un cliente puede tener máximo **5 reservas futuras activas**.
- El cliente cancela hasta **2 horas** antes; la sede puede cancelar en cualquier momento antes del inicio.
- El precio se calcula sumando el precio de cada hora y **queda fijo** aunque la sede cambie el horario después.

### Reportes

Fechas `from`/`to` inclusive, en hora de Colombia, máximo **366 días**.

| Método | Ruta | | Descripción |
| --- | --- | --- | --- |
| GET | `/venues/{id}/reports/summary?from=&to=` | 🏟️ | Totales de la sede y desglose por cancha |
| GET | `/venues/{id}/reports/daily?from=&to=` | 🏟️ | Un punto por día (también los días sin reservas), listo para graficar |
| GET | `/venues/{id}/reports/demand?from=&to=` | 🏟️ | Horas reservadas por día de la semana × hora, y las 5 horas pico |
| GET | `/admin/reports/summary?from=&to=` | 👑 | Usuarios, sedes y canchas activas, reservas por mes y las 5 sedes con más reservas |

Todos los reportes usan el mismo bloque de métricas:

| Métrica | Cómo se calcula |
| --- | --- |
| `reservations`, `confirmed`, `completed`, `cancelled`, `noShow` | Reservas que **empiezan** en el rango, por estado |
| `bookedHours` | Horas de las reservas no canceladas (CONFIRMED + COMPLETED + NO_SHOW) |
| `availableHours` | Horas del horario de las canchas activas menos las bloqueadas |
| `occupancyRate` | `bookedHours ÷ availableHours` |
| `realizedRevenue` | Suma de las COMPLETED (lo que efectivamente se jugó) |
| `projectedRevenue` | Suma de las CONFIRMED (por jugar) |
| `cancellationRate` | `cancelled ÷ reservations` |
| `noShowRate` | `noShow ÷ (completed + noShow)`, es decir, sobre las que ya tienen resultado |

Las tasas van de 0 a 1 con 4 decimales (`0.1538` = 15,38 %). Las NO_SHOW no suman ingresos porque el pago es en la sede.
En el resumen de plataforma `availableHours` y `occupancyRate` se omiten: la ocupación es una métrica por sede.
La disponibilidad usa el horario **actual**: una cancha desactivada hoy cuenta 0 horas también en fechas pasadas.

### Correos

El backend envía correos de texto en segundo plano (si el correo falla, la operación no falla):

| Cuándo | A quién |
| --- | --- |
| Se crea una reserva | Confirmación al cliente y aviso a la sede |
| El cliente cancela | Aviso a la sede |
| La sede cancela | Aviso al cliente, con el motivo |
| Se pide restablecer la contraseña | Enlace al cliente: `APP_FRONTEND_URL/restablecer-contrasena?token=…` (1 hora, un solo uso) |

El frontend debe tener la página `/restablecer-contrasena`, que lee el `token` de la URL y llama a `/auth/password/reset`.
`/auth/password/forgot` responde igual exista o no el email (para no revelar qué cuentas existen) y envía máximo
3 correos por hora a la misma cuenta. Restablecer la contraseña cierra todas las sesiones.

En desarrollo los correos no salen a internet: llegan a **Mailpit**, en http://localhost:8025.

## Paginación

Los listados paginados aceptan `?page=0&size=20&sort=campo,asc|desc` (máximo 100 por página) y responden:

```json
{ "content": [ … ], "page": 0, "size": 20, "totalElements": 42, "totalPages": 3 }
```

## Fechas

Todas las fechas van en ISO-8601 **con zona horaria**. Las respuestas usan la hora de Colombia (`-05:00`), por ejemplo `2026-10-20T18:00:00-05:00`.

## Errores

Todos los errores tienen el mismo formato ([RFC 7807](https://www.rfc-editor.org/rfc/rfc7807)). El frontend debe usar `code`, que no cambia, y no el texto de `detail`:

```json
{ "status": 409, "title": "Conflict", "detail": "Ese horario ya está reservado", "code": "RESERVATION_SLOT_TAKEN" }
```

Los errores de validación incluyen además `errors` con el mensaje de cada campo:

```json
{ "status": 400, "code": "VALIDATION_ERROR", "errors": { "email": "must be a well-formed email address" } }
```

| HTTP | Códigos |
| --- | --- |
| 400 | `INVALID_YEAR`, `INVALID_RESET_TOKEN`, `VALIDATION_ERROR`, `INVALID_SORT`, `INVALID_RANGE`, `RANGE_TOO_LARGE`, `INVALID_START_TIME`, `INVALID_SCHEDULE_RANGE`, `INVALID_CURRENT_PASSWORD` |
| 401 | `UNAUTHORIZED` (sin token o token inválido/vencido), `INVALID_CREDENTIALS`, `INVALID_REFRESH_TOKEN`, `REFRESH_TOKEN_REUSED` |
| 403 | `FORBIDDEN`, `ACCOUNT_DISABLED`, `VENUE_NOT_OWNED`, `COURT_NOT_OWNED` |
| 404 | `USER_NOT_FOUND`, `VENUE_NOT_FOUND`, `COURT_NOT_FOUND`, `BLOCK_NOT_FOUND`, `RESERVATION_NOT_FOUND` |
| 409 | `EMAIL_ALREADY_REGISTERED`, `COURT_NAME_TAKEN`, `RESERVATION_SLOT_TAKEN`, `SLOT_BLOCKED`, `BLOCK_CONFLICTS_WITH_RESERVATIONS`, `DATA_CONFLICT` |
| 429 | `TOO_MANY_LOGIN_ATTEMPTS` (con `Retry-After` y `retryAfterSeconds`) |
| 422 | `OUTSIDE_SCHEDULE`, `DURATION_TOO_LONG`, `CROSSES_MIDNIGHT`, `RESERVATION_IN_PAST`, `TOO_FAR_AHEAD`, `TOO_MANY_ACTIVE_RESERVATIONS`, `COURT_NOT_BOOKABLE`, `CANCELLATION_TOO_LATE`, `RESERVATION_ALREADY_STARTED`, `RESERVATION_NOT_ACTIVE`, `RESERVATION_NOT_STARTED`, `SCHEDULE_OVERLAP`, `CANNOT_REMOVE_OWN_SUPER_ADMIN`, `CANNOT_DISABLE_SELF` |
