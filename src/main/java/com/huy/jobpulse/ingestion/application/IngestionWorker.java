package com.huy.jobpulse.ingestion.application;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.huy.jobpulse.ingestion.infrastructure.IngestionAmqpTopology;
import com.huy.jobpulse.ingestion.infrastructure.IngestionMessage;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class IngestionWorker {

    static final int MAX_ATTEMPTS = 3;

    private final IngestionRequestService requestService;
    private final IngestionCoordinator ingestionCoordinator;
    private final ObjectMapper objectMapper;

    public IngestionWorker(
            IngestionRequestService requestService,
            IngestionCoordinator ingestionCoordinator,
            ObjectMapper objectMapper
    ) {
        this.requestService = requestService;
        this.ingestionCoordinator = ingestionCoordinator;
        this.objectMapper = objectMapper;
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
        try {
            ingestionCoordinator.ingest(
                    work.source(),
                    work.sourceAccount(),
                    work.company()
            );
            requestService.succeed(work);
        } catch (RuntimeException exception) {
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
