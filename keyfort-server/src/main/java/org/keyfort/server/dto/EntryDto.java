package org.keyfort.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EntryDto(
        @NotBlank String id,
        @NotBlank String site,
        @NotBlank String username,
        @NotBlank String sealedPasswordBase64,
        @NotNull Integer keyVersion
) {}
