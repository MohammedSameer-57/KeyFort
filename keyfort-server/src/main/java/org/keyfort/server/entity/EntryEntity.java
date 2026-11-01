package org.keyfort.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Persisted sealed entry representation.
 * STORES STRICTLY CIPHERTEXT. Password is encrypted under HKDF-derived entry key.
 */
@Entity
@Table(name = "entries", indexes = {
        @Index(name = "idx_entries_user_id", columnList = "user_id")
})
public class EntryEntity {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

    @Column(name = "site", nullable = false, length = 256)
    private String site;

    @Column(name = "username", nullable = false, length = 256)
    private String username;

    @Lob
    @Column(name = "sealed_password", nullable = false)
    private byte[] sealedPassword;

    @Column(name = "key_version", nullable = false)
    private int keyVersion = 1;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public EntryEntity() {}

    public EntryEntity(String id, String userId, String site, String username, byte[] sealedPassword, int keyVersion, Instant updatedAt) {
        this.id = id;
        this.userId = userId;
        this.site = site;
        this.username = username;
        this.sealedPassword = sealedPassword;
        this.keyVersion = keyVersion;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getSite() { return site; }
    public void setSite(String site) { this.site = site; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public byte[] getSealedPassword() { return sealedPassword; }
    public void setSealedPassword(byte[] sealedPassword) { this.sealedPassword = sealedPassword; }

    public int getKeyVersion() { return keyVersion; }
    public void setKeyVersion(int keyVersion) { this.keyVersion = keyVersion; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
