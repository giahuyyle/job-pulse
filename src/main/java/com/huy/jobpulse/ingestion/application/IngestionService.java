package com.huy.jobpulse.ingestion.application;

import com.huy.jobpulse.jobs.domain.JobSource;
import org.springframework.stereotype.Service;
import com.huy.jobpulse.ingestion.error.PermanentProviderException;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class IngestionService implements IngestionCoordinator {

    private static final Logger LOGGER = LoggerFactory.getLogger(IngestionService.class);

    private final JobSourceRegistry sourceRegistry;
    private final IngestionWriter writer;
    private final RunRecorder runRecorder;
    private final ProviderFetchPolicy fetchPolicy;
    private final Set<IngestionKey> activeIngestions =
            ConcurrentHashMap.newKeySet();

    @Autowired
    public IngestionService(
            JobSourceRegistry sourceRegistry,
            IngestionWriter writer,
            RunRecorder runRecorder,
            ProviderFetchPolicy fetchPolicy
    ) {
        this.sourceRegistry = sourceRegistry;
        this.writer = writer;
        this.runRecorder = runRecorder;
        this.fetchPolicy = fetchPolicy;
    }

    public IngestionService(JobSourceRegistry sourceRegistry,
            IngestionWriter writer, RunRecorder runRecorder) {
        this(sourceRegistry, writer, runRecorder, null);
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
            List<ExternalJob> fetched = fetchPolicy == null
                    ? client.fetchAll(normalizedSourceAccount, normalizedCompany)
                    : fetchPolicy.fetch(source, normalizedSourceAccount,
                            () -> client.fetchAll(normalizedSourceAccount,
                                    normalizedCompany));
            if (fetched == null) {
                throw new PermanentProviderException("Job source returned no snapshot", null);
            }
            validateCompleteSnapshot(fetched);
            List<ExternalJob> jobs = List.copyOf(fetched);
            IngestionResult result = writer.apply(
                    runId,
                    source,
                    normalizedSourceAccount,
                    jobs
            );
            LOGGER.atInfo()
                    .addKeyValue("runId", runId)
                    .addKeyValue("created", result.created())
                    .addKeyValue("updated", result.updated())
                    .addKeyValue("unchanged", result.unchanged())
                    .addKeyValue("closed", result.closed())
                    .log("Ingestion run completed");
            return result;
        } catch (RuntimeException exception) {
            if (runId != null) {
                runRecorder.failIfRunning(runId, exception.getMessage());
            }
            LOGGER.atError()
                    .addKeyValue("runId", runId)
                    .setCause(exception)
                    .log("Ingestion run failed");
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
                throw new PermanentProviderException(
                        "Job source returned an invalid posting", null);
            }
            if (!sourceJobIds.add(job.sourceJobId())) {
                throw new PermanentProviderException(
                        "Job source returned duplicate posting ID "
                                + job.sourceJobId(), null
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
