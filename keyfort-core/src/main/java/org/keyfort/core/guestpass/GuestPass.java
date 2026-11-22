package org.keyfort.core.guestpass;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable guest pass representing a single-use credential brokered for a target service.
 *
 * Meaning of states: ISSUED = token handed to the owner; REDEEMED = friend presented a valid
 * token and a guest credential was brokered; CONSUMED = the site confirmed exactly one login;
 * BURNED = the guest credential is revoked at the site and rotation was requested.
 */
public record GuestPass(
        String id,
        String entryId,
        String scope,
        byte[] secretHash,
        GuestPassState state,
        Instant createdAt,
        Instant expiresAt,
        long version,
        String credentialRef
) {
    public static final Duration MIN_TTL = Duration.ofMinutes(1);
    public static final Duration MAX_TTL = Duration.ofHours(24);

    private static final Map<GuestPassState, Set<GuestPassState>> ALLOWED_TRANSITIONS = Map.of(
            GuestPassState.CREATED, Set.of(GuestPassState.ISSUED, GuestPassState.EXPIRED, GuestPassState.REVOKED),
            GuestPassState.ISSUED, Set.of(GuestPassState.REDEEMED, GuestPassState.EXPIRED, GuestPassState.REVOKED),
            GuestPassState.REDEEMED, Set.of(GuestPassState.CONSUMED, GuestPassState.EXPIRED, GuestPassState.REVOKED),
            GuestPassState.CONSUMED, Set.of(GuestPassState.BURNED, GuestPassState.EXPIRED, GuestPassState.REVOKED),
            GuestPassState.BURNED, Set.of(),
            GuestPassState.EXPIRED, Set.of(),
            GuestPassState.REVOKED, Set.of()
    );

    public GuestPass {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(entryId, "entryId must not be null");
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(secretHash, "secretHash must not be null");
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");

        if (secretHash.length != 32) {
            throw new IllegalArgumentException("secretHash must be a 32-byte SHA-256 digest");
        }
        Duration ttl = Duration.between(createdAt, expiresAt);
        if (ttl.compareTo(MIN_TTL) < 0 || ttl.compareTo(MAX_TTL) > 0) {
            throw new IllegalArgumentException("TTL must be between 1 minute and 24 hours: " + ttl);
        }

        secretHash = secretHash.clone();
    }

    @Override
    public byte[] secretHash() {
        return secretHash.clone();
    }

    public boolean isExpired(Clock clock) {
        Objects.requireNonNull(clock, "clock must not be null");
        return !clock.instant().isBefore(expiresAt);
    }

    private void validateTransition(GuestPassState target, Clock clock) {
        Objects.requireNonNull(target, "target state must not be null");
        Objects.requireNonNull(clock, "clock must not be null");

        if (state.isTerminal()) {
            throw new IllegalTransitionException("Cannot transition from terminal state " + state + " to " + target);
        }

        if (target != GuestPassState.EXPIRED && isExpired(clock)) {
            throw new GuestPassExpiredException("Guest pass " + id + " expired at " + expiresAt);
        }

        Set<GuestPassState> allowed = ALLOWED_TRANSITIONS.get(state);
        if (allowed == null || !allowed.contains(target)) {
            throw new IllegalTransitionException("Illegal transition from " + state + " to " + target);
        }
    }

    public GuestPass issue(Clock clock) {
        validateTransition(GuestPassState.ISSUED, clock);
        return new GuestPass(id, entryId, scope, secretHash, GuestPassState.ISSUED, createdAt, expiresAt, version + 1, credentialRef);
    }

    public GuestPass redeem(Clock clock, String credentialRef) {
        validateTransition(GuestPassState.REDEEMED, clock);
        Objects.requireNonNull(credentialRef, "credentialRef must not be null");
        return new GuestPass(id, entryId, scope, secretHash, GuestPassState.REDEEMED, createdAt, expiresAt, version + 1, credentialRef);
    }

    public GuestPass consume(Clock clock) {
        validateTransition(GuestPassState.CONSUMED, clock);
        return new GuestPass(id, entryId, scope, secretHash, GuestPassState.CONSUMED, createdAt, expiresAt, version + 1, credentialRef);
    }

    public GuestPass burn(Clock clock) {
        validateTransition(GuestPassState.BURNED, clock);
        return new GuestPass(id, entryId, scope, secretHash, GuestPassState.BURNED, createdAt, expiresAt, version + 1, credentialRef);
    }

    public GuestPass revoke(Clock clock) {
        validateTransition(GuestPassState.REVOKED, clock);
        return new GuestPass(id, entryId, scope, secretHash, GuestPassState.REVOKED, createdAt, expiresAt, version + 1, credentialRef);
    }

    public GuestPass expire(Clock clock) {
        validateTransition(GuestPassState.EXPIRED, clock);
        return new GuestPass(id, entryId, scope, secretHash, GuestPassState.EXPIRED, createdAt, expiresAt, version + 1, credentialRef);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GuestPass that)) return false;
        return version == that.version
                && id.equals(that.id)
                && entryId.equals(that.entryId)
                && scope.equals(that.scope)
                && Arrays.equals(secretHash, that.secretHash)
                && state == that.state
                && createdAt.equals(that.createdAt)
                && expiresAt.equals(that.expiresAt)
                && Objects.equals(credentialRef, that.credentialRef);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(id, entryId, scope, state, createdAt, expiresAt, version, credentialRef);
        result = 31 * result + Arrays.hashCode(secretHash);
        return result;
    }
}
