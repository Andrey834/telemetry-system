package ru.telemetry.query.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.telemetry.query.service.DeviceAdminService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DeviceAdminControllerTest {

    @Mock
    private DeviceAdminService deviceAdminService;

    private DeviceAdminController controller;

    @BeforeEach
    void setUp() {
        controller = new DeviceAdminController(deviceAdminService);
    }

    @Test
    void register_valid_returnsCreatedWithApiKey() {
        var registered = new DeviceAdminService.RegisteredDevice("bus-9", "Автобус 9", "buses", "abc123");
        given(deviceAdminService.register("bus-9", "Автобус 9", "buses")).willReturn(Mono.just(registered));

        StepVerifier.create(controller.register(new DeviceAdminController.RegisterDeviceRequest("bus-9", "Автобус 9", "buses")))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                    assertThat(response.getBody().apiKey()).isEqualTo("abc123");
                })
                .verifyComplete();
    }

    @Test
    void register_blankDeviceId_returnsBadRequestWithoutHittingService() {
        StepVerifier.create(controller.register(new DeviceAdminController.RegisterDeviceRequest("", "Автобус 9", "buses")))
                .assertNext(response -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST))
                .verifyComplete();

        verifyNoInteractions(deviceAdminService);
    }

    @Test
    void register_duplicateDeviceId_propagatesConflict() {
        given(deviceAdminService.register("bus-1", "Автобус 1", "buses"))
                .willReturn(Mono.error(new ResponseStatusException(HttpStatus.CONFLICT)));

        StepVerifier.create(controller.register(new DeviceAdminController.RegisterDeviceRequest("bus-1", "Автобус 1", "buses")))
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
                .verify();
    }
}
