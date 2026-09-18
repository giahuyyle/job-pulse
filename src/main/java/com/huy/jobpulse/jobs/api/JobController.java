package com.huy.jobpulse.jobs.api;

import com.huy.jobpulse.jobs.application.JobCommandService;
import com.huy.jobpulse.jobs.application.JobQueryService;
import com.huy.jobpulse.jobs.application.JobSearchCriteria;
import com.huy.jobpulse.jobs.application.JobSearchFilters;
import com.huy.jobpulse.jobs.application.JobSearchSort;
import com.huy.jobpulse.jobs.domain.JobSource;
import com.huy.jobpulse.jobs.domain.RemotePolicy;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
    public JobSearchPageResponse findAll(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String company,
            @RequestParam(required = false) JobSource source,
            @RequestParam(required = false) RemotePolicy remotePolicy,
            @RequestParam(required = false) String location,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "newest") String sort
    ) {
        JobSearchFilters filters = new JobSearchFilters(
                query,
                company,
                source,
                remotePolicy,
                location
        );
        return JobSearchPageResponse.from(queryService.search(
                new JobSearchCriteria(
                        filters,
                        JobSearchSort.parse(sort),
                        page,
                        size
                )
        ).map(JobSummaryResponse::from));
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
