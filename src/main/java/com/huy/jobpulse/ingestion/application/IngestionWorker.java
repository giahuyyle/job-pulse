package com.huy.jobpulse.ingestion.application;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.huy.jobpulse.ingestion.infrastructure.IngestionAmqpTopology;
import com.huy.jobpulse.ingestion.infrastructure.IngestionMessage;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import com.huy.jobpulse.observability.PipelineMetrics;
import io.micrometer.core.instrument.Timer;

import java.util.Optional;

@Component
public class IngestionWorker {

    static final int MAX_ATTEMPTS = 3;

    private final IngestionRequestService requestService;
    private final IngestionCoordinator ingestionCoordinator;
    private final ObjectMapper objectMapper;
    private final PipelineMetrics metrics;

    @Autowired
    public IngestionWorker(
            IngestionRequestService requestService,
            IngestionCoordinator ingestionCoordinator,
            ObjectMapper objectMapper,
            ObjectProvider<PipelineMetrics> metrics
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
        Timer.Sample sample = metrics == null ? null : metrics.startIngestion();
        try {
            ingestionCoordinator.ingest(
                    work.source(),
                    work.sourceAccount(),
                    work.company()
            );
            requestService.succeed(work);
            if (metrics != null) metrics.finishIngestion(
                    sample, work.source().name(), true);
        } catch (RuntimeException exception) {
            if (metrics != null) metrics.finishIngestion(
                    sample, work.source().name(), false);
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
