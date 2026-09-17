package com.huy.jobpulse.ingestion.application;

import com.huy.jobpulse.jobs.domain.JobSource;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class JobSourceRegistry {

    private final Map<JobSource, JobSourceClient> clients;

    public JobSourceRegistry(List<JobSourceClient> clients) {
        this.clients = new EnumMap<>(JobSource.class);
        for (JobSourceClient client : clients) {
            JobSourceClient previous = this.clients.put(client.source(), client);
            if (previous != null) {
                throw new IllegalStateException(
                        "Multiple clients configured for source " + client.source()
                );
            }
        }
    }

    public JobSourceClient require(JobSource source) {
        JobSourceClient client = clients.get(source);
        if (client == null) {
            throw new IllegalArgumentException(
                    "Ingestion is not supported for source " + source
            );
        }
        return client;
    }

    public void validate(JobSource source, String sourceAccount) {
        require(source).validateSourceAccount(sourceAccount);
    }
}
