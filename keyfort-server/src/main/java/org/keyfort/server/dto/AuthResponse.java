package org.keyfort.server.dto;

import org.keyfort.core.risk.DeviceState;

public record AuthResponse(
        String token,
        String userId,
        String deviceId,
        DeviceState state,
        long expiresInMs
) {}
