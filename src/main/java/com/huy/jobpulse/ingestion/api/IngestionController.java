package com.huy.jobpulse.ingestion.api;

import com.huy.jobpulse.ingestion.application.IngestionResult;
import com.huy.jobpulse.ingestion.application.IngestionCoordinator;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ingestions")
public class IngestionController {

    private final IngestionCoordinator ingestionCoordinator;

    public IngestionController(IngestionCoordinator ingestionCoordinator) {
        this.ingestionCoordinator = ingestionCoordinator;
    }

    @PostMapping
    public IngestionResult ingest(
            @Valid @RequestBody IngestionRequest request
    ) {
        return ingestionCoordinator.ingest(
                request.source(),
                request.sourceAccount()
        );
    }
}
