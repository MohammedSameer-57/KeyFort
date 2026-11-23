package org.keyfort.server.orchestration.dto;

import org.keyfort.core.guestpass.GuestPassState;
import java.time.Instant;

public record CreateGuestPassResponse(
        String id,
        String token,
        GuestPassState state,
        Instant createdAt,
        Instant expiresAt
) {}
