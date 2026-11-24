package org.keyfort.server.orchestration.dto;

import java.util.List;

public record DemoStateDto(
        String phase,
        String rawLinkUrl,
        String tokenFingerprint,
        int vaultKeyVersion,
        boolean rotationPending,
        int secondsLeft,
        int sessionSeconds,
        boolean sessionActive,
        boolean copierBlocked,
        boolean copierUsedFirst,
        boolean signatureVerified,
        int replaysRefusedCount,
        String latestLedgerHashPrefix,
        List<DemoLogEntryDto> logs,
        boolean unlocked,
        String masterKeyHint
) {}
