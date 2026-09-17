package com.huy.jobpulse.ingestion.application;

import com.huy.jobpulse.jobs.domain.JobSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IngestionServiceTest {

    @Test
    void upstreamFailureDoesNotInvokeDatabaseWriter() {
        JobSourceClient client = new FailingJobSourceClient();
        RecordingWriter writer = new RecordingWriter();
        RecordingRunRecorder runRecorder = new RecordingRunRecorder();
        IngestionService service = new IngestionService(
                new JobSourceRegistry(List.of(client)),
                writer,
                runRecorder
        );

        assertThatThrownBy(() -> service.ingest(
                JobSource.GREENHOUSE,
                "example"
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("malformed response");

        assertThat(writer.invoked).isFalse();
        assertThat(runRecorder.failed).isTrue();
    }

    @Test
    void preventsManualAndScheduledRunsFromOverlapping() throws Exception {
        BlockingJobSourceClient client = new BlockingJobSourceClient();
        RecordingWriter writer = new RecordingWriter();
        RecordingRunRecorder runRecorder = new RecordingRunRecorder();
        IngestionService service = new IngestionService(
                new JobSourceRegistry(List.of(client)),
                writer,
                runRecorder
        );

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<IngestionResult> first = executor.submit(() ->
                    service.ingest(JobSource.GREENHOUSE, "example")
            );
            assertThat(client.entered.await(5, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> service.ingest(
                    JobSource.GREENHOUSE,
                    "example"
            )).isInstanceOf(IngestionAlreadyRunningException.class);

            client.release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS))
                    .isEqualTo(new IngestionResult(0, 0, 0, 0, 0));
        }

        assertThat(runRecorder.started).isEqualTo(1);
    }

    private static class FailingJobSourceClient implements JobSourceClient {

        @Override
        public JobSource source() {
            return JobSource.GREENHOUSE;
        }

        @Override
        public void validateSourceAccount(String sourceAccount) {
        }

        @Override
        public List<ExternalJob> fetchAll(String sourceAccount) {
            throw new IllegalStateException("malformed response");
        }
    }

    private static class RecordingWriter implements IngestionWriter {

        private boolean invoked;

        @Override
        public IngestionResult apply(
                UUID runId,
                JobSource source,
                String sourceAccount,
                List<ExternalJob> jobs
        ) {
            invoked = true;
            return new IngestionResult(0, 0, 0, 0, 0);
        }

        @Override
        public boolean hasExistingPostings(
                JobSource source,
                String sourceAccount
        ) {
            return false;
        }
    }

    private static class RecordingRunRecorder implements RunRecorder {

        private final UUID runId = UUID.randomUUID();
        private boolean failed;
        private int started;

        @Override
        public UUID start(JobSource source, String sourceAccount) {
            started++;
            return runId;
        }

        @Override
        public void failIfRunning(UUID runId, String failureMessage) {
            assertThat(runId).isEqualTo(this.runId);
            failed = true;
        }
    }

    private static class BlockingJobSourceClient
            implements JobSourceClient {

        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        @Override
        public JobSource source() {
            return JobSource.GREENHOUSE;
        }

        @Override
        public void validateSourceAccount(String sourceAccount) {
        }

        @Override
        public List<ExternalJob> fetchAll(String sourceAccount) {
            entered.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting for test");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            return List.of();
        }
    }
}
