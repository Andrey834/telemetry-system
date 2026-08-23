CREATE TABLE telemetry_history (
    id            BIGSERIAL PRIMARY KEY,
    device_id     VARCHAR(64)     NOT NULL,
    route_id      BIGINT,
    lat           NUMERIC(9, 6)   NOT NULL,
    lon           NUMERIC(9, 6)   NOT NULL,
    speed_kmh     NUMERIC(6, 2),
    recorded_at   TIMESTAMPTZ     NOT NULL,
    ingested_at   TIMESTAMPTZ     NOT NULL,

    CONSTRAINT uq_telemetry_history_device_recorded UNIQUE (device_id, recorded_at)
);

CREATE INDEX idx_telemetry_history_device_id ON telemetry_history (device_id, recorded_at DESC);
CREATE INDEX idx_telemetry_history_route_id ON telemetry_history (route_id);
