package org.keyfort.server.dto;

import jakarta.validation.constraints.NotBlank;

public record DeviceRegistrationRequest(
        @NotBlank String userId,
        @NotBlank String deviceId,
        @NotBlank String deviceName
) {}
