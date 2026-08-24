package ru.telemetry.query.service;

import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.telemetry.query.model.TelemetryState;

import java.util.List;
import java.util.Objects;

@Service
public class DeviceQueryService {

    private static final String KEY_PREFIX = "telemetry:state:";
    // То же множество, в которое telemetry-processor пишет deviceId при каждом успешном апдейте —
    // отсюда findAll() узнаёт, какие устройства вообще существуют, без SCAN по всему Redis.
    private static final String DEVICE_SET_KEY = "telemetry:devices";

    private final ReactiveRedisTemplate<String, TelemetryState> redisTemplate;
    private final ReactiveStringRedisTemplate stringRedisTemplate;

    public DeviceQueryService(ReactiveRedisTemplate<String, TelemetryState> redisTemplate,
                               ReactiveStringRedisTemplate stringRedisTemplate) {
        this.redisTemplate = redisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
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
}
