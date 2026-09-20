package com.huy.jobpulse.admin.infrastructure;

import com.huy.jobpulse.admin.domain.AdminAuditEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AdminAuditRepository extends JpaRepository<AdminAuditEntry, UUID> {
    Page<AdminAuditEntry> findAllByOrderByOccurredAtDesc(Pageable pageable);
}
