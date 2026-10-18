-- Enlaces de "olvidé mi contraseña". Como en refresh_tokens, solo se guarda el hash SHA-256 del token.
-- Un token sirve una sola vez (used_at) y vence en 1 hora.

CREATE TABLE password_reset_tokens (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL CONSTRAINT ux_password_reset_tokens_hash UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_password_reset_tokens_user ON password_reset_tokens (user_id, created_at DESC);
