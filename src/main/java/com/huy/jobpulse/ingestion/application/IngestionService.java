package com.huy.jobpulse.ingestion.application;

import com.huy.jobpulse.jobs.domain.JobSource;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class IngestionService implements IngestionCoordinator {

    private final JobSourceRegistry sourceRegistry;
    private final IngestionWriter writer;
    private final RunRecorder runRecorder;
    private final Set<IngestionKey> activeIngestions =
            ConcurrentHashMap.newKeySet();

    public IngestionService(
            JobSourceRegistry sourceRegistry,
            IngestionWriter writer,
            RunRecorder runRecorder
    ) {
        this.sourceRegistry = sourceRegistry;
        this.writer = writer;
        this.runRecorder = runRecorder;
    }

    @Override
    public IngestionResult ingest(
            JobSource source,
            String sourceAccount
    ) {
        return ingest(source, sourceAccount, sourceAccount);
    }

    @Override
    public IngestionResult ingest(
            JobSource source,
            String sourceAccount,
            String company
    ) {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        if (sourceAccount == null || sourceAccount.isBlank()) {
            throw new IllegalArgumentException("sourceAccount must not be blank");
        }

        String normalizedSourceAccount = sourceAccount.strip();
        String normalizedCompany = company == null || company.isBlank()
                ? normalizedSourceAccount
                : company.strip();
        JobSourceClient client = sourceRegistry.require(source);
        client.validateSourceAccount(normalizedSourceAccount);
        IngestionKey key = new IngestionKey(source, normalizedSourceAccount);
        if (!activeIngestions.add(key)) {
            throw new IngestionAlreadyRunningException(
                    "Ingestion is already running for "
                            + source + "/" + normalizedSourceAccount
            );
        }

        UUID runId = null;
        try {
            runId = runRecorder.start(source, normalizedSourceAccount);
            List<ExternalJob> jobs = List.copyOf(
                    client.fetchAll(
                            normalizedSourceAccount,
                            normalizedCompany
                    )
            );
            validateCompleteSnapshot(jobs);
            return writer.apply(
                    runId,
                    source,
                    normalizedSourceAccount,
                    jobs
            );
        } catch (RuntimeException exception) {
            if (runId != null) {
                runRecorder.failIfRunning(runId, exception.getMessage());
            }
            throw exception;
        } finally {
            activeIngestions.remove(key);
        }
    }

    private static void validateCompleteSnapshot(List<ExternalJob> jobs) {
        Set<String> sourceJobIds = new HashSet<>();
        for (ExternalJob job : jobs) {
            if (job == null
                    || isBlank(job.sourceJobId())
                    || isBlank(job.company())
                    || isBlank(job.title())
                    || isBlank(job.applyUrl())) {
                throw new IllegalStateException(
                        "Job source returned an invalid posting"
                );
            }
            if (!sourceJobIds.add(job.sourceJobId())) {
                throw new IllegalStateException(
                        "Job source returned duplicate posting ID "
                                + job.sourceJobId()
                );
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record IngestionKey(
            JobSource source,
            String sourceAccount
    ) {
    }
}
