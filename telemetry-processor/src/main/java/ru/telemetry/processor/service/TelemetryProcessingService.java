package ru.telemetry.processor.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverRecord;
import ru.telemetry.processor.dto.TelemetryEvent;
import ru.telemetry.processor.model.TelemetryState;
import ru.telemetry.processor.repository.TelemetryHistoryRepository;

import java.math.BigDecimal;

@Service
public class TelemetryProcessingService {

    private static final Logger log = LoggerFactory.getLogger(TelemetryProcessingService.class);
    private static final String REDIS_KEY_PREFIX = "telemetry:state:";
    // Множество известных deviceId — query-service использует его, чтобы отдать "все устройства"
    // без SCAN по всему Redis (SCAN по telemetry:state:* был бы дорогим и не гарантирует порядок).
    private static final String DEVICE_SET_KEY = "telemetry:devices";

    private final KafkaReceiver<String, TelemetryEvent> receiver;
    private final ReactiveRedisTemplate<String, TelemetryState> redisTemplate;
    private final ReactiveStringRedisTemplate stringRedisTemplate;
    private final TelemetryHistoryRepository historyRepository;

    private Disposable subscription;

    public TelemetryProcessingService(KafkaReceiver<String, TelemetryEvent> receiver,
                                       ReactiveRedisTemplate<String, TelemetryState> redisTemplate,
                                       ReactiveStringRedisTemplate stringRedisTemplate,
                                       TelemetryHistoryRepository historyRepository) {
        this.receiver = receiver;
        this.redisTemplate = redisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.historyRepository = historyRepository;
    }

    @PostConstruct
    public void start() {
        subscription = receiver.receive()
                // Группируем по партиции: внутри одной партиции concatMap строго сохраняет порядок
                // (координаты одного deviceId обрабатываются в порядке поступления), а разные
                // партиции (разные устройства) при этом обрабатываются параллельно через flatMap —
                // ровно то разделение "порядок внутри устройства + параллелизм между устройствами",
                // ради которого топик и партиционировался по deviceId ещё на этапе ingestion-service.
                .groupBy(record -> record.receiverOffset().topicPartition())
                .flatMap(partitionFlux -> partitionFlux.concatMap(this::processRecord))
                .subscribe(
                        v -> { /* обработка идёт по месту в processRecord */ },
                        ex -> log.error("Kafka-подписка telemetry-processor остановлена с ошибкой", ex)
                );
    }

    // package-private — намеренно вызывается напрямую из юнит-теста, без подъёма всей Kafka-подписки
    Mono<Void> processRecord(ReceiverRecord<String, TelemetryEvent> record) {
        TelemetryEvent event = record.value();
        TelemetryState state = toState(event);

        return redisTemplate.opsForValue().set(REDIS_KEY_PREFIX + event.deviceId(), state)
                .then(stringRedisTemplate.opsForSet().add(DEVICE_SET_KEY, event.deviceId()))
                .then(persistHistory(event))
                .doOnSuccess(inserted -> {
                    record.receiverOffset().acknowledge();
                    log.debug("Обработано deviceId={}, offset={}, историческая запись новая={}",
                            event.deviceId(), record.offset(), inserted != null && inserted > 0);
                })
                .then()
                // Ошибку не пробрасываем дальше — иначе один плохой message оборвал бы всю Kafka-подписку.
                // Offset не подтверждён -> при рестарте/rebalance сообщение придёт повторно.
                .onErrorResume(ex -> {
                    log.error("Не удалось обработать событие deviceId={} — offset не подтверждён, " +
                            "ожидается повторная доставка", event.deviceId(), ex);
                    return Mono.empty();
                });
    }

    private Mono<Integer> persistHistory(TelemetryEvent event) {
        return historyRepository.insertIfAbsent(
                event.deviceId(),
                event.routeId(),
                BigDecimal.valueOf(event.lat()),
                BigDecimal.valueOf(event.lon()),
                event.speedKmh() != null ? BigDecimal.valueOf(event.speedKmh()) : null,
                event.recordedAt(),
                event.ingestedAt());
    }

    private TelemetryState toState(TelemetryEvent event) {
        return new TelemetryState(
                event.deviceId(),
                event.routeId(),
                event.lat(),
                event.lon(),
                event.speedKmh(),
                event.recordedAt());
    }
}
