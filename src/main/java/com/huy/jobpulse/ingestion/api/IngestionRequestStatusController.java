package com.huy.jobpulse.ingestion.api;

import com.huy.jobpulse.ingestion.application.IngestionRequestService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ingestion-requests")
public class IngestionRequestStatusController {

    private final IngestionRequestService requestService;

    public IngestionRequestStatusController(
            IngestionRequestService requestService
    ) {
        this.requestService = requestService;
    }

    @GetMapping("/{id}")
    public IngestionRequestResponse find(@PathVariable UUID id) {
        return IngestionRequestResponse.from(requestService.require(id));
    }
}
