package ru.telemetry.processor.service;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveSetOperations;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOffset;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.test.StepVerifier;
import ru.telemetry.processor.dto.TelemetryEvent;
import ru.telemetry.processor.model.TelemetryState;
import ru.telemetry.processor.repository.TelemetryHistoryRepository;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TelemetryProcessingServiceTest {

    @Mock
    private KafkaReceiver<String, TelemetryEvent> receiver;

    @Mock
    private ReactiveRedisTemplate<String, TelemetryState> redisTemplate;

    @Mock
    private ReactiveValueOperations<String, TelemetryState> valueOperations;

    @Mock
    private ReactiveStringRedisTemplate stringRedisTemplate;

    @Mock
    private ReactiveSetOperations<String, String> setOperations;

    @Mock
    private TelemetryHistoryRepository historyRepository;

    @Mock
    private ReceiverOffset receiverOffset;

    private TelemetryProcessingService service;

    @BeforeEach
    void setUp() {
        service = new TelemetryProcessingService(receiver, redisTemplate, stringRedisTemplate, historyRepository);
    }

    private ReceiverRecord<String, TelemetryEvent> record(TelemetryEvent event) {
        ConsumerRecord<String, TelemetryEvent> consumerRecord =
                new ConsumerRecord<>("telemetry.raw", 0, 10L, event.deviceId(), event);
        return new ReceiverRecord<>(consumerRecord, receiverOffset);
    }

    @Test
    void processRecord_success_writesRedisAndPostgresThenAcknowledges() {
        TelemetryEvent event = new TelemetryEvent("bus-42", 7L, 55.75, 37.61, 42.0,
                Instant.parse("2026-08-18T10:00:00Z"), Instant.parse("2026-08-18T10:00:01Z"));

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.set(anyString(), any(TelemetryState.class))).willReturn(Mono.just(true));
        given(stringRedisTemplate.opsForSet()).willReturn(setOperations);
        given(setOperations.add(anyString(), org.mockito.ArgumentMatchers.<String>any())).willReturn(Mono.just(1L));
        given(historyRepository.insertIfAbsent(any(), any(), any(BigDecimal.class), any(BigDecimal.class),
                any(BigDecimal.class), any(Instant.class), any(Instant.class)))
                .willReturn(Mono.just(1));

        StepVerifier.create(service.processRecord(record(event))).verifyComplete();

        ArgumentCaptor<TelemetryState> stateCaptor = ArgumentCaptor.forClass(TelemetryState.class);
        verify(valueOperations).set(org.mockito.ArgumentMatchers.eq("telemetry:state:bus-42"), stateCaptor.capture());
        assertThat(stateCaptor.getValue().deviceId()).isEqualTo("bus-42");
        assertThat(stateCaptor.getValue().lat()).isEqualTo(55.75);

        verify(historyRepository).insertIfAbsent(
                org.mockito.ArgumentMatchers.eq("bus-42"),
                org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq(BigDecimal.valueOf(55.75)),
                org.mockito.ArgumentMatchers.eq(BigDecimal.valueOf(37.61)),
                org.mockito.ArgumentMatchers.eq(BigDecimal.valueOf(42.0)),
                org.mockito.ArgumentMatchers.eq(event.recordedAt()),
                org.mockito.ArgumentMatchers.eq(event.ingestedAt()));

        verify(receiverOffset).acknowledge();
    }

    @Test
    void processRecord_redisFails_doesNotAcknowledgeAndDoesNotPropagateError() {
        TelemetryEvent event = new TelemetryEvent("bus-1", null, 55.0, 37.0, null,
                Instant.now(), Instant.now());

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.set(anyString(), any(TelemetryState.class)))
                .willReturn(Mono.error(new RuntimeException("redis недоступен")));
        // Mono.then(other) строит аргумент other сразу (обычный вызов Java-метода), но подписывается
        // на него только если upstream завершится успешно — здесь Redis падает раньше, поэтому
        // реального обращения к SADD/Postgres не происходит, хотя сами объекты Mono должны быть валидны.
        given(stringRedisTemplate.opsForSet()).willReturn(setOperations);
        given(setOperations.add(anyString(), org.mockito.ArgumentMatchers.<String>any())).willReturn(Mono.just(1L));
        given(historyRepository.insertIfAbsent(any(), any(), any(BigDecimal.class), any(BigDecimal.class),
                any(), any(Instant.class), any(Instant.class)))
                .willReturn(Mono.just(1));

        StepVerifier.create(service.processRecord(record(event))).verifyComplete();

        verify(receiverOffset, never()).acknowledge();
    }
}
