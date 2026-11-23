package org.keyfort.core.guestpass;

import org.keyfort.core.Crypto;
import org.keyfort.core.Vault;
import org.keyfort.core.ledger.Ledger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

public class GuestPassService {

    private final GuestPassStore store;
    private final Clock clock;
    private final Ledger ledger;
    private final Vault vault;
    private final GuestPassListener listener;

    public GuestPassService(GuestPassStore store, Clock clock) {
        this(store, clock, null, null, GuestPassListener.NOOP);
    }

    public GuestPassService(
            GuestPassStore store,
            Clock clock,
            Ledger ledger,
            Vault vault,
            GuestPassListener listener
    ) {
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.ledger = ledger;
        this.vault = vault;
        this.listener = listener != null ? listener : GuestPassListener.NOOP;
    }

    public IssuedToken create(String entryId, String scope, Duration ttl) {
        Objects.requireNonNull(entryId, "entryId must not be null");
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(ttl, "ttl must not be null");

        Instant now = clock.instant();
        Instant expiresAt = now.plus(ttl);

        byte[] idBytes = Crypto.random(8);
        byte[] secretBytes = Crypto.random(16);

        String id = Base64.getUrlEncoder().withoutPadding().encodeToString(idBytes);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);
        String rawToken = id + "." + secret;

        byte[] secretHash = Crypto.sha256(secret.getBytes(StandardCharsets.US_ASCII));

        GuestPass created = new GuestPass(id, entryId, scope, secretHash, GuestPassState.CREATED, now, expiresAt, 1L, null);
        store.save(created);
        appendLedger("GUEST_PASS_CREATED", id);
        listener.onCreated(created);

        GuestPass issued = created.issue(clock);
        if (!store.compareAndSet(id, created.version(), issued)) {
            throw new ConcurrentMutationException("Concurrent update when issuing pass: " + id);
        }
        appendLedger("GUEST_PASS_ISSUED", id);
        listener.onIssued(issued);

        return new IssuedToken(id, rawToken, issued);
    }

    public GuestPass redeem(String rawToken, String credentialRef) {
        Objects.requireNonNull(rawToken, "rawToken must not be null");
        Objects.requireNonNull(credentialRef, "credentialRef must not be null");

        int dotIndex = rawToken.indexOf('.');
        if (dotIndex <= 0 || dotIndex == rawToken.length() - 1 || rawToken.indexOf('.', dotIndex + 1) != -1) {
            throw new InvalidTokenException("Malformed guest pass token format");
        }

        String id = rawToken.substring(0, dotIndex);
        String secret = rawToken.substring(dotIndex + 1);

        GuestPass current = store.findById(id)
                .orElseThrow(() -> new GuestPassNotFoundException("Guest pass not found for id: " + id));

        if (current.isExpired(clock)) {
            throw new GuestPassExpiredException("Guest pass expired: " + id);
        }

        byte[] candidateHash = Crypto.sha256(secret.getBytes(StandardCharsets.US_ASCII));
        if (!MessageDigest.isEqual(candidateHash, current.secretHash())) {
            throw new InvalidTokenException("Invalid guest pass secret token");
        }

        GuestPass redeemed = current.redeem(clock, credentialRef);
        if (!store.compareAndSet(id, current.version(), redeemed)) {
            throw new ConcurrentMutationException("Concurrent redemption of guest pass: " + id);
        }

        appendLedger("GUEST_PASS_REDEEMED", id);
        listener.onRedeemed(redeemed);
        return redeemed;
    }

    public GuestPass markConsumed(String id) {
        Objects.requireNonNull(id, "id must not be null");

        GuestPass current = store.findById(id)
                .orElseThrow(() -> new GuestPassNotFoundException("Guest pass not found for id: " + id));

        GuestPass consumed = current.consume(clock);
        if (!store.compareAndSet(id, current.version(), consumed)) {
            throw new ConcurrentMutationException("Concurrent consumption of guest pass: " + id);
        }

        appendLedger("GUEST_PASS_CONSUMED", id);
        listener.onConsumed(consumed);
        return consumed;
    }

    public GuestPass burn(String id) {
        Objects.requireNonNull(id, "id must not be null");

        GuestPass current = store.findById(id)
                .orElseThrow(() -> new GuestPassNotFoundException("Guest pass not found for id: " + id));

        GuestPass burned = current.burn(clock);
        if (!store.compareAndSet(id, current.version(), burned)) {
            throw new ConcurrentMutationException("Concurrent burning of guest pass: " + id);
        }

        if (vault != null) {
            vault.requestRotation();
        }

        appendLedger("GUEST_PASS_BURNED", id);
        listener.onBurned(burned);
        return burned;
    }

    public GuestPass revoke(String id) {
        Objects.requireNonNull(id, "id must not be null");

        GuestPass current = store.findById(id)
                .orElseThrow(() -> new GuestPassNotFoundException("Guest pass not found for id: " + id));

        GuestPass revoked = current.revoke(clock);
        if (!store.compareAndSet(id, current.version(), revoked)) {
            throw new ConcurrentMutationException("Concurrent revocation of guest pass: " + id);
        }

        appendLedger("GUEST_PASS_REVOKED", id);
        listener.onRevoked(revoked);
        return revoked;
    }

    public GuestPass expire(String id) {
        Objects.requireNonNull(id, "id must not be null");

        GuestPass current = store.findById(id)
                .orElseThrow(() -> new GuestPassNotFoundException("Guest pass not found for id: " + id));

        GuestPass expired = current.expire(clock);
        if (!store.compareAndSet(id, current.version(), expired)) {
            throw new ConcurrentMutationException("Concurrent expiration of guest pass: " + id);
        }

        appendLedger("GUEST_PASS_EXPIRED", id);
        listener.onExpired(expired);
        return expired;
    }

    private String passRef(String id) {
        return Crypto.sha256Hex(id).substring(0, 16);
    }

    private void appendLedger(String type, String id) {
        if (ledger != null) {
            ledger.append(type, "pass_ref=" + passRef(id));
        }
    }
}
