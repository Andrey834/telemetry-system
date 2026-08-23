package ru.telemetry.processor.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;
import ru.telemetry.processor.dto.TelemetryEvent;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Configuration
public class KafkaReceiverConfig {

    @Bean
    public KafkaReceiver<String, TelemetryEvent> telemetryReceiver(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${app.telemetry.topic}") String topic,
            @Value("${app.telemetry.consumer-group}") String groupId) {

        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, groupId,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false   // коммитим сами, только после успешной записи в Redis+Postgres
        );

        // Проект намеренно не тянет spring-kafka (весь Kafka I/O здесь на reactor-kafka ради
        // полностью неблокирующего стека) — поэтому десериализатор написан напрямую на Jackson,
        // а не взят готовым из spring-kafka.
        ReceiverOptions<String, TelemetryEvent> options = ReceiverOptions.<String, TelemetryEvent>create(props)
                .withValueDeserializer(new TelemetryEventDeserializer())
                .commitInterval(Duration.ofSeconds(2))   // периодически коммитит накопленные acknowledge()
                .subscription(List.of(topic));

        return KafkaReceiver.create(options);
    }

    private static final class TelemetryEventDeserializer implements Deserializer<TelemetryEvent> {

        private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

        @Override
        public TelemetryEvent deserialize(String topic, byte[] data) {
            if (data == null) {
                return null;
            }
            try {
                return mapper.readValue(data, TelemetryEvent.class);
            } catch (Exception e) {
                throw new org.apache.kafka.common.errors.SerializationException(
                        "Не удалось десериализовать TelemetryEvent", e);
            }
        }
    }
}
