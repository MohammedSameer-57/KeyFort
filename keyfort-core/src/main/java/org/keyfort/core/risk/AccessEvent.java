package org.keyfort.core.risk;

import java.time.Instant;
import java.util.Objects;

/**
 * Access attempt signal analyzed by the risk engine.
 */
public record AccessEvent(
        Instant at,
        String userId,
        String deviceId,
        String ip,
        String geo,
        boolean success
) {
    public AccessEvent {
        Objects.requireNonNull(at, "at must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(deviceId, "deviceId must not be null");
        Objects.requireNonNull(ip, "ip must not be null");
        Objects.requireNonNull(geo, "geo must not be null");
    }
}
