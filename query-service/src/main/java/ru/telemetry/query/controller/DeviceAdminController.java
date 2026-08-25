package ru.telemetry.query.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import ru.telemetry.query.service.DeviceAdminService;

/** POST /devices — регистрация нового устройства. Доступ ограничен ролью ADMIN (см. SecurityConfig). */
@RestController
@RequestMapping("/devices")
public class DeviceAdminController {

    private final DeviceAdminService deviceAdminService;

    public DeviceAdminController(DeviceAdminService deviceAdminService) {
        this.deviceAdminService = deviceAdminService;
    }

    public record RegisterDeviceRequest(String deviceId, String name, String groupName) {
    }

    @PostMapping
    public Mono<ResponseEntity<DeviceAdminService.RegisteredDevice>> register(@RequestBody RegisterDeviceRequest request) {
        if (isBlank(request.deviceId()) || isBlank(request.name()) || isBlank(request.groupName())) {
            return Mono.just(ResponseEntity.badRequest().build());
        }
        return deviceAdminService.register(request.deviceId(), request.name(), request.groupName())
                .map(registered -> ResponseEntity.status(HttpStatus.CREATED).body(registered));
    }

    public record UpdateDeviceRequest(String name, String groupName, boolean active) {
    }

    @PatchMapping("/{deviceId}")
    public Mono<ResponseEntity<Void>> update(@PathVariable String deviceId, @RequestBody UpdateDeviceRequest request) {
        if (isBlank(request.name()) || isBlank(request.groupName())) {
            return Mono.just(ResponseEntity.badRequest().build());
        }
        return deviceAdminService.update(deviceId, request.name(), request.groupName(), request.active())
                .thenReturn(ResponseEntity.noContent().<Void>build());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
