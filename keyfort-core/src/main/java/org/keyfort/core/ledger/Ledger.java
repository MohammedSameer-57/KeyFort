package org.keyfort.core.ledger;

import org.keyfort.core.Crypto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Append-only, tamper-evident cryptographic hash chain ledger for metadata events.
 * No secrets or plaintext credentials are ever stored.
 */
public final class Ledger {

    public record Block(
            long seq,
            Instant at,
            String type,
            String payload,
            String prevHash,
            String hash
    ) {
        public Block {
            Objects.requireNonNull(at, "at must not be null");
            Objects.requireNonNull(type, "type must not be null");
            Objects.requireNonNull(payload, "payload must not be null");
            Objects.requireNonNull(prevHash, "prevHash must not be null");
            Objects.requireNonNull(hash, "hash must not be null");
        }
    }

    public static final String GENESIS = "0".repeat(64);
    private final List<Block> chain = new ArrayList<>();

    public synchronized Block append(String type, String payload) {
        String prev = chain.isEmpty() ? GENESIS : chain.get(chain.size() - 1).hash();
        long seq = chain.size();
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        Block b = new Block(seq, now, type, payload, prev, digest(seq, now, type, payload, prev));
        chain.add(b);
        return b;
    }

    public synchronized Block append(long seq, Instant at, String type, String payload, String prevHash, String hash) {
        Block b = new Block(seq, at, type, payload, prevHash, hash);
        chain.add(b);
        return b;
    }

    public synchronized boolean verify() {
        String prev = GENESIS;
        for (Block b : chain) {
            if (!b.prevHash().equals(prev)) {
                return false;
            }
            if (!b.hash().equals(digest(b.seq(), b.at(), b.type(), b.payload(), b.prevHash()))) {
                return false;
            }
            prev = b.hash();
        }
        return true;
    }

    public synchronized List<Block> blocks() {
        return List.copyOf(chain);
    }

    public static String digest(long seq, Instant at, String type, String payload, String prev) {
        return Crypto.sha256Hex(seq + "|" + at + "|" + type + "|" + payload + "|" + prev);
    }
}
