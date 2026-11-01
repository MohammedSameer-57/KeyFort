package org.keyfort.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record AccessEventRequest(
        Instant at,
        @NotBlank String userId,
        @NotBlank String deviceId,
        @NotBlank String ip,
        @NotBlank String geo,
        @NotNull Boolean success
) {}
