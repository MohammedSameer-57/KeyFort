package org.keyfort.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record VaultSyncRequest(
        @NotBlank String userId,
        @NotNull Integer iterations,
        @NotBlank String recoveryPubBase64,
        @NotBlank String kdfSaltBase64,
        @NotBlank String wrappedDekBase64,
        @NotBlank String recoveryWrappedDekBase64,
        @NotNull Integer keyVersion,
        boolean rotationPending,
        List<EntryDto> entries
) {}
