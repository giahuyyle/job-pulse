package com.huy.jobpulse.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "admin_audit_log")
public class AdminAuditEntry {
    @Id private UUID id;
    @Column(nullable = false, length = 160) private String actor;
    @Column(name = "action_type", nullable = false, length = 100) private String actionType;
    @Column(name = "target_type", nullable = false, length = 80) private String targetType;
    @Column(name = "target_id", nullable = false, length = 255) private String targetId;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
    @Column(name = "before_value", columnDefinition = "TEXT") private String beforeValue;
    @Column(name = "after_value", columnDefinition = "TEXT") private String afterValue;
    @Column(name = "correlation_id", nullable = false) private UUID correlationId;

    protected AdminAuditEntry() {}

    public static AdminAuditEntry create(String actor, String actionType,
            String targetType, String targetId, Instant occurredAt,
            String beforeValue, String afterValue, UUID correlationId) {
        AdminAuditEntry entry = new AdminAuditEntry();
        entry.id = UUID.randomUUID(); entry.actor = actor; entry.actionType = actionType;
        entry.targetType = targetType; entry.targetId = targetId;
        entry.occurredAt = occurredAt; entry.beforeValue = beforeValue;
        entry.afterValue = afterValue; entry.correlationId = correlationId;
        return entry;
    }

    public UUID getId() { return id; }
    public String getActor() { return actor; }
    public String getActionType() { return actionType; }
    public String getTargetType() { return targetType; }
    public String getTargetId() { return targetId; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getBeforeValue() { return beforeValue; }
    public String getAfterValue() { return afterValue; }
    public UUID getCorrelationId() { return correlationId; }
}
