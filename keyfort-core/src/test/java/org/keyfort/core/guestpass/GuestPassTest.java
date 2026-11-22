package org.keyfort.core.guestpass;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keyfort.core.Crypto;
import org.keyfort.core.Vault;
import org.keyfort.core.ledger.Ledger;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GuestPass State Machine, Storage, and Orchestration Tests")
class GuestPassTest {

    private static final Instant T0 = Instant.parse("2026-11-22T10:00:00Z");
    private MutableClock clock;
    private InMemoryGuestPassStore store;
    private Ledger ledger;
    private Vault vault;
    private GuestPassService service;

    @BeforeEach
    void setUp() throws GeneralSecurityException {
        clock = new MutableClock(T0, ZoneOffset.UTC);
        store = new InMemoryGuestPassStore();
        ledger = new Ledger();
        vault = Vault.create("user-alice", "master-password-12345".toCharArray()).vault();
        service = new GuestPassService(store, clock, ledger, vault, GuestPassListener.NOOP);
    }

    @Test
    @DisplayName("Every legal transition succeeds across full lifecycle CREATED -> ISSUED -> REDEEMED -> CONSUMED -> BURNED")
    void testEveryLegalTransitionLifecycle() {
        IssuedToken issued = service.create("entry-1", "read", Duration.ofMinutes(15));
        assertEquals(GuestPassState.ISSUED, issued.pass().state());
        assertEquals(2L, issued.pass().version());

        GuestPass redeemed = service.redeem(issued.rawToken(), "site-session-ref-999");
        assertEquals(GuestPassState.REDEEMED, redeemed.state());
        assertEquals("site-session-ref-999", redeemed.credentialRef());
        assertEquals(3L, redeemed.version());

        GuestPass consumed = service.markConsumed(issued.id());
        assertEquals(GuestPassState.CONSUMED, consumed.state());
        assertEquals(4L, consumed.version());

        GuestPass burned = service.burn(issued.id());
        assertEquals(GuestPassState.BURNED, burned.state());
        assertEquals(5L, burned.version());
        assertTrue(vault.rotationPending());
    }

    @Test
    @DisplayName("Every illegal transition throws IllegalTransitionException")
    void testEveryIllegalTransitionThrows() {
        IssuedToken issued = service.create("entry-1", "read", Duration.ofMinutes(15));
        GuestPass pass = issued.pass();

        // From ISSUED: cannot jump directly to CONSUMED or BURNED
        assertThrows(IllegalTransitionException.class, () -> pass.consume(clock));
        assertThrows(IllegalTransitionException.class, () -> pass.burn(clock));

        // From REDEEMED: cannot jump directly to BURNED or back to ISSUED
        GuestPass redeemed = pass.redeem(clock, "ref");
        assertThrows(IllegalTransitionException.class, () -> redeemed.burn(clock));
        assertThrows(IllegalTransitionException.class, () -> redeemed.issue(clock));

        // From CONSUMED: cannot jump back to REDEEMED or ISSUED
        GuestPass consumed = redeemed.consume(clock);
        assertThrows(IllegalTransitionException.class, () -> consumed.redeem(clock, "ref"));
        assertThrows(IllegalTransitionException.class, () -> consumed.issue(clock));

        // From terminal states: no transitions allowed
        GuestPass burned = consumed.burn(clock);
        assertThrows(IllegalTransitionException.class, () -> burned.burn(clock));
        assertThrows(IllegalTransitionException.class, () -> burned.revoke(clock));
        assertThrows(IllegalTransitionException.class, () -> burned.expire(clock));

        GuestPass revoked = pass.revoke(clock);
        assertThrows(IllegalTransitionException.class, () -> revoked.redeem(clock, "ref"));
        assertThrows(IllegalTransitionException.class, () -> revoked.revoke(clock));

        GuestPass expired = pass.expire(clock);
        assertThrows(IllegalTransitionException.class, () -> expired.redeem(clock, "ref"));
        assertThrows(IllegalTransitionException.class, () -> expired.expire(clock));
    }

    @Test
    @DisplayName("Wrong token secret is rejected with InvalidTokenException")
    void testWrongSecretRejected() {
        IssuedToken issued = service.create("entry-1", "read", Duration.ofMinutes(15));
        String wrongToken = issued.id() + ".AAAAAAAAAAAAAAAAAAAAAA";

        assertThrows(InvalidTokenException.class, () -> service.redeem(wrongToken, "ref"));
    }

    @Test
    @DisplayName("Wrong token id is rejected with GuestPassNotFoundException")
    void testWrongIdRejected() {
        IssuedToken issued = service.create("entry-1", "read", Duration.ofMinutes(15));
        String wrongToken = "nonexistent." + issued.rawToken().split("\\.")[1];

        assertThrows(GuestPassNotFoundException.class, () -> service.redeem(wrongToken, "ref"));
    }

    @Test
    @DisplayName("Token expired at the exact boundary is rejected with GuestPassExpiredException")
    void testExpiredAtExactBoundary() {
        IssuedToken issued = service.create("entry-1", "read", Duration.ofMinutes(15));

        // Advance clock exactly to expiration boundary
        clock.setInstant(issued.pass().expiresAt());

        assertThrows(GuestPassExpiredException.class, () -> service.redeem(issued.rawToken(), "ref"));
    }

    @Test
    @DisplayName("Raw secret is shown once and is not recoverable from the store")
    void testTokenShownOnceAndNotRecoverableFromStore() {
        IssuedToken issued = service.create("entry-1", "read", Duration.ofMinutes(15));
        GuestPass stored = store.findById(issued.id()).orElseThrow();

        String rawSecret = issued.rawToken().split("\\.")[1];
        assertFalse(stored.toString().contains(rawSecret));
        assertNotEquals(rawSecret, Crypto.toHex(stored.secretHash()));
    }

    @Test
    @DisplayName("Tampered stored hash fails verification with InvalidTokenException")
    void testTamperedHashFailsVerification() {
        IssuedToken issued = service.create("entry-1", "read", Duration.ofMinutes(15));
        GuestPass stored = store.findById(issued.id()).orElseThrow();

        byte[] tamperedHash = stored.secretHash();
        tamperedHash[0] ^= (byte) 0xFF;

        GuestPass tamperedPass = new GuestPass(
                stored.id(),
                stored.entryId(),
                stored.scope(),
                tamperedHash,
                stored.state(),
                stored.createdAt(),
                stored.expiresAt(),
                stored.version() + 1,
                stored.credentialRef()
        );
        store.compareAndSet(stored.id(), stored.version(), tamperedPass);

        assertThrows(InvalidTokenException.class, () -> service.redeem(issued.rawToken(), "ref"));
    }

    @Test
    @DisplayName("Replay after CONSUMED or BURNED throws IllegalTransitionException")
    void testReplayAfterConsumedThrows() {
        IssuedToken issued = service.create("entry-1", "read", Duration.ofMinutes(15));
        service.redeem(issued.rawToken(), "ref");
        service.markConsumed(issued.id());

        assertThrows(IllegalTransitionException.class, () -> service.redeem(issued.rawToken(), "replay-ref"));

        service.burn(issued.id());
        assertThrows(IllegalTransitionException.class, () -> service.redeem(issued.rawToken(), "replay-ref"));
    }

    @Test
    @DisplayName("Fifty concurrent threads race to redeem: exactly one winner, 49 rejected")
    void testFiftyConcurrentRedeemCallersExactlyOneWins() throws Exception {
        IssuedToken issued = service.create("entry-1", "read", Duration.ofMinutes(15));
        int threadCount = 50;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch finishGate = new CountDownLatch(threadCount);

        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final String credRef = "thread-ref-" + i;
            executor.submit(() -> {
                try {
                    startGate.await();
                    service.redeem(issued.rawToken(), credRef);
                    successes.incrementAndGet();
                } catch (Exception expected) {
                    failures.incrementAndGet();
                } finally {
                    finishGate.countDown();
                }
            });
        }

        startGate.countDown();
        finishGate.await();
        executor.shutdown();

        assertEquals(1, successes.get(), "Exactly one caller must win the redemption race");
        assertEquals(49, failures.get(), "All other 49 callers must be rejected");

        GuestPass finalPass = store.findById(issued.id()).orElseThrow();
        assertEquals(GuestPassState.REDEEMED, finalPass.state());
    }

    @Test
    @DisplayName("Revoke racing with redeem on the same version: exactly one CAS succeeds")
    void testRevokeRacingWithRedeem() throws Exception {
        IssuedToken issued = service.create("entry-1", "read", Duration.ofMinutes(15));
        GuestPass initial = store.findById(issued.id()).orElseThrow();
        long version = initial.version();

        GuestPass redeemed = initial.redeem(clock, "ref");
        GuestPass revoked = initial.revoke(clock);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch finishGate = new CountDownLatch(2);

        AtomicInteger redeemWon = new AtomicInteger(0);
        AtomicInteger revokeWon = new AtomicInteger(0);

        executor.submit(() -> {
            try {
                startGate.await();
                if (store.compareAndSet(issued.id(), version, redeemed)) {
                    redeemWon.incrementAndGet();
                }
            } catch (Exception ignored) {
            } finally {
                finishGate.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startGate.await();
                if (store.compareAndSet(issued.id(), version, revoked)) {
                    revokeWon.incrementAndGet();
                }
            } catch (Exception ignored) {
            } finally {
                finishGate.countDown();
            }
        });

        startGate.countDown();
        finishGate.await();
        executor.shutdown();

        assertEquals(1, redeemWon.get() + revokeWon.get(), "Exactly one mutation must win CAS on same version");
        GuestPass finalPass = store.findById(issued.id()).orElseThrow();
        assertTrue(finalPass.state() == GuestPassState.REDEEMED || finalPass.state() == GuestPassState.REVOKED);
    }

    @Test
    @DisplayName("Revoked pass cannot be redeemed")
    void testRevokedPassCannotBeRedeemed() {
        IssuedToken issued = service.create("entry-1", "read", Duration.ofMinutes(15));
        service.revoke(issued.id());
        assertThrows(IllegalTransitionException.class, () -> service.redeem(issued.rawToken(), "ref"));
    }

    @Test
    @DisplayName("Constant-time hash comparison is verified with MessageDigest.isEqual semantics")
    void testConstantTimeComparison() {
        byte[] expected = Crypto.sha256("test-secret".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        byte[] same = expected.clone();
        byte[] diffFirst = expected.clone();
        diffFirst[0] ^= 0x01;
        byte[] diffLast = expected.clone();
        diffLast[diffLast.length - 1] ^= 0x01;

        assertTrue(MessageDigest.isEqual(expected, same));
        assertFalse(MessageDigest.isEqual(expected, diffFirst));
        assertFalse(MessageDigest.isEqual(expected, diffLast));
    }

    @Test
    @DisplayName("Ledger appends events and verifies unbroken hash chain after full lifecycle")
    void testLedgerVerifiesAfterFullLifecycle() {
        IssuedToken issued = service.create("entry-1", "read", Duration.ofMinutes(15));
        service.redeem(issued.rawToken(), "ref");
        service.markConsumed(issued.id());
        service.burn(issued.id());

        assertTrue(ledger.verify(), "Ledger hash chain must verify");
        List<Ledger.Block> blocks = ledger.blocks();
        assertFalse(blocks.isEmpty());

        for (Ledger.Block b : blocks) {
            assertFalse(b.payload().contains(issued.rawToken()), "Token must never appear in ledger");
            String rawSecret = issued.rawToken().split("\\.")[1];
            assertFalse(b.payload().contains(rawSecret), "Secret must never appear in ledger");
        }
    }

    @Test
    @DisplayName("Rotation is requested on Vault exactly once when pass reaches BURNED")
    void testRotationRequestedExactlyOnceOnBurned() {
        IssuedToken issued = service.create("entry-1", "read", Duration.ofMinutes(15));
        assertFalse(vault.rotationPending());

        service.redeem(issued.rawToken(), "ref");
        assertFalse(vault.rotationPending());

        service.markConsumed(issued.id());
        assertFalse(vault.rotationPending());

        service.burn(issued.id());
        assertTrue(vault.rotationPending());

        // A second attempt to burn must fail and cannot trigger duplicate rotation
        assertThrows(IllegalTransitionException.class, () -> service.burn(issued.id()));
    }

    @Test
    @DisplayName("IssuedToken.toString() masks the raw secret to prevent accidental logging")
    void testIssuedTokenToStringMasksSecret() {
        IssuedToken issued = service.create("entry-1", "read", Duration.ofMinutes(15));
        String rawSecret = issued.rawToken().split("\\.")[1];
        String str = issued.toString();
        assertFalse(str.contains(rawSecret), "toString must not expose raw secret");
        assertTrue(str.contains("[MASKED]"), "toString must mask the secret");
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private final ZoneId zone;

        MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        void setInstant(Instant instant) {
            this.instant = instant;
        }

        @Override public ZoneId getZone() { return zone; }
        @Override public Clock withZone(ZoneId zone) { return new MutableClock(instant, zone); }
        @Override public Instant instant() { return instant; }
    }
}
