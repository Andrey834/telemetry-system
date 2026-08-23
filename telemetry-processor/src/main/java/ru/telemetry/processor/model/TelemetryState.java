package ru.telemetry.processor.model;

import java.time.Instant;

/** Значение в Redis по ключу deviceId — «где сейчас находится устройство прямо сейчас». */
public record TelemetryState(
        String deviceId,
        Long routeId,
        double lat,
        double lon,
        Double speedKmh,
        Instant recordedAt
) {
}
