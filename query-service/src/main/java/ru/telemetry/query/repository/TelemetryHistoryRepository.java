package ru.telemetry.query.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
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
}
