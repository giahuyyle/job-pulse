package com.huy.jobpulse.ingestion.application;

import com.huy.jobpulse.ingestion.api.CreateIngestionTargetRequest;
import com.huy.jobpulse.ingestion.domain.IngestionRequest;
import com.huy.jobpulse.ingestion.domain.IngestionRequestStatus;
import com.huy.jobpulse.ingestion.infrastructure.IngestionMessage;
import com.huy.jobpulse.ingestion.infrastructure.IngestionRequestRepository;
import com.huy.jobpulse.ingestion.infrastructure.IngestionDeadLetterRepository;
import com.huy.jobpulse.ingestion.infrastructure.IngestionTargetRepository;
import com.huy.jobpulse.jobs.domain.JobSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@Import(IngestionWorkerTest.MutableClockConfiguration.class)
class IngestionWorkerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired IngestionRequestService requestService;
    @Autowired IngestionTargetService targetService;
    @Autowired IngestionRequestRepository requestRepository;
    @Autowired IngestionDeadLetterRepository deadLetters;
    @Autowired IngestionTargetRepository targetRepository;
    @Autowired ObjectMapper objectMapper;
    @Autowired MutableClock clock;

    @BeforeEach
    void cleanDatabase() {
        deadLetters.deleteAll();
        requestRepository.deleteAll();
        targetRepository.deleteAll();
    }

    @Test
    void duplicateDeliveryCommitsIngestionOnlyOnce() throws Exception {
        IngestionRequest request = request("board-a");
        RecordingCoordinator coordinator = new RecordingCoordinator(false);
        IngestionWorker worker = worker(coordinator);
        byte[] message = message(request);

        worker.receive(message);
        worker.receive(message);

        IngestionRequest completed = reload(request);
        assertThat(coordinator.calls).isOne();
        assertThat(completed.getStatus()).isEqualTo(IngestionRequestStatus.SUCCEEDED);
        assertThat(completed.getAttemptCount()).isOne();
        assertThat(completed.getFinishedAt()).isEqualTo(clock.instant());
    }

    @Test
    void boundedFailuresEndInFailedForDeadLettering() throws Exception {
        IngestionRequest request = request("board-fail");
        RecordingCoordinator coordinator = new RecordingCoordinator(true);
        IngestionWorker worker = worker(coordinator);
        byte[] message = message(request);

        assertThatThrownBy(() -> worker.receive(message))
                .isInstanceOf(ImmediateRequeueAmqpException.class);
        clock.advance(Duration.ofSeconds(6));
        assertThatThrownBy(() -> worker.receive(message))
                .isInstanceOf(ImmediateRequeueAmqpException.class);
        clock.advance(Duration.ofSeconds(31));
        assertThatThrownBy(() -> worker.receive(message))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        IngestionRequest failed = reload(request);
        assertThat(coordinator.calls).isEqualTo(3);
        assertThat(failed.getStatus()).isEqualTo(IngestionRequestStatus.FAILED);
        assertThat(failed.getAttemptCount()).isEqualTo(3);
        assertThat(failed.getLastError()).contains("provider unavailable");

        assertThat(requestService.markPublished(request.getId())).isTrue();
        assertThat(reload(request).getPublishedAt()).isEqualTo(clock.instant());

        var dead = deadLetters.findAll().getFirst();
        var replay = requestService.replayDeadLetter(dead.getId(),
                "test-operator", "Provider account corrected");
        assertThat(replay.getId()).isEqualTo(request.getId());
        assertThat(replay.getStatus()).isEqualTo(IngestionRequestStatus.PENDING);
        assertThat(replay.getAttemptCount()).isZero();
        assertThat(replay.getPublishedAt()).isNull();
        var recorded = deadLetters.findById(dead.getId()).orElseThrow();
        assertThat(recorded.getReplayRequestId()).isEqualTo(request.getId());
        assertThat(recorded.getReplayReason()).isEqualTo("Provider account corrected");
    }

    @Test
    void permanentProviderFailureIsNotRetried() throws Exception {
        IngestionRequest request = request("board-unauthorized");
        IngestionWorker worker = worker((source, account) -> {
            throw new com.huy.jobpulse.ingestion.error.PermanentProviderException(
                    "Provider returned HTTP 401", null);
        });

        assertThatThrownBy(() -> worker.receive(message(request)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThat(reload(request).getAttemptCount()).isOne();
        assertThat(reload(request).getStatus()).isEqualTo(IngestionRequestStatus.FAILED);
    }

    @Test
    void expiredLeaseIsResetForOutboxRepublishing() {
        IngestionRequest request = request("board-recover");
        assertThat(requestService.markPublished(request.getId())).isTrue();
        IngestionWork expiredOwner = requestService.claim(request.getId()).orElseThrow();
        clock.advance(Duration.ofMinutes(11));

        assertThat(requestService.recoverExpired(3)).isOne();

        IngestionRequest recovered = reload(request);
        assertThat(recovered.getStatus()).isEqualTo(IngestionRequestStatus.PENDING);
        assertThat(recovered.getPublishedAt()).isNull();
        assertThat(recovered.getLeaseUntil()).isNull();
        requestService.succeed(expiredOwner);
        assertThat(reload(request).getStatus()).isEqualTo(IngestionRequestStatus.PENDING);
        IngestionWork newOwner = requestService.claim(request.getId()).orElseThrow();
        assertThat(newOwner.leaseOwner()).isNotEqualTo(expiredOwner.leaseOwner());
        requestService.succeed(newOwner);
        assertThat(reload(request).getStatus()).isEqualTo(IngestionRequestStatus.SUCCEEDED);
    }

    @Test
    void activeOwnerCanRenewLeaseAndComplete() {
        IngestionRequest request = request("board-heartbeat");
        IngestionWork work = requestService.claim(request.getId()).orElseThrow();
        Instant originalExpiry = reload(request).getLeaseUntil();
        assertThat(work.leaseOwner()).isNotNull();
        clock.advance(Duration.ofMinutes(9));

        assertThat(requestService.renewLease(work)).isTrue();
        assertThat(reload(request).getLeaseUntil()).isAfter(originalExpiry);
        clock.advance(Duration.ofMinutes(2));
        assertThat(requestService.recoverExpired(3)).isZero();
        requestService.succeed(work);
        assertThat(reload(request).getStatus()).isEqualTo(IngestionRequestStatus.SUCCEEDED);
        assertThat(reload(request).getLeaseOwner()).isNull();
    }

    @Test
    void staleConfirmedPendingDeliveryIsEligibleForRepublishing() {
        IngestionRequest request = request("board-stale-pending");
        assertThat(requestService.markPublished(request.getId())).isTrue();
        clock.advance(Duration.ofMinutes(6));

        assertThat(requestService.recoverExpired(3)).isOne();
        assertThat(reload(request).getStatus()).isEqualTo(IngestionRequestStatus.PENDING);
        assertThat(reload(request).getPublishedAt()).isNull();
    }

    private IngestionWorker worker(IngestionCoordinator coordinator) {
        return new IngestionWorker(requestService, coordinator, objectMapper);
    }

    private IngestionRequest request(String account) {
        targetService.create(new CreateIngestionTargetRequest(
                JobSource.GREENHOUSE,
                account,
                "Company " + account,
                "https://example.com/" + account,
                60
        ));
        return requestService.request(JobSource.GREENHOUSE, account);
    }

    private byte[] message(IngestionRequest request) throws Exception {
        return objectMapper.writeValueAsBytes(new IngestionMessage(request.getId()));
    }

    private IngestionRequest reload(IngestionRequest request) {
        return requestRepository.findById(request.getId()).orElseThrow();
    }

    private static final class RecordingCoordinator implements IngestionCoordinator {
        private final boolean fail;
        private int calls;
        private RecordingCoordinator(boolean fail) { this.fail = fail; }

        @Override
        public IngestionResult ingest(JobSource source, String sourceAccount) {
            calls++;
            if (fail) {
                throw new com.huy.jobpulse.ingestion.error.TransientProviderException(
                        "provider unavailable", null);
            }
            return new IngestionResult(1, 1, 0, 0, 0);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MutableClockConfiguration {
        @Bean @Primary
        MutableClock mutableClock() {
            return new MutableClock(Instant.parse("2026-09-01T12:00:00Z"));
        }
    }

    static class MutableClock extends Clock {
        private Instant instant;
        MutableClock(Instant instant) { this.instant = instant; }
        void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
