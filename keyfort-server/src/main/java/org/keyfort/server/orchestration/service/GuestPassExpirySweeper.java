package org.keyfort.server.orchestration.service;

import org.keyfort.core.connector.SiteConnector;
import org.keyfort.core.guestpass.GuestPass;
import org.keyfort.core.guestpass.GuestPassState;
import org.keyfort.server.orchestration.connector.SiteConnectorRegistry;
import org.keyfort.server.orchestration.entity.GuestPassEntity;
import org.keyfort.server.orchestration.repository.GuestPassRepository;
import org.keyfort.server.orchestration.store.JpaGuestPassStore;
import org.keyfort.server.service.LedgerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Set;

@Component
public class GuestPassExpirySweeper {

    private static final Logger log = LoggerFactory.getLogger(GuestPassExpirySweeper.class);

    private final GuestPassRepository repository;
    private final JpaGuestPassStore store;
    private final SiteConnectorRegistry connectorRegistry;
    private final LedgerService ledgerService;
    private final Clock clock;

    @Autowired
    public GuestPassExpirySweeper(
            GuestPassRepository repository,
            JpaGuestPassStore store,
            SiteConnectorRegistry connectorRegistry,
            LedgerService ledgerService
    ) {
        this(repository, store, connectorRegistry, ledgerService, Clock.systemUTC());
    }

    public GuestPassExpirySweeper(
            GuestPassRepository repository,
            JpaGuestPassStore store,
            SiteConnectorRegistry connectorRegistry,
            LedgerService ledgerService,
            Clock clock
    ) {
        this.repository = repository;
        this.store = store;
        this.connectorRegistry = connectorRegistry;
        this.ledgerService = ledgerService;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 15000)
    @Transactional
    public void sweep() {
        Set<GuestPassState> terminal = Set.of(GuestPassState.BURNED, GuestPassState.EXPIRED, GuestPassState.REVOKED);
        List<GuestPassEntity> expired = repository.findByExpiresAtBeforeAndStateNotIn(clock.instant(), terminal);

        for (GuestPassEntity entity : expired) {
            try {
                if (entity.getCredentialRef() != null) {
                    SiteConnector connector = connectorRegistry.getConnectorForEntry(entity.getEntryId());
                    try {
                        connector.revokeTicket(entity.getCredentialRef());
                    } catch (Exception e) {
                        log.warn("Revoke ticket during expiry sweep: {}", e.getMessage());
                    }
                }
                GuestPass pass = entity.toDomain();
                GuestPass expiredPass = pass.expire(clock);
                store.compareAndSet(pass.id(), pass.version(), expiredPass);

                ledgerService.append("GUEST_PASS_EXPIRED", "passPrefix=" + pass.id().substring(0, Math.min(8, pass.id().length())));
            } catch (Exception e) {
                log.error("Failed to expire pass id={}: {}", entity.getId(), e.getMessage());
            }
        }
    }
}
