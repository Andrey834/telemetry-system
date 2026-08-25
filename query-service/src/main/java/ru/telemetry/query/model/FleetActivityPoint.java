package ru.telemetry.query.model;

import java.time.Instant;

/** Точка графика активности парка — ответ GET /devices/activity, сколько сообщений телеметрии
 * пришло от всех устройств суммарно в минутном бакете. */
public record FleetActivityPoint(Instant bucket, long count) {
}
