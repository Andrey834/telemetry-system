package ru.telemetry.query.service;

import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.telemetry.query.model.Device;
import ru.telemetry.query.model.DeviceEvent;
import ru.telemetry.query.model.DeviceStatus;
import ru.telemetry.query.model.DeviceView;
import ru.telemetry.query.model.FleetActivityPoint;
import ru.telemetry.query.model.RoutePoint;
import ru.telemetry.query.model.TelemetryState;
import ru.telemetry.query.repository.DeviceRepository;
import ru.telemetry.query.repository.TelemetryHistoryRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class DeviceQueryService {

    private static final String KEY_PREFIX = "telemetry:state:";
    // То же множество, в которое telemetry-processor пишет deviceId при каждом успешном апдейте —
    // отсюда findAll() узнаёт, какие устройства вообще существуют, без SCAN по всему Redis.
    private static final String DEVICE_SET_KEY = "telemetry:devices";

    // Пороги статуса — устройство считается online, пока шлёт данные заметно чаще, чем раз в
    // 30с (dashboard опрашивает раз в 5с), stale — задержалось, но ещё может вернуться,
    // offline — молчит достаточно долго, чтобы считать связь потерянной.
    private static final Duration ONLINE_THRESHOLD = Duration.ofSeconds(30);
    private static final Duration STALE_THRESHOLD = Duration.ofMinutes(5);

    private final ReactiveRedisTemplate<String, TelemetryState> redisTemplate;
    private final ReactiveStringRedisTemplate stringRedisTemplate;
    private final DeviceRepository deviceRepository;
    private final TelemetryHistoryRepository historyRepository;

    public DeviceQueryService(ReactiveRedisTemplate<String, TelemetryState> redisTemplate,
                               ReactiveStringRedisTemplate stringRedisTemplate,
                               DeviceRepository deviceRepository,
                               TelemetryHistoryRepository historyRepository) {
        this.redisTemplate = redisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.deviceRepository = deviceRepository;
        this.historyRepository = historyRepository;
    }

    public Mono<TelemetryState> findByDeviceId(String deviceId) {
        return redisTemplate.opsForValue().get(KEY_PREFIX + deviceId);
    }

    public Flux<TelemetryState> findByDeviceIds(List<String> deviceIds) {
        if (deviceIds.isEmpty()) {
            // Redis MGET не допускает пустой список ключей (Lettuce кидает IllegalArgumentException) —
            // findAll() на пустом telemetry:devices попадает сюда до появления первой телеметрии.
            return Flux.empty();
        }
        List<String> keys = deviceIds.stream().map(id -> KEY_PREFIX + id).toList();
        return redisTemplate.opsForValue().multiGet(keys)
                // Redis MGET кладёт null на месте отсутствующих ключей — Flux.fromIterable не
                // допускает null-элементы (нарушение Reactive Streams), поэтому фильтруем ДО
                // построения Flux, а не после.
                .flatMapMany(values -> Flux.fromIterable(values.stream().filter(Objects::nonNull).toList()));
    }

    public Flux<TelemetryState> findAll() {
        return stringRedisTemplate.opsForSet().members(DEVICE_SET_KEY)
                .collectList()
                .flatMapMany(this::findByDeviceIds);
    }

    /** Все устройства из реестра (Postgres), обогащённые текущим состоянием из Redis и статусом. */
    public Flux<DeviceView> findAllWithStatus() {
        return buildViews(deviceRepository.findAll());
    }

    public Flux<DeviceView> findAllWithStatus(List<String> deviceIds) {
        return buildViews(deviceRepository.findAllById(deviceIds));
    }

    private Flux<DeviceView> buildViews(Flux<Device> devices) {
        return devices.collectList()
                .flatMapMany(list -> {
                    List<String> ids = list.stream().map(Device::deviceId).toList();
                    return findByDeviceIds(ids)
                            .collectMap(TelemetryState::deviceId)
                            .flatMapMany(stateByDeviceId -> Flux.fromIterable(list)
                                    .map(device -> toView(device, stateByDeviceId.get(device.deviceId()))));
                });
    }

    private DeviceView toView(Device device, TelemetryState state) {
        if (state == null) {
            return new DeviceView(device.deviceId(), device.name(), device.groupName(), device.active(),
                    DeviceStatus.OFFLINE, null, null, null, null);
        }
        return new DeviceView(device.deviceId(), device.name(), device.groupName(), device.active(),
                statusOf(state.recordedAt()), state.lat(), state.lon(), state.speedKmh(), state.recordedAt());
    }

    private DeviceStatus statusOf(Instant recordedAt) {
        Duration age = Duration.between(recordedAt, Instant.now());
        if (age.compareTo(ONLINE_THRESHOLD) <= 0) {
            return DeviceStatus.ONLINE;
        }
        return age.compareTo(STALE_THRESHOLD) <= 0 ? DeviceStatus.STALE : DeviceStatus.OFFLINE;
    }

    /** Маршрут устройства за последние записи истории (Postgres read-реплика), от старых к новым. */
    public Flux<RoutePoint> findHistory(String deviceId, int limit) {
        return historyRepository.findByDeviceId(deviceId, limit)
                .map(row -> new RoutePoint(
                        row.lat().doubleValue(),
                        row.lon().doubleValue(),
                        row.speedKmh() != null ? row.speedKmh().doubleValue() : null,
                        row.recordedAt()))
                .collectSortedList((a, b) -> a.recordedAt().compareTo(b.recordedAt()))
                .flatMapMany(Flux::fromIterable);
    }

    /** Активность всего парка (сообщений/минуту) за последние minutes — для графика на dashboard. */
    public Flux<FleetActivityPoint> findActivity(int minutes) {
        return historyRepository.countByTimeBucket(minutes);
    }

    /** Журнал событий за последние hoursBack часов — вычисляется из разрывов в telemetry_history
     * (см. TelemetryHistoryRepository.findGaps), а не хранится отдельно. Каждый разрыв длиннее
     * STALE_THRESHOLD даёт пару событий: OFFLINE в момент последней точки перед разрывом, ONLINE —
     * когда точки снова пошли. */
    public Flux<DeviceEvent> findEvents(int hoursBack, int limit) {
        return deviceRepository.findAll()
                .collectMap(Device::deviceId, Device::name)
                .flatMapMany(nameById -> historyRepository.findGaps(hoursBack)
                        .filter(row -> row.gapSeconds() != null && row.gapSeconds() >= STALE_THRESHOLD.toSeconds())
                        .flatMapIterable(row -> {
                            String name = nameById.getOrDefault(row.deviceId(), row.deviceId());
                            return List.of(
                                    new DeviceEvent(row.deviceId(), name, "OFFLINE", row.previousRecordedAt()),
                                    new DeviceEvent(row.deviceId(), name, "ONLINE", row.recordedAt()));
                        }))
                .collectSortedList(Comparator.comparing(DeviceEvent::occurredAt).reversed())
                .flatMapMany(events -> Flux.fromIterable(events.subList(0, Math.min(limit, events.size()))));
    }
}
