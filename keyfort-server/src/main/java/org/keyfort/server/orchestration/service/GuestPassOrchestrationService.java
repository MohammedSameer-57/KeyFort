package org.keyfort.server.orchestration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.keyfort.core.Crypto;
import org.keyfort.core.connector.HmacSigner;
import org.keyfort.core.connector.SignedRequest;
import org.keyfort.core.connector.SiteConnector;
import org.keyfort.core.connector.TicketHandle;
import org.keyfort.core.guestpass.GuestPass;
import org.keyfort.core.guestpass.GuestPassNotFoundException;
import org.keyfort.core.guestpass.GuestPassService;
import org.keyfort.core.guestpass.GuestPassState;
import org.keyfort.core.guestpass.IssuedToken;
import org.keyfort.server.entity.EntryEntity;
import org.keyfort.server.orchestration.connector.SiteConnectorRegistry;
import org.keyfort.server.orchestration.entity.GuestPassEntity;
import org.keyfort.server.orchestration.entity.ProcessedEventEntity;
import org.keyfort.server.orchestration.ratelimit.RedeemRateLimiter;
import org.keyfort.server.orchestration.repository.GuestPassRepository;
import org.keyfort.server.orchestration.repository.ProcessedEventRepository;
import org.keyfort.server.orchestration.store.JpaGuestPassStore;
import org.keyfort.server.repository.EntryRepository;
import org.keyfort.server.repository.VaultRepository;
import org.keyfort.server.service.LedgerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class GuestPassOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(GuestPassOrchestrationService.class);

    private final GuestPassRepository repository;
    private final ProcessedEventRepository processedEventRepository;
    private final EntryRepository entryRepository;
    private final JpaGuestPassStore store;
    private final SiteConnectorRegistry connectorRegistry;
    private final RedeemRateLimiter rateLimiter;
    private final VaultRepository vaultRepository;
    private final LedgerService ledgerService;
    private final ObjectMapper objectMapper;
    private final HmacSigner hmacSigner;
    private final Clock clock;
    private final byte[] connectorSecret;

    @Autowired
    public GuestPassOrchestrationService(
            GuestPassRepository repository,
            ProcessedEventRepository processedEventRepository,
            EntryRepository entryRepository,
            JpaGuestPassStore store,
            SiteConnectorRegistry connectorRegistry,
            RedeemRateLimiter rateLimiter,
            VaultRepository vaultRepository,
            LedgerService ledgerService,
            ObjectMapper objectMapper,
            @Value("${keyfort.connector.secret:}") String connectorSecretStr
    ) {
        this(repository, processedEventRepository, entryRepository, store, connectorRegistry, rateLimiter,
                vaultRepository, ledgerService, objectMapper, Clock.systemUTC(),
                connectorSecretStr != null ? connectorSecretStr.getBytes(StandardCharsets.UTF_8) : new byte[0]);
    }

    public GuestPassOrchestrationService(
            GuestPassRepository repository,
            ProcessedEventRepository processedEventRepository,
            EntryRepository entryRepository,
            JpaGuestPassStore store,
            SiteConnectorRegistry connectorRegistry,
            RedeemRateLimiter rateLimiter,
            VaultRepository vaultRepository,
            LedgerService ledgerService,
            ObjectMapper objectMapper,
            Clock clock,
            byte[] connectorSecret
    ) {
        this.repository = repository;
        this.processedEventRepository = processedEventRepository;
        this.entryRepository = entryRepository;
        this.store = store;
        this.connectorRegistry = connectorRegistry;
        this.rateLimiter = rateLimiter;
        this.vaultRepository = vaultRepository;
        this.ledgerService = ledgerService;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.hmacSigner = new HmacSigner(clock);
        this.connectorSecret = connectorSecret != null ? connectorSecret.clone() : new byte[0];
    }

    @Transactional
    public IssuedToken create(String ownerUserId, String entryId, String scope, Duration ttl) {
        Objects.requireNonNull(ownerUserId, "ownerUserId must not be null");
        Objects.requireNonNull(entryId, "entryId must not be null");
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(ttl, "ttl must not be null");

        // Verify entry ownership: return 404 instead of 403 to prevent entry enumeration
        Optional<EntryEntity> entryOpt = entryRepository.findById(entryId);
        if (entryOpt.isPresent() && !ownerUserId.equals(entryOpt.get().getUserId())) {
            throw new GuestPassNotFoundException("Entry not found: " + entryId);
        }

        GuestPassService coreService = new GuestPassService(store, clock);
        IssuedToken issued = coreService.create(entryId, scope, ttl);
        log.info("Created guest pass id={} for entryId={}", issued.id(), entryId);

        repository.findById(issued.id()).ifPresent(e -> {
            e.setOwnerUserId(ownerUserId);
            repository.save(e);
        });

        return issued;
    }

    @Transactional
    public String redeem(String clientIp, String rawToken) {
        if (rawToken == null || !rawToken.contains(".")) {
            executeDummyComparison(rawToken);
            throw new IllegalArgumentException("Invalid or expired guest pass");
        }

        String[] parts = rawToken.split("\\.", 2);
        String passId = parts[0];
        String rawSecret = parts[1];

        if (!rateLimiter.allowRequest(clientIp, passId)) {
            throw new RateLimitExceededException("Rate limit exceeded for guest pass redemption");
        }

        Optional<GuestPassEntity> entityOpt = repository.findById(passId);
        if (entityOpt.isEmpty()) {
            executeDummyComparison(rawSecret);
            throw new IllegalArgumentException("Invalid or expired guest pass");
        }

        GuestPassEntity entity = entityOpt.get();
        GuestPass pass = entity.toDomain();

        byte[] expectedHash = pass.secretHash();
        byte[] actualHash = Crypto.sha256(rawSecret.getBytes(StandardCharsets.US_ASCII));

        boolean validSecret = MessageDigest.isEqual(expectedHash, actualHash);
        boolean validState = pass.state() == GuestPassState.ISSUED;
        boolean notExpired = clock.instant().isBefore(pass.expiresAt());

        if (!validSecret || !validState || !notExpired) {
            executeDummyComparison(rawSecret);
            throw new IllegalArgumentException("Invalid or expired guest pass");
        }

        SiteConnector connector = connectorRegistry.getConnectorForEntry(pass.entryId());
        Duration remainingTtl = Duration.between(clock.instant(), pass.expiresAt());
        if (remainingTtl.isNegative() || remainingTtl.isZero()) {
            remainingTtl = Duration.ofSeconds(60);
        }

        TicketHandle ticket = connector.provisionTicket(pass.entryId(), pass.scope(), remainingTtl);

        GuestPass redeemed = pass.redeem(clock, ticket.ticketId());
        boolean casSuccess = store.compareAndSet(pass.id(), pass.version(), redeemed);
        if (!casSuccess) {
            throw new IllegalStateException("Guest pass redemption failed due to concurrent modification");
        }
        log.info("Redeemed guest pass id={}", pass.id());

        return ticket.redirectUrl();
    }

    private void executeDummyComparison(String rawSecret) {
        byte[] computed = Crypto.sha256(rawSecret != null ? rawSecret.getBytes(StandardCharsets.US_ASCII) : new byte[0]);
        byte[] dummy = Crypto.sha256("dummy-keyfort-constant-timing-pad".getBytes(StandardCharsets.US_ASCII));
        MessageDigest.isEqual(dummy, computed);
    }

    @Transactional
    public Map<String, Object> handleWebhookEvent(SignedRequest signedRequest) {
        hmacSigner.verify(connectorSecret, signedRequest);

        Map<?, ?> payloadMap;
        try {
            payloadMap = objectMapper.readValue(signedRequest.body(), Map.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON payload in webhook request", e);
        }

        String eventId = (String) payloadMap.get("eventId");
        String ticketId = (String) payloadMap.get("ticketId");
        String entryId = (String) payloadMap.get("entryId");

        if (eventId == null || ticketId == null) {
            throw new IllegalArgumentException("Missing eventId or ticketId in webhook payload");
        }

        if (processedEventRepository.existsById(eventId)) {
            return Map.of("status", "IDEMPOTENT_NOOP");
        }

        Optional<GuestPassEntity> entityOpt = repository.findByCredentialRef(ticketId);
        if (entityOpt.isEmpty()) {
            return Map.of("status", "PASS_NOT_FOUND");
        }

        GuestPassEntity entity = entityOpt.get();
        GuestPass pass = entity.toDomain();

        if (pass.state() == GuestPassState.REDEEMED) {
            GuestPass consumed = pass.consume(clock);
            store.compareAndSet(pass.id(), pass.version(), consumed);

            SiteConnector connector = connectorRegistry.getConnectorForEntry(pass.entryId());
            try {
                connector.revokeTicket(ticketId);
            } catch (Exception e) {
                log.warn("Ticket revocation error on connector: {}", e.getMessage());
            }

            GuestPass burned = consumed.burn(clock);
            store.compareAndSet(consumed.id(), consumed.version(), burned);

            if (entity.getOwnerUserId() != null) {
                vaultRepository.findById(entity.getOwnerUserId()).ifPresent(v -> {
                    v.setRotationPending(true);
                    vaultRepository.save(v);
                });
            }

            String passPrefix = Crypto.toHex(Crypto.sha256(pass.id().getBytes(StandardCharsets.UTF_8))).substring(0, 16);
            ledgerService.append("GUEST_PASS_BURNED", "passPrefix=" + passPrefix + ", entryId=" + pass.entryId());

            String sessionId = (String) payloadMap.get("sessionId");
            if (sessionId != null) {
                entity.setSessionId(sessionId);
                repository.save(entity);
            }

            // Critical Invariant: BURNED does NOT end the live session.
            // The one-time ticket credential is revoked on the target site connector, and the vault key
            // rotates. The live guest session remains active on the target site until its hard TTL expires,
            // idle timeout is reached, or the vault owner terminates it via the session kill switch.
            log.info("Processed login webhook for eventId={}, passId={}", eventId, pass.id());
        }

        processedEventRepository.save(new ProcessedEventEntity(eventId, ticketId, clock.instant()));
        return Map.of("status", "SUCCESS");
    }

    @Transactional(readOnly = true)
    public org.keyfort.core.connector.GuestSessionInfo getSessionStatus(String ownerUserId, String passId) {
        Objects.requireNonNull(ownerUserId, "ownerUserId must not be null");
        Objects.requireNonNull(passId, "passId must not be null");

        GuestPassEntity entity = repository.findById(passId)
                .orElseThrow(() -> new GuestPassNotFoundException("Guest pass not found: " + passId));

        if (entity.getOwnerUserId() != null && !ownerUserId.equals(entity.getOwnerUserId())) {
            throw new GuestPassNotFoundException("Guest pass not found: " + passId);
        }

        if (entity.getSessionId() == null) {
            throw new GuestPassNotFoundException("No active session found for pass: " + passId);
        }

        SiteConnector connector = connectorRegistry.getConnectorForEntry(entity.getEntryId());
        return connector.getSession(entity.getSessionId());
    }

    @Transactional
    public void endSession(String ownerUserId, String passId) {
        Objects.requireNonNull(ownerUserId, "ownerUserId must not be null");
        Objects.requireNonNull(passId, "passId must not be null");

        GuestPassEntity entity = repository.findById(passId)
                .orElseThrow(() -> new GuestPassNotFoundException("Guest pass not found: " + passId));

        if (entity.getOwnerUserId() != null && !ownerUserId.equals(entity.getOwnerUserId())) {
            throw new GuestPassNotFoundException("Guest pass not found: " + passId);
        }

        if (entity.getSessionId() == null) {
            throw new GuestPassNotFoundException("No active session found for pass: " + passId);
        }

        SiteConnector connector = connectorRegistry.getConnectorForEntry(entity.getEntryId());
        connector.terminateSession(entity.getSessionId());

        String passPrefix = Crypto.toHex(Crypto.sha256(entity.getId().getBytes(StandardCharsets.UTF_8))).substring(0, 16);
        ledgerService.append("GUEST_SESSION_TERMINATED", "passPrefix=" + passPrefix + ", entryId=" + entity.getEntryId());
        log.info("Terminated guest session for passId={}", passId);
    }

    @Transactional
    public void revoke(String ownerUserId, String passId) {
        GuestPassEntity entity = repository.findById(passId)
                .orElseThrow(() -> new GuestPassNotFoundException("Guest pass not found: " + passId));

        if (ownerUserId != null && entity.getOwnerUserId() != null && !ownerUserId.equals(entity.getOwnerUserId())) {
            throw new GuestPassNotFoundException("Guest pass not found: " + passId);
        }

        GuestPass pass = entity.toDomain();
        if (pass.credentialRef() != null) {
            SiteConnector connector = connectorRegistry.getConnectorForEntry(pass.entryId());
            try {
                connector.revokeTicket(pass.credentialRef());
            } catch (Exception e) {
                log.warn("Revoke ticket during pass revocation: {}", e.getMessage());
            }
        }

        GuestPass revoked = pass.revoke(clock);
        store.compareAndSet(pass.id(), pass.version(), revoked);

        String passPrefix = Crypto.toHex(Crypto.sha256(pass.id().getBytes(StandardCharsets.UTF_8))).substring(0, 16);
        ledgerService.append("GUEST_PASS_REVOKED", "passPrefix=" + passPrefix);
    }
}
