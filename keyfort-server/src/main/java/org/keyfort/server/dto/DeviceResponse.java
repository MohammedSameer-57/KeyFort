package org.keyfort.server.dto;

import org.keyfort.core.risk.DeviceState;
import java.time.Instant;

public record DeviceResponse(
        String deviceId,
        String deviceName,
        DeviceState state,
        Instant registeredAt,
        Instant lastSeenAt
) {}
