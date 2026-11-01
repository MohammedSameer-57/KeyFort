package org.keyfort.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keyfort.core.Crypto;
import org.keyfort.core.Vault;
import org.keyfort.core.risk.Action;
import org.keyfort.core.risk.DeviceState;
import org.keyfort.server.dto.AccessEventRequest;
import org.keyfort.server.dto.AuthResponse;
import org.keyfort.server.dto.DecisionResponse;
import org.keyfort.server.dto.DeviceRegistrationRequest;
import org.keyfort.server.dto.DeviceVerificationRequest;
import org.keyfort.server.dto.EntryDto;
import org.keyfort.server.dto.LedgerVerificationResponse;
import org.keyfort.server.dto.VaultSyncRequest;
import org.keyfort.server.dto.VaultSyncResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("KeyFort Server REST API & Persistence Integration Tests")
class KeyFortServerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("End-to-end device registration, step-up verification, and JWT issuance")
    void testDeviceRegistrationAndVerification() throws Exception {
        // 1. Register device
        DeviceRegistrationRequest regReq = new DeviceRegistrationRequest("alice", "phone-1", "Alice's Pixel 9");
        MvcResult regResult = mockMvc.perform(post("/auth/device/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regReq)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse regResp = objectMapper.readValue(regResult.getResponse().getContentAsString(), AuthResponse.class);
        assertNotNull(regResp.token());
        assertEquals("alice", regResp.userId());
        assertEquals("phone-1", regResp.deviceId());
        assertEquals(DeviceState.UNKNOWN, regResp.state());

        // 2. Step-up verify device
        DeviceVerificationRequest verifyReq = new DeviceVerificationRequest("alice", "phone-1", "123456");
        MvcResult verifyResult = mockMvc.perform(post("/auth/device/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse verifyResp = objectMapper.readValue(verifyResult.getResponse().getContentAsString(), AuthResponse.class);
        assertEquals(DeviceState.TRUSTED, verifyResp.state());
    }

    @Test
    @DisplayName("Access events risk analysis and distributed coordinated attack detection")
    void testAccessEventRiskEvaluation() throws Exception {
        // First trust the phone
        mockMvc.perform(post("/auth/device/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeviceVerificationRequest("bob", "phone-bob", "123456"))))
                .andExpect(status().isOk());

        Instant now = Instant.now();

        // Normal successful access from trusted phone -> ALLOW
        AccessEventRequest normalEvent = new AccessEventRequest(now, "bob", "phone-bob", "10.0.0.1", "US-NYC", true);
        MvcResult normalResult = mockMvc.perform(post("/access-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(normalEvent)))
                .andExpect(status().isOk())
                .andReturn();

        DecisionResponse normalDecision = objectMapper.readValue(normalResult.getResponse().getContentAsString(), DecisionResponse.class);
        assertEquals(Action.ALLOW, normalDecision.action());
        assertEquals(0, normalDecision.score());
        assertFalse(normalDecision.coordinatedAttack());

        // Distributed bot attack against bob
        DecisionResponse botDecision = null;
        for (int i = 1; i <= 6; i++) {
            AccessEventRequest botEvent = new AccessEventRequest(
                    now.plusSeconds(i * 5L),
                    "bob",
                    "bot-" + i,
                    "203.0.113." + i,
                    "RU",
                    false
            );
            MvcResult botResult = mockMvc.perform(post("/access-events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(botEvent)))
                    .andExpect(status().isOk())
                    .andReturn();
            botDecision = objectMapper.readValue(botResult.getResponse().getContentAsString(), DecisionResponse.class);
        }

        assertNotNull(botDecision);
        assertTrue(botDecision.coordinatedAttack(), "Risk engine must flag coordinated distributed attack");
    }

    @Test
    @DisplayName("Zero-Knowledge vault sync: server stores ONLY ciphertext; client can restore and decrypt")
    void testZeroKnowledgeVaultSync() throws Exception {
        // Register & verify user device to get JWT
        MvcResult authResult = mockMvc.perform(post("/auth/device/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeviceVerificationRequest("carol", "device-carol", "code"))))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readValue(authResult.getResponse().getContentAsString(), AuthResponse.class).token();

        // Client creates Vault locally
        char[] masterSecret = "carol-master-password-correct".toCharArray();
        Vault.Created created = Vault.create("carol", masterSecret);
        Vault clientVault = created.vault();

        String entryId;
        try (Vault.Session s = clientVault.unlock(masterSecret)) {
            entryId = s.add("github.com", "carol_dev", "SuperSecretPassword123!");
        }

        org.keyfort.core.Entry coreEntry = clientVault.entries().get(entryId);
        EntryDto entryDto = new EntryDto(
                coreEntry.id(),
                coreEntry.site(),
                coreEntry.username(),
                Base64.getEncoder().encodeToString(coreEntry.sealedPassword()),
                coreEntry.keyVersion()
        );

        VaultSyncRequest syncRequest = new VaultSyncRequest(
                clientVault.userId(),
                clientVault.iterations(),
                Base64.getEncoder().encodeToString(clientVault.recoveryPub().getEncoded()),
                Base64.getEncoder().encodeToString(clientVault.kdfSalt()),
                Base64.getEncoder().encodeToString(clientVault.wrappedDek()),
                Base64.getEncoder().encodeToString(clientVault.recoveryWrappedDek()),
                clientVault.keyVersion(),
                clientVault.rotationPending(),
                List.of(entryDto)
        );

        // Upload ciphertext to server
        MvcResult putResult = mockMvc.perform(put("/vault/sync")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(syncRequest)))
                .andExpect(status().isOk())
                .andReturn();

        VaultSyncResponse putResponse = objectMapper.readValue(putResult.getResponse().getContentAsString(), VaultSyncResponse.class);
        assertEquals("carol", putResponse.userId());
        assertEquals(1, putResponse.entries().size());

        // Download ciphertext from server
        MvcResult getResult = mockMvc.perform(get("/vault/sync")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        VaultSyncResponse getResponse = objectMapper.readValue(getResult.getResponse().getContentAsString(), VaultSyncResponse.class);
        assertEquals(1, getResponse.keyVersion());

        // Client reconstructs Vault from downloaded ciphertext and unlocks with master secret
        PublicKey recoveryPub = KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(getResponse.recoveryPubBase64())));

        Map<String, org.keyfort.core.Entry> reconstructedEntries = new HashMap<>();
        for (EntryDto dto : getResponse.entries()) {
            reconstructedEntries.put(dto.id(), new org.keyfort.core.Entry(
                    dto.id(),
                    dto.site(),
                    dto.username(),
                    Base64.getDecoder().decode(dto.sealedPasswordBase64()),
                    dto.keyVersion()
            ));
        }

        Vault restoredVault = Vault.restore(
                getResponse.userId(),
                getResponse.iterations(),
                recoveryPub,
                Base64.getDecoder().decode(getResponse.kdfSaltBase64()),
                Base64.getDecoder().decode(getResponse.wrappedDekBase64()),
                Base64.getDecoder().decode(getResponse.recoveryWrappedDekBase64()),
                getResponse.keyVersion(),
                getResponse.rotationPending(),
                reconstructedEntries
        );

        // Prove that the client can decrypt and reveal the secret
        try (Vault.Session s = restoredVault.unlock(masterSecret)) {
            assertEquals("SuperSecretPassword123!", s.reveal(entryId));
        }
    }

    @Test
    @DisplayName("Device revocation and rotation acknowledgment")
    void testDeviceRevokeAndRotationAck() throws Exception {
        MvcResult authResult = mockMvc.perform(post("/auth/device/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeviceVerificationRequest("dave", "device-dave", "code"))))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readValue(authResult.getResponse().getContentAsString(), AuthResponse.class).token();

        // List devices
        mockMvc.perform(get("/devices")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // Revoke device
        mockMvc.perform(post("/devices/device-dave/revoke")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("REVOKED"));

        // Upload initial vault for dave
        Vault.Created created = Vault.create("dave", "dave-secret".toCharArray());
        Vault v = created.vault();
        VaultSyncRequest syncRequest = new VaultSyncRequest(
                "dave", v.iterations(),
                Base64.getEncoder().encodeToString(v.recoveryPub().getEncoded()),
                Base64.getEncoder().encodeToString(v.kdfSalt()),
                Base64.getEncoder().encodeToString(v.wrappedDek()),
                Base64.getEncoder().encodeToString(v.recoveryWrappedDek()),
                1, true, List.of()
        );
        mockMvc.perform(put("/vault/sync")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(syncRequest)))
                .andExpect(status().isOk());

        // Acknowledge rotation
        mockMvc.perform(post("/vault/rotation/ack")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceId\":\"device-dave\",\"newKeyVersion\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.newKeyVersion").value(2));
    }

    @Test
    @DisplayName("Ledger verification endpoint validates unbroken hash chain")
    void testLedgerVerificationEndpoint() throws Exception {
        // Perform an audited action to guarantee at least one block exists
        mockMvc.perform(post("/auth/device/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeviceVerificationRequest("ledger-user", "ledger-dev", "code"))))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/ledger/verify"))
                .andExpect(status().isOk())
                .andReturn();

        LedgerVerificationResponse resp = objectMapper.readValue(result.getResponse().getContentAsString(), LedgerVerificationResponse.class);
        assertTrue(resp.valid(), "Cryptographic ledger hash chain must be valid");
        assertTrue(resp.blockCount() > 0, "Ledger must contain audited blocks from test actions");
    }
}
