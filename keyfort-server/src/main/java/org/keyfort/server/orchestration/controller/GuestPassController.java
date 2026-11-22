package org.keyfort.server.orchestration.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.keyfort.core.connector.SignatureVerificationException;
import org.keyfort.core.connector.SignedRequest;
import org.keyfort.core.guestpass.IssuedToken;
import org.keyfort.server.orchestration.dto.CreateGuestPassRequest;
import org.keyfort.server.orchestration.dto.CreateGuestPassResponse;
import org.keyfort.server.orchestration.dto.RedeemGuestPassRequest;
import org.keyfort.server.orchestration.dto.RedeemGuestPassResponse;
import org.keyfort.server.orchestration.service.GuestPassOrchestrationService;
import org.keyfort.server.orchestration.service.RateLimitExceededException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping
public class GuestPassController {

    private final GuestPassOrchestrationService orchestrationService;

    public GuestPassController(GuestPassOrchestrationService orchestrationService) {
        this.orchestrationService = orchestrationService;
    }

    @PostMapping("/api/guest-passes")
    public ResponseEntity<CreateGuestPassResponse> createGuestPass(
            @RequestBody CreateGuestPassRequest request,
            Authentication authentication
    ) {
        String userId = authentication.getName();
        Duration ttl = Duration.ofSeconds(request.ttlSeconds() > 0 ? request.ttlSeconds() : 900);
        IssuedToken issued = orchestrationService.create(userId, request.entryId(), request.scope(), ttl);

        return ResponseEntity.status(HttpStatus.CREATED).body(new CreateGuestPassResponse(
                issued.id(),
                issued.rawToken(),
                issued.pass().state(),
                issued.pass().createdAt(),
                issued.pass().expiresAt()
        ));
    }

    @PostMapping("/api/guest-passes/redeem")
    public ResponseEntity<RedeemGuestPassResponse> redeemGuestPass(
            @RequestBody RedeemGuestPassRequest request,
            HttpServletRequest servletRequest
    ) {
        String clientIp = servletRequest.getHeader("X-Forwarded-For");
        if (clientIp == null || clientIp.isBlank()) {
            clientIp = servletRequest.getRemoteAddr();
        }

        String redirectUrl = orchestrationService.redeem(clientIp, request.token());
        return ResponseEntity.ok(new RedeemGuestPassResponse(redirectUrl));
    }

    @PostMapping("/api/connector/events")
    public ResponseEntity<Map<String, Object>> handleConnectorEvent(
            @RequestHeader("X-KeyFort-Timestamp") long timestamp,
            @RequestHeader("X-KeyFort-Nonce") String nonce,
            @RequestHeader("X-KeyFort-Signature") String signature,
            @RequestBody String body
    ) {
        SignedRequest signedRequest = new SignedRequest(
                "POST",
                "/api/connector/events",
                timestamp,
                nonce,
                body,
                signature
        );

        Map<String, Object> result = orchestrationService.handleWebhookEvent(signedRequest);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/api/guest-passes/{id}")
    public ResponseEntity<Map<String, String>> revokeGuestPass(
            @PathVariable("id") String id,
            Authentication authentication
    ) {
        String userId = authentication.getName();
        orchestrationService.revoke(userId, id);
        return ResponseEntity.ok(Map.of("status", "REVOKED"));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Map<String, String>> handleRateLimit(RateLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("error", "Rate limit exceeded"));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, String>> handleInvalidPass(Exception ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Invalid or expired guest pass"));
    }

    @ExceptionHandler(SignatureVerificationException.class)
    public ResponseEntity<Map<String, String>> handleInvalidSignature(SignatureVerificationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Invalid signature"));
    }
}
