package com.huy.jobpulse.alerts.api;

import com.huy.jobpulse.alerts.application.SavedSearchService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/saved-searches")
public class SavedSearchController {

    private final SavedSearchService service;

    public SavedSearchController(SavedSearchService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<SavedSearchResponse> create(
            @Valid @RequestBody CreateSavedSearchRequest request
    ) {
        SavedSearchResponse response = SavedSearchResponse.from(
                service.create(request)
        );
        return ResponseEntity.created(URI.create(
                "/api/v1/saved-searches/" + response.id()
        )).body(response);
    }

    @GetMapping
    public List<SavedSearchResponse> findAll() {
        return service.findAll().stream()
                .map(SavedSearchResponse::from)
                .toList();
    }

    @PatchMapping("/{id}")
    public SavedSearchResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSavedSearchRequest request
    ) {
        return SavedSearchResponse.from(service.update(id, request));
    }
}
