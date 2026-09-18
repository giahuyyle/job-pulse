package com.huy.jobpulse.discovery.application;

import com.huy.jobpulse.discovery.domain.CompanySeed;
import com.huy.jobpulse.discovery.domain.CompanySeedStatus;
import com.huy.jobpulse.discovery.infrastructure.CompanySeedRepository;
import com.huy.jobpulse.ingestion.api.CreateIngestionTargetRequest;
import com.huy.jobpulse.ingestion.application.DuplicateIngestionTargetException;
import com.huy.jobpulse.ingestion.application.IngestionTargetService;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class BoardDiscoveryService {

    private static final int DEFAULT_INTERVAL_MINUTES = 60;

    private final CompanySeedRepository seedRepository;
    private final CareersPageFetcher pageFetcher;
    private final BoardCandidateExtractor candidateExtractor;
    private final BoardVerifierRegistry verifierRegistry;
    private final IngestionTargetService targetService;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean();

    public BoardDiscoveryService(
            CompanySeedRepository seedRepository,
            CareersPageFetcher pageFetcher,
            BoardCandidateExtractor candidateExtractor,
            BoardVerifierRegistry verifierRegistry,
            IngestionTargetService targetService,
            Clock clock
    ) {
        this.seedRepository = seedRepository;
        this.pageFetcher = pageFetcher;
        this.candidateExtractor = candidateExtractor;
        this.verifierRegistry = verifierRegistry;
        this.targetService = targetService;
        this.clock = clock;
    }

    public DiscoveryRunResult runAll() {
        if (!running.compareAndSet(false, true)) {
            throw new DiscoveryAlreadyRunningException();
        }
        try {
            List<DiscoverySeedOutcome> outcomes = seedRepository
                    .findAllByOrderByCompanyNameAsc()
                    .stream()
                    .map(this::discoverSafely)
                    .toList();
            return DiscoveryRunResult.from(outcomes);
        } finally {
            running.set(false);
        }
    }

    private DiscoverySeedOutcome discoverSafely(CompanySeed seed) {
        Instant checkedAt = clock.instant();
        try {
            discover(seed, checkedAt);
        } catch (RuntimeException exception) {
            seed.record(
                    CompanySeedStatus.ERROR,
                    checkedAt,
                    null,
                    message(exception)
            );
        }
        return DiscoverySeedOutcome.from(seedRepository.save(seed));
    }

    private void discover(CompanySeed seed, Instant checkedAt) {
        FetchedCareersPage page = pageFetcher.fetch(
                URI.create(seed.getCareersUrl())
        );
        BoardCandidateExtraction extraction = candidateExtractor.extract(page);
        if (!extraction.unsupportedBoardUrls().isEmpty()) {
            seed.record(
                    CompanySeedStatus.NEEDS_REVIEW,
                    checkedAt,
                    extraction.unsupportedBoardUrls().getFirst().toString(),
                    "Recognized board host is not supported yet"
            );
            return;
        }
        if (extraction.candidates().isEmpty()) {
            seed.record(
                    CompanySeedStatus.NEEDS_REVIEW,
                    checkedAt,
                    null,
                    "No supported board link found"
            );
            return;
        }

        List<BoardCandidate> verified = new ArrayList<>();
        List<String> unavailable = new ArrayList<>();
        List<String> retryableErrors = new ArrayList<>();
        for (BoardCandidate candidate : extraction.candidates()) {
            try {
                BoardVerification verification = verifierRegistry.verify(
                        candidate
                );
                if (verification.valid()) {
                    verified.add(candidate);
                } else {
                    unavailable.add(candidate.source() + "/"
                            + candidate.sourceAccount() + ": "
                            + verification.detail());
                }
            } catch (RuntimeException exception) {
                retryableErrors.add(candidate.source() + "/"
                        + candidate.sourceAccount() + ": "
                        + message(exception));
            }
        }

        if (!retryableErrors.isEmpty()) {
            seed.record(
                    CompanySeedStatus.ERROR,
                    checkedAt,
                    null,
                    String.join("; ", retryableErrors)
            );
            return;
        }
        if (verified.size() > 1) {
            seed.record(
                    CompanySeedStatus.NEEDS_REVIEW,
                    checkedAt,
                    verified.getFirst().matchedUrl().toString(),
                    "Multiple verified boards: " + verified.stream()
                            .map(candidate -> candidate.source() + "/"
                                    + candidate.sourceAccount())
                            .toList()
            );
            return;
        }
        if (verified.isEmpty()) {
            seed.record(
                    CompanySeedStatus.NEEDS_REVIEW,
                    checkedAt,
                    extraction.candidates().getFirst().matchedUrl().toString(),
                    unavailable.isEmpty()
                            ? "No board candidate verified"
                            : String.join("; ", unavailable)
            );
            return;
        }

        BoardCandidate candidate = verified.getFirst();
        CompanySeedStatus outcome;
        try {
            targetService.create(new CreateIngestionTargetRequest(
                    candidate.source(),
                    candidate.sourceAccount(),
                    seed.getCompanyName(),
                    seed.getCareersUrl(),
                    DEFAULT_INTERVAL_MINUTES
            ));
            outcome = CompanySeedStatus.ADDED;
        } catch (DuplicateIngestionTargetException exception) {
            outcome = CompanySeedStatus.ALREADY_EXISTS;
        }
        seed.record(
                outcome,
                checkedAt,
                candidate.matchedUrl().toString(),
                null
        );
    }

    private static String message(RuntimeException exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }
}
