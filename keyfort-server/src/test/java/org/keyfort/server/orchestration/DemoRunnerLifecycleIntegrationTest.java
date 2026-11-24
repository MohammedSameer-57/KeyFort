package org.keyfort.server.orchestration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keyfort.core.Crypto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "demo"})
@DisplayName("Demo Runner Lifecycle and Crypto Facts Tests")
class DemoRunnerLifecycleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static HttpServer mockTargetSiteServer;

    @BeforeAll
    static void startTargetSiteServer() throws IOException {
        try {
            mockTargetSiteServer = HttpServer.create(new InetSocketAddress(9090), 0);
            mockTargetSiteServer.createContext("/connector/tickets", exchange -> {
                if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    String uniqueId = "tkt-demo-" + UUID.randomUUID();
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
            mockTargetSiteServer.createContext("/connector/sessions", exchange -> {
                String path = exchange.getRequestURI().getPath();
                if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    String sid = path.substring(path.lastIndexOf('/') + 1);
                    String resp = "{\"sessionId\":\"" + sid + "\",\"status\":\"ACTIVE\",\"expiresAt\":\"2026-11-22T14:15:00Z\",\"remainingSeconds\":600}";
                    byte[] bytes = resp.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, bytes.length);
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
        } catch (BindException ignored) {
        }
    }

    @AfterAll
    static void stopTargetSiteServer() {
        if (mockTargetSiteServer != null) {
            mockTargetSiteServer.stop(0);
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        mockMvc.perform(post("/api/demo/reset")).andExpect(status().isOk());
    }

    private void unlock() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("masterKey", "demo-vault-master-pass"));
        mockMvc.perform(post("/api/demo/unlock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unlocked").value(true));
    }

    @Test
    @DisplayName("In-process full lifecycle execution twice in a row")
    void testLifecycleTwiceInARow() throws Exception {
        for (int cycle = 1; cycle <= 2; cycle++) {
            unlock();

            mockMvc.perform(post("/api/demo/create-link"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.phase").value("link_ready"))
                    .andExpect(jsonPath("$.rawLinkUrl").isNotEmpty());

            mockMvc.perform(post("/api/demo/friend-login"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.phase").value("signed_in"))
                    .andExpect(jsonPath("$.sessionActive").value(true))
                    .andExpect(jsonPath("$.rotationPending").value(true));

            mockMvc.perform(post("/api/demo/copier-try"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Refused: this link was already used once. Every later attempt with a copied link is blocked."));

            mockMvc.perform(post("/api/demo/end-session"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sessionActive").value(false));

            mockMvc.perform(post("/api/demo/renew-key"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.vaultKeyVersion").value(2))
                    .andExpect(jsonPath("$.rotationPending").value(false))
                    .andExpect(jsonPath("$.phase").value("renewed"));

            mockMvc.perform(post("/api/demo/reset"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.phase").value("idle"))
                    .andExpect(jsonPath("$.unlocked").value(false));
        }
    }

    @Test
    @DisplayName("Copier-first ordering: copier acts before friend")
    void testCopierFirstOrdering() throws Exception {
        unlock();

        mockMvc.perform(post("/api/demo/create-link"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("link_ready"));

        // Copier acts first
        mockMvc.perform(post("/api/demo/copier-try"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("signed_in"))
                .andExpect(jsonPath("$.copierUsedFirst").value(true));

        // Friend attempt is now refused
        mockMvc.perform(post("/api/demo/friend-login"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("First use wins. If this happens, end the session."));

        // Owner ends the session
        mockMvc.perform(post("/api/demo/end-session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionActive").value(false));

        // Owner renews key
        mockMvc.perform(post("/api/demo/renew-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vaultKeyVersion").value(2));
    }

    @Test
    @DisplayName("Crypto-facts endpoint matches core cryptographic constants")
    void testCryptoFactsMatchCoreConstants() throws Exception {
        mockMvc.perform(get("/api/crypto-facts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kdfAlgorithm").value("PBKDF2WithHmacSHA256"))
                .andExpect(jsonPath("$.kdfIterations").value(Crypto.PBKDF2_ITERATIONS))
                .andExpect(jsonPath("$.cipherAlgorithm").value("AES-256-GCM"))
                .andExpect(jsonPath("$.hkdfAlgorithm").value("HKDF-SHA256"))
                .andExpect(jsonPath("$.recoveryKeyAlgorithm").value("RSA-3072-OAEP"))
                .andExpect(jsonPath("$.tokenSecretBits").value(128))
                .andExpect(jsonPath("$.tokenIdBits").value(64))
                .andExpect(jsonPath("$.tokenHashAlgorithm").value("SHA-256"))
                .andExpect(jsonPath("$.ledgerHashAlgorithm").value("SHA-256"))
                .andExpect(jsonPath("$.connectorHmacAlgorithm").value("HmacSHA256"))
                .andExpect(jsonPath("$.clockSkewAllowanceSeconds").value(300));
    }
}
