package org.keyfort.demosite.controller;

import org.keyfort.core.connector.HmacSigner;
import org.keyfort.core.connector.SignedRequest;
import org.keyfort.demosite.entity.GuestSessionEntity;
import org.keyfort.demosite.service.GuestSessionService;
import org.keyfort.demosite.service.SessionInvalidException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/connector/sessions")
public class ConnectorSessionController {

    private final GuestSessionService sessionService;
    private final HmacSigner hmacSigner;
    private final byte[] secret;
    private final Clock clock;

    public ConnectorSessionController(
            GuestSessionService sessionService,
            @Value("${keyfort.connector.secret:demo-site-connector-hmac-shared-secret-key-material-256bits!!}") String secretStr
    ) {
        this.sessionService = sessionService;
        this.clock = Clock.systemUTC();
        this.hmacSigner = new HmacSigner(clock);
        this.secret = secretStr.getBytes(StandardCharsets.UTF_8);
    }

    public record SessionInfoResponse(
            String sessionId,
            String status,
            String expiresAt,
            long remainingSeconds
    ) {}

    @GetMapping("/{id}")
    public ResponseEntity<SessionInfoResponse> getSession(
            @PathVariable("id") String id,
            @RequestHeader(value = "X-KeyFort-Timestamp", required = false, defaultValue = "0") long timestamp,
            @RequestHeader(value = "X-KeyFort-Nonce", required = false, defaultValue = "") String nonce,
            @RequestHeader(value = "X-KeyFort-Signature", required = false, defaultValue = "") String signature
    ) {
        SignedRequest signedReq = new SignedRequest("GET", "/connector/sessions/" + id, timestamp, nonce, "", signature);
        hmacSigner.verify(secret, signedReq);

        return sessionService.getSession(id)
                .map(session -> {
                    long remaining = Math.max(0, Duration.between(clock.instant(), session.getExpiresAt()).toSeconds());
                    return ResponseEntity.ok(new SessionInfoResponse(
                            session.getId(),
                            session.getStatus().name(),
                            session.getExpiresAt().toString(),
                            remaining
                    ));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> terminateSession(
            @PathVariable("id") String id,
            @RequestHeader(value = "X-KeyFort-Timestamp", required = false, defaultValue = "0") long timestamp,
            @RequestHeader(value = "X-KeyFort-Nonce", required = false, defaultValue = "") String nonce,
            @RequestHeader(value = "X-KeyFort-Signature", required = false, defaultValue = "") String signature
    ) {
        SignedRequest signedReq = new SignedRequest("DELETE", "/connector/sessions/" + id, timestamp, nonce, "", signature);
        hmacSigner.verify(secret, signedReq);

        try {
            sessionService.terminateSession(id);
            return ResponseEntity.noContent().build();
        } catch (SessionInvalidException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(org.keyfort.core.connector.SignatureVerificationException.class)
    public ResponseEntity<String> handleSignatureError(org.keyfort.core.connector.SignatureVerificationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("HMAC verification failed");
    }
}
