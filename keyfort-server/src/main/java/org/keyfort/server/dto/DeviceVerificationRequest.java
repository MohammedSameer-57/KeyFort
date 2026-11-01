package org.keyfort.server.dto;

import jakarta.validation.constraints.NotBlank;

public record DeviceVerificationRequest(
        @NotBlank String userId,
        @NotBlank String deviceId,
        String verificationCode
) {}
