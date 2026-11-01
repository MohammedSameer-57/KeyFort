package org.keyfort.core;

import javax.crypto.AEADBadTagException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Zero-knowledge encrypted vault:
 *  - Master secret derives KEK via PBKDF2; KEK wraps a random 256-bit DEK with AES-256-GCM.
 *  - Server only holds ciphertext and public metadata; never master secret, DEK, or plaintext.
 *  - Per-entry keys derived from DEK via HKDF-SHA256 with AAD bound to (entry id + key version).
 *  - Recovery: DEK wrapped under RSA-3072-OAEP recovery public key; user keeps private recovery kit.
 *  - Atomic rotation: creates new DEK, re-encrypts all entries, re-wraps under KEK & recovery pub.
 */
public final class Vault {

    public record Created(Vault vault, byte[] recoveryKit) {}

    private final String userId;
    private final int iterations;
    private final PublicKey recoveryPub;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private byte[] kdfSalt;
    private byte[] wrappedDek;          // AES-256-GCM under KEK
    private byte[] recoveryWrappedDek;  // RSA-OAEP under recovery public key
    private int keyVersion = 1;
    private boolean rotationPending;

    private Vault(String userId, int iterations, PublicKey recoveryPub) {
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.iterations = iterations;
        this.recoveryPub = Objects.requireNonNull(recoveryPub, "recoveryPub must not be null");
    }

    public static Created create(String userId, char[] masterSecret) throws GeneralSecurityException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(3072);
        KeyPair recovery = kpg.generateKeyPair();

        Vault v = new Vault(userId, Crypto.PBKDF2_ITERATIONS, recovery.getPublic());
        v.kdfSalt = Crypto.random(16);
        byte[] kek = Crypto.pbkdf2(masterSecret, v.kdfSalt, v.iterations);
        byte[] dek = Crypto.random(32);
        try {
            v.wrappedDek = Crypto.seal(kek, dek, dekAad(1));
            v.recoveryWrappedDek = Crypto.rsaWrap(v.recoveryPub, dek);
            return new Created(v, recovery.getPrivate().getEncoded());
        } finally {
            Crypto.wipe(kek);
            Crypto.wipe(dek);
        }
    }

    /**
     * Reconstruct a Vault from stored ciphertext without needing the master secret.
     */
    public static Vault restore(
            String userId,
            int iterations,
            PublicKey recoveryPub,
            byte[] kdfSalt,
            byte[] wrappedDek,
            byte[] recoveryWrappedDek,
            int keyVersion,
            boolean rotationPending,
            Map<String, Entry> initialEntries
    ) {
        Vault v = new Vault(userId, iterations, recoveryPub);
        v.kdfSalt = kdfSalt != null ? kdfSalt.clone() : null;
        v.wrappedDek = wrappedDek != null ? wrappedDek.clone() : null;
        v.recoveryWrappedDek = recoveryWrappedDek != null ? recoveryWrappedDek.clone() : null;
        v.keyVersion = keyVersion;
        v.rotationPending = rotationPending;
        if (initialEntries != null) {
            v.entries.putAll(initialEntries);
        }
        return v;
    }

    public String userId() { return userId; }
    public int iterations() { return iterations; }
    public PublicKey recoveryPub() { return recoveryPub; }
    public synchronized byte[] kdfSalt() { return kdfSalt != null ? kdfSalt.clone() : null; }
    public synchronized byte[] wrappedDek() { return wrappedDek != null ? wrappedDek.clone() : null; }
    public synchronized byte[] recoveryWrappedDek() { return recoveryWrappedDek != null ? recoveryWrappedDek.clone() : null; }
    public synchronized int keyVersion() { return keyVersion; }
    public synchronized boolean rotationPending() { return rotationPending; }

    public synchronized Map<String, Entry> entries() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(entries));
    }

    /** Called by orchestrator/server; rotation executes client-side on next trusted unlock. */
    public synchronized boolean requestRotation() {
        if (rotationPending) return false;
        rotationPending = true;
        return true;
    }

    public synchronized void setRotationPending(boolean pending) {
        this.rotationPending = pending;
    }

    public synchronized Session unlock(char[] masterSecret) throws GeneralSecurityException, AuthException {
        byte[] kek = Crypto.pbkdf2(masterSecret, kdfSalt, iterations);
        byte[] dek;
        try {
            dek = Crypto.open(kek, wrappedDek, dekAad(keyVersion));
        } catch (AEADBadTagException e) {
            Crypto.wipe(kek);
            throw new AuthException("Invalid master secret", e);
        } catch (GeneralSecurityException e) {
            Crypto.wipe(kek);
            throw new AuthException("Failed to decrypt vault: " + e.getMessage(), e);
        }

        Session s = new Session(kek, dek);
        if (rotationPending) {
            s.rotateKeys();
        }
        return s;
    }

    /** Recovery without exposing credentials: un-wraps DEK via recovery kit and re-wraps under new master secret. */
    public synchronized void recover(byte[] recoveryKit, char[] newMasterSecret)
            throws GeneralSecurityException, AuthException {
        PrivateKey priv;
        try {
            priv = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(recoveryKit));
        } catch (GeneralSecurityException e) {
            throw new AuthException("Invalid recovery kit key format", e);
        }

        byte[] dek;
        try {
            dek = Crypto.rsaUnwrap(priv, recoveryWrappedDek);
        } catch (GeneralSecurityException e) {
            throw new AuthException("Invalid recovery kit", e);
        }

        byte[] newSalt = Crypto.random(16);
        byte[] kek = Crypto.pbkdf2(newMasterSecret, newSalt, iterations);
        try {
            kdfSalt = newSalt;
            wrappedDek = Crypto.seal(kek, dek, dekAad(keyVersion));
        } finally {
            Crypto.wipe(kek);
            Crypto.wipe(dek);
        }
    }

    public static byte[] dekAad(int version) {
        return ("keyfort|dek|v" + version).getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] entryAad(String id, int version) {
        return ("keyfort|entry|" + id + "|v" + version).getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] entryKey(byte[] dek, String id) throws GeneralSecurityException {
        return Crypto.hkdf(dek, "keyfort-entry-salt".getBytes(StandardCharsets.UTF_8),
                id.getBytes(StandardCharsets.UTF_8), 32);
    }

    /**
     * Unlocked session. Holds key material in memory only until closed.
     */
    public final class Session implements AutoCloseable {
        private byte[] kek;
        private byte[] dek;
        private boolean closed = false;

        private Session(byte[] kek, byte[] dek) {
            this.kek = kek;
            this.dek = dek;
        }

        private void checkNotClosed() {
            if (closed || kek == null || dek == null) {
                throw new IllegalStateException("Vault session is closed");
            }
        }

        public String add(String site, String username, String password) throws GeneralSecurityException {
            checkNotClosed();
            synchronized (Vault.this) {
                String id = UUID.randomUUID().toString();
                byte[] ek = entryKey(dek, id);
                try {
                    byte[] sealed = Crypto.seal(ek, password.getBytes(StandardCharsets.UTF_8), entryAad(id, keyVersion));
                    entries.put(id, new Entry(id, site, username, sealed, keyVersion));
                    return id;
                } finally {
                    Crypto.wipe(ek);
                }
            }
        }

        public void update(String id, String site, String username, String password) throws GeneralSecurityException {
            checkNotClosed();
            synchronized (Vault.this) {
                if (!entries.containsKey(id)) {
                    throw new IllegalArgumentException("No such entry: " + id);
                }
                byte[] ek = entryKey(dek, id);
                try {
                    byte[] sealed = Crypto.seal(ek, password.getBytes(StandardCharsets.UTF_8), entryAad(id, keyVersion));
                    entries.put(id, new Entry(id, site, username, sealed, keyVersion));
                } finally {
                    Crypto.wipe(ek);
                }
            }
        }

        public boolean remove(String id) {
            checkNotClosed();
            synchronized (Vault.this) {
                return entries.remove(id) != null;
            }
        }

        public String reveal(String id) throws GeneralSecurityException {
            checkNotClosed();
            synchronized (Vault.this) {
                Entry e = Objects.requireNonNull(entries.get(id), "no such entry");
                byte[] ek = entryKey(dek, id);
                byte[] pt = null;
                try {
                    pt = Crypto.open(ek, e.sealedPassword(), entryAad(id, e.keyVersion()));
                    return new String(pt, StandardCharsets.UTF_8);
                } finally {
                    Crypto.wipe(ek);
                    Crypto.wipe(pt);
                }
            }
        }

        public Map<String, Entry> entries() {
            checkNotClosed();
            return Vault.this.entries();
        }

        /**
         * Full rotation: new DEK, every entry re-encrypted, DEK re-wrapped for master + recovery. Atomic swap.
         */
        public void rotateKeys() throws GeneralSecurityException {
            checkNotClosed();
            synchronized (Vault.this) {
                int newVersion = keyVersion + 1;
                byte[] newDek = Crypto.random(32);
                Map<String, Entry> rotated = new LinkedHashMap<>();
                for (Entry e : entries.values()) {
                    byte[] oldKey = entryKey(dek, e.id());
                    byte[] pt = null;
                    byte[] newKey = null;
                    try {
                        pt = Crypto.open(oldKey, e.sealedPassword(), entryAad(e.id(), e.keyVersion()));
                        newKey = entryKey(newDek, e.id());
                        byte[] sealed = Crypto.seal(newKey, pt, entryAad(e.id(), newVersion));
                        rotated.put(e.id(), new Entry(e.id(), e.site(), e.username(), sealed, newVersion));
                    } finally {
                        Crypto.wipe(oldKey);
                        Crypto.wipe(newKey);
                        Crypto.wipe(pt);
                    }
                }
                byte[] newWrapped = Crypto.seal(kek, newDek, dekAad(newVersion));
                byte[] newRecovery = Crypto.rsaWrap(recoveryPub, newDek);

                // Commit only after all operations succeeded
                entries.clear();
                entries.putAll(rotated);
                wrappedDek = newWrapped;
                recoveryWrappedDek = newRecovery;
                keyVersion = newVersion;
                rotationPending = false;
                Crypto.wipe(dek);
                dek = newDek;
            }
        }

        @Override
        public void close() {
            closed = true;
            Crypto.wipe(kek);
            Crypto.wipe(dek);
            kek = null;
            dek = null;
        }
    }
}
