package ru.telemetry.ingestion.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.telemetry.ingestion.model.Device;
import ru.telemetry.ingestion.repository.DeviceRepository;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DeviceAuthServiceTest {

    private static final String CORRECT_KEY = "correct-key";

    @Mock
    private DeviceRepository deviceRepository;

    private DeviceAuthService service;

    @BeforeEach
    void setUp() {
        service = new DeviceAuthService(deviceRepository);
    }

    @Test
    void isValid_correctKey_returnsTrue() {
        given(deviceRepository.findById("bus-1"))
                .willReturn(Mono.just(new Device("bus-1", "Автобус 1", "buses", sha256(CORRECT_KEY))));

        StepVerifier.create(service.isValid("bus-1", CORRECT_KEY))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void isValid_wrongKey_returnsFalse() {
        given(deviceRepository.findById("bus-1"))
                .willReturn(Mono.just(new Device("bus-1", "Автобус 1", "buses", sha256(CORRECT_KEY))));

        StepVerifier.create(service.isValid("bus-1", "wrong-key"))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void isValid_unknownDevice_returnsFalse() {
        given(deviceRepository.findById("unknown")).willReturn(Mono.empty());

        StepVerifier.create(service.isValid("unknown", CORRECT_KEY))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void isValid_blankKey_returnsFalseWithoutHittingDb() {
        StepVerifier.create(service.isValid("bus-1", ""))
                .expectNext(false)
                .verifyComplete();
        StepVerifier.create(service.isValid("bus-1", null))
                .expectNext(false)
                .verifyComplete();

        verifyNoInteractions(deviceRepository);
    }

    private static String sha256(String value) {
        // Дублирует приватную логику DeviceAuthService намеренно — тест не должен зависеть
        // от того, что метод внутри сервиса приватный/публичный.
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
