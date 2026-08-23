package ru.telemetry.ingestion.service;

import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import reactor.test.StepVerifier;
import ru.telemetry.ingestion.dto.TelemetryEvent;
import ru.telemetry.ingestion.dto.TelemetryRequest;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TelemetryIngestionServiceTest {

    private static final String TOPIC = "telemetry.raw";

    @Mock
    private KafkaTemplate<String, TelemetryEvent> kafkaTemplate;

    private TelemetryIngestionService service;

    @BeforeEach
    void setUp() {
        service = new TelemetryIngestionService(kafkaTemplate, TOPIC);
    }

    @SuppressWarnings("unchecked")
    @Test
    void ingest_publishesWithDeviceIdAsPartitionKey() {
        TelemetryRequest request = new TelemetryRequest("bus-42", 7L, 55.75, 37.61, 42.0, Instant.now());

        SendResult<String, TelemetryEvent> sendResult = mock(SendResult.class);
        given(sendResult.getRecordMetadata()).willReturn(mock(RecordMetadata.class));
        given(kafkaTemplate.send(eq(TOPIC), eq("bus-42"), any(TelemetryEvent.class)))
                .willReturn(CompletableFuture.completedFuture(sendResult));

        StepVerifier.create(service.ingest(request)).verifyComplete();

        ArgumentCaptor<TelemetryEvent> captor = ArgumentCaptor.forClass(TelemetryEvent.class);
        verify(kafkaTemplate).send(eq(TOPIC), eq("bus-42"), captor.capture());
        assertThat(captor.getValue().deviceId()).isEqualTo("bus-42");
        assertThat(captor.getValue().routeId()).isEqualTo(7L);
        assertThat(captor.getValue().lat()).isEqualTo(55.75);
    }

    @Test
    void ingest_kafkaSendFails_propagatesErrorThroughMono() {
        TelemetryRequest request = new TelemetryRequest("bus-1", null, 55.0, 37.0, null, Instant.now());

        CompletableFuture<SendResult<String, TelemetryEvent>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker unavailable"));
        given(kafkaTemplate.send(eq(TOPIC), eq("bus-1"), any(TelemetryEvent.class)))
                .willReturn(failed);

        StepVerifier.create(service.ingest(request))
                .expectErrorMatches(e -> e.getMessage().contains("broker unavailable"))
                .verify();
    }
}
