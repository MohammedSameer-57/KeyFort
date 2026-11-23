package org.keyfort.server.orchestration.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "processed_connector_events")
public class ProcessedEventEntity {

    @Id
    @Column(nullable = false, length = 64)
    private String eventId;

    @Column(nullable = false, length = 64)
    private String ticketId;

    @Column(nullable = false)
    private Instant processedAt;

    public ProcessedEventEntity() {}

    public ProcessedEventEntity(String eventId, String ticketId, Instant processedAt) {
        this.eventId = eventId;
        this.ticketId = ticketId;
        this.processedAt = processedAt;
    }

    public String getEventId() { return eventId; }
    public String getTicketId() { return ticketId; }
    public Instant getProcessedAt() { return processedAt; }
}
