package ru.telemetry.processor.repository;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import ru.telemetry.processor.model.TelemetryHistory;

import java.math.BigDecimal;
import java.time.Instant;

@Repository
public interface TelemetryHistoryRepository extends R2dbcRepository<TelemetryHistory, Long> {

    /**
     * Идемпотентная запись истории: at-least-once доставка из Kafka означает, что одно и то же
     * событие может прийти повторно (после rebalance, после retry продюсера). Уникальный индекс
     * на (device_id, recorded_at) + ON CONFLICT DO NOTHING гарантирует, что повторная доставка
     * не создаёт дубликат строки — то же самое решение, что уже применялось для обезличивания
     * персональных данных в podarok86.ru, только здесь идемпотентность, а не GDPR.
     */
    @Modifying
    @Query("""
            INSERT INTO telemetry_history (device_id, route_id, lat, lon, speed_kmh, recorded_at, ingested_at)
            VALUES (:deviceId, :routeId, :lat, :lon, :speedKmh, :recordedAt, :ingestedAt)
            ON CONFLICT (device_id, recorded_at) DO NOTHING
            """)
    Mono<Integer> insertIfAbsent(@Param("deviceId") String deviceId,
                                  @Param("routeId") Long routeId,
                                  @Param("lat") BigDecimal lat,
                                  @Param("lon") BigDecimal lon,
                                  @Param("speedKmh") BigDecimal speedKmh,
                                  @Param("recordedAt") Instant recordedAt,
                                  @Param("ingestedAt") Instant ingestedAt);
}
