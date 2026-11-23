package org.keyfort.server.orchestration.connector;

import org.keyfort.core.connector.AssistedConnector;
import org.keyfort.core.connector.SiteConnector;
import org.keyfort.server.connector.HttpSiteConnector;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class SiteConnectorRegistry {

    private final HttpSiteConnector defaultHttpConnector;
    private final AssistedConnector assistedConnector;

    public SiteConnectorRegistry(HttpSiteConnector defaultHttpConnector) {
        this.defaultHttpConnector = defaultHttpConnector;
        this.assistedConnector = new AssistedConnector("Unintegrated Site");
    }

    public SiteConnector getConnectorForEntry(String entryId) {
        Objects.requireNonNull(entryId, "entryId must not be null");
        // Cooperating sites integrating with KeyFort (e.g. keyfort-demo-site or entry-site-*)
        if (entryId.contains("site") || entryId.contains("demo")) {
            return defaultHttpConnector;
        }
        // Unintegrated sites fallback to AssistedConnector
        return assistedConnector;
    }
}
