package org.keyfort.core.connector;

import java.time.Duration;
import java.util.Objects;

/**
 * Fallback connector for target services that do not implement a KeyFort connector integration.
 * Does not provision remote credentials; provides explicit owner instructions to rotate passwords manually.
 */
public class AssistedConnector implements SiteConnector {

    private final String siteName;

    public AssistedConnector(String siteName) {
        this.siteName = Objects.requireNonNull(siteName, "siteName must not be null");
    }

    @Override
    public TicketHandle provisionTicket(String entryId, String scope, Duration ttl) {
        return TicketHandle.assisted(
                "Assisted mode for " + siteName + ": Please update the account password after sharing."
        );
    }

    @Override
    public void revokeTicket(String ticketId) {
        // No-op for unintegrated sites
    }

    @Override
    public LoginEvent verifyLoginEvent(SignedEvent signedEvent) {
        throw new ConnectorException("AssistedConnector does not accept remote webhook events");
    }
}
