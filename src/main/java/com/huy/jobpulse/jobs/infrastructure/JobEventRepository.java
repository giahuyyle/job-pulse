package com.huy.jobpulse.jobs.infrastructure;

import com.huy.jobpulse.jobs.domain.JobEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JobEventRepository extends JpaRepository<JobEvent, UUID> {
}
