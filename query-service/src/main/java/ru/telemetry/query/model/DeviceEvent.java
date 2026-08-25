package ru.telemetry.query.model;

import java.time.Instant;

/** Точка журнала событий — ответ GET /devices/events. type: "OFFLINE"/"ONLINE". Вычисляется на
 * лету из разрывов в telemetry_history (DeviceQueryService.findEvents), не хранится отдельно. */
public record DeviceEvent(String deviceId, String deviceName, String type, Instant occurredAt) {
}
