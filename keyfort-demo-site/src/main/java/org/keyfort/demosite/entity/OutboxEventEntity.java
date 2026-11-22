package org.keyfort.demosite.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "outbox_events")
public class OutboxEventEntity {

    public static final int DEFAULT_MAX_ATTEMPTS = 5;

    @Id
    @Column(nullable = false, length = 64)
    private String id;

    @Column(nullable = false, length = 64)
    private String eventType;

    @Lob
    @Column(nullable = false)
    private String payload;

    @Column(nullable = false)
    private int retryCount;

    @Column(nullable = false)
    private int maxAttempts;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OutboxStatus status;

    @Column(nullable = false)
    private boolean delivered;

    @Column(nullable = false)
    private Instant createdAt;

    @Column
    private Instant lastAttemptAt;

    @Column
    private Instant nextAttemptAt;

    public OutboxEventEntity() {}

    public OutboxEventEntity(String id, String eventType, String payload, Instant createdAt) {
        this(id, eventType, payload, createdAt, DEFAULT_MAX_ATTEMPTS);
    }

    public OutboxEventEntity(String id, String eventType, String payload, Instant createdAt, int maxAttempts) {
        this.id = id;
        this.eventType = eventType;
        this.payload = payload;
        this.retryCount = 0;
        this.maxAttempts = maxAttempts;
        this.status = OutboxStatus.PENDING;
        this.delivered = false;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public int getRetryCount() { return retryCount; }
    public void incrementRetry() { this.retryCount++; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public OutboxStatus getStatus() { return status; }
    public void setStatus(OutboxStatus status) { this.status = status; }
    public boolean isDelivered() { return delivered; }
    public void setDelivered(boolean delivered) { this.delivered = delivered; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastAttemptAt() { return lastAttemptAt; }
    public void setLastAttemptAt(Instant lastAttemptAt) { this.lastAttemptAt = lastAttemptAt; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(Instant nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }
}
