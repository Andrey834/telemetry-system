package ru.telemetry.ingestion.dto;

import java.time.Instant;

/**
 * Контракт события в Kafka-топике — намеренно отдельный от {@link TelemetryRequest}.
 * REST-контракт и контракт события эволюционируют независимо: новое поле в API не обязано
 * сразу попадать в топик, и наоборот.
 */
public record TelemetryEvent(
        String deviceId,
        Long routeId,
        double lat,
        double lon,
        Double speedKmh,
        Instant recordedAt,
        Instant ingestedAt
) {
    public static TelemetryEvent from(TelemetryRequest request, Instant ingestedAt) {
        return new TelemetryEvent(
                request.deviceId(),
                request.routeId(),
                request.lat(),
                request.lon(),
                request.speedKmh(),
                request.timestamp(),
                ingestedAt
        );
    }
}
