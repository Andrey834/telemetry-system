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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class KafkaReceiverConfig {

    @Bean
    public KafkaReceiver<String, TelemetryEvent> telemetryReceiver(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${app.telemetry.topic}") String topic,
            @Value("${app.telemetry.consumer-group}") String groupId,
            @Value("${spring.kafka.properties.security.protocol:PLAINTEXT}") String securityProtocol,
            @Value("${spring.kafka.properties.sasl.username:}") String saslUsername,
            @Value("${spring.kafka.properties.sasl.password:}") String saslPassword) {

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false); // коммитим сами, только после успешной записи в Redis+Postgres
        props.put("security.protocol", securityProtocol);
        if (!saslUsername.isBlank()) {
            props.put("sasl.mechanism", "PLAIN");
            props.put("sasl.jaas.config", "org.apache.kafka.common.security.plain.PlainLoginModule required username=\""
                    + saslUsername + "\" password=\"" + saslPassword + "\";");
        }

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
