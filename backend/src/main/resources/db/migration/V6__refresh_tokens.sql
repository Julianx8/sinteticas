-- Refresh tokens: permiten tokens de acceso cortos (15 min) sin pedir la contraseña a cada rato,
-- y cerrar o revocar sesiones (logout, cuenta desactivada, cambio de contraseña).
--
-- Nunca se guarda el token: solo su hash SHA-256. Si la tabla se filtra, los tokens no sirven.
-- Cada uso "rota" el token: el viejo queda revocado y apunta al nuevo (replaced_by).
-- family_id agrupa la cadena de rotaciones de un mismo login: si alguien reutiliza un token ya
-- rotado (señal de robo), se revoca toda la familia.

CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    family_id   UUID        NOT NULL,
    token_hash  VARCHAR(64) NOT NULL CONSTRAINT ux_refresh_tokens_hash UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    replaced_by UUID REFERENCES refresh_tokens (id) ON DELETE SET NULL,
    ip          VARCHAR(45),
    user_agent  VARCHAR(255),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX ix_refresh_tokens_family ON refresh_tokens (family_id);
CREATE INDEX ix_refresh_tokens_expires ON refresh_tokens (expires_at);
