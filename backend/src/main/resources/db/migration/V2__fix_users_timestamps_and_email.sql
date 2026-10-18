-- V1 ya está en develop, así que las correcciones van en esta migración.

-- 1. Fechas con zona horaria: TIMESTAMP sin zona es ambiguo en un sistema de reservas.
--    Los valores existentes se interpretan como UTC.
ALTER TABLE users
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

ALTER TABLE users
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE;

-- 2. Email único sin distinguir mayúsculas: "Juan@x.com" y "juan@x.com" son el mismo usuario.
ALTER TABLE users DROP CONSTRAINT users_email_key;
CREATE UNIQUE INDEX ux_users_email_lower ON users (lower(email));

-- 3. Índice para buscar los usuarios de un rol (user_roles solo tiene PK (user_id, role_id)).
CREATE INDEX ix_user_roles_role_id ON user_roles (role_id);
