package org.keyfort.core.connector;

import java.time.Duration;

public interface SiteConnector {
    TicketHandle provisionTicket(String entryId, String scope, Duration ttl) throws ConnectorException;
    void revokeTicket(String ticketId) throws ConnectorException;
    LoginEvent verifyLoginEvent(SignedEvent signedEvent) throws ConnectorException;

    default GuestSessionInfo getSession(String sessionId) throws ConnectorException {
        throw new UnsupportedOperationException("getSession not implemented");
    }

    default void terminateSession(String sessionId) throws ConnectorException {
        throw new UnsupportedOperationException("terminateSession not implemented");
    }
}

