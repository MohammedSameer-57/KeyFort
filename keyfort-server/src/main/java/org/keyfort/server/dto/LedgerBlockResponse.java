package org.keyfort.server.dto;

import java.time.Instant;

public record LedgerBlockResponse(
        long seq,
        Instant at,
        String type,
        String payload,
        String prevHash,
        String hash
) {}
