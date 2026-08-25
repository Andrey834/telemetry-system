package ru.telemetry.query.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import ru.telemetry.query.model.Device;

public interface DeviceRepository extends ReactiveCrudRepository<Device, String> {
}
