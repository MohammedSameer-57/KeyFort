package org.keyfort.server.orchestration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.keyfort.core.AuthException;
import org.keyfort.core.Crypto;
import org.keyfort.core.Vault;
import org.keyfort.core.connector.HmacSigner;
import org.keyfort.core.connector.SignedRequest;
import org.keyfort.core.guestpass.GuestPassState;
import org.keyfort.core.guestpass.IssuedToken;
import org.keyfort.server.entity.EntryEntity;
import org.keyfort.server.entity.LedgerBlockEntity;
import org.keyfort.server.entity.VaultEntity;
import org.keyfort.server.orchestration.dto.DemoLogEntryDto;
import org.keyfort.server.orchestration.dto.DemoStateDto;
import org.keyfort.server.orchestration.entity.GuestPassEntity;
import org.keyfort.server.orchestration.repository.GuestPassRepository;
import org.keyfort.server.repository.EntryRepository;
import org.keyfort.server.repository.LedgerBlockRepository;
import org.keyfort.server.repository.VaultRepository;
import org.keyfort.server.service.LedgerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Service driving real, zero-knowledge workflows against seeded demo data under the "demo" profile.
 * Documented in THREAT_MODEL.md as a presentation and testing aid.
 */
@Service
@Profile("demo")
public class DemoRunnerService {

    private static final Logger log = LoggerFactory.getLogger(DemoRunnerService.class);

    public static final String DEMO_OWNER_ID = "demo-owner";
    public static final String DEMO_ENTRY_ID = "demo-entry-cinemastream";
    public static final String DEMO_SITE = "CinemaStream Family";
    public static final String DEMO_USERNAME = "family@household.org";
    public static final String DEMO_MASTER_KEY = "demo-vault-master-pass";

    private Vault inMemoryVault = null;
    private Vault.Session activeVaultSession = null;
    private int failedUnlockAttempts = 0;
    private Instant lockoutUntil = null;

    private final GuestPassOrchestrationService orchestrationService;
    private final GuestPassRepository guestPassRepository;
    private final VaultRepository vaultRepository;
    private final EntryRepository entryRepository;
    private final LedgerService ledgerService;
    private final LedgerBlockRepository ledgerBlockRepository;
    private final ObjectMapper objectMapper;
    private final String connectorSecret;
    private final String publicBaseUrl;

    // In-memory demo workflow state
    private String phase = "idle"; // "idle" | "link_ready" | "signed_in" | "ended" | "renewed"
    private String activePassId = null;
    private String activeRawToken = null;
    private String tokenFingerprint = "--------";
    private int vaultKeyVersion = 1;
    private boolean rotationPending = false;
    private int secondsLeft = 600;
    private int sessionSeconds = 899;
    private boolean sessionActive = false;
    private boolean copierBlocked = false;
    private boolean copierUsedFirst = false;
    private boolean signatureVerified = false;
    private int replaysRefusedCount = 0;

    private final List<DemoLogEntryDto> logs = new CopyOnWriteArrayList<>();
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public DemoRunnerService(
            GuestPassOrchestrationService orchestrationService,
            GuestPassRepository guestPassRepository,
            VaultRepository vaultRepository,
            EntryRepository entryRepository,
            LedgerService ledgerService,
            LedgerBlockRepository ledgerBlockRepository,
            ObjectMapper objectMapper,
            @Value("${keyfort.connector.secret:demo-connector-shared-secret-key-32b!!}") String connectorSecret,
            @Value("${keyfort.public-base-url:http://localhost:8080}") String publicBaseUrl
    ) {
        this.orchestrationService = orchestrationService;
        this.guestPassRepository = guestPassRepository;
        this.vaultRepository = vaultRepository;
        this.entryRepository = entryRepository;
        this.ledgerService = ledgerService;
        this.ledgerBlockRepository = ledgerBlockRepository;
        this.objectMapper = objectMapper;
        this.connectorSecret = (connectorSecret != null && !connectorSecret.isBlank())
                ? connectorSecret : "demo-connector-shared-secret-key-32b!!";
        this.publicBaseUrl = publicBaseUrl != null ? publicBaseUrl : "http://localhost:8080";
    }

    @PostConstruct
    public synchronized void init() {
        reset();
    }

    public synchronized boolean isUnlocked() {
        return activeVaultSession != null && !activeVaultSession.isClosed();
    }

    private void checkUnlocked() {
        if (!isUnlocked()) {
            throw new VaultLockedException();
        }
    }

    public synchronized DemoStateDto getState() {
        String latestHash = getLatestLedgerPrefix();
        return new DemoStateDto(
                phase,
                activeRawToken != null ? publicBaseUrl + "/g/" + activeRawToken : "",
                tokenFingerprint,
                vaultKeyVersion,
                rotationPending,
                secondsLeft,
                sessionSeconds,
                sessionActive,
                copierBlocked,
                copierUsedFirst,
                signatureVerified,
                replaysRefusedCount,
                latestHash,
                new ArrayList<>(logs),
                isUnlocked(),
                DEMO_MASTER_KEY
        );
    }

    public synchronized DemoStateDto unlock(String masterKey) {
        Instant now = Instant.now();
        if (lockoutUntil != null && now.isBefore(lockoutUntil)) {
            throw new LockoutException();
        }
        if (masterKey == null) {
            handleFailedUnlock();
            throw new WrongKeyException();
        }

        char[] keyChars = masterKey.toCharArray();
        try {
            if (inMemoryVault == null) {
                seedDatabase();
            }
            Vault.Session session = inMemoryVault.unlock(keyChars);
            if (activeVaultSession != null && !activeVaultSession.isClosed()) {
                activeVaultSession.close();
            }
            activeVaultSession = session;
            failedUnlockAttempts = 0;
            lockoutUntil = null;

            if (inMemoryVault.keyVersion() > vaultKeyVersion) {
                vaultKeyVersion = inMemoryVault.keyVersion();
                rotationPending = false;
                phase = "renewed";
                vaultRepository.findById(DEMO_OWNER_ID).ifPresent(v -> {
                    v.setKeyVersion(vaultKeyVersion);
                    v.setRotationPending(false);
                    vaultRepository.save(v);
                });
                addLog("Vault unlocked and key renewed on owner device");
            } else {
                addLog("Vault unlocked with master key");
            }
            broadcast();
            return getState();
        } catch (AuthException | GeneralSecurityException e) {
            handleFailedUnlock();
            throw new WrongKeyException();
        } finally {
            Crypto.wipe(keyChars);
        }
    }

    private void handleFailedUnlock() {
        failedUnlockAttempts++;
        if (failedUnlockAttempts >= 5) {
            lockoutUntil = Instant.now().plusSeconds(30);
            throw new LockoutException();
        }
    }

    public synchronized DemoStateDto lock() {
        if (activeVaultSession != null) {
            activeVaultSession.close();
            activeVaultSession = null;
        }
        addLog("Vault locked (session keys wiped from memory)");
        broadcast();
        return getState();
    }

    public synchronized DemoStateDto createLink(int durationMinutes) {
        checkUnlocked();
        if (!"idle".equals(phase) && !"renewed".equals(phase)) {
            activePassId = null;
            activeRawToken = null;
            tokenFingerprint = "--------";
            sessionActive = false;
            copierBlocked = false;
            copierUsedFirst = false;
            signatureVerified = false;
        }

        Duration ttl = Duration.ofMinutes(durationMinutes > 0 ? durationMinutes : 10);
        IssuedToken issued = orchestrationService.create(DEMO_OWNER_ID, DEMO_ENTRY_ID, "read-only", ttl);
        activePassId = issued.id();
        activeRawToken = issued.rawToken();

        String rawSecret = activeRawToken.split("\\.")[1];
        byte[] secretHash = Crypto.sha256(rawSecret.getBytes(StandardCharsets.US_ASCII));
        tokenFingerprint = Crypto.toHex(secretHash).substring(0, 8);

        phase = "link_ready";
        secondsLeft = (int) ttl.toSeconds();
        copierBlocked = false;
        copierUsedFirst = false;
        sessionActive = false;

        addLog("One-time link created for " + DEMO_SITE);
        broadcast();
        return getState();
    }

    public synchronized DemoStateDto friendLogin() {
        checkUnlocked();
        if (copierUsedFirst) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "First use wins. If this happens, end the session.");
        }
        if (!"link_ready".equals(phase)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No link is ready to open");
        }

        orchestrationService.redeem("127.0.0.1", activeRawToken);

        GuestPassEntity passEntity = guestPassRepository.findById(activePassId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pass not found"));
        String ticketId = passEntity.getCredentialRef();

        dispatchVerifiedWebhook(ticketId);

        phase = "signed_in";
        sessionActive = true;
        sessionSeconds = 899;
        rotationPending = true;
        signatureVerified = true;
        if (inMemoryVault != null) {
            inMemoryVault.setRotationPending(true);
        }
        vaultRepository.findById(DEMO_OWNER_ID).ifPresent(v -> {
            v.setRotationPending(true);
            vaultRepository.save(v);
        });

        addLog("Friend signed in once; link marked already used");
        broadcast();
        return getState();
    }

    public synchronized DemoStateDto copierTry() {
        checkUnlocked();
        if ("link_ready".equals(phase)) {
            // Copier acts before friend!
            copierUsedFirst = true;
            orchestrationService.redeem("198.51.100.24", activeRawToken);

            GuestPassEntity passEntity = guestPassRepository.findById(activePassId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pass not found"));
            String ticketId = passEntity.getCredentialRef();

            dispatchVerifiedWebhook(ticketId);

            phase = "signed_in";
            sessionActive = true;
            sessionSeconds = 899;
            rotationPending = true;
            signatureVerified = true;
            if (inMemoryVault != null) {
                inMemoryVault.setRotationPending(true);
            }
            vaultRepository.findById(DEMO_OWNER_ID).ifPresent(v -> {
                v.setRotationPending(true);
                vaultRepository.save(v);
            });

            addLog("Copier used link first; original friend will be refused");
            broadcast();
            return getState();
        } else {
            copierBlocked = true;
            replaysRefusedCount++;
            addLog("Copier attempt refused (already used)");
            broadcast();
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refused: this link was already used once. Every later attempt with a copied link is blocked.");
        }
    }

    public synchronized DemoStateDto endSession() {
        checkUnlocked();
        if (activePassId != null) {
            try {
                orchestrationService.endSession(DEMO_OWNER_ID, activePassId);
            } catch (Exception e) {
                log.info("Session end caught: {}", e.getMessage());
            }
        }
        sessionActive = false;
        phase = "ended";
        addLog("Guest session ended immediately by vault owner");
        broadcast();
        return getState();
    }

    public synchronized DemoStateDto renewKey() {
        checkUnlocked();
        if (activeVaultSession != null) {
            try {
                activeVaultSession.rotateKeys();
            } catch (Exception e) {
                log.error("Rotation error in active session: {}", e.getMessage());
            }
        }
        if (inMemoryVault != null) {
            vaultKeyVersion = inMemoryVault.keyVersion();
        } else {
            vaultKeyVersion = 2;
        }
        rotationPending = false;
        phase = "renewed";

        vaultRepository.findById(DEMO_OWNER_ID).ifPresent(v -> {
            v.setKeyVersion(vaultKeyVersion);
            v.setRotationPending(false);
            vaultRepository.save(v);
        });

        addLog("Vault key renewed on owner device at unlock");
        broadcast();
        return getState();
    }

    public synchronized DemoStateDto reset() {
        if (activeVaultSession != null) {
            activeVaultSession.close();
            activeVaultSession = null;
        }
        failedUnlockAttempts = 0;
        lockoutUntil = null;

        phase = "idle";
        activePassId = null;
        activeRawToken = null;
        tokenFingerprint = "--------";
        vaultKeyVersion = 1;
        rotationPending = false;
        secondsLeft = 600;
        sessionSeconds = 899;
        sessionActive = false;
        copierBlocked = false;
        copierUsedFirst = false;
        signatureVerified = false;
        replaysRefusedCount = 0;

        try {
            seedDatabase();
        } catch (Exception e) {
            log.error("Failed to seed demo database: {}", e.getMessage(), e);
        }

        logs.clear();
        addLog("Demo reset · Ready to create a one-time link");
        broadcast();
        return getState();
    }

    public org.keyfort.server.dto.LedgerVerificationResponse checkHistory() {
        return ledgerService.verify();
    }

    public SseEmitter createEmitter() {
        SseEmitter emitter = new SseEmitter(180_000L);
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(t -> emitters.remove(emitter));

        try {
            emitter.send(SseEmitter.event().name("state").data(getState()));
        } catch (Exception e) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    private void broadcast() {
        DemoStateDto current = getState();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("state").data(current));
            } catch (Exception e) {
                emitters.remove(emitter);
            }
        }
    }

    private void addLog(String text) {
        String meta = Instant.now().toString().substring(11, 19) + " · #" + getLatestLedgerPrefix();
        logs.add(0, new DemoLogEntryDto(text, meta));
        while (logs.size() > 8) {
            logs.remove(logs.size() - 1);
        }
    }

    private String getLatestLedgerPrefix() {
        Optional<LedgerBlockEntity> top = ledgerBlockRepository.findTopByOrderBySeqDesc();
        if (top.isPresent()) {
            String h = top.get().getHash();
            return h.length() >= 8 ? h.substring(0, 8) : h;
        }
        return "8f3ac91e";
    }

    private void dispatchVerifiedWebhook(String ticketId) {
        long ts = Instant.now().getEpochSecond();
        String nonce = "demo-nonce-" + Crypto.toHex(Crypto.random(4));
        String eventId = "demo-evt-" + Crypto.toHex(Crypto.random(4));
        String path = "/api/connector/events";

        Map<String, Object> payload = Map.of(
                "eventId", eventId,
                "ticketId", ticketId,
                "entryId", DEMO_ENTRY_ID,
                "scope", "read-only",
                "sessionId", "sess-demo-" + ticketId,
                "timestamp", Instant.now().toString()
        );

        try {
            String json = objectMapper.writeValueAsString(payload);
            byte[] keyBytes = connectorSecret.getBytes(StandardCharsets.UTF_8);
            String sig = HmacSigner.sign(keyBytes, "POST", path, ts, nonce, json);
            SignedRequest req = new SignedRequest("POST", path, ts, nonce, sig, json);
            orchestrationService.handleWebhookEvent(req);
        } catch (Exception e) {
            log.error("Error executing demo webhook dispatch: {}", e.getMessage(), e);
        }
    }

    private void seedDatabase() throws GeneralSecurityException, AuthException {
        // Clear previous demo guest passes
        List<GuestPassEntity> passes = guestPassRepository.findAll();
        for (GuestPassEntity p : passes) {
            if (DEMO_OWNER_ID.equals(p.getOwnerUserId())) {
                guestPassRepository.delete(p);
            }
        }

        // Seed demo vault
        char[] masterSecret = DEMO_MASTER_KEY.toCharArray();
        Vault.Created created = Vault.create(DEMO_OWNER_ID, masterSecret);
        inMemoryVault = created.vault();
        try (Vault.Session initSession = inMemoryVault.unlock(masterSecret)) {
            initSession.add(DEMO_SITE, DEMO_USERNAME, "DemoCinemaStreamPass2026!");
        }
        Crypto.wipe(masterSecret);

        VaultEntity vaultEntity = new VaultEntity(
                DEMO_OWNER_ID,
                inMemoryVault.iterations(),
                inMemoryVault.recoveryPub().getEncoded(),
                inMemoryVault.kdfSalt(),
                inMemoryVault.wrappedDek(),
                inMemoryVault.recoveryWrappedDek(),
                1,
                false,
                Instant.now()
        );
        vaultRepository.save(vaultEntity);

        // Seed demo entry
        EntryEntity entryEntity = new EntryEntity(
                DEMO_ENTRY_ID,
                DEMO_OWNER_ID,
                DEMO_SITE,
                DEMO_USERNAME,
                Crypto.random(48), // sealed ciphertext simulation
                1,
                Instant.now()
        );
        entryRepository.save(entryEntity);
    }

    public static class VaultLockedException extends RuntimeException {
        public VaultLockedException() { super("vault locked"); }
    }

    public static class WrongKeyException extends RuntimeException {
        public WrongKeyException() { super("wrong key"); }
    }

    public static class LockoutException extends RuntimeException {
        public LockoutException() { super("locked out"); }
    }
}
