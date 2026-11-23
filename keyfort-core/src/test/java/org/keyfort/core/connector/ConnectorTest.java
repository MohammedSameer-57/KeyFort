package org.keyfort.core.connector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Core SiteConnector and HMAC Canonical Signing Tests")
class ConnectorTest {

    private static final byte[] SECRET = "secret-key-material-for-connector-12345678".getBytes(StandardCharsets.UTF_8);
    private static final Instant T0 = Instant.parse("2026-11-22T12:00:00Z");

    private MutableClock clock;
    private HmacSigner signer;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(T0);
        signer = new HmacSigner(clock);
    }

    @Test
    @DisplayName("Valid HMAC-SHA256 signature verifies successfully")
    void testValidSignatureVerifies() {
        long ts = clock.instant().getEpochSecond();
        String nonce = "nonce-abc-123";
        String body = "{\"ticketId\":\"t-100\"}";
        String sig = HmacSigner.sign(SECRET, "POST", "/connector/tickets", ts, nonce, body);

        SignedRequest req = new SignedRequest("POST", "/connector/tickets", ts, nonce, body, sig);
        assertDoesNotThrow(() -> signer.verify(SECRET, req));
    }

    @Test
    @DisplayName("Tampered signature is rejected with SignatureVerificationException")
    void testTamperedSignatureRejected() {
        long ts = clock.instant().getEpochSecond();
        String nonce = "nonce-abc-123";
        String body = "{\"ticketId\":\"t-100\"}";
        String sig = HmacSigner.sign(SECRET, "POST", "/connector/tickets", ts, nonce, body);
        String tamperedSig = sig.substring(0, sig.length() - 2) + "00";

        SignedRequest req = new SignedRequest("POST", "/connector/tickets", ts, nonce, body, tamperedSig);
        assertThrows(SignatureVerificationException.class, () -> signer.verify(SECRET, req));
    }

    @Test
    @DisplayName("Tampered body is rejected with SignatureVerificationException")
    void testTamperedBodyRejected() {
        long ts = clock.instant().getEpochSecond();
        String nonce = "nonce-abc-123";
        String body = "{\"ticketId\":\"t-100\"}";
        String sig = HmacSigner.sign(SECRET, "POST", "/connector/tickets", ts, nonce, body);

        SignedRequest req = new SignedRequest("POST", "/connector/tickets", ts, nonce, "{\"ticketId\":\"t-TAMPERED\"}", sig);
        assertThrows(SignatureVerificationException.class, () -> signer.verify(SECRET, req));
    }

    @Test
    @DisplayName("Timestamp exceeding 5-minute clock skew is rejected")
    void testClockSkewExceededRejected() {
        long pastTs = clock.instant().minus(Duration.ofMinutes(6)).getEpochSecond();
        String nonce = "nonce-past-1";
        String body = "";
        String sig = HmacSigner.sign(SECRET, "DELETE", "/connector/tickets/1", pastTs, nonce, body);

        SignedRequest req = new SignedRequest("DELETE", "/connector/tickets/1", pastTs, nonce, body, sig);
        assertThrows(SignatureVerificationException.class, () -> signer.verify(SECRET, req));

        long futureTs = clock.instant().plus(Duration.ofMinutes(6)).getEpochSecond();
        String sigFuture = HmacSigner.sign(SECRET, "DELETE", "/connector/tickets/1", futureTs, "nonce-fut-1", body);
        SignedRequest reqFuture = new SignedRequest("DELETE", "/connector/tickets/1", futureTs, "nonce-fut-1", body, sigFuture);
        assertThrows(SignatureVerificationException.class, () -> signer.verify(SECRET, reqFuture));
    }

    @Test
    @DisplayName("Replayed nonce is rejected with SignatureVerificationException")
    void testReplayedNonceRejected() {
        long ts = clock.instant().getEpochSecond();
        String nonce = "nonce-unique-999";
        String body = "{\"test\":true}";
        String sig = HmacSigner.sign(SECRET, "POST", "/events", ts, nonce, body);

        SignedRequest req = new SignedRequest("POST", "/events", ts, nonce, body, sig);
        signer.verify(SECRET, req);

        // Second verification with identical nonce must fail
        assertThrows(SignatureVerificationException.class, () -> signer.verify(SECRET, req));
    }

    @Test
    @DisplayName("AssistedConnector returns assisted TicketHandle and rejects webhook events")
    void testAssistedConnectorBehavior() {
        AssistedConnector connector = new AssistedConnector("example.com");
        TicketHandle handle = connector.provisionTicket("entry-42", "read", Duration.ofMinutes(15));

        assertTrue(handle.assisted());
        assertNull(handle.ticketId());
        assertNull(handle.redirectUrl());
        assertTrue(handle.instruction().contains("example.com"));

        assertDoesNotThrow(() -> connector.revokeTicket("ignored-id"));

        SignedEvent event = new SignedEvent("LOGIN_OBSERVED", "payload", clock.instant().getEpochSecond(), "nonce", "sig");
        assertThrows(ConnectorException.class, () -> connector.verifyLoginEvent(event));
    }

    @Test
    @DisplayName("GuestSessionInfo validates mandatory fields")
    void testGuestSessionInfoValidation() {
        assertThrows(NullPointerException.class, () -> new GuestSessionInfo(null, "ACTIVE", Instant.now(), 300));
        assertThrows(NullPointerException.class, () -> new GuestSessionInfo("gs-1", null, Instant.now(), 300));
        assertThrows(NullPointerException.class, () -> new GuestSessionInfo("gs-1", "ACTIVE", null, 300));

        GuestSessionInfo info = new GuestSessionInfo("gs-1", "ACTIVE", Instant.EPOCH.plusSeconds(300), 300);
        assertEquals("gs-1", info.sessionId());
        assertEquals("ACTIVE", info.status());
        assertEquals(300, info.remainingSeconds());
    }

    @Test
    @DisplayName("AssistedConnector rejects session query and ignores session termination")
    void testAssistedConnectorSessionMethods() {
        AssistedConnector connector = new AssistedConnector("example.com");
        assertThrows(ConnectorException.class, () -> connector.getSession("gs-1"));
        assertDoesNotThrow(() -> connector.terminateSession("gs-1"));
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
