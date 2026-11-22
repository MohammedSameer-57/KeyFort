package org.keyfort.core.connector;

import org.keyfort.core.Crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class HmacSigner {

    public static final Duration MAX_SKEW = Duration.ofMinutes(5);
    private static final int MAX_NONCE_CACHE_SIZE = 10_000;

    private final Clock clock;
    private final ConcurrentHashMap<String, Long> nonceCache = new ConcurrentHashMap<>();

    public HmacSigner(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public static String canonical(String method, String path, long timestamp, String nonce, String body) {
        String bodyHash = Crypto.sha256Hex(body == null ? "" : body);
        return method.toUpperCase() + "\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + bodyHash;
    }

    public static String sign(byte[] secret, String method, String path, long timestamp, String nonce, String body) {
        Objects.requireNonNull(secret, "secret must not be null");
        String canon = canonical(method, path, timestamp, nonce, body);
        try {
            return Crypto.toHex(Crypto.hmac(secret, canon.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new ConnectorException("HMAC computation failed", e);
        }
    }

    public void verify(byte[] secret, SignedRequest req) {
        Objects.requireNonNull(secret, "secret must not be null");
        Objects.requireNonNull(req, "request must not be null");

        long now = clock.instant().getEpochSecond();
        if (Math.abs(now - req.timestamp()) > MAX_SKEW.toSeconds()) {
            throw new SignatureVerificationException("Timestamp skew exceeds 5 minutes: " + req.timestamp());
        }

        cleanupNonceCache(now);

        Long prev = nonceCache.putIfAbsent(req.nonce(), req.timestamp());
        if (prev != null) {
            throw new SignatureVerificationException("Nonce replayed: " + req.nonce());
        }

        String expected = sign(secret, req.method(), req.path(), req.timestamp(), req.nonce(), req.body());
        byte[] expectedBytes = expected.getBytes(StandardCharsets.US_ASCII);
        byte[] actualBytes = req.signature().getBytes(StandardCharsets.US_ASCII);

        if (!MessageDigest.isEqual(expectedBytes, actualBytes)) {
            throw new SignatureVerificationException("HMAC signature mismatch");
        }
    }

    private void cleanupNonceCache(long nowEpochSeconds) {
        if (nonceCache.size() > MAX_NONCE_CACHE_SIZE) {
            long cutoff = nowEpochSeconds - MAX_SKEW.toSeconds();
            nonceCache.entrySet().removeIf(e -> e.getValue() < cutoff);
        }
    }
}
