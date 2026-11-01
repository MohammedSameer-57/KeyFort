package org.keyfort.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keyfort.core.ledger.Ledger;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Ledger Hash-Chaining and Tamper-Evidence Tests")
class LedgerTest {

    @Test
    @DisplayName("Ledger appends blocks and verifies uncorrupted hash chain")
    void testLedgerAppendAndVerify() {
        Ledger ledger = new Ledger();

        Ledger.Block b0 = ledger.append("DEVICE_TRUSTED", "user=a1b2c3d4e5f6;device=phone-1");
        assertEquals(0, b0.seq());
        assertEquals(Ledger.GENESIS, b0.prevHash());
        assertNotNull(b0.hash());

        Ledger.Block b1 = ledger.append("DEVICE_REVOKED", "user=a1b2c3d4e5f6;device=phone-2");
        assertEquals(1, b1.seq());
        assertEquals(b0.hash(), b1.prevHash());

        Ledger.Block b2 = ledger.append("ROTATION_REQUESTED", "user=a1b2c3d4e5f6;device=phone-1;v1");
        assertEquals(2, b2.seq());
        assertEquals(b1.hash(), b2.prevHash());

        assertTrue(ledger.verify(), "Intact ledger chain must pass cryptographic verification");
        assertEquals(3, ledger.blocks().size());
    }

    @Test
    @DisplayName("Tampering with a block payload in chain fails verification")
    void testTamperPayload() {
        Ledger original = new Ledger();
        original.append("TYPE_A", "payload-1");
        original.append("TYPE_B", "payload-2");
        original.append("TYPE_C", "payload-3");

        assertTrue(original.verify());

        // Construct a modified ledger where block 1 has altered payload without updating hash
        Ledger tampered = new Ledger();
        List<Ledger.Block> blocks = original.blocks();
        for (int i = 0; i < blocks.size(); i++) {
            Ledger.Block b = blocks.get(i);
            if (i == 1) {
                tampered.append(b.seq(), b.at(), b.type(), "TAMPERED_PAYLOAD", b.prevHash(), b.hash());
            } else {
                tampered.append(b.seq(), b.at(), b.type(), b.payload(), b.prevHash(), b.hash());
            }
        }

        assertFalse(tampered.verify(), "Ledger verification must fail when payload was tampered");
    }

    @Test
    @DisplayName("Tampering with prevHash breaks chain continuity and fails verification")
    void testTamperPrevHash() {
        Ledger original = new Ledger();
        original.append("EVENT_1", "meta1");
        original.append("EVENT_2", "meta2");

        Ledger tampered = new Ledger();
        List<Ledger.Block> blocks = original.blocks();
        Ledger.Block b0 = blocks.get(0);
        Ledger.Block b1 = blocks.get(1);

        tampered.append(b0.seq(), b0.at(), b0.type(), b0.payload(), b0.prevHash(), b0.hash());
        // Alter prevHash of block 1
        tampered.append(b1.seq(), b1.at(), b1.type(), b1.payload(), "1".repeat(64), b1.hash());

        assertFalse(tampered.verify(), "Ledger verification must fail when prevHash is inconsistent");
    }
}
