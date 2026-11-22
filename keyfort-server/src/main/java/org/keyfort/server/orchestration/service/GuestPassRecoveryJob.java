package org.keyfort.server.orchestration.service;

import org.keyfort.core.connector.SiteConnector;
import org.keyfort.core.guestpass.GuestPass;
import org.keyfort.core.guestpass.GuestPassState;
import org.keyfort.server.orchestration.connector.SiteConnectorRegistry;
import org.keyfort.server.orchestration.entity.GuestPassEntity;
import org.keyfort.server.orchestration.repository.GuestPassRepository;
import org.keyfort.server.orchestration.store.JpaGuestPassStore;
import org.keyfort.server.repository.VaultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Component
public class GuestPassRecoveryJob {

    private static final Logger log = LoggerFactory.getLogger(GuestPassRecoveryJob.class);

    private final GuestPassRepository repository;
    private final JpaGuestPassStore store;
    private final SiteConnectorRegistry connectorRegistry;
    private final VaultRepository vaultRepository;
    private final Clock clock;

    @Autowired
    public GuestPassRecoveryJob(
            GuestPassRepository repository,
            JpaGuestPassStore store,
            SiteConnectorRegistry connectorRegistry,
            VaultRepository vaultRepository
    ) {
        this(repository, store, connectorRegistry, vaultRepository, Clock.systemUTC());
    }

    public GuestPassRecoveryJob(
            GuestPassRepository repository,
            JpaGuestPassStore store,
            SiteConnectorRegistry connectorRegistry,
            VaultRepository vaultRepository,
            Clock clock
    ) {
        this.repository = repository;
        this.store = store;
        this.connectorRegistry = connectorRegistry;
        this.vaultRepository = vaultRepository;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 10000)
    @Transactional
    public void recover() {
        // Recover passes stuck in CONSUMED (e.g. server restart during burn)
        List<GuestPassEntity> consumedPasses = repository.findByState(GuestPassState.CONSUMED);
        for (GuestPassEntity entity : consumedPasses) {
            try {
                if (entity.getCredentialRef() != null) {
                    SiteConnector connector = connectorRegistry.getConnectorForEntry(entity.getEntryId());
                    try {
                        connector.revokeTicket(entity.getCredentialRef());
                    } catch (Exception e) {
                        log.warn("Ticket revocation in recovery: {}", e.getMessage());
                    }
                }
                GuestPass pass = entity.toDomain();
                GuestPass burned = pass.burn(clock);
                store.compareAndSet(pass.id(), pass.version(), burned);

                if (entity.getOwnerUserId() != null) {
                    vaultRepository.findById(entity.getOwnerUserId()).ifPresent(v -> {
                        v.setRotationPending(true);
                        vaultRepository.save(v);
                    });
                }
            } catch (Exception e) {
                log.error("Failed to recover CONSUMED pass id={}: {}", entity.getId(), e.getMessage());
            }
        }

        // Recover passes stuck in BURNED where rotation is not pending
        List<GuestPassEntity> burnedPasses = repository.findByState(GuestPassState.BURNED);
        for (GuestPassEntity entity : burnedPasses) {
            if (entity.getOwnerUserId() != null) {
                vaultRepository.findById(entity.getOwnerUserId()).ifPresent(v -> {
                    if (!v.isRotationPending()) {
                        v.setRotationPending(true);
                        vaultRepository.save(v);
                    }
                });
            }
        }
    }
}
