package ru.telemetry.query.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import ru.telemetry.query.model.FleetActivityPoint;
import ru.telemetry.query.model.HistoryRow;

@Repository
public interface TelemetryHistoryRepository extends R2dbcRepository<HistoryRow, Long> {

    @Query("""
            SELECT id, device_id, lat, lon, speed_kmh, recorded_at
            FROM telemetry_history
            WHERE device_id = :deviceId
            ORDER BY recorded_at DESC
            LIMIT :limit
            """)
    Flux<HistoryRow> findByDeviceId(@Param("deviceId") String deviceId, @Param("limit") int limit);

    /** Активность всего парка (не одного устройства) — число сообщений в минутных бакетах за
     * последние sinceMinutes, для графика "активность парка во времени" на dashboard. */
    @Query("""
            SELECT date_trunc('minute', recorded_at) AS bucket, count(*) AS count
            FROM telemetry_history
            WHERE recorded_at > now() - make_interval(mins => :sinceMinutes)
            GROUP BY bucket
            ORDER BY bucket
            """)
    Flux<FleetActivityPoint> countByTimeBucket(@Param("sinceMinutes") int sinceMinutes);
}
