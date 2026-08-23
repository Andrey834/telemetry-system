package ru.telemetry.query.model;

import java.time.Instant;

/**
 * Контракт значения в Redis по ключу telemetry:state:{deviceId} — должен оставаться в точности
 * совместим с ru.telemetry.processor.model.TelemetryState на стороне telemetry-processor,
 * который эти значения пишет (общий формат данных, не общий Java-класс).
 */
public record TelemetryState(
        String deviceId,
        Long routeId,
        double lat,
        double lon,
        Double speedKmh,
        Instant recordedAt
) {
}
