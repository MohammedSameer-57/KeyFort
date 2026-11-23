package org.keyfort.server.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keyfort.core.risk.DeviceState;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JWT Service Hardening and Secret Validation Tests")
class JwtServiceTest {

    private static final String VALID_SECRET = "valid-secret-key-material-that-is-at-least-256-bits-long!!";

    @Test
    @DisplayName("Missing JWT secret in non-dev environment throws IllegalStateException")
    void testMissingSecretThrowsInProd() {
        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new JwtService("", 900000, prodEnv));
        assertTrue(ex.getMessage().contains("KEYFORT_JWT_SECRET"));
    }

    @Test
    @DisplayName("JWT secret under 32 bytes (256 bits) throws IllegalStateException")
    void testShortSecretThrows() {
        MockEnvironment env = new MockEnvironment();

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new JwtService("short-secret-under-32-bytes", 900000, env));
        assertTrue(ex.getMessage().contains("at least 32 bytes"));
    }

    @Test
    @DisplayName("Dev environment allows default test secret when KEYFORT_JWT_SECRET is empty")
    void testDevEnvironmentAllowsDefault() {
        MockEnvironment devEnv = new MockEnvironment();
        devEnv.setActiveProfiles("dev");

        JwtService service = new JwtService("", 900000, devEnv);
        String token = service.generateToken("user-dev", "device-dev", DeviceState.TRUSTED);
        assertNotNull(token);
        assertEquals("user-dev", service.extractUserId(token));
    }

    @Test
    @DisplayName("Valid 32-byte secret generates and validates claims accurately")
    void testValidSecretTokenRoundTrip() {
        JwtService service = new JwtService(VALID_SECRET, 900000);
        String token = service.generateToken("alice", "device-phone-1", DeviceState.TRUSTED);

        assertNotNull(token);
        assertEquals("alice", service.extractUserId(token));
        assertEquals("device-phone-1", service.extractDeviceId(token));
    }
}
