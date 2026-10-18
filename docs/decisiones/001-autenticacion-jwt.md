# ADR 001 — Autenticación con JWT y refresh tokens

**Estado:** aceptada (actualizada: se agregaron refresh tokens y límite de intentos de login)

## Contexto

El frontend (React) y el backend (Spring Boot) son aplicaciones separadas que se comunican por HTTP.
Necesitamos identificar al usuario en cada petición y saber sus roles (`CUSTOMER`, `ADMIN`, `SUPER_ADMIN`),
y poder cerrar sesiones (logout, cuenta desactivada, contraseña cambiada).

## Decisión

- **Token de acceso:** JWT firmado con HMAC-SHA256 (`JWT_SECRET`) con el id del usuario (`sub`), su email, nombre y
  roles. Dura **15 minutos**. Va en cada petición: `Authorization: Bearer <token>`. El backend no guarda sesiones
  para validarlo (`STATELESS`): solo revisa firma y vencimiento.
- **Refresh token:** valor aleatorio de 256 bits que dura **14 días**. En la base de datos se guarda **solo su hash**
  SHA-256 (`refresh_tokens`). Con `POST /auth/refresh` se cambia por un par nuevo.
- **Rotación:** cada refresh token sirve una sola vez. Si uno ya usado vuelve a aparecer, se asume que fue robado y
  se revoca toda la cadena de esa sesión (`REFRESH_TOKEN_REUSED`). La fila se bloquea (`SELECT … FOR UPDATE`) para
  que dos renovaciones simultáneas no roten el mismo token dos veces.
- **Revocación:** logout (`POST /auth/logout`), cerrar todas las sesiones (`DELETE /users/me/sessions`), desactivar
  la cuenta y cambiar la contraseña revocan los refresh tokens.
- **Roles siempre actuales:** cada renovación lee los roles de la base, así que un cambio de roles aplica en
  máximo 15 minutos sin volver a iniciar sesión.
- **Límite de intentos:** 5 fallos por email y 20 por IP en 15 minutos → `429` con `Retry-After`.
- Contraseñas con **BCrypt**. CSRF desactivado porque no se usan cookies de sesión.

## Consecuencias

- ✅ Si un token de acceso se filtra, sirve 15 minutos como máximo.
- ✅ Las sesiones se pueden cerrar de verdad, también en otros dispositivos.
- ⚠️ El frontend debe renovar el token: cuando una petición responda `401`, llamar a `/auth/refresh` una vez y
  repetir la petición; si el refresh también falla, mandar al login.
- ⚠️ Una cuenta desactivada conserva su token de acceso actual hasta que venza (máximo 15 minutos).
- ⚠️ Los contadores de intentos de login están en memoria: sirven con una sola instancia del backend.
- ⚠️ `JWT_SECRET` es crítico: si se filtra, cualquiera puede fabricar tokens. Nunca se sube a Git.
