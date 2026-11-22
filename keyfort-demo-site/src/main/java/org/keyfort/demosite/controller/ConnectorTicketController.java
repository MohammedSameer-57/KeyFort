package org.keyfort.demosite.controller;

import org.keyfort.core.connector.HmacSigner;
import org.keyfort.core.connector.SignedRequest;
import org.keyfort.demosite.entity.TicketEntity;
import org.keyfort.demosite.service.TicketService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/connector/tickets")
public class ConnectorTicketController {

    private final TicketService ticketService;
    private final HmacSigner hmacSigner;
    private final byte[] secret;

    public ConnectorTicketController(
            TicketService ticketService,
            @Value("${keyfort.connector.secret:demo-site-connector-hmac-shared-secret-key-material-256bits!!}") String secretStr
    ) {
        this.ticketService = ticketService;
        this.hmacSigner = new HmacSigner(Clock.systemUTC());
        this.secret = secretStr.getBytes(StandardCharsets.UTF_8);
    }

    public record CreateTicketRequest(String entryId, String scope, long ttlSeconds) {}
    public record CreateTicketResponse(String ticketId, String redirectUrl) {}

    @PostMapping
    public ResponseEntity<CreateTicketResponse> createTicket(
            @RequestHeader(value = "X-KeyFort-Timestamp", required = false, defaultValue = "0") long timestamp,
            @RequestHeader(value = "X-KeyFort-Nonce", required = false, defaultValue = "") String nonce,
            @RequestHeader(value = "X-KeyFort-Signature", required = false, defaultValue = "") String signature,
            @RequestBody String rawBody
    ) {
        SignedRequest signedReq = new SignedRequest("POST", "/connector/tickets", timestamp, nonce, rawBody, signature);
        hmacSigner.verify(secret, signedReq);

        CreateTicketRequest req;
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            req = mapper.readValue(rawBody, CreateTicketRequest.class);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }

        Duration ttl = Duration.ofSeconds(req.ttlSeconds() > 0 ? req.ttlSeconds() : 900);
        TicketEntity ticket = ticketService.createTicket(req.entryId(), req.scope(), ttl);

        String redirectUrl = "http://localhost:9090/keyfort/login?ticket=" + ticket.getTicketToken();
        return ResponseEntity.status(HttpStatus.CREATED).body(new CreateTicketResponse(ticket.getId(), redirectUrl));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revokeTicket(
            @PathVariable("id") String id,
            @RequestHeader(value = "X-KeyFort-Timestamp", required = false, defaultValue = "0") long timestamp,
            @RequestHeader(value = "X-KeyFort-Nonce", required = false, defaultValue = "") String nonce,
            @RequestHeader(value = "X-KeyFort-Signature", required = false, defaultValue = "") String signature
    ) {
        SignedRequest signedReq = new SignedRequest("DELETE", "/connector/tickets/" + id, timestamp, nonce, "", signature);
        hmacSigner.verify(secret, signedReq);

        ticketService.revokeTicket(id);
        return ResponseEntity.noContent().build();
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(org.keyfort.core.connector.SignatureVerificationException.class)
    public ResponseEntity<String> handleSignatureError(org.keyfort.core.connector.SignatureVerificationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("HMAC verification failed");
    }
}
