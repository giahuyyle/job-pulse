package com.huy.jobpulse.admin.api;

import com.huy.jobpulse.discovery.api.CompanySeedResponse;
import com.huy.jobpulse.discovery.api.LocalAdminGuard;
import com.huy.jobpulse.discovery.domain.CompanySeed;
import com.huy.jobpulse.discovery.domain.CompanySeedStatus;
import com.huy.jobpulse.discovery.infrastructure.CompanySeedRepository;
import com.huy.jobpulse.ingestion.api.CreateIngestionTargetRequest;
import com.huy.jobpulse.ingestion.api.IngestionRequestResponse;
import com.huy.jobpulse.ingestion.api.IngestionTargetResponse;
import com.huy.jobpulse.ingestion.application.IngestionRequestService;
import com.huy.jobpulse.ingestion.application.IngestionTargetService;
import com.huy.jobpulse.ingestion.domain.IngestionRequestStatus;
import com.huy.jobpulse.ingestion.domain.IngestionRun;
import com.huy.jobpulse.ingestion.domain.IngestionRunStatus;
import com.huy.jobpulse.ingestion.domain.IngestionTarget;
import com.huy.jobpulse.ingestion.infrastructure.IngestionRequestRepository;
import com.huy.jobpulse.ingestion.infrastructure.IngestionRunRepository;
import com.huy.jobpulse.ingestion.infrastructure.IngestionTargetRepository;
import com.huy.jobpulse.jobs.infrastructure.JobEventRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
    private final IngestionTargetRepository targets;
    private final IngestionRequestRepository requests;
    private final IngestionRunRepository runs;
    private final CompanySeedRepository seeds;
    private final JobEventRepository events;
    private final IngestionTargetService targetService;
    private final IngestionRequestService requestService;
    private final LocalAdminGuard guard;
    private final Clock clock;

    public AdminController(IngestionTargetRepository targets,
            IngestionRequestRepository requests, IngestionRunRepository runs,
            CompanySeedRepository seeds, JobEventRepository events,
            IngestionTargetService targetService,
            IngestionRequestService requestService, LocalAdminGuard guard,
            Clock clock) {
        this.targets = targets;
        this.requests = requests;
        this.runs = runs;
        this.seeds = seeds;
        this.events = events;
        this.targetService = targetService;
        this.requestService = requestService;
        this.guard = guard;
        this.clock = clock;
    }

    @GetMapping("/summary")
    public AdminSummaryResponse summary(HttpServletRequest request) {
        guard.requireLocal(request);
        var now = clock.instant();
        return new AdminSummaryResponse(
                targets.countByEnabledTrue(),
                targets.countByEnabledTrueAndNextRunAtLessThanEqual(now),
                requests.countByStatus(IngestionRequestStatus.PENDING),
                requests.countByStatus(IngestionRequestStatus.RUNNING),
                runs.countByStatusAndStartedAtGreaterThanEqual(
                        IngestionRunStatus.FAILED,
                        now.minus(Duration.ofHours(24))),
                events.countByPublishedAtIsNull(),
                events.findOldestUnpublishedAt());
    }

    @GetMapping("/events/summary")
    public EventSummary events(HttpServletRequest request) {
        guard.requireLocal(request);
        return new EventSummary(events.countByPublishedAtIsNull(),
                events.findOldestUnpublishedAt());
    }

    @GetMapping("/targets")
    public AdminPageResponse<IngestionTargetResponse> targets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        guard.requireLocal(request);
        Page<IngestionTargetResponse> result = targets
                .findAllByOrderByCompanyAsc(pageable(page, size))
                .map(IngestionTargetResponse::from);
        return AdminPageResponse.from(result);
    }

    @GetMapping("/requests")
    public AdminPageResponse<IngestionRequestResponse> requests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        guard.requireLocal(request);
        return AdminPageResponse.from(requests
                .findAllByOrderByCreatedAtDesc(pageable(page, size))
                .map(IngestionRequestResponse::from));
    }

    @GetMapping("/ingestion-runs")
    public AdminPageResponse<AdminRunResponse> runs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) IngestionRunStatus status,
            HttpServletRequest request) {
        guard.requireLocal(request);
        Pageable pageable = pageable(page, size);
        Page<IngestionRun> result = status == null
                ? runs.findAllByOrderByStartedAtDesc(pageable)
                : runs.findAllByStatusOrderByStartedAtDesc(status, pageable);
        return AdminPageResponse.from(result.map(AdminRunResponse::from));
    }

    @GetMapping("/discovery")
    public List<CompanySeedResponse> discovery(
            @RequestParam(defaultValue = "NEEDS_REVIEW") CompanySeedStatus status,
            HttpServletRequest request) {
        guard.requireLocal(request);
        return seeds.findAllByStatusOrderByCompanyNameAsc(status).stream()
                .map(CompanySeedResponse::from).toList();
    }

    @PostMapping("/targets/{id}/run")
    public IngestionRequestResponse runNow(@PathVariable UUID id,
            HttpServletRequest request) {
        guard.requireLocal(request);
        return IngestionRequestResponse.from(requestService.requestTarget(id));
    }

    @PatchMapping("/targets/{id}")
    public IngestionTargetResponse updateTarget(@PathVariable UUID id,
            @Valid @RequestBody UpdateAdminTargetRequest body,
            HttpServletRequest request) {
        guard.requireLocal(request);
        return IngestionTargetResponse.from(targetService.update(
                id, body.enabled(), body.intervalMinutes()));
    }

    @PostMapping("/ingestion-runs/{id}/retry")
    public IngestionRequestResponse retry(@PathVariable UUID id,
            HttpServletRequest request) {
        guard.requireLocal(request);
        IngestionRun run = runs.findById(id).orElseThrow(() ->
                new EntityNotFoundException("Ingestion run not found: " + id));
        if (run.getStatus() != IngestionRunStatus.FAILED) {
            throw new IllegalArgumentException("Only failed runs can be retried");
        }
        IngestionTarget target = targets.findBySourceAndSourceAccount(
                run.getSource(), run.getSourceAccount()).orElseThrow(() ->
                new EntityNotFoundException("Target for run no longer exists"));
        return IngestionRequestResponse.from(
                requestService.requestTarget(target.getId()));
    }

    @PostMapping("/discovery/{id}/approve")
    public IngestionTargetResponse approve(@PathVariable UUID id,
            @Valid @RequestBody ApproveDiscoveryRequest body,
            HttpServletRequest request) {
        guard.requireLocal(request);
        CompanySeed seed = seeds.findById(id).orElseThrow(() ->
                new EntityNotFoundException("Discovery seed not found: " + id));
        IngestionTargetResponse response = targets.findBySourceAndSourceAccount(
                        body.source(), body.sourceAccount().strip())
                .map(IngestionTargetResponse::from)
                .orElseGet(() -> IngestionTargetResponse.from(
                        targetService.create(new CreateIngestionTargetRequest(
                                body.source(), body.sourceAccount(),
                                seed.getCompanyName(), seed.getCareersUrl(),
                                body.resolvedIntervalMinutes()))));
        seed.record(CompanySeedStatus.ADDED, clock.instant(),
                seed.getMatchedUrl(), null);
        seeds.save(seed);
        return response;
    }

    private static Pageable pageable(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "page must be >= 0 and size must be between 1 and 100");
        }
        return PageRequest.of(page, size);
    }

    public record EventSummary(long unpublishedJobEvents,
                               java.time.Instant oldestUnpublishedEventAt) {}
}
