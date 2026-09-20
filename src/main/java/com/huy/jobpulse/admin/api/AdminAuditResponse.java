package com.huy.jobpulse.admin.api;

import com.huy.jobpulse.admin.domain.AdminAuditEntry;
import java.time.Instant;
import java.util.UUID;

public record AdminAuditResponse(UUID id, String actor, String actionType,
        String targetType, String targetId, Instant occurredAt,
        String beforeValue, String afterValue, UUID correlationId) {
    public static AdminAuditResponse from(AdminAuditEntry entry) {
        return new AdminAuditResponse(entry.getId(), entry.getActor(),
                entry.getActionType(), entry.getTargetType(), entry.getTargetId(),
                entry.getOccurredAt(), entry.getBeforeValue(), entry.getAfterValue(),
                entry.getCorrelationId());
    }
}
