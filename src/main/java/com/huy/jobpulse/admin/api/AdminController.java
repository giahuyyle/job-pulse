package com.huy.jobpulse.admin.api;

import com.huy.jobpulse.discovery.api.CompanySeedResponse;
import com.huy.jobpulse.discovery.api.AdminGuard;
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
import com.huy.jobpulse.jobs.domain.JobEventType;
import com.huy.jobpulse.jobs.domain.JobSource;
import com.huy.jobpulse.jobs.domain.JobStatus;
import com.huy.jobpulse.jobs.infrastructure.JobPostingRepository;
import com.huy.jobpulse.admin.application.AdminAuditService;
import com.huy.jobpulse.ingestion.infrastructure.IngestionDeadLetterRepository;
import com.huy.jobpulse.ingestion.infrastructure.IngestionAmqpTopology;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.amqp.rabbit.core.RabbitAdmin;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        prefix = "jobpulse.admin", name = "enabled", havingValue = "true")
public class AdminController {
    private final IngestionTargetRepository targets;
    private final IngestionRequestRepository requests;
    private final IngestionRunRepository runs;
    private final CompanySeedRepository seeds;
    private final JobEventRepository events;
    private final IngestionTargetService targetService;
    private final IngestionRequestService requestService;
    private final AdminGuard guard;
    private final Clock clock;
    private final AdminAuditService audit;
    private final IngestionDeadLetterRepository deadLetters;
    private final ObjectProvider<RabbitAdmin> rabbitAdmin;
    private final JobPostingRepository postings;

    public AdminController(IngestionTargetRepository targets,
            IngestionRequestRepository requests, IngestionRunRepository runs,
            CompanySeedRepository seeds, JobEventRepository events,
            IngestionTargetService targetService,
            IngestionRequestService requestService, AdminGuard guard,
            Clock clock, AdminAuditService audit,
            IngestionDeadLetterRepository deadLetters,
            ObjectProvider<RabbitAdmin> rabbitAdmin,
            JobPostingRepository postings) {
        this.targets = targets;
        this.requests = requests;
        this.runs = runs;
        this.seeds = seeds;
        this.events = events;
        this.targetService = targetService;
        this.requestService = requestService;
        this.guard = guard;
        this.clock = clock;
        this.audit = audit;
        this.deadLetters = deadLetters;
        this.rabbitAdmin = rabbitAdmin;
        this.postings = postings;
    }

    @GetMapping("/overview")
    public AdminOverviewResponse overview(HttpServletRequest request) {
        guard.requireAdmin(request);
        var successful = runs.findFirstByStatusOrderByCompletedAtDesc(
                IngestionRunStatus.SUCCEEDED);
        var now = clock.instant();
        return new AdminOverviewResponse(targets.count(), targets.countByEnabledTrue(),
                postings.count(), postings.countByStatus(JobStatus.ACTIVE),
                runs.countByStatusAndStartedAtGreaterThanEqual(IngestionRunStatus.FAILED,
                        now.minus(Duration.ofHours(24))),
                events.countByPublishedAtIsNull(),
                Math.max(queueDepth(IngestionAmqpTopology.DEAD_LETTER_QUEUE),
                        deadLetters.countByReplayedAtIsNull()),
                successful == null ? null : successful.getCompletedAt());
    }

    @GetMapping("/summary")
    public AdminSummaryResponse summary(HttpServletRequest request) {
        guard.requireAdmin(request);
        var now = clock.instant();
        IngestionRun lastRun = runs.findFirstByOrderByStartedAtDesc();
        long totalTargets = targets.count();
        long enabledTargets = targets.countByEnabledTrue();
        return new AdminSummaryResponse(
                enabledTargets,
                totalTargets - enabledTargets,
                targets.countByEnabledTrueAndNextRunAtLessThanEqual(now),
                lastRun == null ? null : lastRun.getStartedAt(),
                requests.countByStatus(IngestionRequestStatus.PENDING),
                requests.countByStatus(IngestionRequestStatus.RUNNING),
                runs.countByStatus(IngestionRunStatus.SUCCEEDED),
                runs.countByStatus(IngestionRunStatus.FAILED),
                runs.countByStatusAndStartedAtGreaterThanEqual(
                        IngestionRunStatus.FAILED,
                        now.minus(Duration.ofHours(24))),
                events.countByEventType(JobEventType.CREATED),
                events.countByEventType(JobEventType.UPDATED),
                runs.sumUnchanged(),
                events.countByEventType(JobEventType.CLOSED),
                queueDepth(IngestionAmqpTopology.QUEUE),
                Math.max(queueDepth(IngestionAmqpTopology.DEAD_LETTER_QUEUE),
                        deadLetters.countByReplayedAtIsNull()),
                events.countByPublishedAtIsNull(),
                events.countByPublishedAtIsNullAndLastPublishErrorIsNotNull(),
                events.findOldestUnpublishedAt());
    }

    @GetMapping("/events/summary")
    public EventSummary events(HttpServletRequest request) {
        guard.requireAdmin(request);
        return new EventSummary(events.countByPublishedAtIsNull(),
                events.findOldestUnpublishedAt());
    }

    @GetMapping("/targets")
    public AdminPageResponse<IngestionTargetResponse> targets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        guard.requireAdmin(request);
        Page<IngestionTargetResponse> result = targets
                .findAllByOrderByCompanyAsc(pageable(page, size))
                .map(IngestionTargetResponse::from);
        return AdminPageResponse.from(result);
    }

    @GetMapping("/boards")
    public AdminPageResponse<AdminBoardResponse> boards(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        guard.requireAdmin(request);
        return AdminPageResponse.from(targets.findAllByOrderByCompanyAsc(pageable(page, size))
                .map(this::boardResponse));
    }

    @GetMapping("/requests")
    public AdminPageResponse<IngestionRequestResponse> requests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        guard.requireAdmin(request);
        return AdminPageResponse.from(requests
                .findAllByOrderByCreatedAtDesc(pageable(page, size))
                .map(IngestionRequestResponse::from));
    }

    @GetMapping("/ingestion-runs")
    public AdminPageResponse<AdminRunResponse> runs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) IngestionRunStatus status,
            @RequestParam(required = false) UUID boardId,
            @RequestParam(required = false) JobSource provider,
            HttpServletRequest request) {
        guard.requireAdmin(request);
        Pageable pageable = pageable(page, size);
        Specification<IngestionRun> spec = (root, query, builder) -> builder.conjunction();
        if (status != null) spec = spec.and((root, query, builder) ->
                builder.equal(root.get("status"), status));
        if (provider != null) spec = spec.and((root, query, builder) ->
                builder.equal(root.get("source"), provider));
        if (boardId != null) {
            IngestionTarget board = targets.findById(boardId).orElseThrow(() ->
                    new EntityNotFoundException("Board not found: " + boardId));
            spec = spec.and((root, query, builder) -> builder.and(
                    builder.equal(root.get("source"), board.getSource()),
                    builder.equal(root.get("sourceAccount"), board.getSourceAccount())));
        }
        Page<IngestionRun> result = runs.findAll(spec, PageRequest.of(page, size,
                org.springframework.data.domain.Sort.by("startedAt").descending()));
        return AdminPageResponse.from(result.map(this::runResponse));
    }

    @GetMapping("/discovery")
    public List<CompanySeedResponse> discovery(
            @RequestParam(defaultValue = "NEEDS_REVIEW") CompanySeedStatus status,
            HttpServletRequest request) {
        guard.requireAdmin(request);
        return seeds.findAllByStatusOrderByCompanyNameAsc(status).stream()
                .map(CompanySeedResponse::from).toList();
    }

    @PostMapping("/targets/{id}/run")
    public IngestionRequestResponse runNow(@PathVariable UUID id,
            HttpServletRequest request) {
        guard.requireAdmin(request);
        var result = requestService.requestTarget(id);
        audit.record(request, "BOARD_RUN_TRIGGERED", "INGESTION_TARGET", id, null, result);
        return IngestionRequestResponse.from(result);
    }

    @PostMapping("/targets")
    public IngestionTargetResponse createTarget(
            @Valid @RequestBody CreateIngestionTargetRequest body,
            HttpServletRequest request) {
        guard.requireAdmin(request);
        var result = targetService.create(body);
        audit.record(request, "BOARD_CREATED", "INGESTION_TARGET", result.getId(), null,
                IngestionTargetResponse.from(result));
        return IngestionTargetResponse.from(result);
    }

    @PostMapping("/boards")
    public AdminBoardResponse createBoard(
            @Valid @RequestBody CreateIngestionTargetRequest body,
            HttpServletRequest request) {
        guard.requireAdmin(request);
        IngestionTarget result = targetService.create(body);
        AdminBoardResponse response = boardResponse(result);
        audit.record(request, "BOARD_CREATED", "INGESTION_TARGET", result.getId(), null, response);
        return response;
    }

    @PatchMapping("/boards/{id}/enabled")
    public AdminBoardResponse setBoardEnabled(@PathVariable UUID id,
            @Valid @RequestBody SetBoardEnabledRequest body,
            HttpServletRequest request) {
        guard.requireAdmin(request);
        AdminBoardResponse before = targets.findById(id).map(this::boardResponse).orElse(null);
        AdminBoardResponse result = boardResponse(targetService.setEnabled(id, body.enabled()));
        audit.record(request, "BOARD_ENABLED_CHANGED", "INGESTION_TARGET", id, before, result);
        return result;
    }

    @PostMapping("/boards/{id}/ingestion")
    public IngestionRequestResponse ingestBoard(@PathVariable UUID id,
            HttpServletRequest request) {
        return runNow(id, request);
    }

    @PatchMapping("/targets/{id}")
    public IngestionTargetResponse updateTarget(@PathVariable UUID id,
            @Valid @RequestBody UpdateAdminTargetRequest body,
            HttpServletRequest request) {
        guard.requireAdmin(request);
        var before = targets.findById(id).map(IngestionTargetResponse::from).orElse(null);
        var result = IngestionTargetResponse.from(targetService.update(
                id, body.enabled(), body.intervalMinutes()));
        audit.record(request, "BOARD_UPDATED", "INGESTION_TARGET", id, before, result);
        return result;
    }

    @PostMapping("/ingestion-runs/{id}/retry")
    public IngestionRequestResponse retry(@PathVariable UUID id,
            HttpServletRequest request) {
        guard.requireAdmin(request);
        IngestionRun run = runs.findById(id).orElseThrow(() ->
                new EntityNotFoundException("Ingestion run not found: " + id));
        if (run.getStatus() != IngestionRunStatus.FAILED) {
            throw new IllegalArgumentException("Only failed runs can be retried");
        }
        IngestionTarget target = targets.findBySourceAndSourceAccount(
                run.getSource(), run.getSourceAccount()).orElseThrow(() ->
                new EntityNotFoundException("Target for run no longer exists"));
        var retried = requestService.retryTarget(target.getId(), run.getId());
        audit.record(request, "INGESTION_RUN_RETRIED", "INGESTION_RUN", id,
                AdminRunResponse.from(run), retried);
        return IngestionRequestResponse.from(retried);
    }

    @PostMapping("/discovery/{id}/approve")
    public IngestionTargetResponse approve(@PathVariable UUID id,
            @Valid @RequestBody ApproveDiscoveryRequest body,
            HttpServletRequest request) {
        guard.requireAdmin(request);
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
        seed.review(CompanySeedStatus.ADDED, audit.actor(request), clock.instant(), null);
        seeds.save(seed);
        audit.record(request, "DISCOVERY_APPROVED", "COMPANY_SEED", id, null, response);
        return response;
    }

    private long queueDepth(String queue) {
        try {
            RabbitAdmin admin = rabbitAdmin.getIfAvailable();
            if (admin == null || admin.getQueueInfo(queue) == null) return 0;
            return admin.getQueueInfo(queue).getMessageCount();
        } catch (RuntimeException unavailable) {
            return 0;
        }
    }

    private AdminRunResponse runResponse(IngestionRun run) {
        return AdminRunResponse.from(run,
                Math.toIntExact(requests.countByRetryOfRunId(run.getId())));
    }

    private AdminBoardResponse boardResponse(IngestionTarget target) {
        return AdminBoardResponse.from(target,
                runs.findFirstBySourceAndSourceAccountOrderByStartedAtDesc(
                        target.getSource(), target.getSourceAccount()));
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
