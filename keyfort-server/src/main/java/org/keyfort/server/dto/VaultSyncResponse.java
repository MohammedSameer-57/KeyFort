package org.keyfort.server.dto;

import java.util.List;

public record VaultSyncResponse(
        String userId,
        int iterations,
        String recoveryPubBase64,
        String kdfSaltBase64,
        String wrappedDekBase64,
        String recoveryWrappedDekBase64,
        int keyVersion,
        boolean rotationPending,
        List<EntryDto> entries
) {}
