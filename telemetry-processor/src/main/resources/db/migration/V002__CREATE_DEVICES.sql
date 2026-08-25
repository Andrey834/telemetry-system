-- Реестр устройств — источник правды для API-key авторизации ingestion-service и для
-- группировки/статуса устройств на dashboard. Заполняется вручную/сидом (см. V004), без REST API
-- регистрации — для объёма этого проекта управление парком устройств не требуется.
CREATE TABLE devices (
    device_id    VARCHAR(64)  PRIMARY KEY,
    name         VARCHAR(128) NOT NULL,
    group_name   VARCHAR(64)  NOT NULL,
    -- SHA-256(hex) ключа устройства — не bcrypt: это высокоэнтропийный сгенерированный токен,
    -- а не пароль человека, детерминированный хеш достаточен и не тормозит каждый POST /telemetry.
    api_key_hash VARCHAR(64)  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_devices_group_name ON devices (group_name);
