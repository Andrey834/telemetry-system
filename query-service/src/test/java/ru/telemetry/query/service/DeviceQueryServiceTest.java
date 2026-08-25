package ru.telemetry.query.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveSetOperations;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.telemetry.query.model.Device;
import ru.telemetry.query.model.DeviceStatus;
import ru.telemetry.query.model.FleetActivityPoint;
import ru.telemetry.query.model.HistoryRow;
import ru.telemetry.query.model.TelemetryState;
import ru.telemetry.query.repository.DeviceRepository;
import ru.telemetry.query.repository.TelemetryHistoryRepository;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class DeviceQueryServiceTest {

    @Mock
    private ReactiveRedisTemplate<String, TelemetryState> redisTemplate;

    @Mock
    private ReactiveValueOperations<String, TelemetryState> valueOperations;

    @Mock
    private ReactiveStringRedisTemplate stringRedisTemplate;

    @Mock
    private ReactiveSetOperations<String, String> setOperations;

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private TelemetryHistoryRepository historyRepository;

    private DeviceQueryService service;

    @BeforeEach
    void setUp() {
        service = new DeviceQueryService(redisTemplate, stringRedisTemplate, deviceRepository, historyRepository);
    }

    @Test
    void findByDeviceId_found_returnsState() {
        TelemetryState state = new TelemetryState("bus-42", 7L, 55.75, 37.61, 42.0, Instant.now());
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(eq("telemetry:state:bus-42"))).willReturn(Mono.just(state));

        StepVerifier.create(service.findByDeviceId("bus-42"))
                .expectNext(state)
                .verifyComplete();
    }

    @Test
    void findByDeviceId_notFound_returnsEmpty() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(eq("telemetry:state:unknown"))).willReturn(Mono.empty());

        StepVerifier.create(service.findByDeviceId("unknown")).verifyComplete();
    }

    @Test
    void findByDeviceIds_filtersOutMissingKeys() {
        TelemetryState busOne = new TelemetryState("bus-1", 1L, 10.0, 20.0, 30.0, Instant.now());
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.multiGet(List.of("telemetry:state:bus-1", "telemetry:state:bus-2")))
                .willReturn(Mono.just(Arrays.asList(busOne, null)));

        StepVerifier.create(service.findByDeviceIds(List.of("bus-1", "bus-2")))
                .expectNext(busOne)
                .verifyComplete();
    }

    @Test
    void findAll_readsDeviceSetThenResolvesStates() {
        TelemetryState busOne = new TelemetryState("bus-1", 1L, 10.0, 20.0, 30.0, Instant.now());
        given(stringRedisTemplate.opsForSet()).willReturn(setOperations);
        given(setOperations.members("telemetry:devices")).willReturn(Flux.just("bus-1"));
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.multiGet(List.of("telemetry:state:bus-1")))
                .willReturn(Mono.just(List.of(busOne)));

        StepVerifier.create(service.findAll())
                .expectNext(busOne)
                .verifyComplete();
    }

    @Test
    void findAllWithStatus_recentDevice_isOnline() {
        Device device = new Device("bus-1", "Автобус 1", "buses");
        TelemetryState state = new TelemetryState("bus-1", 1L, 10.0, 20.0, 30.0, Instant.now());
        given(deviceRepository.findAll()).willReturn(Flux.just(device));
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.multiGet(List.of("telemetry:state:bus-1"))).willReturn(Mono.just(List.of(state)));

        StepVerifier.create(service.findAllWithStatus())
                .assertNext(view -> {
                    assertThat(view.status()).isEqualTo(DeviceStatus.ONLINE);
                    assertThat(view.name()).isEqualTo("Автобус 1");
                    assertThat(view.groupName()).isEqualTo("buses");
                    assertThat(view.lat()).isEqualTo(10.0);
                })
                .verifyComplete();
    }

    @Test
    void findAllWithStatus_oldButRecentEnough_isStale() {
        Device device = new Device("bus-1", "Автобус 1", "buses");
        TelemetryState state = new TelemetryState("bus-1", 1L, 10.0, 20.0, 30.0,
                Instant.now().minus(Duration.ofMinutes(2)));
        given(deviceRepository.findAll()).willReturn(Flux.just(device));
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.multiGet(List.of("telemetry:state:bus-1"))).willReturn(Mono.just(List.of(state)));

        StepVerifier.create(service.findAllWithStatus())
                .assertNext(view -> assertThat(view.status()).isEqualTo(DeviceStatus.STALE))
                .verifyComplete();
    }

    @Test
    void findAllWithStatus_veryOld_isOffline() {
        Device device = new Device("bus-1", "Автобус 1", "buses");
        TelemetryState state = new TelemetryState("bus-1", 1L, 10.0, 20.0, 30.0,
                Instant.now().minus(Duration.ofHours(1)));
        given(deviceRepository.findAll()).willReturn(Flux.just(device));
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.multiGet(List.of("telemetry:state:bus-1"))).willReturn(Mono.just(List.of(state)));

        StepVerifier.create(service.findAllWithStatus())
                .assertNext(view -> assertThat(view.status()).isEqualTo(DeviceStatus.OFFLINE))
                .verifyComplete();
    }

    @Test
    void findAllWithStatus_neverReported_isOfflineWithNullCoordinates() {
        Device device = new Device("bus-1", "Автобус 1", "buses");
        given(deviceRepository.findAll()).willReturn(Flux.just(device));
        // Устройство есть в реестре, но ни разу не присылало данные — findByDeviceIds попадает
        // на ветку с непустым списком ключей, Redis отдаёт пустой результат (не null-элемент).
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.multiGet(List.of("telemetry:state:bus-1")))
                .willReturn(Mono.just(Arrays.asList((TelemetryState) null)));

        StepVerifier.create(service.findAllWithStatus())
                .assertNext(view -> {
                    assertThat(view.status()).isEqualTo(DeviceStatus.OFFLINE);
                    assertThat(view.lat()).isNull();
                    assertThat(view.recordedAt()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void findHistory_returnsPointsOldestFirst() {
        Instant older = Instant.now().minusSeconds(60);
        Instant newer = Instant.now();
        // Репозиторий уже отдаёт DESC (новые первыми) — сервис должен развернуть в хронологический порядок.
        given(historyRepository.findByDeviceId("bus-1", 10)).willReturn(Flux.just(
                new HistoryRow(2L, "bus-1", BigDecimal.valueOf(11.0), BigDecimal.valueOf(21.0), BigDecimal.valueOf(50.0), newer),
                new HistoryRow(1L, "bus-1", BigDecimal.valueOf(10.0), BigDecimal.valueOf(20.0), BigDecimal.valueOf(30.0), older)
        ));

        StepVerifier.create(service.findHistory("bus-1", 10))
                .assertNext(point -> assertThat(point.recordedAt()).isEqualTo(older))
                .assertNext(point -> assertThat(point.recordedAt()).isEqualTo(newer))
                .verifyComplete();
    }

    @Test
    void findActivity_delegatesToRepositoryWithGivenWindow() {
        FleetActivityPoint point = new FleetActivityPoint(Instant.now(), 42L);
        given(historyRepository.countByTimeBucket(60)).willReturn(Flux.just(point));

        StepVerifier.create(service.findActivity(60))
                .expectNext(point)
                .verifyComplete();
    }
}
