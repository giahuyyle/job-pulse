package com.huy.jobpulse.ingestion.api;

import com.huy.jobpulse.ingestion.application.IngestionRequestService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ingestions")
public class IngestionController {

    private final IngestionRequestService requestService;

    public IngestionController(IngestionRequestService requestService) {
        this.requestService = requestService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public IngestionRequestResponse ingest(
            @Valid @RequestBody IngestionRequest request
    ) {
        return IngestionRequestResponse.from(requestService.request(
                request.source(),
                request.sourceAccount()
        ));
    }
}
