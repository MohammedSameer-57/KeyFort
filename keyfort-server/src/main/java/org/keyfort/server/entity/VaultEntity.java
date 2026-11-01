package org.keyfort.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Persisted encrypted vault representation.
 * STORES STRICTLY CIPHERTEXT. Never holds master secrets, DEK, or plaintext passwords.
 */
@Entity
@Table(name = "vaults")
public class VaultEntity {

    @Id
    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

    @Column(name = "iterations", nullable = false)
    private int iterations;

    @Lob
    @Column(name = "recovery_pub", nullable = false)
    private byte[] recoveryPub;

    @Lob
    @Column(name = "kdf_salt", nullable = false)
    private byte[] kdfSalt;

    @Lob
    @Column(name = "wrapped_dek", nullable = false)
    private byte[] wrappedDek;

    @Lob
    @Column(name = "recovery_wrapped_dek", nullable = false)
    private byte[] recoveryWrappedDek;

    @Column(name = "key_version", nullable = false)
    private int keyVersion = 1;

    @Column(name = "rotation_pending", nullable = false)
    private boolean rotationPending = false;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public VaultEntity() {}

    public VaultEntity(
            String userId,
            int iterations,
            byte[] recoveryPub,
            byte[] kdfSalt,
            byte[] wrappedDek,
            byte[] recoveryWrappedDek,
            int keyVersion,
            boolean rotationPending,
            Instant updatedAt
    ) {
        this.userId = userId;
        this.iterations = iterations;
        this.recoveryPub = recoveryPub;
        this.kdfSalt = kdfSalt;
        this.wrappedDek = wrappedDek;
        this.recoveryWrappedDek = recoveryWrappedDek;
        this.keyVersion = keyVersion;
        this.rotationPending = rotationPending;
        this.updatedAt = updatedAt;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public int getIterations() { return iterations; }
    public void setIterations(int iterations) { this.iterations = iterations; }

    public byte[] getRecoveryPub() { return recoveryPub; }
    public void setRecoveryPub(byte[] recoveryPub) { this.recoveryPub = recoveryPub; }

    public byte[] getKdfSalt() { return kdfSalt; }
    public void setKdfSalt(byte[] kdfSalt) { this.kdfSalt = kdfSalt; }

    public byte[] getWrappedDek() { return wrappedDek; }
    public void setWrappedDek(byte[] wrappedDek) { this.wrappedDek = wrappedDek; }

    public byte[] getRecoveryWrappedDek() { return recoveryWrappedDek; }
    public void setRecoveryWrappedDek(byte[] recoveryWrappedDek) { this.recoveryWrappedDek = recoveryWrappedDek; }

    public int getKeyVersion() { return keyVersion; }
    public void setKeyVersion(int keyVersion) { this.keyVersion = keyVersion; }

    public boolean isRotationPending() { return rotationPending; }
    public void setRotationPending(boolean rotationPending) { this.rotationPending = rotationPending; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
