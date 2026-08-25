package ru.telemetry.query.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import ru.telemetry.query.model.FleetActivityPoint;
import ru.telemetry.query.model.GapRow;
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

    /** Разрывы между соседними точками истории каждого устройства за последние hoursBack часов —
     * основа журнала событий (DeviceQueryService.findEvents). Считается на лету, без отдельной
     * таблицы/детектора: у query-service 2 реплики, живой таймер-детектор внутри сервиса писал бы
     * дублирующиеся события с каждой реплики независимо. Фильтр по реестру devices — без него
     * в журнал попадают и осиротевшие device_id (например, из нагрузочного теста с одноразовыми
     * id), которых нет в реестре и никогда не будет. */
    @Query("""
            SELECT device_id, recorded_at,
                   LAG(recorded_at) OVER (PARTITION BY device_id ORDER BY recorded_at) AS previous_recorded_at,
                   EXTRACT(EPOCH FROM (recorded_at - LAG(recorded_at) OVER (PARTITION BY device_id ORDER BY recorded_at))) AS gap_seconds
            FROM telemetry_history
            WHERE recorded_at > now() - make_interval(hours => :hoursBack)
              AND device_id IN (SELECT device_id FROM devices)
            ORDER BY device_id, recorded_at
            """)
    Flux<GapRow> findGaps(@Param("hoursBack") int hoursBack);
}
