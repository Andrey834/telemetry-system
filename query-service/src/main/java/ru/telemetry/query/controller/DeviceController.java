package ru.telemetry.query.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.telemetry.query.model.DeviceEvent;
import ru.telemetry.query.model.DeviceView;
import ru.telemetry.query.model.FleetActivityPoint;
import ru.telemetry.query.model.RoutePoint;
import ru.telemetry.query.model.TelemetryState;
import ru.telemetry.query.service.DeviceQueryService;

import java.time.Duration;
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

    /** Весь реестр устройств (Postgres) + текущее состояние/статус (Redis), не только "кто жив". */
    @GetMapping
    public Flux<DeviceView> getDevices(@RequestParam(required = false) List<String> ids) {
        return ids == null ? queryService.findAllWithStatus() : queryService.findAllWithStatus(ids);
    }

    @GetMapping("/{deviceId}/history")
    public Flux<RoutePoint> getHistory(@PathVariable String deviceId,
                                        @RequestParam(defaultValue = "200") int limit) {
        return queryService.findHistory(deviceId, limit);
    }

    /** Активность всего парка (сообщений/минуту) — график "активность парка во времени". */
    @GetMapping("/activity")
    public Flux<FleetActivityPoint> getActivity(@RequestParam(defaultValue = "60") int minutes) {
        return queryService.findActivity(minutes);
    }

    /** Журнал событий (переходы online/offline) за последние hours часов, вычисляется на лету
     * из разрывов в истории. */
    @GetMapping("/events")
    public Flux<DeviceEvent> getEvents(@RequestParam(defaultValue = "24") int hours,
                                        @RequestParam(defaultValue = "50") int limit) {
        return queryService.findEvents(hours, limit);
    }

    /** Push вместо поллинга: сервис сам опрашивает Redis раз в 2с и шлёт клиенту только когда
     * список реально изменился (distinctUntilChanged) — не Redis pub/sub между сервисами, чтобы
     * не плодить новую связь между telemetry-processor и query-service ради этого. */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<List<DeviceView>>> streamDevices() {
        return Flux.interval(Duration.ZERO, Duration.ofSeconds(2))
                .flatMap(tick -> queryService.findAllWithStatus().collectList())
                .distinctUntilChanged()
                .map(list -> ServerSentEvent.<List<DeviceView>>builder(list).build());
    }
}
