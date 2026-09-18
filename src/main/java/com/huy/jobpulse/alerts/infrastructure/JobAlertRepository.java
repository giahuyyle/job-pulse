package com.huy.jobpulse.alerts.infrastructure;

import com.huy.jobpulse.alerts.domain.JobAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JobAlertRepository extends JpaRepository<JobAlert, UUID> {

    List<JobAlert> findAllByOrderByCreatedAtDescIdDesc();

    List<JobAlert> findAllByReadAtIsNullOrderByCreatedAtDescIdDesc();
}
