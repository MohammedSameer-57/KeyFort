package org.keyfort.core.connector;

public record SignedRequest(
        String method,
        String path,
        long timestamp,
        String nonce,
        String body,
        String signature
) {}
