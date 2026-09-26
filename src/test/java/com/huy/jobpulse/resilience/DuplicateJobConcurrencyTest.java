package com.huy.jobpulse.resilience;

import com.huy.jobpulse.jobs.api.CreateJobRequest;
import com.huy.jobpulse.jobs.application.DuplicateJobException;
import com.huy.jobpulse.jobs.application.JobCommandService;
import com.huy.jobpulse.jobs.domain.JobSource;
import com.huy.jobpulse.jobs.domain.RemotePolicy;
import com.huy.jobpulse.jobs.infrastructure.JobPostingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class DuplicateJobConcurrencyTest {
    @Container @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired JobCommandService commands;
    @Autowired JobPostingRepository postings;

    @Test
    void simultaneousCreatesLeaveOneLogicalPosting() throws Exception {
        String account = "race-" + UUID.randomUUID();
        var request = new CreateJobRequest(JobSource.LEVER, account, "job-1",
                "Concurrency test", "Engineer", "Remote", "Description",
                "Full-time", RemotePolicy.REMOTE, "https://example.com/apply", null);
        var start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> create(start, request));
            var second = workers.submit(() -> create(start, request));
            start.countDown();
            int outcomes = first.get(20, TimeUnit.SECONDS) + second.get(20, TimeUnit.SECONDS);
            assertThat(outcomes).isOne();
        }
        assertThat(postings.findBySourceAndSourceAccountAndSourceJobId(
                JobSource.LEVER, account, "job-1")).isPresent();
    }

    private int create(CountDownLatch start, CreateJobRequest request) {
        try {
            start.await();
            commands.create(request);
            return 1;
        } catch (DuplicateJobException duplicate) {
            return 0;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}
