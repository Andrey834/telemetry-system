package ru.telemetry.query.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.telemetry.query.model.TelemetryState;
import ru.telemetry.query.service.DeviceQueryService;

import java.util.List;

@RestController
@RequestMapping("/devices")
public class DeviceController {

    private final DeviceQueryService queryService;

    public DeviceController(DeviceQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/{deviceId}")
    public Mono<ResponseEntity<TelemetryState>> getDevice(@PathVariable String deviceId) {
        return queryService.findByDeviceId(deviceId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping
    public Flux<TelemetryState> getDevices(@RequestParam(required = false) List<String> ids) {
        return ids == null ? queryService.findAll() : queryService.findByDeviceIds(ids);
    }
}
