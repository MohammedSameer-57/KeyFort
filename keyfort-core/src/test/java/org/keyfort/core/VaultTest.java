package org.keyfort.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Zero-Knowledge Vault and Session Tests")
class VaultTest {

    @Test
    @DisplayName("Vault creation generates RSA recovery kit and initializes version 1")
    void testVaultCreation() throws Exception {
        char[] master = "master-secret-phrase-123".toCharArray();
        Vault.Created created = Vault.create("alice", master);

        assertNotNull(created);
        assertNotNull(created.recoveryKit());
        assertTrue(created.recoveryKit().length > 1000, "RSA-3072 private key in PKCS#8 format is typically >1KB");

        Vault vault = created.vault();
        assertEquals("alice", vault.userId());
        assertEquals(1, vault.keyVersion());
        assertFalse(vault.rotationPending());
        assertNotNull(vault.recoveryPub());
        assertEquals(16, vault.kdfSalt().length);
        assertNotNull(vault.wrappedDek());
        assertNotNull(vault.recoveryWrappedDek());
    }

    @Test
    @DisplayName("Unlock with valid master secret allows adding and revealing credentials")
    void testUnlockAddAndReveal() throws Exception {
        char[] master = "correct-horse-battery-staple".toCharArray();
        Vault.Created created = Vault.create("bob", master);
        Vault vault = created.vault();

        String entryId;
        try (Vault.Session session = vault.unlock(master)) {
            entryId = session.add("github.com", "bob", "S3cretToken!");
            assertNotNull(entryId);

            String revealed = session.reveal(entryId);
            assertEquals("S3cretToken!", revealed);
        }

        // Verify entry can still be revealed on subsequent unlock
        try (Vault.Session session = vault.unlock(master)) {
            String revealed = session.reveal(entryId);
            assertEquals("S3cretToken!", revealed);
        }
    }

    @Test
    @DisplayName("Unlock with invalid master secret throws AuthException")
    void testUnlockWithWrongMasterSecret() throws Exception {
        char[] master = "valid-master-secret".toCharArray();
        char[] wrongMaster = "wrong-master-secret".toCharArray();

        Vault.Created created = Vault.create("charlie", master);
        Vault vault = created.vault();

        AuthException ex = assertThrows(AuthException.class, () -> vault.unlock(wrongMaster));
        assertTrue(ex.getMessage().contains("Invalid master secret") || ex.getCause() != null);
    }

    @Test
    @DisplayName("Session close wipes key material and prevents subsequent operations")
    void testSessionClose() throws Exception {
        char[] master = "session-test-master".toCharArray();
        Vault.Created created = Vault.create("david", master);
        Vault vault = created.vault();

        Vault.Session session = vault.unlock(master);
        String id = session.add("bank.com", "david", "Pass123!");
        session.close();

        assertThrows(IllegalStateException.class, () -> session.reveal(id));
        assertThrows(IllegalStateException.class, () -> session.add("site.com", "u", "p"));
        assertThrows(IllegalStateException.class, session::rotateKeys);
    }

    @Test
    @DisplayName("Key rotation re-encrypts all entries under new DEK, bumps key version, and updates wrapped DEKs")
    void testKeyRotation() throws Exception {
        char[] master = "rotate-master-pass".toCharArray();
        Vault.Created created = Vault.create("eve", master);
        Vault vault = created.vault();

        String entry1;
        String entry2;
        try (Vault.Session session = vault.unlock(master)) {
            entry1 = session.add("service1.com", "eve", "P@ss1");
            entry2 = session.add("service2.com", "eve", "P@ss2");
        }

        byte[] oldWrappedDek = vault.wrappedDek();
        byte[] oldRecoveryWrappedDek = vault.recoveryWrappedDek();
        int initialVersion = vault.keyVersion();
        assertEquals(1, initialVersion);

        // Request rotation
        assertTrue(vault.requestRotation());
        assertTrue(vault.rotationPending());

        // On next unlock, pending rotation executes automatically
        try (Vault.Session session = vault.unlock(master)) {
            assertEquals(2, vault.keyVersion(), "Key version must increment to 2 after rotation");
            assertFalse(vault.rotationPending(), "Rotation pending flag must clear");
            assertFalse(java.util.Arrays.equals(oldWrappedDek, vault.wrappedDek()), "Wrapped DEK must change");
            assertFalse(java.util.Arrays.equals(oldRecoveryWrappedDek, vault.recoveryWrappedDek()), "Recovery wrapped DEK must change");

            // Both entries must decrypt properly under the rotated DEK
            assertEquals("P@ss1", session.reveal(entry1));
            assertEquals("P@ss2", session.reveal(entry2));

            // Verify entry key version metadata was updated
            assertEquals(2, vault.entries().get(entry1).keyVersion());
            assertEquals(2, vault.entries().get(entry2).keyVersion());
        }

        // Subsequent unlock works directly at version 2
        try (Vault.Session session = vault.unlock(master)) {
            assertEquals("P@ss1", session.reveal(entry1));
            assertEquals("P@ss2", session.reveal(entry2));
        }
    }

    @Test
    @DisplayName("Recovery kit un-wraps DEK and re-wraps under new master secret; entries remain decryptable")
    void testRecoveryFlow() throws Exception {
        char[] initialSecret = "first-secret-12345".toCharArray();
        char[] newSecret = "new-recovered-secret-999".toCharArray();

        Vault.Created created = Vault.create("frank", initialSecret);
        Vault vault = created.vault();

        String id;
        try (Vault.Session session = vault.unlock(initialSecret)) {
            id = session.add("cloud.com", "frank", "SecretCloudKey99!");
        }

        // Recover vault with recovery kit and new master secret
        vault.recover(created.recoveryKit(), newSecret);

        // Old secret must now fail
        assertThrows(AuthException.class, () -> vault.unlock(initialSecret));

        // New secret unlocks and reveals data
        try (Vault.Session session = vault.unlock(newSecret)) {
            assertEquals("SecretCloudKey99!", session.reveal(id));
        }
    }

    @Test
    @DisplayName("Recovery with invalid recovery kit fails with AuthException")
    void testRecoveryWithInvalidKit() throws Exception {
        char[] secret = "valid-secret".toCharArray();
        Vault.Created created = Vault.create("grace", secret);
        Vault vault = created.vault();

        byte[] fakeKit = KeyPairGenerator.getInstance("RSA").generateKeyPair().getPrivate().getEncoded();

        assertThrows(AuthException.class, () -> vault.recover(fakeKit, "new-secret".toCharArray()));
    }

    @Test
    @DisplayName("Tampering with stored wrapped DEK prevents unlock (tamper detection)")
    void testTamperWrappedDek() throws Exception {
        char[] secret = "tamper-test-pass".toCharArray();
        Vault.Created created = Vault.create("heidi", secret);
        Vault vault = created.vault();

        byte[] tamperedWrappedDek = vault.wrappedDek();
        tamperedWrappedDek[14] ^= 0x42; // Tamper with AES-GCM ciphertext

        Vault tamperedVault = Vault.restore(
                vault.userId(),
                vault.iterations(),
                vault.recoveryPub(),
                vault.kdfSalt(),
                tamperedWrappedDek,
                vault.recoveryWrappedDek(),
                vault.keyVersion(),
                vault.rotationPending(),
                vault.entries()
        );

        assertThrows(AuthException.class, () -> tamperedVault.unlock(secret));
    }
}
