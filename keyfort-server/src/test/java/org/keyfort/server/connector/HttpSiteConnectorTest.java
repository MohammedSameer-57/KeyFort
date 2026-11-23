package org.keyfort.server.connector;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keyfort.core.connector.HmacSigner;
import org.keyfort.core.connector.LoginEvent;
import org.keyfort.core.connector.SignedEvent;
import org.keyfort.core.connector.TicketHandle;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import com.sun.net.httpserver.HttpServer;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HttpSiteConnector Integration and Contract Tests")
class HttpSiteConnectorTest {

    private static final byte[] SECRET = "test-secret-for-http-connector-12345678".getBytes(StandardCharsets.UTF_8);
    private static final Instant T0 = Instant.parse("2026-11-22T14:00:00Z");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("HttpSiteConnector provisions and revokes tickets over HTTP")
    void testProvisionAndRevokeTicketOverHttp() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();

        server.createContext("/connector/tickets", exchange -> {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String resp = "{\"ticketId\":\"tkt-123\",\"redirectUrl\":\"http://localhost:" + port + "/login?ticket=tkt-123\"}";
                byte[] bytes = resp.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(201, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            } else if ("DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
            }
        });
        server.start();

        try {
            HttpSiteConnector connector = new HttpSiteConnector(
                    "http://localhost:" + port,
                    SECRET,
                    HttpClient.newHttpClient(),
                    objectMapper,
                    Clock.fixed(T0, ZoneOffset.UTC)
            );

            TicketHandle handle = connector.provisionTicket("entry-1", "read", Duration.ofMinutes(15));
            assertFalse(handle.assisted());
            assertEquals("tkt-123", handle.ticketId());
            assertEquals("http://localhost:" + port + "/login?ticket=tkt-123", handle.redirectUrl());

            assertDoesNotThrow(() -> connector.revokeTicket("tkt-123"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("HttpSiteConnector verifies incoming signed login webhook events")
    void testVerifySignedLoginEvent() throws Exception {
        HttpSiteConnector connector = new HttpSiteConnector(
                "http://localhost:9090",
                SECRET,
                HttpClient.newHttpClient(),
                objectMapper,
                Clock.fixed(T0, ZoneOffset.UTC)
        );

        long ts = T0.getEpochSecond();
        String nonce = "nonce-evt-1";
        Map<String, Object> payloadMap = Map.of(
                "ticketId", "tkt-abc",
                "entryId", "entry-99",
                "timestamp", T0.toString(),
                "scope", "read-only"
        );
        String payload = objectMapper.writeValueAsString(payloadMap);
        String signature = HmacSigner.sign(SECRET, "POST", "/api/webhooks/login", ts, nonce, payload);

        SignedEvent signedEvent = new SignedEvent("LOGIN_OBSERVED", payload, ts, nonce, signature);
        LoginEvent event = connector.verifyLoginEvent(signedEvent);

        assertEquals("tkt-abc", event.ticketId());
        assertEquals("entry-99", event.entryId());
        assertEquals("cred-tkt-abc", event.credentialRef());
        assertEquals("read-only", event.metadata().get("scope"));
    }

    @Test
    @DisplayName("HttpSiteConnector queries and terminates guest sessions over HTTP")
    void testQueryAndTerminateGuestSessionOverHttp() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();

        server.createContext("/connector/sessions/gs-123", exchange -> {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                String resp = "{\"sessionId\":\"gs-123\",\"status\":\"ACTIVE\",\"expiresAt\":\"2026-11-22T14:15:00Z\",\"remainingSeconds\":900}";
                byte[] bytes = resp.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            } else if ("DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
            }
        });
        server.start();

        try {
            HttpSiteConnector connector = new HttpSiteConnector(
                    "http://localhost:" + port,
                    SECRET,
                    HttpClient.newHttpClient(),
                    objectMapper,
                    Clock.fixed(T0, ZoneOffset.UTC)
            );

            org.keyfort.core.connector.GuestSessionInfo session = connector.getSession("gs-123");
            assertEquals("gs-123", session.sessionId());
            assertEquals("ACTIVE", session.status());
            assertEquals(900, session.remainingSeconds());

            assertDoesNotThrow(() -> connector.terminateSession("gs-123"));
        } finally {
            server.stop(0);
        }
    }
}
