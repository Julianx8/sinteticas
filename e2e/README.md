# Pruebas de punta a punta (e2e)

Abren un navegador (Chromium) y usan Sintéticas **como una persona**: hacen clic en el frontend real, que habla con el backend real y la base de datos real. Complementan los tests del backend: esos prueban la API; estas prueban que las pantallas y la API funcionan juntas.

Corren solas en GitHub Actions en cada push y cada PR (trabajo "Pruebas de punta a punta").

## Qué prueban

| Archivo | Flujos |
|---|---|
| `tests/cuenta.spec.ts` | Registro → "olvidé mi contraseña" → correo en Mailpit → restablecer → entrar · cambiar el nombre · renovación automática del token vencido · rutas protegidas |
| `tests/reservas.spec.ts` | Reservar 2 horas con dos toques, revisar, calendario/WhatsApp/mapa, dividir la cuenta y cancelar · alguien toma la hora mientras se revisa (aviso y hora sugerida) · sin sesión se pide iniciar sesión |
| `tests/dueno.spec.ts` | Panel del dueño · horario con franjas que se cruzan (error claro) · bloqueos que quitan horas de la disponibilidad · cancelar desde la agenda con diálogo de confirmación · agenda y reportes |
| `tests/movil.spec.ts` | En pantalla de celular: navegación inferior, ninguna página se sale del ancho, barra de resumen visible, página 404, "Mis reservas" sin internet y borrado de copias al cerrar sesión |

`global-setup.ts` crea por la API los datos que necesitan (un dueño con sede, cancha y horario, y un cliente) con correos únicos, así que se pueden correr muchas veces sobre la misma base.

## Correrlas en tu computador

Con el sistema levantado (`docker compose up --build`), desde la raíz del proyecto en Git Bash:

```bash
cd e2e
npm install
npx playwright install chromium   # solo la primera vez
set -a; . ../.env; set +a          # toma SUPER_ADMIN_EMAIL / SUPER_ADMIN_PASSWORD del .env
npx playwright test                # o: npx playwright test --ui  para verlas paso a paso
```

Si una falla, `npx playwright show-report` muestra la captura de pantalla y el paso a paso.

> El backend permite 10 solicitudes de "olvidé mi contraseña" por hora desde la misma IP. Si corres las pruebas muchas veces seguidas, pon `PASSWORD_RESET_MAX_PER_IP=100` en tu `.env` y reinicia el backend.
