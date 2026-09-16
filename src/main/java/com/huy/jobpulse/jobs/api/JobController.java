package com.huy.jobpulse.jobs.api;

import com.huy.jobpulse.jobs.application.JobQueryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

    private final JobQueryService queryService;

    public JobController(JobQueryService queryService) {
        this.queryService = queryService;
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
}