# Hoja de ruta del frontend (mobile-first)

Análisis de UX/UI y arquitectura del frontend de Sintéticas, hecho sobre lo que el sistema **ya tiene** (endpoints, reglas y códigos de error del backend). Cada propuesta indica si se hace **solo en el frontend** o si **necesita backend** (⚠️).

> Dos decisiones de partida:
> - La reserva se pide como **hora de inicio + duración** (1 a 3 horas enteras), no como inicio y fin libres. Así es imposible pedir un fin menor que el inicio o una hora suelta como 7:30.
> - **No se muestra clima simulado**: inventar el clima a alguien que va a jugar al aire libre es engañarlo. Se usa un pronóstico real (Open-Meteo, gratis y sin clave) cuando la sede tenga coordenadas.

Reglas del backend que condicionan la interfaz (`app.reservations` en `application.yml`):

| Regla | Valor |
|---|---|
| Duración máxima | 3 horas |
| Anticipación máxima | 30 días |
| Cancelación del cliente | hasta 2 horas antes |
| Reservas futuras activas por cliente | 5 |
| Tipos de cancha | Fútbol 5, 6, 7, 8 y 11 |
| Estados de una hora | `AVAILABLE`, `BOOKED`, `BLOCKED`, `PAST` |

---

## 1. Componentes clave (mobile-first)

### 1.1 Pantalla principal y buscador
1. **Navegación inferior en celular** (Inicio · Reservas · Mi sede · Perfil): el pulgar llega abajo, no arriba.
2. **Tarjeta "Tu próximo partido"** con cuenta regresiva, sede, cancha y botones *Cómo llegar* y *Compartir*.
3. **Buscador por fecha y hora** ("¿cuándo quieres jugar?") con chips *Hoy · Mañana · Fin de semana*. ⚠️ Necesita un endpoint de búsqueda por disponibilidad (fase B).
4. **Filtros como chips deslizables**: tipo de cancha (ya existe), franja (mañana/tarde/noche), precio. *Techada* y *Cerca de mí* ⚠️ necesitan datos nuevos en la base.
5. **Tarjetas de sede**: foto ⚠️, nombre, barrio, tipos de cancha, "Desde $X/h" ⚠️ (precio mínimo en la lista de sedes) y las próximas horas libres como atajos.
6. **Estados vacíos útiles**: sugerir alternativas en vez de "No hay resultados".

### 1.2 Disponibilidad
1. **Tira de días horizontal** (hoy a +30 días) en vez del calendario nativo.
2. **Línea de tiempo vertical** con los 4 estados en texto y color (nunca solo color), horas pasadas ocultas por defecto.
3. **Selección de rango con dos toques** (inicio y fin): el rango no puede cruzar horas ocupadas ni pasar de 3 horas, así que es imposible pedir algo inválido.
4. **Pestañas por cancha** cuando la sede tiene varias.
5. **Cambios de precio visibles** ("Desde las 18:00: $90.000/h") y aviso de festivo.
6. **Barra fija abajo** con el resumen y el botón de reservar.

### 1.3 Confirmación
- **Hoja de revisión** (sube desde abajo): sede, cancha, fecha en palabras, rango horario, desglose del precio por franja, **hasta cuándo se puede cancelar** (hora calculada), nota para la sede y botón que se bloquea al tocarlo.
- **Pantalla de éxito**: *Agregar al calendario* (`.ics`), *Compartir por WhatsApp*, *Cómo llegar*, *Ver mi reserva*.
- **Pago en línea** (Wompi/PSE) ⚠️: trabajo futuro; por ahora "Pago en la sede".

## 2. UX específica para fútbol sintético

### 2.1 Partidos pendientes
- Tarjeta de próximo partido con cuenta regresiva y estado ("ya no se puede cancelar", "en juego"...).
- Recordatorio por correo 3 h antes ⚠️ (tarea programada en el backend, fase B).
- Pronóstico real con Open-Meteo ⚠️ (coordenadas de la sede, fase C).
- *Cómo llegar* (enlace a Google Maps) y calendario (`.ics`): solo frontend.

### 2.2 Dividir la cuenta (solo frontend)
- Selector de jugadores con valor por defecto según el tipo (F5 → 10, F6 → 12, F7 → 14, F8 → 16, F11 → 22).
- "**$18.000** por jugador", redondeado a la centena, indicando cuánto sobra.
- Botón para enviar el mensaje al grupo de WhatsApp.
- Lista de jugadores con "ya pagó" ⚠️: trabajo futuro.

### 2.3 Fidelización
- Contador de partidos jugados con insignias, calculado con las reservas `COMPLETED` (fase C).
- Descuentos reales ⚠️ y "tercer tiempo": fuera del alcance. No se muestran promesas que el backend no respalde.

## 3. Requerimientos técnicos

### 3.1 PWA
| Función | Esfuerzo | Fase |
|---|---|---|
| Instalable (manifest, íconos, service worker) | Bajo | A |
| Ver "Mis reservas" sin conexión (últimos datos guardados, con aviso) | Medio | A |
| Notificaciones push ⚠️ (VAPID, suscripciones; en iPhone solo instalada, iOS 16.4+) | Alto | Futuro |

Sin conexión **nunca se permite reservar**: la disponibilidad siempre viene del servidor.

### 3.2 Rendimiento
- Peso actual: ~84 KB comprimido.
- Cargar el panel del dueño y del administrador solo cuando se necesitan (`React.lazy`).
- Siluetas de carga en vez de "Cargando...".
- Imágenes WebP con `srcset` y carga diferida (cuando haya fotos).
- Producción con nginx y compresión (paso de producción).
- Meta: Lighthouse móvil ≥ 90 y contenido principal visible en menos de 2,5 s en 4G.

## 4. Integración con el backend

### 4.1 Errores con mensaje y acción (`lib/errors.ts`)
| Código | Qué hace la interfaz |
|---|---|
| `RESERVATION_SLOT_TAKEN` | "Alguien acaba de tomar esa hora": recarga la disponibilidad y sugiere la hora libre más cercana |
| `SLOT_BLOCKED` | "La sede cerró ese horario": recarga y sugiere otra |
| `OUTSIDE_SCHEDULE`, `CROSSES_MIDNIGHT`, `RESERVATION_IN_PAST`, `INVALID_START_TIME` | Recarga el día |
| `TOO_FAR_AHEAD`, `DURATION_TOO_LONG` | Prevenidos por la interfaz |
| `TOO_MANY_ACTIVE_RESERVATIONS` | Explica el máximo y lleva a *Mis reservas* |
| `CANCELLATION_TOO_LATE` | El botón se oculta antes; se ofrece llamar a la sede |
| `COURT_NOT_BOOKABLE` | La cancha dejó de recibir reservas |
| `TOO_MANY_LOGIN_ATTEMPTS`, `TOO_MANY_RESET_REQUESTS` | Muestra cuánto esperar (`Retry-After`) |
| `REFRESH_TOKEN_REUSED`, `INVALID_REFRESH_TOKEN` | "Tu sesión terminó" y vuelve a entrar |
| `VALIDATION_ERROR` | Marca el campo exacto |
| Sin conexión / 500 | "Sin conexión" con *Reintentar*; nunca pantalla en blanco |

### 4.2 Datos que pide el frontend
**Ya disponibles:** sedes (paginadas y filtradas), canchas, disponibilidad por cancha y día (estado y precio por hora), reservas propias por estado, festivos.

**Cambios recomendados al backend, en orden:**
1. `minPrice` y `courtTypes` en la lista de sedes.
2. Búsqueda por disponibilidad: `GET /venues/available?date=&hour=&courtType=`.
3. Disponibilidad de toda la sede en una sola petición.
4. Migración V8: coordenadas y fotos de la sede, "techada" en la cancha.
5. Recordatorios por correo.

---

## Fases

**Fase A: solo frontend**
1. Navegación inferior en celular.
2. Tira de días y línea de tiempo con selección de rango.
3. Hoja de revisión y pantalla de éxito (`.ics`, WhatsApp, Cómo llegar).
4. Dividir la cuenta.
5. Errores con acción (`lib/errors.ts`).
6. Siluetas de carga, página "No encontrado", protección ante errores, títulos por página, diálogos propios.
7. PWA instalable, "Mis reservas" sin conexión y carga por secciones.

**Fase B: backend pequeño + frontend**
8. Precio mínimo y tipos en la lista de sedes.
9. Disponibilidad de toda la sede en una petición.
10. Búsqueda por fecha y hora.
11. Recordatorios por correo.

**Fase C: funciones con migración**
12. Fotos, coordenadas y canchas techadas.
13. Mapa, "cerca de mí" y clima real por sede.
14. Contador de partidos con insignias.

**Trabajo futuro (fuera del alcance de la tesis):** pagos en línea, notificaciones push, lista de jugadores con "ya pagó", descuentos por fidelización, tercer tiempo.

Cada fase entra por Pull Request a `develop` con las pruebas de punta a punta (`e2e/`) ampliadas.
