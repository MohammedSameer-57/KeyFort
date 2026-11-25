package org.keyfort.android.send;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.keyfort.core.Crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

/**
 * Pure-Java zero-knowledge envelope builder and verifier for KeyFort Send.
 * Supports:
 * - v2:  keyfort-send://v2.<ct>#<key>
 * - v2p: keyfort-send://v2p.<salt>.<ct>#<key> (bound with PBKDF2-HMAC-SHA256 passcode)
 * - v1:  keyfort-send://<ct>#<key> (legacy compatibility)
 */
public final class SendEnvelope {

    public static final String PREFIX = "keyfort-send://";
    private static final byte[] AAD_V1 = "keyfort|send|v1".getBytes(StandardCharsets.UTF_8);
    private static final byte[] AAD_V2 = "keyfort|send|v2".getBytes(StandardCharsets.UTF_8);
    private static final int SEND_PIN_ITERATIONS = 100_000;

    private SendEnvelope() {}

    public static final class ExpiredException extends GeneralSecurityException {
        private final long expiredAtEpochSeconds;
        public ExpiredException(long expiredAtEpochSeconds) {
            super("This encrypted Send expired");
            this.expiredAtEpochSeconds = expiredAtEpochSeconds;
        }
        public long getExpiredAtEpochSeconds() { return expiredAtEpochSeconds; }
    }

    public static final class PasscodeRequiredException extends GeneralSecurityException {
        public PasscodeRequiredException() {
            super("Passcode required to decrypt this Send");
        }
    }

    public record Inspection(boolean valid, int version, boolean requiresPasscode) {}

    public record Decrypted(String text, int version, long createdAtEpochSeconds,
                            long expiresAtEpochSeconds, boolean passcodeProtected) {}

    public static String create(String secretText, long ttlSeconds, char[] passcode, long nowEpochSeconds)
            throws GeneralSecurityException {
        if (secretText == null || secretText.isEmpty()) {
            throw new IllegalArgumentException("Secret text must not be empty");
        }

        boolean hasPin = passcode != null && passcode.length > 0;
        long expiresAt = ttlSeconds > 0 ? nowEpochSeconds + ttlSeconds : 0L;

        JsonObject envelope = new JsonObject();
        envelope.addProperty("v", 2);
        envelope.addProperty("msg", secretText);
        envelope.addProperty("createdAt", nowEpochSeconds);
        envelope.addProperty("expiresAt", expiresAt);
        envelope.addProperty("pin", hasPin);

        byte[] plaintext = envelope.toString().getBytes(StandardCharsets.UTF_8);
        byte[] randomKey = Crypto.random(32);
        byte[] salt = hasPin ? Crypto.random(16) : null;
        byte[] pinDerived = null;
        byte[] aesKey = null;

        try {
            if (hasPin) {
                pinDerived = Crypto.pbkdf2(passcode, salt, SEND_PIN_ITERATIONS);
                aesKey = Crypto.hkdf(randomKey, salt, pinDerived, 32);
            } else {
                aesKey = randomKey.clone();
            }

            byte[] ct = Crypto.seal(aesKey, plaintext, AAD_V2);
            Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
            String ctB64 = enc.encodeToString(ct);
            String keyB64 = enc.encodeToString(randomKey);

            if (hasPin) {
                return PREFIX + "v2p." + enc.encodeToString(salt) + "." + ctB64 + "#" + keyB64;
            }
            return PREFIX + "v2." + ctB64 + "#" + keyB64;
        } finally {
            Crypto.wipe(plaintext);
            Crypto.wipe(randomKey);
            Crypto.wipe(pinDerived);
            Crypto.wipe(aesKey);
        }
    }

    public static Inspection inspect(String rawBundle) {
        if (rawBundle == null) return new Inspection(false, 0, false);
        String trimmed = rawBundle.trim();
        if (!trimmed.startsWith(PREFIX)) return new Inspection(false, 0, false);
        String rest = trimmed.substring(PREFIX.length());
        int hashIdx = rest.indexOf('#');
        if (hashIdx <= 0 || hashIdx == rest.length() - 1) return new Inspection(false, 0, false);

        String left = rest.substring(0, hashIdx);
        if (left.startsWith("v2p.")) {
            String[] segs = left.split("\\.", 3);
            return segs.length == 3 ? new Inspection(true, 2, true) : new Inspection(false, 0, false);
        } else if (left.startsWith("v2.")) {
            return left.length() > 3 ? new Inspection(true, 2, false) : new Inspection(false, 0, false);
        }
        return new Inspection(true, 1, false);
    }

    public static Decrypted open(String rawBundle, char[] passcode, long nowEpochSeconds)
            throws GeneralSecurityException {
        Inspection info = inspect(rawBundle);
        if (!info.valid()) {
            throw new IllegalArgumentException("Invalid KeyFort Send bundle format");
        }

        String trimmed = rawBundle.trim();
        String rest = trimmed.substring(PREFIX.length());
        int hashIdx = rest.indexOf('#');
        String left = rest.substring(0, hashIdx);
        String keyB64 = rest.substring(hashIdx + 1);

        Base64.Decoder dec = Base64.getUrlDecoder();
        byte[] randomKey = dec.decode(keyB64);
        byte[] pinDerived = null;
        byte[] aesKey = null;
        byte[] pt = null;

        try {
            if (left.startsWith("v2p.")) {
                if (passcode == null || passcode.length == 0) {
                    throw new PasscodeRequiredException();
                }
                String[] segs = left.split("\\.", 3);
                byte[] salt = dec.decode(segs[1]);
                byte[] ct = dec.decode(segs[2]);
                pinDerived = Crypto.pbkdf2(passcode, salt, SEND_PIN_ITERATIONS);
                aesKey = Crypto.hkdf(randomKey, salt, pinDerived, 32);
                pt = Crypto.open(aesKey, ct, AAD_V2);
                return parseV2(new String(pt, StandardCharsets.UTF_8), nowEpochSeconds);
            } else if (left.startsWith("v2.")) {
                byte[] ct = dec.decode(left.substring(3));
                aesKey = randomKey.clone();
                pt = Crypto.open(aesKey, ct, AAD_V2);
                return parseV2(new String(pt, StandardCharsets.UTF_8), nowEpochSeconds);
            } else {
                byte[] ct = dec.decode(left);
                aesKey = randomKey.clone();
                pt = Crypto.open(aesKey, ct, AAD_V1);
                return new Decrypted(new String(pt, StandardCharsets.UTF_8), 1, 0L, 0L, false);
            }
        } finally {
            Crypto.wipe(randomKey);
            Crypto.wipe(pinDerived);
            Crypto.wipe(aesKey);
            Crypto.wipe(pt);
        }
    }

    private static Decrypted parseV2(String jsonStr, long nowEpochSeconds) throws GeneralSecurityException {
        JsonObject obj = JsonParser.parseString(jsonStr).getAsJsonObject();
        long expiresAt = obj.has("expiresAt") ? obj.get("expiresAt").getAsLong() : 0L;
        if (expiresAt > 0L && nowEpochSeconds > expiresAt) {
            throw new ExpiredException(expiresAt);
        }
        String msg = obj.get("msg").getAsString();
        long createdAt = obj.has("createdAt") ? obj.get("createdAt").getAsLong() : 0L;
        boolean pin = obj.has("pin") && obj.get("pin").getAsBoolean();
        return new Decrypted(msg, 2, createdAt, expiresAt, pin);
    }
}
