package com.huy.jobpulse.ingestion.application;

import com.huy.jobpulse.jobs.domain.JobSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IngestionServiceTest {

    @Test
    void upstreamFailureDoesNotInvokeDatabaseWriter() {
        JobSourceClient client = new FailingJobSourceClient();
        RecordingWriter writer = new RecordingWriter();
        RecordingRunRecorder runRecorder = new RecordingRunRecorder();
        IngestionService service = new IngestionService(
                List.of(client),
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

    private static class FailingJobSourceClient implements JobSourceClient {

        @Override
        public JobSource source() {
            return JobSource.GREENHOUSE;
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

        @Override
        public UUID start(JobSource source, String sourceAccount) {
            return runId;
        }

        @Override
        public void failIfRunning(UUID runId, String failureMessage) {
            assertThat(runId).isEqualTo(this.runId);
            failed = true;
        }
    }
}
