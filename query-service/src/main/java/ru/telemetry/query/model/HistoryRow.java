package ru.telemetry.query.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** Строка telemetry_history (read-реплика) — lat/lon/speed как BigDecimal (тип колонки NUMERIC). */
@Table("telemetry_history")
public record HistoryRow(@Id Long id, String deviceId, BigDecimal lat, BigDecimal lon,
                          BigDecimal speedKmh, Instant recordedAt) {
}
