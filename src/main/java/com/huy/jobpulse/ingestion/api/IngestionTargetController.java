package com.huy.jobpulse.ingestion.api;

import com.huy.jobpulse.ingestion.application.IngestionTargetService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ingestion-targets")
public class IngestionTargetController {

    private final IngestionTargetService service;

    public IngestionTargetController(IngestionTargetService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<IngestionTargetResponse> create(
            @Valid @RequestBody CreateIngestionTargetRequest request
    ) {
        IngestionTargetResponse response = IngestionTargetResponse.from(
                service.create(request)
        );
        return ResponseEntity
                .created(URI.create(
                        "/api/v1/ingestion-targets/" + response.id()
                ))
                .body(response);
    }

    @GetMapping
    public List<IngestionTargetResponse> findAll() {
        return service.findAll().stream()
                .map(IngestionTargetResponse::from)
                .toList();
    }

    @PutMapping("/{id}/enabled")
    public IngestionTargetResponse setEnabled(
            @PathVariable UUID id,
            @Valid @RequestBody SetIngestionTargetEnabledRequest request
    ) {
        return IngestionTargetResponse.from(
                service.setEnabled(id, request.enabled())
        );
    }
}
