package org.keyfort.demosite.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "guest_sessions")
public class GuestSessionEntity {

    @Id
    @Column(nullable = false, length = 64)
    private String id;

    @Column(nullable = false, length = 64)
    private String ticketId;

    @Column(nullable = false, length = 64)
    private String account;

    @Column(nullable = false, length = 64)
    private String scope;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private Instant lastAccessAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SessionStatus status;

    @Column(length = 128)
    private String deviceBindingHash;

    @Version
    @Column(nullable = false)
    private Long version;

    public GuestSessionEntity() {}

    public GuestSessionEntity(
            String id,
            String ticketId,
            String account,
            String scope,
            Instant createdAt,
            Instant expiresAt,
            Instant lastAccessAt,
            SessionStatus status,
            String deviceBindingHash
    ) {
        this.id = id;
        this.ticketId = ticketId;
        this.account = account;
        this.scope = scope;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.lastAccessAt = lastAccessAt;
        this.status = status;
        this.deviceBindingHash = deviceBindingHash;
        this.version = 0L;
    }

    public String getId() { return id; }
    public String getTicketId() { return ticketId; }
    public String getAccount() { return account; }
    public String getScope() { return scope; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getLastAccessAt() { return lastAccessAt; }
    public void setLastAccessAt(Instant lastAccessAt) { this.lastAccessAt = lastAccessAt; }
    public SessionStatus getStatus() { return status; }
    public void setStatus(SessionStatus status) { this.status = status; }
    public String getDeviceBindingHash() { return deviceBindingHash; }
    public void setDeviceBindingHash(String deviceBindingHash) { this.deviceBindingHash = deviceBindingHash; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
