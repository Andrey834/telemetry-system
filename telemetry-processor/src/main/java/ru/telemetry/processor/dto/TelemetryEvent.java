package ru.telemetry.processor.dto;

import java.time.Instant;

/**
 * Контракт события в Kafka-топике telemetry.raw — должен оставаться в точности совместим
 * с ru.telemetry.ingestion.dto.TelemetryEvent на стороне ingestion-service (общий формат
 * сообщения, а не общий Java-класс — сервисы намеренно не делят между собой jar с моделью).
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
}
