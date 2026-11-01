package org.keyfort.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keyfort.core.orchestration.Orchestrator;
import org.keyfort.core.risk.AccessEvent;
import org.keyfort.core.risk.Action;
import org.keyfort.core.risk.Decision;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Orchestrator End-to-End Coordination Tests")
class OrchestratorTest {

    @Test
    @DisplayName("Orchestrator coordinates risk assessment, automatic rotation request, and ledger audit logging")
    void testOrchestratorWorkflow() throws Exception {
        List<String> notifications = new ArrayList<>();
        Orchestrator orchestrator = new Orchestrator(notifications::add);

        char[] master = "correct-horse-battery-staple".toCharArray();
        Vault.Created created = Vault.create("alice", master);
        Vault vault = created.vault();
        orchestrator.register(vault);

        String entryId;
        try (Vault.Session s = vault.unlock(master)) {
            entryId = s.add("github.com", "alice", "S3cret-gh!");
            s.add("bank.example", "alice", "Tr0ub4dor&3");
        }
        orchestrator.trustDevice("alice", "phone-1");

        Instant t = Instant.now();

        // 1. Owner logs in from trusted phone -> ALLOW
        Decision d1 = orchestrator.onAccess(new AccessEvent(t, "alice", "phone-1", "49.37.1.1", "IN-AP", true));
        assertEquals(Action.ALLOW, d1.action());

        // 2. Distributed attack from 6 bot devices
        Decision botDecision = null;
        for (int i = 1; i <= 6; i++) {
            botDecision = orchestrator.onAccess(new AccessEvent(t.plusSeconds(i * 5L), "alice", "bot-" + i,
                    "203.0.113." + i, "RU", false));
        }
        assertNotNull(botDecision);
        assertTrue(botDecision.coordinatedAttack());
        assertTrue(vault.rotationPending(), "Coordinated attack must trigger pending rotation on vault");

        // 3. Attacker device cannot unlock
        orchestrator.onAccess(new AccessEvent(t.plusSeconds(35), "alice", "bot-6", "203.0.113.6", "RU", false));
        // Verify revoked device cannot unlock
        orchestrator.revokeDevice("alice", "bot-6");
        assertThrows(AuthException.class, () -> orchestrator.unlock("alice", "bot-6", master));

        // 4. Owner returns on trusted phone: unlocks -> pending rotation completes client-side
        assertEquals(1, vault.keyVersion());
        try (Vault.Session s = orchestrator.unlock("alice", "phone-1", master)) {
            assertEquals(2, vault.keyVersion(), "Vault key version must advance to 2");
            assertFalse(vault.rotationPending());
            assertEquals("S3cret-gh!", s.reveal(entryId));
        }

        // 5. Ledger integrity holds and recorded the rotation
        assertTrue(orchestrator.ledger().verify());
        assertTrue(orchestrator.ledger().blocks().stream()
                .anyMatch(b -> "KEY_VERSION_CHANGED".equals(b.type())));
    }
}
