# Modelo de datos

PostgreSQL 16. El esquema lo crean **solo** las migraciones de Flyway en
`backend/src/main/resources/db/migration` (`V1__…` a `V5__…`). Hibernate únicamente *valida* que las entidades
coincidan (`ddl-auto: validate`), nunca modifica tablas.

```mermaid
erDiagram
    users ||--o{ user_roles : tiene
    roles ||--o{ user_roles : asignado
    users ||--o{ venues : "es dueño de"
    venues ||--o{ courts : tiene
    courts ||--o{ court_schedules : "abre en"
    courts ||--o{ schedule_blocks : "se bloquea en"
    courts ||--o{ reservations : recibe
    users ||--o{ reservations : hace

    users {
        uuid id PK
        varchar full_name
        varchar email "único sin distinguir mayúsculas"
        varchar password_hash "BCrypt"
        boolean enabled
        timestamptz created_at
        timestamptz updated_at
    }
    roles {
        uuid id PK
        varchar name "CUSTOMER | ADMIN | SUPER_ADMIN"
    }
    user_roles {
        uuid user_id FK
        uuid role_id FK
    }
    venues {
        uuid id PK
        uuid owner_id FK
        varchar name
        varchar address
        varchar neighborhood
        varchar phone
        text description
        boolean active
    }
    courts {
        uuid id PK
        uuid venue_id FK
        varchar name "único por sede"
        varchar court_type "FUTBOL_5 ... FUTBOL_11"
        boolean active
    }
    court_schedules {
        uuid id PK
        uuid court_id FK
        smallint day_of_week "1 lunes - 7 domingo"
        smallint start_hour "0-23"
        smallint end_hour "1-24, exclusiva"
        numeric price_per_hour
    }
    schedule_blocks {
        uuid id PK
        uuid court_id FK
        timestamptz start_at
        timestamptz end_at
        varchar reason
        uuid created_by FK
    }
    reservations {
        uuid id PK
        uuid court_id FK
        uuid customer_id FK
        timestamptz start_at
        timestamptz end_at
        numeric total_price "congelado al reservar"
        varchar status "CONFIRMED | CANCELLED | COMPLETED | NO_SHOW"
        timestamptz cancelled_at
        uuid cancelled_by FK
    }
```

## Restricciones importantes

| Restricción | Tabla | Qué garantiza |
| --- | --- | --- |
| `ex_reservations_no_overlap` (EXCLUDE + GiST) | reservations | Dos reservas no canceladas de la misma cancha nunca se cruzan, aunque lleguen al mismo tiempo. Ver [ADR 002](../decisiones/002-reservas-sin-solapamiento.md) |
| `ex_schedules_no_overlap` (EXCLUDE + GiST) | court_schedules | Las franjas de un mismo día no se solapan |
| `ux_users_email_lower` | users | Email único sin distinguir mayúsculas |
| `ux_courts_venue_name` | courts | Nombre de cancha único por sede |
| `ck_*` | varias | Rangos válidos (fin > inicio, horas 0-24, precios ≥ 0, estados y tipos permitidos) |

## Convenciones

- Llaves primarias `UUID` generadas por la aplicación (también hay `DEFAULT gen_random_uuid()` para inserts manuales).
- Todas las fechas son `TIMESTAMPTZ` en UTC. La conversión a hora de Colombia se hace en el backend (`BusinessTime`).
- Las sedes y canchas **no se borran**: se desactivan (`active = false`) para conservar el historial de reservas.
- Cada cambio al esquema va en una migración nueva (`V6__…`). Una migración que ya está en `develop` **no se edita**.
