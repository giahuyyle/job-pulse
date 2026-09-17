package com.huy.jobpulse.ingestion.api;

import com.huy.jobpulse.ingestion.application.IngestionResult;
import com.huy.jobpulse.ingestion.application.IngestionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ingestions")
public class IngestionController {

    private final IngestionService ingestionService;

    public IngestionController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping
    public IngestionResult ingest(
            @Valid @RequestBody IngestionRequest request
    ) {
        return ingestionService.ingest(
                request.source(),
                request.sourceAccount()
        );
    }
}
