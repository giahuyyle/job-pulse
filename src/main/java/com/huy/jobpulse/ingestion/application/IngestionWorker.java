package com.huy.jobpulse.ingestion.application;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.huy.jobpulse.ingestion.infrastructure.IngestionAmqpTopology;
import com.huy.jobpulse.ingestion.infrastructure.IngestionMessage;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import com.huy.jobpulse.observability.JobPulseMetrics;
import io.micrometer.core.instrument.Timer;

import java.util.Optional;

@Component
public class IngestionWorker {

    static final int MAX_ATTEMPTS = 3;
    private static final Logger LOGGER = LoggerFactory.getLogger(IngestionWorker.class);

    private final IngestionRequestService requestService;
    private final IngestionCoordinator ingestionCoordinator;
    private final ObjectMapper objectMapper;
    private final JobPulseMetrics metrics;

    @Autowired
    public IngestionWorker(
            IngestionRequestService requestService,
            IngestionCoordinator ingestionCoordinator,
            ObjectMapper objectMapper,
            ObjectProvider<JobPulseMetrics> metrics
    ) {
        this.requestService = requestService;
        this.ingestionCoordinator = ingestionCoordinator;
        this.objectMapper = objectMapper;
        this.metrics = metrics.getIfAvailable();
    }

    public IngestionWorker(IngestionRequestService requestService,
            IngestionCoordinator ingestionCoordinator, ObjectMapper objectMapper) {
        this.requestService = requestService;
        this.ingestionCoordinator = ingestionCoordinator;
        this.objectMapper = objectMapper;
        this.metrics = null;
    }

    @RabbitListener(queues = IngestionAmqpTopology.QUEUE)
    public void receive(byte[] body) {
        IngestionMessage message = deserialize(body);
        Optional<IngestionWork> claimed = requestService.claim(
                message.requestId()
        );
        if (claimed.isEmpty()) {
            return;
        }

        IngestionWork work = claimed.orElseThrow();
        Timer.Sample sample = metrics == null ? null : metrics.startTimer();
        try (MDC.MDCCloseable requestId = MDC.putCloseable(
                        "requestId", work.requestId().toString());
                MDC.MDCCloseable boardId = MDC.putCloseable(
                        "boardId", work.targetId().toString());
                MDC.MDCCloseable provider = MDC.putCloseable(
                        "provider", work.source().name().toLowerCase())) {
            try {
                IngestionResult result = ingestionCoordinator.ingest(
                        work.source(),
                        work.sourceAccount(),
                        work.company()
                );
                requestService.succeed(work);
                if (metrics != null) {
                    metrics.recordPostings(work.source().name(), "created", result.created());
                    metrics.recordPostings(work.source().name(), "updated", result.updated());
                    metrics.recordPostings(work.source().name(), "unchanged", result.unchanged());
                    metrics.recordPostings(work.source().name(), "closed", result.closed());
                    metrics.finishIngestion(sample, work.source().name(), "success");
                }
                LOGGER.atInfo()
                        .addKeyValue("attempt", work.attemptCount())
                        .addKeyValue("created", result.created())
                        .addKeyValue("updated", result.updated())
                        .addKeyValue("unchanged", result.unchanged())
                        .addKeyValue("closed", result.closed())
                        .log("Ingestion request completed");
            } catch (RuntimeException exception) {
                if (metrics != null) {
                    metrics.finishIngestion(sample, work.source().name(), "failed");
                }
                LOGGER.atError()
                        .addKeyValue("attempt", work.attemptCount())
                        .setCause(exception)
                        .log("Ingestion request failed");
                if (requestService.fail(work, exception, MAX_ATTEMPTS)) {
                    throw new ImmediateRequeueAmqpException(
                            "Retrying ingestion request " + work.requestId(),
                            exception
                    );
                }
                throw new AmqpRejectAndDontRequeueException(
                        "Ingestion request exhausted retries " + work.requestId(),
                        exception
                );
            }
        }
    }

    private IngestionMessage deserialize(byte[] body) {
        try {
            IngestionMessage message = objectMapper.readValue(
                    body,
                    IngestionMessage.class
            );
            if (message.requestId() == null) {
                throw new IllegalArgumentException("requestId is required");
            }
            return message;
        } catch (JacksonException | IllegalArgumentException exception) {
            throw new AmqpRejectAndDontRequeueException(
                    "Invalid ingestion message",
                    exception
            );
        }
    }
}
