package ru.telemetry.processor.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import ru.telemetry.processor.model.TelemetryState;

@Configuration
public class RedisConfig {

    @Bean
    public ReactiveRedisTemplate<String, TelemetryState> telemetryStateRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory) {

        // Без disable(WRITE_DATES_AS_TIMESTAMPS) Instant пишется как число (epoch seconds),
        // а не ISO-8601 строка — ломает и чтение redis-cli, и new Date(...) на фронте.
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        Jackson2JsonRedisSerializer<TelemetryState> valueSerializer =
                new Jackson2JsonRedisSerializer<>(mapper, TelemetryState.class);

        RedisSerializationContext<String, TelemetryState> context = RedisSerializationContext
                .<String, TelemetryState>newSerializationContext(new StringRedisSerializer())
                .value(valueSerializer)
                .build();

        return new ReactiveRedisTemplate<>(connectionFactory, context);
    }
}
