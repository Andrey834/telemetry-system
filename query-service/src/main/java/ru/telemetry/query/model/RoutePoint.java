package ru.telemetry.query.model;

import java.time.Instant;

/** Точка маршрута — ответ GET /devices/{id}/history, для прорисовки трека на карте. */
public record RoutePoint(double lat, double lon, Double speedKmh, Instant recordedAt) {
}
