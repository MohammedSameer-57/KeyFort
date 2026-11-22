package org.keyfort.demosite.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "tickets")
public class TicketEntity {

    @Id
    @Column(nullable = false, length = 64)
    private String id;

    @Column(nullable = false, unique = true, length = 128)
    private String ticketToken;

    @Column(nullable = false, length = 64)
    private String entryId;

    @Column(nullable = false, length = 64)
    private String scope;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean used;

    @Column(nullable = false)
    private boolean revoked;

    @Version
    private Long version;

    public TicketEntity() {}

    public TicketEntity(String id, String ticketToken, String entryId, String scope, Instant createdAt, Instant expiresAt) {
        this.id = id;
        this.ticketToken = ticketToken;
        this.entryId = entryId;
        this.scope = scope;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.used = false;
        this.revoked = false;
    }

    public String getId() { return id; }
    public String getTicketToken() { return ticketToken; }
    public String getEntryId() { return entryId; }
    public String getScope() { return scope; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isUsed() { return used; }
    public void setUsed(boolean used) { this.used = used; }
    public boolean isRevoked() { return revoked; }
    public void setRevoked(boolean revoked) { this.revoked = revoked; }
    public Long getVersion() { return version; }
}
