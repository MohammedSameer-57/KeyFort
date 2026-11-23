package org.keyfort.server.orchestration.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.keyfort.core.guestpass.GuestPass;
import org.keyfort.core.guestpass.GuestPassState;

import java.time.Instant;

@Entity
@Table(name = "guest_passes")
public class GuestPassEntity {

    @Id
    @Column(nullable = false, length = 64)
    private String id;

    @Column(nullable = false, length = 64)
    private String entryId;

    @Column(nullable = false, length = 64)
    private String scope;

    @Lob
    @Column(nullable = false)
    private byte[] secretHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private GuestPassState state;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(length = 128)
    private String credentialRef;

    @Column(length = 64)
    private String ownerUserId;

    @Column(length = 64)
    private String sessionId;

    @Version
    @Column(nullable = false)
    private Long version;

    public GuestPassEntity() {}

    public GuestPassEntity(
            String id,
            String entryId,
            String scope,
            byte[] secretHash,
            GuestPassState state,
            Instant createdAt,
            Instant expiresAt,
            String credentialRef
    ) {
        this.id = id;
        this.entryId = entryId;
        this.scope = scope;
        this.secretHash = secretHash != null ? secretHash.clone() : new byte[0];
        this.state = state;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.credentialRef = credentialRef;
        this.version = 0L;
    }

    public static GuestPassEntity fromDomain(GuestPass pass, String ownerUserId) {
        GuestPassEntity entity = new GuestPassEntity(
                pass.id(),
                pass.entryId(),
                pass.scope(),
                pass.secretHash(),
                pass.state(),
                pass.createdAt(),
                pass.expiresAt(),
                pass.credentialRef()
        );
        entity.setVersion(pass.version());
        entity.setOwnerUserId(ownerUserId);
        return entity;
    }

    public GuestPass toDomain() {
        return new GuestPass(
                this.id,
                this.entryId,
                this.scope,
                this.secretHash != null ? this.secretHash.clone() : new byte[0],
                this.state,
                this.createdAt,
                this.expiresAt,
                this.version != null ? this.version : 0L,
                this.credentialRef
        );
    }

    public String getId() { return id; }
    public String getEntryId() { return entryId; }
    public String getScope() { return scope; }
    public byte[] getSecretHash() { return secretHash != null ? secretHash.clone() : new byte[0]; }
    public GuestPassState getState() { return state; }
    public void setState(GuestPassState state) { this.state = state; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public String getCredentialRef() { return credentialRef; }
    public void setCredentialRef(String credentialRef) { this.credentialRef = credentialRef; }
    public String getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(String ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
