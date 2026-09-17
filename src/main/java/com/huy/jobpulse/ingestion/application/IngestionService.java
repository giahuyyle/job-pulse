package com.huy.jobpulse.ingestion.application;

import com.huy.jobpulse.jobs.domain.JobSource;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class IngestionService {

    private final Map<JobSource, JobSourceClient> clients;
    private final IngestionWriter writer;

    public IngestionService(
            List<JobSourceClient> clients,
            IngestionWriter writer
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

        List<ExternalJob> jobs = List.copyOf(
                client.fetchAll(normalizedSourceAccount)
        );
        return writer.upsert(source, normalizedSourceAccount, jobs);
    }
}
