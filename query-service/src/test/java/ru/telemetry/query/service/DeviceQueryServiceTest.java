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
import ru.telemetry.query.model.TelemetryState;
import ru.telemetry.query.repository.DeviceRepository;
import ru.telemetry.query.repository.TelemetryHistoryRepository;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

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
}
