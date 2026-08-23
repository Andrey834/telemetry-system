package ru.telemetry.ingestion.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;

/** Входной контракт REST API — то, что реально присылает датчик/бортовое устройство. */
public record TelemetryRequest(
        @NotBlank String deviceId,
        Long routeId,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double lat,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double lon,
        @PositiveOrZero Double speedKmh,
        @NotNull Instant timestamp
) {
}
