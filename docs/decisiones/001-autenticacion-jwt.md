# ADR 001 — Autenticación con JWT sin estado

**Estado:** aceptada

## Contexto

El frontend (React) y el backend (Spring Boot) son aplicaciones separadas que se comunican por HTTP.
Necesitamos identificar al usuario en cada petición y saber sus roles (`CUSTOMER`, `ADMIN`, `SUPER_ADMIN`).

## Decisión

- Al iniciar sesión, el backend emite un **JWT firmado con HMAC-SHA256** (`JWT_SECRET`) que contiene el id
  del usuario (`sub`), su email, su nombre y sus roles. Dura 8 horas.
- El frontend lo envía en cada petición: `Authorization: Bearer <token>`.
- El backend no guarda sesiones (`SessionCreationPolicy.STATELESS`): valida la firma y la expiración del token.
- Usamos el soporte oficial de Spring Security (`oauth2-resource-server`) en vez de un filtro JWT propio.
- Las contraseñas se guardan con **BCrypt**.
- CSRF está desactivado porque no usamos cookies de sesión.

## Consecuencias

- ✅ Simple de escalar y de probar; no hay tabla de sesiones.
- ⚠️ Un token no se puede revocar antes de que expire. Si se desactiva una cuenta o se le cambian los roles,
  el cambio aplica cuando la persona vuelve a iniciar sesión (máximo 8 horas). Para este proyecto es aceptable;
  si se necesitara revocación inmediata, se agregarían *refresh tokens* o una lista de tokens revocados.
- ⚠️ `JWT_SECRET` es crítico: si se filtra, cualquiera puede fabricar tokens. Nunca se sube a Git.
