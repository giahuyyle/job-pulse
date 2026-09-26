package com.huy.jobpulse.admin.api;

import com.huy.jobpulse.admin.application.AdminAuditService;
import com.huy.jobpulse.admin.infrastructure.AdminAuditRepository;
import com.huy.jobpulse.admin.application.AdminEventService;
import com.huy.jobpulse.discovery.api.LocalAdminGuard;
import com.huy.jobpulse.discovery.application.BoardCandidate;
import com.huy.jobpulse.discovery.application.BoardVerification;
import com.huy.jobpulse.discovery.application.BoardVerifierRegistry;
import com.huy.jobpulse.discovery.domain.CompanySeedStatus;
import com.huy.jobpulse.discovery.infrastructure.CompanySeedRepository;
import com.huy.jobpulse.ingestion.api.IngestionRequestResponse;
import com.huy.jobpulse.ingestion.application.IngestionRequestService;
import com.huy.jobpulse.ingestion.domain.IngestionRun;
import com.huy.jobpulse.ingestion.infrastructure.IngestionDeadLetterRepository;
import com.huy.jobpulse.ingestion.infrastructure.IngestionRunRepository;
import com.huy.jobpulse.ingestion.infrastructure.IngestionTargetRepository;
import com.huy.jobpulse.jobs.domain.JobEvent;
import com.huy.jobpulse.jobs.domain.JobEventType;
import com.huy.jobpulse.jobs.domain.JobPosting;
import com.huy.jobpulse.jobs.domain.JobSource;
import com.huy.jobpulse.jobs.domain.JobStatus;
import com.huy.jobpulse.jobs.infrastructure.JobEventRepository;
import com.huy.jobpulse.jobs.infrastructure.JobPostingRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        prefix = "jobpulse.admin", name = "enabled", havingValue = "true")
public class AdminOperationsController {
    private final LocalAdminGuard guard; private final AdminAuditService audit;
    private final AdminAuditRepository audits; private final IngestionRequestService requests;
    private final IngestionDeadLetterRepository deadLetters; private final AdminEventService eventService;
    private final JobEventRepository events; private final JobPostingRepository jobs;
    private final IngestionTargetRepository targets; private final IngestionRunRepository runs;
    private final CompanySeedRepository seeds; private final BoardVerifierRegistry verifiers; private final Clock clock;

    public AdminOperationsController(LocalAdminGuard guard, AdminAuditService audit,
            AdminAuditRepository audits, IngestionRequestService requests,
            IngestionDeadLetterRepository deadLetters, AdminEventService eventService,
            JobEventRepository events, JobPostingRepository jobs,
            IngestionTargetRepository targets, IngestionRunRepository runs,
            CompanySeedRepository seeds, BoardVerifierRegistry verifiers, Clock clock) {
        this.guard=guard; this.audit=audit; this.audits=audits; this.requests=requests;
        this.deadLetters=deadLetters; this.eventService=eventService; this.events=events; this.jobs=jobs;
        this.targets=targets; this.runs=runs; this.seeds=seeds; this.verifiers=verifiers; this.clock=clock;
    }

    @PostMapping("/requests/{id}/cancel")
    public IngestionRequestResponse cancel(@PathVariable UUID id, HttpServletRequest request) {
        guard.requireLocal(request); var result=requests.cancel(id);
        audit.record(request,"INGESTION_REQUEST_CANCELLED","INGESTION_REQUEST",id,null,result);
        return IngestionRequestResponse.from(result);
    }

    @GetMapping("/targets/{id}/runs")
    public AdminPageResponse<AdminRunResponse> targetRuns(@PathVariable UUID id,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size,
            HttpServletRequest request) {
        guard.requireLocal(request); var target=targets.findById(id).orElseThrow(() ->
                new EntityNotFoundException("Target not found: "+id));
        return AdminPageResponse.from(runs.findAllBySourceAndSourceAccountOrderByStartedAtDesc(
                target.getSource(),target.getSourceAccount(),PageRequest.of(page,size)).map(this::runResponse));
    }

    @GetMapping("/ingestion-runs/{id}")
    public AdminRunResponse run(@PathVariable UUID id, HttpServletRequest request) {
        guard.requireLocal(request); return runResponse(runs.findById(id).orElseThrow(() ->
                new EntityNotFoundException("Ingestion run not found: "+id)));
    }

    @GetMapping("/ingestion-runs/{id}/logs")
    public List<RunLogEntry> logs(@PathVariable UUID id, HttpServletRequest request) {
        guard.requireLocal(request); IngestionRun run=runs.findById(id).orElseThrow(() ->
                new EntityNotFoundException("Ingestion run not found: "+id));
        var result=new java.util.ArrayList<RunLogEntry>();
        result.add(new RunLogEntry(run.getStartedAt(),"INFO","Ingestion started",run.getCorrelationId()));
        if(run.getFailureMessage()!=null) result.add(new RunLogEntry(run.getCompletedAt(),"ERROR",run.getFailureMessage(),run.getCorrelationId()));
        else if(run.getCompletedAt()!=null) result.add(new RunLogEntry(run.getCompletedAt(),"INFO","Ingestion completed",run.getCorrelationId()));
        return result;
    }

    @GetMapping("/dead-letters")
    public AdminPageResponse<AdminDeadLetterResponse> deadLetters(@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size, HttpServletRequest request) {
        guard.requireLocal(request); return AdminPageResponse.from(
                deadLetters.findAllByOrderByFailedAtDesc(PageRequest.of(page,size))
                        .map(AdminDeadLetterResponse::from));
    }

    @PostMapping("/dead-letters/{id}/replay")
    public IngestionRequestResponse replay(@PathVariable UUID id,
            @org.springframework.web.bind.annotation.RequestBody(required=false) ReplayDeadLetterRequest body,
            HttpServletRequest request) {
        guard.requireLocal(request);
        String reason = body == null ? "Manual replay" : body.reason();
        var result=requests.replayDeadLetter(id, audit.actor(request), reason);
        audit.record(request,"DLQ_MESSAGE_REPLAYED","DEAD_LETTER",id,
                java.util.Map.of("reason",reason),result);
        return IngestionRequestResponse.from(result);
    }

    @GetMapping("/events")
    public AdminPageResponse<AdminEventResponse> events(@RequestParam(required=false) UUID jobId,
            @RequestParam(required=false) JobEventType type, @RequestParam(required=false) String status,
            @RequestParam(required=false) Boolean published,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size,
            HttpServletRequest request) {
        guard.requireLocal(request); Specification<JobEvent> spec=(root,query,builder)->builder.conjunction();
        if(jobId!=null) spec=spec.and((r,q,b)->b.equal(r.get("jobPostingId"),jobId));
        if(type!=null) spec=spec.and((r,q,b)->b.equal(r.get("eventType"),type));
        if(from!=null) spec=spec.and((r,q,b)->b.greaterThanOrEqualTo(r.get("createdAt"),from));
        if(to!=null) spec=spec.and((r,q,b)->b.lessThanOrEqualTo(r.get("createdAt"),to));
        if("PUBLISHED".equalsIgnoreCase(status)) spec=spec.and((r,q,b)->b.isNotNull(r.get("publishedAt")));
        if("UNPUBLISHED".equalsIgnoreCase(status)) spec=spec.and((r,q,b)->b.equal(r.get("publishStatus"),"PENDING"));
        if("PUBLISHING".equalsIgnoreCase(status)) spec=spec.and((r,q,b)->b.equal(r.get("publishStatus"),"PUBLISHING"));
        if("FAILED".equalsIgnoreCase(status)) spec=spec.and((r,q,b)->b.equal(r.get("publishStatus"),"FAILED"));
        if(Boolean.TRUE.equals(published)) spec=spec.and((r,q,b)->b.isNotNull(r.get("publishedAt")));
        if(Boolean.FALSE.equals(published)) spec=spec.and((r,q,b)->b.isNull(r.get("publishedAt")));
        return AdminPageResponse.from(events.findAll(spec,PageRequest.of(page,size,
                org.springframework.data.domain.Sort.by("createdAt").descending())).map(AdminEventResponse::from));
    }

    @GetMapping("/events/{id}")
    public AdminEventResponse event(@PathVariable UUID id, HttpServletRequest request) {
        guard.requireLocal(request);
        return AdminEventResponse.from(events.findById(id).orElseThrow(() ->
                new EntityNotFoundException("Event not found: " + id)));
    }

    @PostMapping("/events/{id}/retry")
    public AdminEventResponse retryEvent(@PathVariable UUID id, HttpServletRequest request) {
        guard.requireLocal(request); var result=eventService.requestRetry(id);
        audit.record(request,"OUTBOX_EVENT_RETRIED","JOB_EVENT",id,null,AdminEventResponse.from(result));
        return AdminEventResponse.from(result);
    }

    @GetMapping("/jobs")
    public AdminPageResponse<AdminJobSummaryResponse> jobs(@RequestParam(required=false) String q,
            @RequestParam(required=false) JobSource source, @RequestParam(required=false) JobStatus status,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size,
            HttpServletRequest request) {
        guard.requireLocal(request); Specification<JobPosting> spec=(root,query,builder)->builder.conjunction();
        if(q!=null&&!q.isBlank()){String like="%"+q.strip().toLowerCase()+"%";spec=spec.and((r,x,b)->b.or(
                b.like(b.lower(r.get("title")),like),b.like(b.lower(r.get("company")),like),
                b.like(b.lower(r.get("sourceJobId")),like)));}
        if(source!=null) spec=spec.and((r,x,b)->b.equal(r.get("source"),source));
        if(status!=null) spec=spec.and((r,x,b)->b.equal(r.get("status"),status));
        return AdminPageResponse.from(jobs.findAll(spec,PageRequest.of(page,size,
                org.springframework.data.domain.Sort.by("lastSeenAt").descending())).map(AdminJobSummaryResponse::from));
    }

    @GetMapping("/jobs/{id}")
    public AdminJobResponse job(@PathVariable UUID id,HttpServletRequest request){guard.requireLocal(request);
        var job=jobs.findById(id).orElseThrow(()->new EntityNotFoundException("Job not found: "+id));
        return AdminJobResponse.from(job,events.findAllByJobPostingIdOrderByCreatedAtDesc(id).stream().map(AdminEventResponse::from).toList());}

    @PostMapping("/jobs/{id}/close") @Transactional
    public AdminJobResponse closeJob(@PathVariable UUID id,HttpServletRequest request){guard.requireLocal(request);
        var job=jobs.findById(id).orElseThrow(()->new EntityNotFoundException("Job not found: "+id));
        var before=AdminJobSummaryResponse.from(job); if(job.closeManually(clock.instant()))
            events.save(JobEvent.capture(job,JobEventType.CLOSED,clock.instant()));
        audit.record(request,"JOB_MANUALLY_CLOSED","JOB_POSTING",id,before,AdminJobSummaryResponse.from(job));
        return AdminJobResponse.from(job,events.findAllByJobPostingIdOrderByCreatedAtDesc(id).stream().map(AdminEventResponse::from).toList());}

    @PostMapping("/jobs/{id}/reprocess")
    public IngestionRequestResponse reprocess(@PathVariable UUID id,HttpServletRequest request){guard.requireLocal(request);
        var job=jobs.findById(id).orElseThrow(()->new EntityNotFoundException("Job not found: "+id));
        var target=targets.findBySourceAndSourceAccount(job.getSource(),job.getSourceAccount()).orElseThrow(()->
                new EntityNotFoundException("Board for job no longer exists")); var result=requests.requestTarget(target.getId());
        audit.record(request,"JOB_REPROCESS_REQUESTED","JOB_POSTING",id,null,result);return IngestionRequestResponse.from(result);}

    @PostMapping("/discovery/{id}/reject") @Transactional
    public void reject(@PathVariable UUID id,@RequestBody(required=false) RejectRequest body,HttpServletRequest request){guard.requireLocal(request);
        var seed=seeds.findById(id).orElseThrow(()->new EntityNotFoundException("Discovery seed not found: "+id));
        seed.review(CompanySeedStatus.REJECTED,audit.actor(request),clock.instant(),body==null?null:body.reason());
        audit.record(request,"DISCOVERY_REJECTED","COMPANY_SEED",id,null,body);}

    @PostMapping("/discovery/{id}/test")
    public BoardVerification testDiscovery(@PathVariable UUID id,@RequestBody ApproveDiscoveryRequest body,HttpServletRequest request){guard.requireLocal(request);
        var seed=seeds.findById(id).orElseThrow(()->new EntityNotFoundException("Discovery seed not found: "+id));
        URI matched=seed.getMatchedUrl()==null?URI.create(seed.getCareersUrl()):URI.create(seed.getMatchedUrl());
        return verifiers.verify(new BoardCandidate(body.source(),body.sourceAccount(),matched));}

    @GetMapping("/audit-log")
    public AdminPageResponse<AdminAuditResponse> auditLog(@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="50") int size,HttpServletRequest request){guard.requireLocal(request);
        return AdminPageResponse.from(audits.findAllByOrderByOccurredAtDesc(PageRequest.of(page,size))
                .map(AdminAuditResponse::from));}

    public record RunLogEntry(Instant timestamp,String level,String message,UUID correlationId){}
    public record RejectRequest(String reason){}

    private AdminRunResponse runResponse(IngestionRun run) {
        return AdminRunResponse.from(run,
                Math.toIntExact(requests.countRetriesOfRun(run.getId())));
    }
}
