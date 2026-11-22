package org.keyfort.core.connector;

import java.util.Objects;

public record TicketHandle(String ticketId, String redirectUrl, boolean assisted, String instruction) {
    public static TicketHandle of(String ticketId, String redirectUrl) {
        return new TicketHandle(
                Objects.requireNonNull(ticketId, "ticketId must not be null"),
                Objects.requireNonNull(redirectUrl, "redirectUrl must not be null"),
                false,
                null
        );
    }

    public static TicketHandle assisted(String instruction) {
        return new TicketHandle(
                null,
                null,
                true,
                Objects.requireNonNull(instruction, "instruction must not be null")
        );
    }
}
