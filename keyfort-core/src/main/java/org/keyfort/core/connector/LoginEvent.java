package org.keyfort.core.connector;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record LoginEvent(
        String ticketId,
        String entryId,
        Instant timestamp,
        String credentialRef,
        String sessionId,
        Map<String, String> metadata
) {
    public LoginEvent(String ticketId, String entryId, Instant timestamp, String credentialRef, Map<String, String> metadata) {
        this(ticketId, entryId, timestamp, credentialRef, null, metadata);
    }

    public LoginEvent {
        Objects.requireNonNull(ticketId, "ticketId must not be null");
        Objects.requireNonNull(entryId, "entryId must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
