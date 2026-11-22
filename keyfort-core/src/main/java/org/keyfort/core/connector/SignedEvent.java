package org.keyfort.core.connector;

public record SignedEvent(
        String eventType,
        String payload,
        long timestamp,
        String nonce,
        String signature
) {}
