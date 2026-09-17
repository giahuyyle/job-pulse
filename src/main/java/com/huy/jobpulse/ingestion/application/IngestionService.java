package com.huy.jobpulse.ingestion.application;

import com.huy.jobpulse.jobs.domain.JobSource;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class IngestionService {

    private final Map<JobSource, JobSourceClient> clients;
    private final IngestionWriter writer;
    private final RunRecorder runRecorder;

    public IngestionService(
            List<JobSourceClient> clients,
            IngestionWriter writer,
            RunRecorder runRecorder
    ) {
        this.clients = new EnumMap<>(JobSource.class);
        for (JobSourceClient client : clients) {
            JobSourceClient previous = this.clients.put(client.source(), client);
            if (previous != null) {
                throw new IllegalStateException(
                        "Multiple clients configured for source " + client.source()
                );
            }
        }
        this.writer = writer;
        this.runRecorder = runRecorder;
    }

    public IngestionResult ingest(
            JobSource source,
            String sourceAccount
    ) {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        if (sourceAccount == null || sourceAccount.isBlank()) {
            throw new IllegalArgumentException("sourceAccount must not be blank");
        }

        String normalizedSourceAccount = sourceAccount.strip();
        JobSourceClient client = clients.get(source);
        if (client == null) {
            throw new IllegalArgumentException(
                    "Ingestion is not supported for source " + source
            );
        }

        UUID runId = runRecorder.start(source, normalizedSourceAccount);
        try {
            List<ExternalJob> jobs = List.copyOf(
                    client.fetchAll(normalizedSourceAccount)
            );
            validateCompleteSnapshot(jobs);
            if (jobs.isEmpty()
                    && writer.hasExistingPostings(
                            source,
                            normalizedSourceAccount
                    )) {
                throw new IllegalStateException(
                        "Refusing suspicious empty snapshot for existing board"
                );
            }
            return writer.apply(
                    runId,
                    source,
                    normalizedSourceAccount,
                    jobs
            );
        } catch (RuntimeException exception) {
            runRecorder.failIfRunning(runId, exception.getMessage());
            throw exception;
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
}
