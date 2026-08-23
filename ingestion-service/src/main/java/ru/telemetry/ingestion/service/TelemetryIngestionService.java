package ru.telemetry.ingestion.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.telemetry.ingestion.dto.TelemetryEvent;
import ru.telemetry.ingestion.dto.TelemetryRequest;

import java.time.Instant;

@Service
public class TelemetryIngestionService {

    private static final Logger log = LoggerFactory.getLogger(TelemetryIngestionService.class);

    private final KafkaTemplate<String, TelemetryEvent> kafkaTemplate;
    private final String topic;

    public TelemetryIngestionService(KafkaTemplate<String, TelemetryEvent> kafkaTemplate,
                                      @Value("${app.telemetry.topic}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    /**
     * Публикует событие в Kafka с ключом = deviceId — гарантирует, что все координаты одного
     * устройства попадут в одну партицию и будут обработаны строго по порядку поступления.
     * KafkaTemplate.send(...) сам по себе не блокирует вызывающий поток — реальный сетевой I/O
     * идёт в клиенте Kafka асинхронно, поэтому оборачивание CompletableFuture в Mono безопасно
     * вызывать прямо из WebFlux-хендлера, не блокируя event loop.
     */
    public Mono<Void> ingest(TelemetryRequest request) {
        TelemetryEvent event = TelemetryEvent.from(request, Instant.now());

        return Mono.fromFuture(kafkaTemplate.send(topic, event.deviceId(), event))
                .doOnNext(result -> logSuccess(event, result))
                .doOnError(ex -> log.error("Не удалось опубликовать телеметрию deviceId={}: {}",
                        event.deviceId(), ex.getMessage(), ex))
                .then();
    }

    private void logSuccess(TelemetryEvent event, SendResult<String, TelemetryEvent> result) {
        log.debug("Опубликовано deviceId={} -> partition={}, offset={}",
                event.deviceId(),
                result.getRecordMetadata().partition(),
                result.getRecordMetadata().offset());
    }
}
