package com.huy.jobpulse.jobs.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JobFingerprintTest {

    @Test
    void formattingDifferencesProduceTheSameHash() {
        String first = JobFingerprint.create(
                " Backend Engineer ", "Remote", "Build   APIs",
                "https://example.com/apply");

        String second = JobFingerprint.create(
                "backend engineer", " remote ", "build APIs",
                "https://example.com/apply");

        assertThat(first).isEqualTo(second);
        assertThat(first).matches("[0-9a-f]{64}");
    }

    @Test
    void changedDescriptionProducesADifferentHash() {
        String first = JobFingerprint.create(
                "Backend Engineer", "Remote", "Build APIs",
                "https://example.com/apply");

        String second = JobFingerprint.create(
                "Backend Engineer", "Remote", "Build distributed APIs",
                "https://example.com/apply");

        assertThat(second).isNotEqualTo(first);
    }
}
