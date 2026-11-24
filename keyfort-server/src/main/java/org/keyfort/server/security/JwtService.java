package org.keyfort.server.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.keyfort.core.risk.DeviceState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;

/**
 * Issues and validates short-lived JSON Web Tokens for verified devices.
 */
@Service
public class JwtService {

    public static final String DEV_DEFAULT_SECRET = "dev-only-insecure-test-jwt-secret-material-must-be-at-least-256-bits-long!!";

    private final SecretKey key;
    private final long expirationMs;

    @Autowired
    public JwtService(
            @Value("${keyfort.jwt.secret:}") String secret,
            @Value("${keyfort.jwt.expiration-ms:900000}") long expirationMs,
            Environment env
    ) {
        this.key = Keys.hmacShaKeyFor(validateSecret(secret, env));
        this.expirationMs = expirationMs;
    }

    public JwtService(String secret, long expirationMs) {
        this(secret, expirationMs, null);
    }

    private static byte[] validateSecret(String secret, Environment env) {
        boolean isDevOrTest = env != null && Arrays.stream(env.getActiveProfiles())
                .anyMatch(p -> "dev".equalsIgnoreCase(p) || "test".equalsIgnoreCase(p) || "demo".equalsIgnoreCase(p));

        if (secret == null || secret.isBlank()) {
            if (isDevOrTest) {
                secret = DEV_DEFAULT_SECRET;
            } else {
                throw new IllegalStateException("KEYFORT_JWT_SECRET environment variable is missing");
            }
        }

        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("KEYFORT_JWT_SECRET must be at least 32 bytes (256 bits) long");
        }
        return secretBytes;
    }

    public String generateToken(String userId, String deviceId, DeviceState state) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(userId)
                .claim("deviceId", deviceId)
                .claim("deviceState", state.name())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public Claims validateAndExtract(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUserId(String token) {
        return validateAndExtract(token).getSubject();
    }

    public String extractDeviceId(String token) {
        return validateAndExtract(token).get("deviceId", String.class);
    }
}
