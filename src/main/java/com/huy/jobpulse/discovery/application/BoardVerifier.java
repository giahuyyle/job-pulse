package com.huy.jobpulse.discovery.application;

import com.huy.jobpulse.jobs.domain.JobSource;

public interface BoardVerifier {

    JobSource source();

    BoardVerification verify(String sourceAccount);
}
