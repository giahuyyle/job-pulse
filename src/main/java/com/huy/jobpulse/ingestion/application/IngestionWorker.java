package com.huy.jobpulse.ingestion.application;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.huy.jobpulse.ingestion.infrastructure.IngestionAmqpTopology;
import com.huy.jobpulse.ingestion.infrastructure.IngestionMessage;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import com.huy.jobpulse.ingestion.error.ProviderRateLimitException;
import com.huy.jobpulse.ingestion.error.TransientProviderException;
import java.time.Duration;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.transaction.CannotCreateTransactionException;
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
    private final IngestionPublisher publisher;
    private final IngestionLeaseHeartbeat heartbeat;

    @Autowired
    public IngestionWorker(
            IngestionRequestService requestService,
            IngestionCoordinator ingestionCoordinator,
            ObjectMapper objectMapper,
            ObjectProvider<JobPulseMetrics> metrics,
            IngestionPublisher publisher,
            IngestionLeaseHeartbeat heartbeat
    ) {
        this.requestService = requestService;
        this.ingestionCoordinator = ingestionCoordinator;
        this.objectMapper = objectMapper;
        this.metrics = metrics.getIfAvailable();
        this.publisher = publisher;
        this.heartbeat = heartbeat;
    }

    public IngestionWorker(IngestionRequestService requestService,
            IngestionCoordinator ingestionCoordinator, ObjectMapper objectMapper) {
        this.requestService = requestService;
        this.ingestionCoordinator = ingestionCoordinator;
        this.objectMapper = objectMapper;
        this.metrics = null;
        this.publisher = null;
        this.heartbeat = null;
    }

    @RabbitListener(queues = IngestionAmqpTopology.QUEUE)
    public void receive(byte[] body) {
        IngestionMessage message = deserialize(body);
        Optional<IngestionWork> claimed;
        try {
            claimed = requestService.claim(message.requestId());
        } catch (DataAccessException | CannotCreateTransactionException unavailable) {
            if (metrics != null) metrics.recordRabbitRedelivery("database_unavailable");
            LOGGER.atError().addKeyValue("requestId", message.requestId())
                    .setCause(unavailable)
                    .log("Durable ingestion request could not be claimed");
            throw new AmqpRejectAndDontRequeueException(
                    "Database unavailable while claiming durable ingestion request",
                    unavailable);
        }
        if (claimed.isEmpty()) {
            if (metrics != null) metrics.recordRabbitRedelivery("duplicate");
            return;
        }

        IngestionWork work = claimed.orElseThrow();
        Timer.Sample sample = metrics == null ? null : metrics.startTimer();
        try (IngestionLeaseHeartbeat.LeaseHandle lease = heartbeat == null
                    ? () -> {} : heartbeat.start(work);
                MDC.MDCCloseable requestId = MDC.putCloseable(
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
                boolean retryable = isRetryable(exception);
                boolean retry;
                try {
                    retry = requestService.fail(work, exception,
                            retryable ? MAX_ATTEMPTS : work.attemptCount());
                } catch (DataAccessException | CannotCreateTransactionException unavailable) {
                    if (metrics != null) metrics.recordRabbitRedelivery("database_unavailable");
                    LOGGER.atError().addKeyValue("requestId", work.requestId())
                            .setCause(unavailable)
                            .log("Ingestion failure could not be recorded");
                    throw new AmqpRejectAndDontRequeueException(
                            "Database unavailable while recording ingestion failure",
                            unavailable);
                }
                if (retry) {
                    if (publisher != null) {
                        try {
                            publisher.publishRetry(work.requestId(), work.attemptCount());
                            requestService.markPublished(work.requestId());
                            if (metrics != null) metrics.recordRabbitRedelivery("delayed");
                            LOGGER.atWarn().addKeyValue("requestId", work.requestId())
                                    .addKeyValue("attempt", work.attemptCount())
                                    .log("Ingestion request sent to delayed retry queue");
                            return;
                        } catch (RuntimeException publishFailure) {
                            throw new ImmediateRequeueAmqpException(
                                    "Could not publish delayed retry", publishFailure);
                        }
                    }
                    throw new ImmediateRequeueAmqpException(
                            "Retrying ingestion request " + work.requestId(), exception);
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

    private static boolean isRetryable(RuntimeException failure) {
        if (failure instanceof ProviderRateLimitException rate
                && rate.retryAfter().compareTo(Duration.ofSeconds(5)) > 0) return false;
        if (failure instanceof TransientProviderException
                || failure instanceof TransientDataAccessException
                || failure instanceof RecoverableDataAccessException
                || failure instanceof CannotCreateTransactionException) return true;
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.sql.SQLException sql) {
                String state = sql.getSQLState();
                if (state != null && (state.startsWith("08")
                        || state.startsWith("40") || "57P01".equals(state))) return true;
            }
        }
        return false;
    }
}
