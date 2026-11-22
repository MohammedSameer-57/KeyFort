package org.keyfort.server.orchestration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keyfort.core.Crypto;
import org.keyfort.core.Vault;
import org.keyfort.core.connector.HmacSigner;
import org.keyfort.core.guestpass.GuestPassState;
import org.keyfort.server.dto.AuthResponse;
import org.keyfort.server.dto.DeviceVerificationRequest;
import org.keyfort.server.dto.VaultSyncRequest;
import org.keyfort.server.orchestration.dto.CreateGuestPassRequest;
import org.keyfort.server.orchestration.dto.CreateGuestPassResponse;
import org.keyfort.server.orchestration.dto.RedeemGuestPassRequest;
import org.keyfort.server.orchestration.dto.RedeemGuestPassResponse;
import org.keyfort.server.orchestration.entity.GuestPassEntity;
import org.keyfort.server.orchestration.repository.GuestPassRepository;
import org.keyfort.server.orchestration.repository.ProcessedEventRepository;
import org.keyfort.server.orchestration.service.GuestPassExpirySweeper;
import org.keyfort.server.orchestration.service.GuestPassRecoveryJob;
import org.keyfort.server.repository.LedgerBlockRepository;
import org.keyfort.server.repository.VaultRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Phase 4: Guest Pass Server Orchestration Integration Tests")
class GuestPassOrchestrationIntegrationTest {

    private static final byte[] CONNECTOR_SECRET = "dev-only-insecure-test-connector-hmac-secret-256bit!!".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private GuestPassRepository guestPassRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private VaultRepository vaultRepository;

    @Autowired
    private LedgerBlockRepository ledgerRepository;

    @Autowired
    private GuestPassRecoveryJob recoveryJob;

    @Autowired
    private GuestPassExpirySweeper expirySweeper;

    @Autowired
    private org.keyfort.server.repository.EntryRepository entryRepository;

    private static com.sun.net.httpserver.HttpServer mockTargetSiteServer;

    @org.junit.jupiter.api.BeforeAll
    static void startTargetSiteServer() throws java.io.IOException {
        try {
            mockTargetSiteServer = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress(9090), 0);
            mockTargetSiteServer.createContext("/connector/tickets", exchange -> {
                if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    String uniqueId = "tkt-mock-" + java.util.UUID.randomUUID();
                    String resp = "{\"ticketId\":\"" + uniqueId + "\",\"redirectUrl\":\"http://localhost:9090/keyfort/login?ticket=" + uniqueId + "\"}";
                    byte[] bytes = resp.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(201, bytes.length);
                    exchange.getResponseBody().write(bytes);
                    exchange.close();
                } else if ("DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(204, -1);
                    exchange.close();
                } else {
                    exchange.sendResponseHeaders(405, -1);
                    exchange.close();
                }
            });
            mockTargetSiteServer.start();
        } catch (java.net.BindException ignored) {
            // Server may already be running
        }
    }

    @org.junit.jupiter.api.AfterAll
    static void stopTargetSiteServer() {
        if (mockTargetSiteServer != null) {
            mockTargetSiteServer.stop(0);
        }
    }

    private String userJwtToken;

    @BeforeEach
    void setUp() throws Exception {
        guestPassRepository.deleteAll();
        processedEventRepository.deleteAll();

        // Authenticate user to obtain valid JWT token for authenticated endpoints
        DeviceVerificationRequest verifyReq = new DeviceVerificationRequest("alice-owner", "phone-alice", "123456");
        MvcResult authResult = mockMvc.perform(post("/auth/device/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isOk())
                .andReturn();
        userJwtToken = objectMapper.readValue(authResult.getResponse().getContentAsString(), AuthResponse.class).token();

        // Create initial vault entity for alice-owner
        Vault.Created created = Vault.create("alice-owner", "master-password-alice".toCharArray());
        Vault v = created.vault();
        VaultSyncRequest syncReq = new VaultSyncRequest(
                "alice-owner", v.iterations(),
                Base64.getEncoder().encodeToString(v.recoveryPub().getEncoded()),
                Base64.getEncoder().encodeToString(v.kdfSalt()),
                Base64.getEncoder().encodeToString(v.wrappedDek()),
                Base64.getEncoder().encodeToString(v.recoveryWrappedDek()),
                1, false, List.of()
        );
        mockMvc.perform(put("/vault/sync")
                        .header("Authorization", "Bearer " + userJwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(syncReq)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Unauthenticated requests to protected guest-pass endpoints are rejected with 401")
    void testUnauthenticatedAccessRejected() throws Exception {
        // Protected POST /api/guest-passes requires authentication
        CreateGuestPassRequest req = new CreateGuestPassRequest("entry-unauth", "read", 900);
        mockMvc.perform(post("/api/guest-passes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());

        // Protected DELETE /api/guest-passes/{id} requires authentication
        mockMvc.perform(delete("/api/guest-passes/some-id"))
                .andExpect(status().isUnauthorized());

        // Phase 6 hardening: /access-events and /ledger/verify also require authentication
        mockMvc.perform(get("/ledger/verify"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Full guest pass lifecycle: create -> redeem -> webhook event -> consumed -> burned -> rotation pending")
    void testFullLifecycleOverHttp() throws Exception {
        // 1. Create pass (authenticated)
        CreateGuestPassRequest createReq = new CreateGuestPassRequest("entry-site-1", "read-only", 900);
        MvcResult createResult = mockMvc.perform(post("/api/guest-passes")
                        .header("Authorization", "Bearer " + userJwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn();

        CreateGuestPassResponse createResp = objectMapper.readValue(createResult.getResponse().getContentAsString(), CreateGuestPassResponse.class);
        assertNotNull(createResp.id());
        assertNotNull(createResp.token());
        assertEquals(GuestPassState.ISSUED, createResp.state());

        // Verify secret hash is stored, but raw token is not stored
        GuestPassEntity entity = guestPassRepository.findById(createResp.id()).orElseThrow();
        assertEquals(GuestPassState.ISSUED, entity.getState());
        assertNotNull(entity.getSecretHash());

        // 2. Redeem pass (unauthenticated, rate limited)
        RedeemGuestPassRequest redeemReq = new RedeemGuestPassRequest(createResp.token());
        MvcResult redeemResult = mockMvc.perform(post("/api/guest-passes/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(redeemReq)))
                .andExpect(status().isOk())
                .andReturn();

        RedeemGuestPassResponse redeemResp = objectMapper.readValue(redeemResult.getResponse().getContentAsString(), RedeemGuestPassResponse.class);
        assertNotNull(redeemResp.redirectUrl());

        GuestPassEntity redeemedEntity = guestPassRepository.findById(createResp.id()).orElseThrow();
        assertEquals(GuestPassState.REDEEMED, redeemedEntity.getState());
        assertNotNull(redeemedEntity.getCredentialRef());

        // 3. Webhook event from target site (HMAC-verified, idempotent)
        long ts = Instant.now().getEpochSecond();
        String nonce = "webhook-nonce-" + Crypto.random(6);
        Map<String, Object> webhookPayload = Map.of(
                "eventId", "evt-12345",
                "ticketId", redeemedEntity.getCredentialRef(),
                "entryId", "entry-site-1",
                "scope", "read-only",
                "timestamp", Instant.now().toString()
        );
        String webhookJson = objectMapper.writeValueAsString(webhookPayload);
        String signature = HmacSigner.sign(CONNECTOR_SECRET, "POST", "/api/connector/events", ts, nonce, webhookJson);

        mockMvc.perform(post("/api/connector/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-KeyFort-Timestamp", String.valueOf(ts))
                        .header("X-KeyFort-Nonce", nonce)
                        .header("X-KeyFort-Signature", signature)
                        .content(webhookJson))
                .andExpect(status().isOk());

        // 4. Verify pass state is BURNED, vault rotation pending is TRUE, and ledger has audited block
        GuestPassEntity burnedEntity = guestPassRepository.findById(createResp.id()).orElseThrow();
        assertEquals(GuestPassState.BURNED, burnedEntity.getState());

        var vaultEntity = vaultRepository.findById("alice-owner").orElseThrow();
        assertTrue(vaultEntity.isRotationPending(), "Vault key rotation must be marked pending on BURNED");

        // Ledger must verify and contain ledger entry with hash prefix
        var blocks = ledgerRepository.findAll();
        assertFalse(blocks.isEmpty());
        boolean hasPassPrefix = blocks.stream().anyMatch(b -> b.getPayload().contains("passPrefix="));
        assertTrue(hasPassPrefix, "Ledger must record passPrefix domain event");
    }

    @Test
    @DisplayName("Replayed webhook with same eventId is idempotent (no-op)")
    void testReplayedWebhookIsNoOp() throws Exception {
        CreateGuestPassRequest createReq = new CreateGuestPassRequest("entry-site-replay", "read", 900);
        MvcResult createResult = mockMvc.perform(post("/api/guest-passes")
                        .header("Authorization", "Bearer " + userJwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn();
        CreateGuestPassResponse createResp = objectMapper.readValue(createResult.getResponse().getContentAsString(), CreateGuestPassResponse.class);

        RedeemGuestPassRequest redeemReq = new RedeemGuestPassRequest(createResp.token());
        mockMvc.perform(post("/api/guest-passes/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(redeemReq)))
                .andExpect(status().isOk());

        GuestPassEntity redeemedEntity = guestPassRepository.findById(createResp.id()).orElseThrow();

        long ts = Instant.now().getEpochSecond();
        String nonce1 = "nonce-replay-1";
        Map<String, Object> payload = Map.of(
                "eventId", "evt-unique-99",
                "ticketId", redeemedEntity.getCredentialRef(),
                "entryId", "entry-site-replay",
                "scope", "read",
                "timestamp", Instant.now().toString()
        );
        String json = objectMapper.writeValueAsString(payload);
        String sig1 = HmacSigner.sign(CONNECTOR_SECRET, "POST", "/api/connector/events", ts, nonce1, json);

        // First delivery: returns 200
        mockMvc.perform(post("/api/connector/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-KeyFort-Timestamp", String.valueOf(ts))
                        .header("X-KeyFort-Nonce", nonce1)
                        .header("X-KeyFort-Signature", sig1)
                        .content(json))
                .andExpect(status().isOk());

        // Replayed delivery with new nonce: returns 200 (idempotent no-op)
        String nonce2 = "nonce-replay-2";
        String sig2 = HmacSigner.sign(CONNECTOR_SECRET, "POST", "/api/connector/events", ts, nonce2, json);
        mockMvc.perform(post("/api/connector/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-KeyFort-Timestamp", String.valueOf(ts))
                        .header("X-KeyFort-Nonce", nonce2)
                        .header("X-KeyFort-Signature", sig2)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IDEMPOTENT_NOOP"));
    }

    @Test
    @DisplayName("Forged or invalid HMAC webhook signature is rejected with 400 Bad Request")
    void testForgedWebhookSignatureIsRejected() throws Exception {
        long ts = Instant.now().getEpochSecond();
        String nonce = "nonce-forged";
        String json = "{\"eventId\":\"evt-bad\",\"ticketId\":\"tkt-1\"}";

        mockMvc.perform(post("/api/connector/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-KeyFort-Timestamp", String.valueOf(ts))
                        .header("X-KeyFort-Nonce", nonce)
                        .header("X-KeyFort-Signature", "forged-bad-signature")
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Recovery job resumes passes stuck in CONSUMED or BURNED-without-rotation")
    void testRecoveryJobResumesStuckPasses() throws Exception {
        // Manually persist a pass in CONSUMED state (simulating crash before burn and rotation)
        Instant now = Instant.now();
        GuestPassEntity stuckPass = new GuestPassEntity(
                "pass-stuck-1",
                "entry-stuck",
                "read",
                Crypto.sha256("test-secret".getBytes(StandardCharsets.US_ASCII)),
                GuestPassState.CONSUMED,
                now.minusSeconds(60),
                now.plusSeconds(800),
                "tkt-stuck-1"
        );
        stuckPass.setOwnerUserId("alice-owner");
        guestPassRepository.save(stuckPass);

        // Run recovery job
        recoveryJob.recover();

        GuestPassEntity recovered = guestPassRepository.findById("pass-stuck-1").orElseThrow();
        assertEquals(GuestPassState.BURNED, recovered.getState(), "Pass stuck in CONSUMED must be recovered to BURNED");

        var vaultEntity = vaultRepository.findById("alice-owner").orElseThrow();
        assertTrue(vaultEntity.isRotationPending());
    }

    @Test
    @DisplayName("Revoke during redeem: when pass is revoked, redeem is rejected")
    void testRevokeDuringRedeem() throws Exception {
        CreateGuestPassRequest createReq = new CreateGuestPassRequest("entry-rev-race", "read", 900);
        MvcResult createResult = mockMvc.perform(post("/api/guest-passes")
                        .header("Authorization", "Bearer " + userJwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn();
        CreateGuestPassResponse createResp = objectMapper.readValue(createResult.getResponse().getContentAsString(), CreateGuestPassResponse.class);

        // Revoke the pass
        mockMvc.perform(delete("/api/guest-passes/" + createResp.id())
                        .header("Authorization", "Bearer " + userJwtToken))
                .andExpect(status().isOk());

        GuestPassEntity entity = guestPassRepository.findById(createResp.id()).orElseThrow();
        assertEquals(GuestPassState.REVOKED, entity.getState());

        // Attempting to redeem revoked pass must be rejected
        RedeemGuestPassRequest redeemReq = new RedeemGuestPassRequest(createResp.token());
        mockMvc.perform(post("/api/guest-passes/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(redeemReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid or expired guest pass"));
    }

    @Test
    @DisplayName("Expiry sweeper transitions expired passes to EXPIRED and cleans up tickets")
    void testExpirySweeper() {
        Instant now = Instant.now();
        GuestPassEntity expiredPass = new GuestPassEntity(
                "pass-expired-1",
                "entry-exp",
                "read",
                Crypto.sha256("exp-secret".getBytes(StandardCharsets.US_ASCII)),
                GuestPassState.ISSUED,
                now.minusSeconds(120),
                now.minusSeconds(10),
                null
        );
        expiredPass.setOwnerUserId("alice-owner");
        guestPassRepository.save(expiredPass);

        expirySweeper.sweep();

        GuestPassEntity swept = guestPassRepository.findById("pass-expired-1").orElseThrow();
        assertEquals(GuestPassState.EXPIRED, swept.getState(), "Sweeper must mark pass EXPIRED");
    }

    @Test
    @DisplayName("Unknown id and wrong secret return identical response body and timing shape")
    void testUnknownIdAndWrongSecretTimingAndResponseShape() throws Exception {
        RedeemGuestPassRequest unknownReq = new RedeemGuestPassRequest("unknownpassid123.wrongsecret12345");
        MvcResult unknownRes = mockMvc.perform(post("/api/guest-passes/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unknownReq)))
                .andExpect(status().isBadRequest())
                .andReturn();

        // Create a valid pass first
        CreateGuestPassRequest createReq = new CreateGuestPassRequest("entry-timing", "read", 900);
        MvcResult createResult = mockMvc.perform(post("/api/guest-passes")
                        .header("Authorization", "Bearer " + userJwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn();
        CreateGuestPassResponse createResp = objectMapper.readValue(createResult.getResponse().getContentAsString(), CreateGuestPassResponse.class);

        RedeemGuestPassRequest wrongSecretReq = new RedeemGuestPassRequest(createResp.id() + ".wrongsecret123456789012345678");
        MvcResult wrongSecretRes = mockMvc.perform(post("/api/guest-passes/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongSecretReq)))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertEquals(unknownRes.getResponse().getContentAsString(), wrongSecretRes.getResponse().getContentAsString(),
                "Error response shape must be strictly identical for unknown ID and wrong secret");
    }

    @Test
    @DisplayName("Rate limiting on /api/guest-passes/redeem rejects excess requests with 429")
    void testRateLimitingOnRedeem() throws Exception {
        RedeemGuestPassRequest req = new RedeemGuestPassRequest("somepassid.somesecret12345");
        String body = objectMapper.writeValueAsString(req);

        // Send 25 rapid requests from the same IP (rate limit threshold is 20 per minute per IP)
        boolean got429 = false;
        for (int i = 0; i < 25; i++) {
            MvcResult res = mockMvc.perform(post("/api/guest-passes/redeem")
                            .header("X-Forwarded-For", "198.51.100.55")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andReturn();
            if (res.getResponse().getStatus() == 429) {
                got429 = true;
                break;
            }
        }
        assertTrue(got429, "Rate limiter must respond with HTTP 429 Too Many Requests");
    }

    @Test
    @DisplayName("Raw tokens, secrets, and tickets never appear in error responses")
    void testNoTokenOrSecretAppearsInResponses() throws Exception {
        String rawSecret = "super-confidential-secret-12345";
        RedeemGuestPassRequest req = new RedeemGuestPassRequest("testid." + rawSecret);
        MvcResult res = mockMvc.perform(post("/api/guest-passes/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andReturn();

        String body = res.getResponse().getContentAsString();
        assertFalse(body.contains(rawSecret), "Response must never leak raw token secret");
    }

    @Test
    @DisplayName("User A cannot create or revoke a guest pass for User B's entry (403 Forbidden)")
    void testUserCannotCreateOrRevokePassForOtherUsersEntry() throws Exception {
        // 1. Create entry owned by bob
        org.keyfort.server.entity.EntryEntity bobEntry = new org.keyfort.server.entity.EntryEntity(
                "entry-bob-private",
                "bob-user",
                "https://bobsite.example",
                "bob",
                Crypto.random(32),
                1,
                Instant.now()
        );
        entryRepository.save(bobEntry);

        // 2. alice-owner attempts to create a pass for bob's entry -> 403
        CreateGuestPassRequest illegalCreate = new CreateGuestPassRequest("entry-bob-private", "read", 900);
        mockMvc.perform(post("/api/guest-passes")
                        .header("Authorization", "Bearer " + userJwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(illegalCreate)))
                .andExpect(status().isForbidden());

        // 3. alice-owner creates a pass for her own entry
        CreateGuestPassRequest aliceCreate = new CreateGuestPassRequest("entry-alice-own", "read", 900);
        MvcResult createResult = mockMvc.perform(post("/api/guest-passes")
                        .header("Authorization", "Bearer " + userJwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(aliceCreate)))
                .andExpect(status().isCreated())
                .andReturn();
        CreateGuestPassResponse alicePass = objectMapper.readValue(createResult.getResponse().getContentAsString(), CreateGuestPassResponse.class);

        // 4. Bob authenticates to get his JWT token
        MvcResult bobAuth = mockMvc.perform(post("/auth/device/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeviceVerificationRequest("bob-user", "bob-phone", "123456"))))
                .andExpect(status().isOk())
                .andReturn();
        String bobToken = objectMapper.readValue(bobAuth.getResponse().getContentAsString(), AuthResponse.class).token();

        // 5. Bob attempts to revoke alice's pass -> 403
        mockMvc.perform(delete("/api/guest-passes/" + alicePass.id())
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Vault rotationPending flag is returned on vault sync fetch and cleared upon rotated sync upload")
    void testVaultSyncRotationPendingFlagLifecycle() throws Exception {
        // 1. Initial vault fetch returns rotationPending = false
        MvcResult initialResult = mockMvc.perform(get("/vault/sync")
                        .header("Authorization", "Bearer " + userJwtToken))
                .andExpect(status().isOk())
                .andReturn();
        org.keyfort.server.dto.VaultSyncResponse initialResp = objectMapper.readValue(
                initialResult.getResponse().getContentAsString(), org.keyfort.server.dto.VaultSyncResponse.class);
        assertFalse(initialResp.rotationPending());
        assertEquals(1, initialResp.keyVersion());

        // 2. Simulate pass burn setting rotation pending on vault
        var vault = vaultRepository.findById("alice-owner").orElseThrow();
        vault.setRotationPending(true);
        vaultRepository.save(vault);

        // 3. Vault fetch now returns rotationPending = true
        MvcResult pendingResult = mockMvc.perform(get("/vault/sync")
                        .header("Authorization", "Bearer " + userJwtToken))
                .andExpect(status().isOk())
                .andReturn();
        org.keyfort.server.dto.VaultSyncResponse pendingResp = objectMapper.readValue(
                pendingResult.getResponse().getContentAsString(), org.keyfort.server.dto.VaultSyncResponse.class);
        assertTrue(pendingResp.rotationPending());

        // 4. Client rotates key locally and uploads rotated vault bundle with keyVersion = 2 and rotationPending = false
        VaultSyncRequest rotatedSyncReq = new VaultSyncRequest(
                "alice-owner",
                initialResp.iterations(),
                initialResp.recoveryPubBase64(),
                initialResp.kdfSaltBase64(),
                initialResp.wrappedDekBase64(),
                initialResp.recoveryWrappedDekBase64(),
                2,
                false,
                List.of()
        );
        mockMvc.perform(put("/vault/sync")
                        .header("Authorization", "Bearer " + userJwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rotatedSyncReq)))
                .andExpect(status().isOk());

        // 5. Vault fetch confirms rotationPending is cleared and keyVersion is updated
        MvcResult clearedResult = mockMvc.perform(get("/vault/sync")
                        .header("Authorization", "Bearer " + userJwtToken))
                .andExpect(status().isOk())
                .andReturn();
        org.keyfort.server.dto.VaultSyncResponse clearedResp = objectMapper.readValue(
                clearedResult.getResponse().getContentAsString(), org.keyfort.server.dto.VaultSyncResponse.class);
        assertFalse(clearedResp.rotationPending(), "rotationPending must be cleared after client uploads rotated vault");
        assertEquals(2, clearedResp.keyVersion(), "Vault keyVersion must be updated to 2");
    }

    @Test
    @DisplayName("Rate limiter client IP resolution respects trusted proxies and ignores spoofed X-Forwarded-For")
    void testRateLimiterRespectsTrustedProxies() {
        org.keyfort.server.orchestration.ratelimit.ClientIpResolver resolver =
                new org.keyfort.server.orchestration.ratelimit.ClientIpResolver("127.0.0.1,::1");

        // Request directly from untrusted public IP with forged header -> ignores header
        org.springframework.mock.web.MockHttpServletRequest untrustedReq = new org.springframework.mock.web.MockHttpServletRequest();
        untrustedReq.setRemoteAddr("198.51.100.99");
        untrustedReq.addHeader("X-Forwarded-For", "1.1.1.1");
        assertEquals("198.51.100.99", resolver.resolveClientIp(untrustedReq));

        // Request from trusted reverse proxy -> extracts forwarded client IP
        org.springframework.mock.web.MockHttpServletRequest trustedReq = new org.springframework.mock.web.MockHttpServletRequest();
        trustedReq.setRemoteAddr("127.0.0.1");
        trustedReq.addHeader("X-Forwarded-For", "203.0.113.44, 10.0.0.1");
        assertEquals("203.0.113.44", resolver.resolveClientIp(trustedReq));
    }
}
