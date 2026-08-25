package ru.telemetry.ingestion.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import ru.telemetry.ingestion.dto.TelemetryRequest;
import ru.telemetry.ingestion.service.DeviceAuthService;
import ru.telemetry.ingestion.service.TelemetryIngestionService;

@RestController
public class TelemetryController {

    private final TelemetryIngestionService ingestionService;
    private final DeviceAuthService deviceAuthService;

    public TelemetryController(TelemetryIngestionService ingestionService, DeviceAuthService deviceAuthService) {
        this.ingestionService = ingestionService;
        this.deviceAuthService = deviceAuthService;
    }

    @PostMapping("/telemetry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<Void> receive(@Valid @RequestBody TelemetryRequest request,
                               @RequestHeader(value = "X-Device-Key", required = false) String apiKey) {
        return deviceAuthService.isValid(request.deviceId(), apiKey)
                .flatMap(valid -> valid
                        ? ingestionService.ingest(request)
                        : Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                                "Неверный или отсутствующий X-Device-Key для deviceId=" + request.deviceId())));
    }
}
