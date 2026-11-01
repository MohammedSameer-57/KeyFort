package org.keyfort.core;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.util.Arrays;

/**
 * KeyFort cryptographic primitives (pure Java 17+, JDK crypto only).
 * Compatible with Android < API 34 by using a custom hex utility instead of java.util.HexFormat.
 */
public final class Crypto {

    private Crypto() {}

    public static final SecureRandom RNG = new SecureRandom();
    public static final int PBKDF2_ITERATIONS = 600_000;

    private static final OAEPParameterSpec OAEP = new OAEPParameterSpec(
            "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);

    public static byte[] random(int n) {
        byte[] b = new byte[n];
        RNG.nextBytes(b);
        return b;
    }

    public static byte[] pbkdf2(char[] secret, byte[] salt, int iterations) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(secret, salt, iterations, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    public static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac m = Mac.getInstance("HmacSHA256");
        m.init(new SecretKeySpec(key, "HmacSHA256"));
        return m.doFinal(data);
    }

    /**
     * RFC 5869 HKDF-SHA256 key derivation.
     */
    public static byte[] hkdf(byte[] ikm, byte[] salt, byte[] info, int len) throws GeneralSecurityException {
        byte[] prk = hmac(salt == null || salt.length == 0 ? new byte[32] : salt, ikm);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] t = new byte[0];
        try {
            for (int i = 1; out.size() < len; i++) {
                Mac m = Mac.getInstance("HmacSHA256");
                m.init(new SecretKeySpec(prk, "HmacSHA256"));
                m.update(t);
                if (info != null && info.length > 0) {
                    m.update(info);
                }
                m.update((byte) i);
                t = m.doFinal();
                out.write(t, 0, t.length);
            }
            return Arrays.copyOf(out.toByteArray(), len);
        } finally {
            wipe(prk);
            wipe(t);
        }
    }

    /**
     * AES-256-GCM authenticated encryption; output = IV(12) || ciphertext + tag(16).
     */
    public static byte[] seal(byte[] key, byte[] plaintext, byte[] aad) throws GeneralSecurityException {
        byte[] iv = random(12);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        if (aad != null && aad.length > 0) {
            c.updateAAD(aad);
        }
        byte[] ct = c.doFinal(plaintext);
        byte[] out = new byte[iv.length + ct.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(ct, 0, out, iv.length, ct.length);
        return out;
    }

    /**
     * AES-256-GCM authenticated decryption.
     */
    public static byte[] open(byte[] key, byte[] blob, byte[] aad) throws GeneralSecurityException {
        if (blob == null || blob.length < 12) {
            throw new GeneralSecurityException("Invalid ciphertext length");
        }
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(128, Arrays.copyOfRange(blob, 0, 12)));
        if (aad != null && aad.length > 0) {
            c.updateAAD(aad);
        }
        return c.doFinal(blob, 12, blob.length - 12);
    }

    public static byte[] rsaWrap(PublicKey pub, byte[] data) throws GeneralSecurityException {
        Cipher c = Cipher.getInstance("RSA/ECB/OAEPPadding");
        c.init(Cipher.ENCRYPT_MODE, pub, OAEP);
        return c.doFinal(data);
    }

    public static byte[] rsaUnwrap(PrivateKey priv, byte[] data) throws GeneralSecurityException {
        Cipher c = Cipher.getInstance("RSA/ECB/OAEPPadding");
        c.init(Cipher.DECRYPT_MODE, priv, OAEP);
        return c.doFinal(data);
    }

    public static void wipe(byte[] b) {
        if (b != null) {
            Arrays.fill(b, (byte) 0);
        }
    }

    public static void wipe(char[] c) {
        if (c != null) {
            Arrays.fill(c, '\0');
        }
    }

    public static String toHex(byte[] bytes) {
        if (bytes == null) return "";
        char[] hexDigits = "0123456789abcdef".toCharArray();
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            out[i * 2] = hexDigits[v >>> 4];
            out[i * 2 + 1] = hexDigits[v & 0x0F];
        }
        return new String(out);
    }

    public static byte[] fromHex(String hex) {
        if (hex == null || hex.isEmpty()) return new byte[0];
        int len = hex.length();
        if (len % 2 != 0) {
            throw new IllegalArgumentException("Hex string must have an even length");
        }
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int h = Character.digit(hex.charAt(i), 16);
            int l = Character.digit(hex.charAt(i + 1), 16);
            if (h == -1 || l == -1) {
                throw new IllegalArgumentException("Invalid hex character at index " + i);
            }
            data[i / 2] = (byte) ((h << 4) + l);
        }
        return data;
    }

    public static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 must be supported by JVM", e);
        }
    }

    public static String sha256Hex(String s) {
        return toHex(sha256(s.getBytes(StandardCharsets.UTF_8)));
    }
}
