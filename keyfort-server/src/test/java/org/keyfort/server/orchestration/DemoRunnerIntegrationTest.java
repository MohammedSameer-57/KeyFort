package org.keyfort.server.orchestration;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "demo"})
@DisplayName("Demo Runner Master Key Gate and Security Tests")
class DemoRunnerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc.perform(post("/api/demo/reset")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Default state starts locked and exposes master key hint")
    void testDefaultStateIsLocked() throws Exception {
        mockMvc.perform(get("/api/demo/state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unlocked").value(false))
                .andExpect(jsonPath("$.masterKeyHint").value("demo-vault-master-pass"));
    }

    @Test
    @DisplayName("Protected actions return 409 while vault is locked")
    void testActionsWhileLockedGive409() throws Exception {
        mockMvc.perform(post("/api/demo/create-link"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("vault locked"));

        mockMvc.perform(post("/api/demo/friend-login"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("vault locked"));

        mockMvc.perform(post("/api/demo/copier-try"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("vault locked"));

        mockMvc.perform(post("/api/demo/end-session"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("vault locked"));

        mockMvc.perform(post("/api/demo/renew-key"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("vault locked"));
    }

    @Test
    @DisplayName("Wrong key gives 401 and stays locked")
    void testWrongKeyGives401() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("masterKey", "wrong-password"));
        mockMvc.perform(post("/api/demo/unlock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("wrong key"));

        mockMvc.perform(get("/api/demo/state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unlocked").value(false));
    }

    @Test
    @DisplayName("Right master key unlocks vault successfully")
    void testRightKeyUnlocks() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("masterKey", "demo-vault-master-pass"));
        mockMvc.perform(post("/api/demo/unlock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unlocked").value(true));
    }

    @Test
    @DisplayName("Lock endpoint closes session and re-locks vault")
    void testLockClosesSession() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("masterKey", "demo-vault-master-pass"));
        mockMvc.perform(post("/api/demo/unlock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unlocked").value(true));

        mockMvc.perform(post("/api/demo/lock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unlocked").value(false));

        mockMvc.perform(post("/api/demo/create-link"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("vault locked"));
    }

    @Test
    @DisplayName("5 consecutive unlock failures trigger 429 lockout")
    void testLockoutAfter5Failures() throws Exception {
        String wrongBody = objectMapper.writeValueAsString(Map.of("masterKey", "invalid-key"));
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/demo/unlock")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(wrongBody))
                    .andExpect(status().isUnauthorized());
        }

        // 5th attempt triggers lockout (429)
        mockMvc.perform(post("/api/demo/unlock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(wrongBody))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("locked out"));

        // 6th attempt is still locked out
        mockMvc.perform(post("/api/demo/unlock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(wrongBody))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("locked out"));
    }

    @Test
    @DisplayName("Master key is never logged anywhere")
    void testMasterKeyNeverAppearsInLogs() throws Exception {
        Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        rootLogger.addAppender(listAppender);

        String secretProbe = "unique-canary-passphrase-98765";
        try {
            String unlockBody = objectMapper.writeValueAsString(Map.of("masterKey", secretProbe));
            mockMvc.perform(post("/api/demo/unlock")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(unlockBody));

            String validBody = objectMapper.writeValueAsString(Map.of("masterKey", "demo-vault-master-pass"));
            mockMvc.perform(post("/api/demo/unlock")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validBody));

            for (ILoggingEvent event : listAppender.list) {
                String msg = event.getFormattedMessage();
                assertFalse(msg.contains(secretProbe), "Found probe secret in logs: " + msg);
                assertFalse(msg.contains("demo-vault-master-pass"), "Found demo master key in logs: " + msg);
            }
        } finally {
            rootLogger.detachAppender(listAppender);
        }
    }
}
