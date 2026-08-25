package ru.telemetry.query.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.FetchSpec;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.telemetry.query.config.PrimaryDb;

import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class DeviceAdminServiceTest {

    private static final Pattern HEX_32 = Pattern.compile("^[0-9a-f]{32}$");

    @Mock
    private PrimaryDb primaryDb;

    @Mock
    private DatabaseClient databaseClient;

    @Mock
    private DatabaseClient.GenericExecuteSpec executeSpec;

    @Mock
    private FetchSpec<Map<String, Object>> fetchSpec;

    private DeviceAdminService service;

    @BeforeEach
    void setUp() {
        service = new DeviceAdminService(primaryDb);
    }

    @Test
    void generateApiKey_producesRandom32CharHex() {
        String a = DeviceAdminService.generateApiKey();
        String b = DeviceAdminService.generateApiKey();

        assertThat(a).matches(HEX_32);
        assertThat(b).matches(HEX_32);
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void sha256Hex_knownVector() {
        // SHA-256("") — стандартный тестовый вектор.
        assertThat(DeviceAdminService.sha256Hex(""))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    void register_success_insertsHashedKeyAndReturnsPlaintextOnce() {
        given(primaryDb.client()).willReturn(databaseClient);
        given(databaseClient.sql(anyString())).willReturn(executeSpec);
        given(executeSpec.bind(anyString(), any())).willReturn(executeSpec);
        given(executeSpec.fetch()).willReturn(fetchSpec);
        given(fetchSpec.rowsUpdated()).willReturn(Mono.just(1L));

        StepVerifier.create(service.register("bus-9", "Автобус 9", "buses"))
                .assertNext(registered -> {
                    assertThat(registered.deviceId()).isEqualTo("bus-9");
                    assertThat(registered.name()).isEqualTo("Автобус 9");
                    assertThat(registered.groupName()).isEqualTo("buses");
                    assertThat(registered.apiKey()).matches(HEX_32);
                })
                .verifyComplete();
    }

    @Test
    void register_duplicateDeviceId_mapsToConflict() {
        given(primaryDb.client()).willReturn(databaseClient);
        given(databaseClient.sql(anyString())).willReturn(executeSpec);
        given(executeSpec.bind(anyString(), any())).willReturn(executeSpec);
        given(executeSpec.fetch()).willReturn(fetchSpec);
        given(fetchSpec.rowsUpdated()).willReturn(Mono.error(new DuplicateKeyException("device_id уже существует")));

        StepVerifier.create(service.register("bus-1", "Автобус 1", "buses"))
                .expectErrorSatisfies(e -> {
                    assertThat(e).isInstanceOf(ResponseStatusException.class);
                    assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(409);
                })
                .verify();
    }

    @Test
    void update_existingDevice_completes() {
        given(primaryDb.client()).willReturn(databaseClient);
        given(databaseClient.sql(anyString())).willReturn(executeSpec);
        given(executeSpec.bind(anyString(), any())).willReturn(executeSpec);
        given(executeSpec.fetch()).willReturn(fetchSpec);
        given(fetchSpec.rowsUpdated()).willReturn(Mono.just(1L));

        StepVerifier.create(service.update("bus-1", "Автобус 1 (новое имя)", "buses", false))
                .verifyComplete();
    }

    @Test
    void update_unknownDevice_mapsToNotFound() {
        given(primaryDb.client()).willReturn(databaseClient);
        given(databaseClient.sql(anyString())).willReturn(executeSpec);
        given(executeSpec.bind(anyString(), any())).willReturn(executeSpec);
        given(executeSpec.fetch()).willReturn(fetchSpec);
        given(fetchSpec.rowsUpdated()).willReturn(Mono.just(0L));

        StepVerifier.create(service.update("unknown", "x", "y", true))
                .expectErrorSatisfies(e -> {
                    assertThat(e).isInstanceOf(ResponseStatusException.class);
                    assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                })
                .verify();
    }
}
