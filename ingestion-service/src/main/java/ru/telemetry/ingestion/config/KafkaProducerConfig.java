package ru.telemetry.ingestion.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import ru.telemetry.ingestion.dto.TelemetryEvent;

/**
 * Автоконфигурация Boot создаёт только {@code KafkaTemplate<Object, Object>} — из-за
 * инвариантности generic-типов он не подходит под {@code KafkaTemplate<String, TelemetryEvent>},
 * который ожидает {@link ru.telemetry.ingestion.service.TelemetryIngestionService}, поэтому бин
 * нужно объявить явно с конкретной параметризацией (свойства из spring.kafka.producer.* — те же).
 */
@Configuration
public class KafkaProducerConfig {

    @Bean
    public ProducerFactory<String, TelemetryEvent> producerFactory(KafkaProperties kafkaProperties) {
        return new DefaultKafkaProducerFactory<>(kafkaProperties.buildProducerProperties());
    }

    @Bean
    public KafkaTemplate<String, TelemetryEvent> kafkaTemplate(
            ProducerFactory<String, TelemetryEvent> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    // DefaultKafkaProducerFactory создаёт реальный KafkaProducer лениво, при первом send() —
    // без прогрева это ~5-7с (SASL-логин + TLS handshake + metadata fetch) на самом первом
    // запросе к /telemetry после старта пода. partitionsFor() форсирует создание продюсера
    // здесь, до того как под станет Ready (пробы уже учитывают эту задержку в initialDelaySeconds).
    @Bean
    public ApplicationRunner warmUpKafkaProducer(
            KafkaTemplate<String, TelemetryEvent> kafkaTemplate,
            @Value("${app.telemetry.topic}") String topic) {
        return args -> kafkaTemplate.partitionsFor(topic);
    }
}
