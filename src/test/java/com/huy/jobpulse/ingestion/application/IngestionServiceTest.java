package com.huy.jobpulse.ingestion.application;

import com.huy.jobpulse.jobs.domain.JobSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IngestionServiceTest {

    @Test
    void upstreamFailureDoesNotInvokeDatabaseWriter() {
        JobSourceClient client = new FailingJobSourceClient();
        RecordingWriter writer = new RecordingWriter();
        IngestionService service = new IngestionService(
                List.of(client),
                writer
        );

        assertThatThrownBy(() -> service.ingest(
                JobSource.GREENHOUSE,
                "example"
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("malformed response");

        assertThat(writer.invoked).isFalse();
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
        public IngestionResult upsert(
                JobSource source,
                String sourceAccount,
                List<ExternalJob> jobs
        ) {
            invoked = true;
            return new IngestionResult(0, 0, 0, 0);
        }
    }
}
