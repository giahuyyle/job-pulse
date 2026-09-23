package com.huy.jobpulse.ingestion.application;

import com.huy.jobpulse.ingestion.domain.IngestionRequest;
import com.huy.jobpulse.ingestion.domain.IngestionRequestStatus;
import com.huy.jobpulse.ingestion.domain.IngestionTarget;
import com.huy.jobpulse.ingestion.infrastructure.IngestionRequestRepository;
import com.huy.jobpulse.ingestion.infrastructure.IngestionTargetRepository;
import com.huy.jobpulse.ingestion.infrastructure.IngestionDeadLetterRepository;
import com.huy.jobpulse.ingestion.domain.IngestionDeadLetter;
import com.huy.jobpulse.jobs.domain.JobSource;
import com.huy.jobpulse.observability.JobPulseMetrics;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.util.UUID;

@Service
public class IngestionRequestService {

    private static final Logger LOGGER = LoggerFactory.getLogger(IngestionRequestService.class);

    private static final List<IngestionRequestStatus> OUTSTANDING = List.of(
            IngestionRequestStatus.PENDING,
            IngestionRequestStatus.RUNNING
    );
    private static final Duration LEASE_DURATION = Duration.ofMinutes(10);

    private final IngestionRequestRepository requestRepository;
    private final IngestionTargetRepository targetRepository;
    private final Clock clock;
    private final IngestionDeadLetterRepository deadLetters;
    private final JobPulseMetrics metrics;

    public IngestionRequestService(
            IngestionRequestRepository requestRepository,
            IngestionTargetRepository targetRepository,
            Clock clock,
            IngestionDeadLetterRepository deadLetters,
            JobPulseMetrics metrics
    ) {
        this.requestRepository = requestRepository;
        this.targetRepository = targetRepository;
        this.clock = clock;
        this.deadLetters = deadLetters;
        this.metrics = metrics;
    }

    @Transactional
    public IngestionRequest request(JobSource source, String sourceAccount) {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        if (sourceAccount == null || sourceAccount.isBlank()) {
            throw new IllegalArgumentException("sourceAccount must not be blank");
        }
        IngestionTarget target = targetRepository
                .findLockedBySourceAndSourceAccount(source, sourceAccount.strip())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Ingestion target not found: "
                                + source + "/" + sourceAccount.strip()
                ));
        return createOrGetOutstanding(target.getId(), clock.instant());
    }

    @Transactional
    public IngestionRequest requestTarget(UUID targetId) {
        return requestTarget(targetId, null);
    }

    @Transactional
    public IngestionRequest retryTarget(UUID targetId, UUID failedRunId) {
        return requestTarget(targetId, Objects.requireNonNull(failedRunId));
    }

    private IngestionRequest requestTarget(UUID targetId, UUID failedRunId) {
        IngestionTarget target = targetRepository.findLockedById(targetId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Ingestion target not found: " + targetId
                ));
        if (!target.isEnabled()) {
            throw new IllegalArgumentException(
                    "Enable this board before running it manually"
            );
        }
        if (requestRepository
                .findFirstByIngestionTargetIdAndStatusInOrderByCreatedAtAsc(
                        targetId, OUTSTANDING
                ).isPresent()) {
            throw new IngestionAlreadyRunningException(
                    "This board already has pending or running work"
            );
        }
        return requestRepository.save(failedRunId == null
                ? IngestionRequest.create(targetId, clock.instant())
                : IngestionRequest.retry(targetId, failedRunId, clock.instant()));
    }

    @Transactional(readOnly = true)
    public IngestionRequest require(UUID id) {
        return requestRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Ingestion request not found: " + id
                ));
    }

    @Transactional(readOnly = true)
    public List<UUID> findUnpublishedIds() {
        return requestRepository
                .findTop50ByPublishedAtIsNullAndStatusOrderByCreatedAtAsc(
                        IngestionRequestStatus.PENDING
                ).stream()
                .map(IngestionRequest::getId)
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markPublished(UUID id) {
        return requestRepository.markPublished(id, clock.instant()) == 1;
    }

    @Transactional
    public int enqueueDueTargets(Instant now) {
        List<IngestionTarget> targets = targetRepository
                .findTop10ByEnabledTrueAndNextRunAtLessThanEqualOrderByNextRunAtAsc(
                        now
                );
        for (IngestionTarget target : targets) {
            createOrGetOutstanding(target.getId(), now);
            target.markScheduled(now);
        }
        return targets.size();
    }

    @Transactional
    public Optional<IngestionWork> claim(UUID requestId) {
        Instant now = clock.instant();
        if (requestRepository.claim(
                requestId,
                now,
                now.plus(LEASE_DURATION)
        ) == 0) {
            return Optional.empty();
        }
        IngestionRequest request = requestRepository.findById(requestId)
                .orElseThrow();
        IngestionTarget target = targetRepository
                .findById(request.getIngestionTargetId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Ingestion target not found: "
                                + request.getIngestionTargetId()
                ));
        return Optional.of(new IngestionWork(
                request.getId(),
                target.getId(),
                target.getSource(),
                target.getSourceAccount(),
                target.getCompany(),
                request.getAttemptCount()
        ));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void succeed(IngestionWork work) {
        Instant now = clock.instant();
        Optional<IngestionTarget> target = targetRepository.findLockedById(
                work.targetId()
        );
        if (requestRepository.markSucceeded(work.requestId(), now) == 1
                && target.isPresent()) {
            target.orElseThrow().markSucceeded(now);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean fail(IngestionWork work, Throwable failure, int maxAttempts) {
        String message = failureMessage(failure);
        if (work.attemptCount() < maxAttempts) {
            return requestRepository.releaseForRetry(
                    work.requestId(),
                    message
            ) == 1;
        }
        Instant now = clock.instant();
        Optional<IngestionTarget> target = targetRepository.findLockedById(
                work.targetId()
        );
        if (requestRepository.markFailed(
                work.requestId(),
                now,
                message
        ) == 1 && target.isPresent()) {
            target.orElseThrow().markFailed(now, message);
            deadLetters.save(IngestionDeadLetter.create(
                    work.requestId(), work.targetId(), now, message));
            metrics.recordDeadLetter("ingestion");
            LOGGER.atError()
                    .addKeyValue("requestId", work.requestId())
                    .addKeyValue("boardId", work.targetId())
                    .addKeyValue("provider", work.source().name().toLowerCase())
                    .addKeyValue("attempt", work.attemptCount())
                    .log("Ingestion message moved to the dead-letter queue");
        }
        return false;
    }

    @Transactional
    public IngestionRequest cancel(UUID id) {
        if (requestRepository.cancel(id, clock.instant()) != 1) {
            throw new IllegalArgumentException("Only a pending request can be cancelled");
        }
        return require(id);
    }

    @Transactional
    public IngestionRequest replayDeadLetter(UUID id) {
        IngestionDeadLetter dead = deadLetters.findById(id).orElseThrow(() ->
                new EntityNotFoundException("Dead letter not found: " + id));
        if (dead.getReplayedAt() != null) {
            throw new IllegalArgumentException("Dead letter has already been replayed");
        }
        IngestionRequest replay = requestTarget(dead.getTargetId());
        dead.replayed(clock.instant(), replay.getId());
        return replay;
    }

    @Transactional(readOnly = true)
    public long countRetriesOfRun(UUID runId) {
        return requestRepository.countByRetryOfRunId(runId);
    }

    @Transactional
    public int recoverExpired(int maxAttempts) {
        Instant now = clock.instant();
        List<IngestionRequest> expired = requestRepository
                .findTop50ByStatusAndLeaseUntilBeforeOrderByLeaseUntilAsc(
                        IngestionRequestStatus.RUNNING,
                        now
                );
        int recovered = 0;
        for (IngestionRequest request : expired) {
            if (request.getAttemptCount() >= maxAttempts) {
                Optional<IngestionTarget> target = targetRepository
                        .findLockedById(request.getIngestionTargetId());
                if (requestRepository.markFailed(
                        request.getId(),
                        now,
                        "Worker lease expired after maximum attempts"
                ) == 1 && target.isPresent()) {
                    target.orElseThrow().markFailed(
                            now,
                            "Worker lease expired after maximum attempts"
                    );
                    deadLetters.save(IngestionDeadLetter.create(
                            request.getId(), request.getIngestionTargetId(), now,
                            "Worker lease expired after maximum attempts"));
                    metrics.recordDeadLetter("ingestion");
                }
            } else {
                recovered += requestRepository.recoverExpired(
                        request.getId(),
                        now,
                        "Worker lease expired; request rescheduled"
                );
            }
        }
        return recovered;
    }

    private IngestionRequest createOrGetOutstanding(UUID targetId, Instant now) {
        Optional<IngestionRequest> existing = requestRepository
                .findFirstByIngestionTargetIdAndStatusInOrderByCreatedAtAsc(
                        targetId,
                        OUTSTANDING
                );
        return existing.orElseGet(() -> requestRepository.save(
                IngestionRequest.create(targetId, now)
        ));
    }

    private static String failureMessage(Throwable failure) {
        String message = failure == null ? null : failure.getMessage();
        return message == null || message.isBlank()
                ? "Ingestion failed"
                : message;
    }
}
