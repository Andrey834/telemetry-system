package ru.telemetry.ingestion.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import ru.telemetry.ingestion.model.Device;

public interface DeviceRepository extends ReactiveCrudRepository<Device, String> {
}
