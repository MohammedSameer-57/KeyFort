package org.keyfort.server.orchestration.dto;

public record CreateGuestPassRequest(
        String entryId,
        String scope,
        long ttlSeconds
) {}
