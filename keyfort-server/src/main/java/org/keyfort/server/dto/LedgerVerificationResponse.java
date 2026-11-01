package org.keyfort.server.dto;

public record LedgerVerificationResponse(
        boolean valid,
        long blockCount,
        String message
) {}
