package org.keyfort.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keyfort.core.risk.AccessEvent;
import org.keyfort.core.risk.Action;
import org.keyfort.core.risk.Decision;
import org.keyfort.core.risk.DeviceRegistry;
import org.keyfort.core.risk.RiskEngine;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Risk Engine Multi-Signal Assessment Tests")
class RiskEngineTest {

    private DeviceRegistry devices;
    private RiskEngine engine;

    @BeforeEach
    void setup() {
        devices = new DeviceRegistry();
        engine = new RiskEngine(devices);
    }

    @Test
    @DisplayName("Trusted device normal successful access results in ALLOW with score 0")
    void testTrustedDeviceAllow() {
        devices.trust("alice", "phone-1");
        Instant now = Instant.now();

        Decision decision = engine.assess(new AccessEvent(now, "alice", "phone-1", "10.0.0.1", "US-NYC", true));

        assertEquals(Action.ALLOW, decision.action());
        assertEquals(0, decision.score());
        assertFalse(decision.coordinatedAttack());
        assertTrue(decision.reasons().isEmpty());
    }

    @Test
    @DisplayName("Unfamiliar device raises score +25 and triggers ALERT on first failure")
    void testUnfamiliarDeviceAlert() {
        Instant now = Instant.now();

        Decision decision = engine.assess(new AccessEvent(now, "alice", "laptop-unknown", "10.0.0.2", "US-NYC", true));

        assertEquals(Action.ALLOW, decision.action()); // score 25 < 30
        assertEquals(25, decision.score());
        assertTrue(decision.reasons().contains("unfamiliar device"));

        // With one failure, score becomes 25 + 10 = 35 -> ALERT (threshold 30)
        Decision failDecision = engine.assess(new AccessEvent(now.plusSeconds(5), "alice", "laptop-unknown", "10.0.0.2", "US-NYC", false));
        assertEquals(Action.ALERT, failDecision.action());
        assertTrue(failDecision.score() >= 35);
    }

    @Test
    @DisplayName("Revoked device attempting access accumulates score >= 75 and REVOKE_DEVICE")
    void testRevokedDevice() {
        devices.revoke("alice", "stolen-tablet");
        Instant now = Instant.now();

        // 1st failure: score 60 (revoked) + 10 (1 fail) = 70 -> STEP_UP_AUTH
        Decision d1 = engine.assess(new AccessEvent(now, "alice", "stolen-tablet", "10.0.0.5", "US-NYC", false));
        assertEquals(Action.STEP_UP_AUTH, d1.action());
        assertEquals(70, d1.score());

        // 2nd failure: score 60 (revoked) + 20 (2 fails) = 80 >= 75 -> REVOKE_DEVICE
        Decision d2 = engine.assess(new AccessEvent(now.plusSeconds(5), "alice", "stolen-tablet", "10.0.0.5", "US-NYC", false));
        assertEquals(Action.REVOKE_DEVICE, d2.action());
        assertTrue(d2.score() >= 75);
        assertTrue(d2.reasons().stream().anyMatch(r -> r.contains("revoked device")));
    }

    @Test
    @DisplayName("Impossible travel within 1 hour adds risk score +25")
    void testImpossibleTravel() {
        devices.trust("alice", "phone-1");
        Instant t0 = Instant.now();

        // Login in London
        engine.assess(new AccessEvent(t0, "alice", "phone-1", "80.0.0.1", "GB-LON", true));

        // 10 minutes later, login from Tokyo
        Instant t1 = t0.plusSeconds(600);
        Decision travelDecision = engine.assess(new AccessEvent(t1, "alice", "phone-1", "133.0.0.1", "JP-TYO", true));

        assertTrue(travelDecision.reasons().stream().anyMatch(r -> r.contains("impossible travel")));
        assertEquals(25, travelDecision.score());
    }

    @Test
    @DisplayName("Coordinated distributed attack flags coordinatedAttack and raises score")
    void testCoordinatedAttack() {
        Instant t = Instant.now();
        Decision lastDecision = null;

        // 6 failures from 6 different bot devices across different IPs
        for (int i = 1; i <= 6; i++) {
            lastDecision = engine.assess(new AccessEvent(
                    t.plusSeconds(i * 3L),
                    "alice",
                    "bot-device-" + i,
                    "198.51.100." + i,
                    "RU",
                    false
            ));
        }

        assertNotNull(lastDecision);
        assertTrue(lastDecision.coordinatedAttack(), "Should identify coordinated distributed attack");
        assertTrue(lastDecision.reasons().stream().anyMatch(r -> r.contains("coordinated failures")));
    }

    @Test
    @DisplayName("Anti-lockout rule: a TRUSTED device is NEVER auto-revoked even with score >= 75, only challenged with STEP_UP_AUTH")
    void testAntiLockoutForTrustedDevice() {
        devices.trust("alice", "trusted-phone");
        Instant t = Instant.now();

        // Initial success in New York
        engine.assess(new AccessEvent(t, "alice", "trusted-phone", "10.0.0.1", "US-NYC", true));

        // Impossible travel to London (+25) with 4 device failures (+40) = 65
        for (int i = 1; i <= 4; i++) {
            engine.assess(new AccessEvent(t.plusSeconds(60L + i * 2L), "alice", "trusted-phone", "80.0.0.1", "GB-LON", false));
        }

        // Distributed bot attack concurrent with owner's attempt (+30 coordinated)
        engine.assess(new AccessEvent(t.plusSeconds(70), "alice", "bot-1", "198.51.100.1", "RU", false));
        engine.assess(new AccessEvent(t.plusSeconds(72), "alice", "bot-2", "198.51.100.2", "CN", false));
        engine.assess(new AccessEvent(t.plusSeconds(74), "alice", "bot-3", "198.51.100.3", "KP", false));

        // Total score = 40 (device fails) + 25 (impossible travel) + 30 (coordinated) = 95 >= 75
        Decision highRiskDecision = engine.assess(new AccessEvent(
                t.plusSeconds(80),
                "alice",
                "trusted-phone",
                "80.0.0.1",
                "GB-LON",
                false
        ));

        assertTrue(highRiskDecision.score() >= 75, "Score must be >= 75 (calculated: " + highRiskDecision.score() + ")");
        // Under standard rules, score >= 75 produces Action.REVOKE_DEVICE.
        // However, because st == DeviceState.TRUSTED, anti-lockout rule ensures:
        assertEquals(Action.STEP_UP_AUTH, highRiskDecision.action(),
                "Trusted device must never be auto-revoked; stepped down to STEP_UP_AUTH");
    }
}
