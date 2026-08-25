package ru.telemetry.query.model;

import java.time.Instant;

/** Сырая строка запроса с LAG() по telemetry_history — разрыв между соседними точками одного
 * устройства. previousRecordedAt/gapSeconds — null для самой первой точки устройства в выборке
 * (LAG() возвращает NULL, если предыдущей строки нет). */
public record GapRow(String deviceId, Instant recordedAt, Instant previousRecordedAt, Double gapSeconds) {
}
