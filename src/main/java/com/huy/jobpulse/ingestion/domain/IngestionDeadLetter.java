package com.huy.jobpulse.ingestion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ingestion_dead_letters")
public class IngestionDeadLetter {
    @Id private UUID id;
    @Column(name="request_id", nullable=false) private UUID requestId;
    @Column(name="target_id", nullable=false) private UUID targetId;
    @Column(name="failed_at", nullable=false) private Instant failedAt;
    @Column(name="error_message", nullable=false, columnDefinition="TEXT") private String errorMessage;
    @Column(nullable=false, columnDefinition="TEXT") private String payload;
    @Column(name="replayed_at") private Instant replayedAt;
    @Column(name="replay_request_id") private UUID replayRequestId;
    @Column(name="replayed_by", length=160) private String replayedBy;
    @Column(name="replay_reason", columnDefinition="TEXT") private String replayReason;

    protected IngestionDeadLetter() {}
    public static IngestionDeadLetter create(UUID requestId, UUID targetId, Instant at, String error) {
        IngestionDeadLetter value = new IngestionDeadLetter(); value.id=UUID.randomUUID();
        value.requestId=requestId; value.targetId=targetId; value.failedAt=at;
        value.errorMessage=error; value.payload="{\"requestId\":\""+requestId+"\"}"; return value;
    }
    public void replayed(Instant at, UUID replayRequestId, String actor, String reason) {
        this.replayedAt=at; this.replayRequestId=replayRequestId;
        this.replayedBy=actor; this.replayReason=reason;
    }
    public UUID getId(){return id;} public UUID getRequestId(){return requestId;}
    public UUID getTargetId(){return targetId;} public Instant getFailedAt(){return failedAt;}
    public String getErrorMessage(){return errorMessage;} public String getPayload(){return payload;}
    public Instant getReplayedAt(){return replayedAt;} public UUID getReplayRequestId(){return replayRequestId;}
    public String getReplayedBy(){return replayedBy;} public String getReplayReason(){return replayReason;}
}
