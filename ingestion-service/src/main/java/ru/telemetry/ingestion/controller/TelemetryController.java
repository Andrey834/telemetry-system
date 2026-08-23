package ru.telemetry.ingestion.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import ru.telemetry.ingestion.dto.TelemetryRequest;
import ru.telemetry.ingestion.service.TelemetryIngestionService;

@RestController
public class TelemetryController {

    private final TelemetryIngestionService ingestionService;

    public TelemetryController(TelemetryIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/telemetry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<Void> receive(@Valid @RequestBody TelemetryRequest request) {
        return ingestionService.ingest(request);
    }
}
