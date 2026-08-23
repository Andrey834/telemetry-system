package ru.telemetry.processor.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Table("telemetry_history")
public class TelemetryHistory {
    @Id
    private Long id;
    private String deviceId;
    private Long routeId;
    private BigDecimal lat;
    private BigDecimal lon;
    private BigDecimal speedKmh;
    private Instant recordedAt;
    private Instant ingestedAt;
}
