-- Пользователи dashboard (JWT-логин вместо Basic Auth на Ingress, см. query-service auth).
CREATE TABLE users (
    id            BIGSERIAL    PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    password_hash VARCHAR(72)  NOT NULL, -- bcrypt
    role          VARCHAR(16)  NOT NULL CHECK (role IN ('OPERATOR', 'ADMIN')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
