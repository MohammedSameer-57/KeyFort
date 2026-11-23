package org.keyfort.core.connector;

import java.time.Instant;
import java.util.Objects;

public record GuestSessionInfo(
        String sessionId,
        String status,
        Instant expiresAt,
        long remainingSeconds
) {
    public GuestSessionInfo {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }
}
