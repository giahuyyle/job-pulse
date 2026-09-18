package com.huy.jobpulse.ingestion.api;

import com.huy.jobpulse.ingestion.application.IngestionResult;
import com.huy.jobpulse.ingestion.application.IngestionCoordinator;
import com.huy.jobpulse.ingestion.application.IngestionTargetService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ingestions")
public class IngestionController {

    private final IngestionCoordinator ingestionCoordinator;
    private final IngestionTargetService targetService;

    public IngestionController(
            IngestionCoordinator ingestionCoordinator,
            IngestionTargetService targetService
    ) {
        this.ingestionCoordinator = ingestionCoordinator;
        this.targetService = targetService;
    }

    @PostMapping
    public IngestionResult ingest(
            @Valid @RequestBody IngestionRequest request
    ) {
        String company = targetService.findCompany(
                request.source(),
                request.sourceAccount().strip()
        ).orElse(request.sourceAccount());
        return ingestionCoordinator.ingest(
                request.source(),
                request.sourceAccount(),
                company
        );
    }
}
