package com.huy.jobpulse.jobs.api;

import com.huy.jobpulse.jobs.application.JobQueryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;
import com.huy.jobpulse.jobs.application.JobCommandService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;

import java.net.URI;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

    private final JobQueryService queryService;
    private final JobCommandService commandService;

    public JobController(
            JobQueryService queryService,
            JobCommandService commandService
    ) {
        this.queryService = queryService;
        this.commandService = commandService;
    }

    @GetMapping
    public Page<JobResponse> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return queryService
                .findAll(PageRequest.of(page, size))
                .map(JobResponse::from);
    }

    @GetMapping("/{id}")
    public JobResponse findById(@PathVariable UUID id) {
        return JobResponse.from(queryService.require(id));
    }

    @PostMapping
    public ResponseEntity<JobResponse> create(
            @Valid @RequestBody CreateJobRequest request
    ) {
        JobResponse response = JobResponse.from(
                commandService.create(request)
        );

        URI location = URI.create(
                "/api/v1/jobs/" + response.id()
        );

        return ResponseEntity
                .created(location)
                .body(response);
    }
}