package org.keyfort.demosite.service;

import org.keyfort.core.Crypto;
import org.keyfort.demosite.entity.GuestSessionEntity;
import org.keyfort.demosite.entity.SessionStatus;
import org.keyfort.demosite.repository.GuestSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

@Service
public class GuestSessionService {

    private final GuestSessionRepository repository;
    private final Clock clock;
    private final Duration hardTtl;
    private final Duration idleTimeout;

    @Autowired
    public GuestSessionService(
            GuestSessionRepository repository,
            @Value("${keyfort.demo-site.session.hard-ttl-minutes:10}") long hardTtlMinutes,
            @Value("${keyfort.demo-site.session.idle-timeout-minutes:5}") long idleTimeoutMinutes
    ) {
        this(repository, Clock.systemUTC(), Duration.ofMinutes(hardTtlMinutes), Duration.ofMinutes(idleTimeoutMinutes));
    }

    public GuestSessionService(
            GuestSessionRepository repository,
            Clock clock,
            Duration hardTtl,
            Duration idleTimeout
    ) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.hardTtl = Objects.requireNonNull(hardTtl, "hardTtl must not be null");
        this.idleTimeout = Objects.requireNonNull(idleTimeout, "idleTimeout must not be null");
    }

    public Duration getHardTtl() {
        return hardTtl;
    }

    public Duration getIdleTimeout() {
        return idleTimeout;
    }

    @Transactional
    public GuestSessionEntity createSession(String ticketId, String account, String scope, String userAgent, String deviceCookie) {
        Objects.requireNonNull(ticketId, "ticketId must not be null");
        Objects.requireNonNull(account, "account must not be null");
        Objects.requireNonNull(scope, "scope must not be null");

        String id = "gs_" + Base64.getUrlEncoder().withoutPadding().encodeToString(Crypto.random(16));
        Instant now = clock.instant();
        Instant expiresAt = now.plus(hardTtl);

        String deviceBindingHash = computeDeviceBindingHash(userAgent, deviceCookie);

        GuestSessionEntity session = new GuestSessionEntity(
                id,
                ticketId,
                account,
                scope,
                now,
                expiresAt,
                now,
                SessionStatus.ACTIVE,
                deviceBindingHash
        );

        return repository.save(session);
    }

    @Transactional(noRollbackFor = SessionInvalidException.class)
    public GuestSessionEntity validateAndTouchSession(String sessionId, String userAgent, String deviceCookie) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");

        GuestSessionEntity session = repository.findById(sessionId)
                .orElseThrow(() -> new SessionInvalidException("Session not found"));

        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new SessionInvalidException("Session is not active");
        }

        Instant now = clock.instant();

        // Check hard TTL
        if (!now.isBefore(session.getExpiresAt())) {
            session.setStatus(SessionStatus.ENDED);
            repository.saveAndFlush(session);
            throw new SessionInvalidException("Session expired (hard TTL)");
        }

        // Check idle timeout
        if (Duration.between(session.getLastAccessAt(), now).compareTo(idleTimeout) > 0) {
            session.setStatus(SessionStatus.ENDED);
            repository.saveAndFlush(session);
            throw new SessionInvalidException("Session expired (idle timeout)");
        }

        // Check device binding if enabled
        if (session.getDeviceBindingHash() != null) {
            String currentHash = computeDeviceBindingHash(userAgent, deviceCookie);
            if (currentHash == null || !session.getDeviceBindingHash().equals(currentHash)) {
                throw new SessionInvalidException("Device binding mismatch");
            }
        }

        // Slide idle timeout on access
        session.setLastAccessAt(now);
        return repository.save(session);
    }

    @Transactional
    public void terminateSession(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        GuestSessionEntity session = repository.findById(sessionId)
                .orElseThrow(() -> new SessionInvalidException("Session not found: " + sessionId));
        session.setStatus(SessionStatus.ENDED);
        repository.save(session);
    }

    @Transactional(readOnly = true)
    public Optional<GuestSessionEntity> getSession(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        return repository.findById(sessionId);
    }

    public static String computeDeviceBindingHash(String userAgent, String deviceCookie) {
        if (userAgent == null || deviceCookie == null) {
            return null;
        }
        return Crypto.toHex(Crypto.sha256((userAgent + ":" + deviceCookie).getBytes(StandardCharsets.UTF_8)));
    }
}
