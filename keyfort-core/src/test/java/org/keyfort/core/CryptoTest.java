package org.keyfort.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.AEADBadTagException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Crypto Primitives Tests")
class CryptoTest {

    @Test
    @DisplayName("Random bytes generation generates expected length and non-zero bytes")
    void testRandom() {
        byte[] b1 = Crypto.random(32);
        byte[] b2 = Crypto.random(32);
        assertEquals(32, b1.length);
        assertEquals(32, b2.length);
        assertFalse(Arrays.equals(b1, b2), "Successive random byte arrays should not be identical");
    }

    @Test
    @DisplayName("PBKDF2 derives deterministic 256-bit key from secret and salt")
    void testPbkdf2Deterministic() throws Exception {
        char[] secret = "correct-horse-battery-staple".toCharArray();
        byte[] salt = "salt1234salt1234".getBytes(StandardCharsets.UTF_8);
        int iterations = 10_000;

        byte[] k1 = Crypto.pbkdf2(secret, salt, iterations);
        byte[] k2 = Crypto.pbkdf2(secret, salt, iterations);

        assertEquals(32, k1.length);
        assertArrayEquals(k1, k2, "PBKDF2 must be deterministic for identical secret, salt, and iterations");

        byte[] diffSalt = "diff1234diff1234".getBytes(StandardCharsets.UTF_8);
        byte[] k3 = Crypto.pbkdf2(secret, diffSalt, iterations);
        assertFalse(Arrays.equals(k1, k3), "PBKDF2 with different salt must yield different key");
    }

    @Test
    @DisplayName("HKDF-SHA256 conforms to RFC 5869 extract-and-expand")
    void testHkdf() throws Exception {
        byte[] ikm = "input-key-material-32-bytes-long!".getBytes(StandardCharsets.UTF_8);
        byte[] salt = "salt-value-for-hkdf".getBytes(StandardCharsets.UTF_8);
        byte[] info1 = "entry-id-1".getBytes(StandardCharsets.UTF_8);
        byte[] info2 = "entry-id-2".getBytes(StandardCharsets.UTF_8);

        byte[] key1 = Crypto.hkdf(ikm, salt, info1, 32);
        byte[] key1Again = Crypto.hkdf(ikm, salt, info1, 32);
        byte[] key2 = Crypto.hkdf(ikm, salt, info2, 32);

        assertEquals(32, key1.length);
        assertArrayEquals(key1, key1Again, "HKDF must produce identical output for identical inputs");
        assertFalse(Arrays.equals(key1, key2), "Different info parameter must produce distinct subkeys");
    }

    @Test
    @DisplayName("AES-256-GCM authenticated encryption seal and open round-trip")
    void testSealAndOpen() throws Exception {
        byte[] key = Crypto.random(32);
        byte[] plaintext = "TopSecretPassword123!".getBytes(StandardCharsets.UTF_8);
        byte[] aad = "keyfort|entry|uuid-1|v1".getBytes(StandardCharsets.UTF_8);

        byte[] blob = Crypto.seal(key, plaintext, aad);
        assertTrue(blob.length >= 12 + plaintext.length + 16, "Blob must contain IV(12) + ciphertext + tag(16)");

        byte[] decrypted = Crypto.open(key, blob, aad);
        assertArrayEquals(plaintext, decrypted, "Decrypted plaintext must match original");
    }

    @Test
    @DisplayName("AES-256-GCM rejects ciphertext tampering (tamper detection)")
    void testSealTamperCiphertext() throws Exception {
        byte[] key = Crypto.random(32);
        byte[] plaintext = "SensitiveCredentials".getBytes(StandardCharsets.UTF_8);
        byte[] aad = "aad-data".getBytes(StandardCharsets.UTF_8);

        byte[] blob = Crypto.seal(key, plaintext, aad);

        // Tamper with ciphertext byte
        blob[15] ^= 0x01;

        assertThrows(AEADBadTagException.class, () -> Crypto.open(key, blob, aad),
                "Decryption must throw AEADBadTagException when ciphertext is tampered");
    }

    @Test
    @DisplayName("AES-256-GCM rejects AAD tampering (tamper detection)")
    void testSealTamperAad() throws Exception {
        byte[] key = Crypto.random(32);
        byte[] plaintext = "SensitiveCredentials".getBytes(StandardCharsets.UTF_8);
        byte[] aad = "keyfort|entry|uuid-1|v1".getBytes(StandardCharsets.UTF_8);
        byte[] tamperedAad = "keyfort|entry|uuid-1|v2".getBytes(StandardCharsets.UTF_8);

        byte[] blob = Crypto.seal(key, plaintext, aad);

        assertThrows(AEADBadTagException.class, () -> Crypto.open(key, blob, tamperedAad),
                "Decryption must throw AEADBadTagException when AAD does not match");
    }

    @Test
    @DisplayName("RSA-OAEP wrap and unwrap round-trip")
    void testRsaWrapUnwrap() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(3072);
        KeyPair kp = kpg.generateKeyPair();

        byte[] dek = Crypto.random(32);
        byte[] wrapped = Crypto.rsaWrap(kp.getPublic(), dek);
        byte[] unwrapped = Crypto.rsaUnwrap(kp.getPrivate(), wrapped);

        assertArrayEquals(dek, unwrapped, "Unwrapped DEK must match original 256-bit DEK");
    }

    @Test
    @DisplayName("Hex utility encodes and decodes accurately without java.util.HexFormat")
    void testHexUtil() {
        byte[] data = new byte[] { 0x00, 0x0F, 0x10, (byte) 0xAA, (byte) 0xFF };
        String hex = Crypto.toHex(data);
        assertEquals("000f10aaff", hex);

        byte[] decoded = Crypto.fromHex(hex);
        assertArrayEquals(data, decoded);

        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                Crypto.sha256Hex(""));
    }

    @Test
    @DisplayName("Wipe zeros out byte array and char array")
    void testWipe() {
        byte[] b = new byte[] { 1, 2, 3, 4, 5 };
        Crypto.wipe(b);
        assertArrayEquals(new byte[] { 0, 0, 0, 0, 0 }, b);

        char[] c = new char[] { 'a', 'b', 'c' };
        Crypto.wipe(c);
        assertArrayEquals(new char[] { '\0', '\0', '\0' }, c);
    }
}
