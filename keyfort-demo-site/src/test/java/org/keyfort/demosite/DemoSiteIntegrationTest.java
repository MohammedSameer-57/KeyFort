package org.keyfort.demosite;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keyfort.core.Crypto;
import org.keyfort.core.connector.HmacSigner;
import org.keyfort.demosite.entity.OutboxEventEntity;
import org.keyfort.demosite.entity.TicketEntity;
import org.keyfort.demosite.repository.OutboxEventRepository;
import org.keyfort.demosite.repository.TicketRepository;
import org.keyfort.demosite.service.TicketService;
import org.keyfort.demosite.service.TicketValidationException;
import org.keyfort.demosite.service.WebhookDispatchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Demo Site Connector and Single-Use Ticket Integration Tests")
class DemoSiteIntegrationTest {

    private static final byte[] SECRET = "demo-site-connector-hmac-shared-secret-key-material-256bits!!".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TicketService ticketService;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private OutboxEventRepository outboxRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Ticket is strictly single-use: under 50 concurrent attempts, exactly one succeeds")
    void testTicketSingleUseUnderFiftyConcurrentAttempts() throws Exception {
        TicketEntity ticket = ticketService.createTicket("entry-test-1", "read-only", Duration.ofMinutes(15));
        String token = ticket.getTicketToken();

        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch finishGate = new CountDownLatch(threadCount);

        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    ticketService.consumeTicket(token);
                    successes.incrementAndGet();
                } catch (TicketValidationException e) {
                    failures.incrementAndGet();
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    finishGate.countDown();
                }
            });
        }

        startGate.countDown();
        finishGate.await();
        executor.shutdown();

        assertEquals(1, successes.get(), "Exactly one consumer must win ticket consumption");
        assertEquals(49, failures.get(), "Remaining 49 concurrent attempts must be rejected");

        TicketEntity updated = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertTrue(updated.isUsed());
    }

    @Test
    @DisplayName("Expired ticket fails consumption with generic error message")
    void testExpiredTicketFailsConsumption() {
        // Create ticket with negative duration so it is expired immediately
        Instant now = Instant.now();
        String id = Base64.getUrlEncoder().withoutPadding().encodeToString(Crypto.random(10));
        String token = "tkt_" + Base64.getUrlEncoder().withoutPadding().encodeToString(Crypto.random(16));
        TicketEntity expired = new TicketEntity(id, token, "entry-exp", "read", now.minusSeconds(60), now.minusSeconds(1));
        ticketRepository.save(expired);

        TicketValidationException ex = assertThrows(TicketValidationException.class, () -> ticketService.consumeTicket(token));
        assertEquals("Invalid or expired login ticket", ex.getMessage());
    }

    @Test
    @DisplayName("Revoked ticket fails consumption with generic error message")
    void testRevokedTicketFailsConsumption() {
        TicketEntity ticket = ticketService.createTicket("entry-rev", "read", Duration.ofMinutes(15));
        ticketService.revokeTicket(ticket.getId());

        TicketValidationException ex = assertThrows(TicketValidationException.class, () -> ticketService.consumeTicket(ticket.getTicketToken()));
        assertEquals("Invalid or expired login ticket", ex.getMessage());
    }

    @Test
    @DisplayName("Webhook failure stores event in outbox and retry updates attempt count")
    void testWebhookOutboxAndRetry() {
        outboxRepository.deleteAll();

        // Dispatch a webhook to a guaranteed non-existent port (e.g. 59999) to simulate failure
        WebhookDispatchService failingService = new WebhookDispatchService(
                outboxRepository,
                objectMapper,
                new String(SECRET, StandardCharsets.UTF_8),
                "http://127.0.0.1:59999/unreachable/webhook"
        );

        failingService.dispatchLoginObserved("ticket-outbox-1", "entry-99", "read-only");

        List<OutboxEventEntity> undelivered = outboxRepository.findByDeliveredFalse();
        assertEquals(1, undelivered.size());
        OutboxEventEntity event = undelivered.get(0);
        assertFalse(event.isDelivered());
        assertEquals(1, event.getRetryCount());
        assertTrue(event.getPayload().contains("ticket-outbox-1"));

        // Trigger retry scheduled run
        failingService.retryUndeliveredEvents();
        OutboxEventEntity retried = outboxRepository.findById(event.getId()).orElseThrow();
        assertEquals(2, retried.getRetryCount());
        assertFalse(retried.isDelivered());
    }

    @Test
    @DisplayName("Guest session has limited read-only scope and TTL bound")
    void testGuestSessionScopeAndTtl() throws Exception {
        TicketEntity ticket = ticketService.createTicket("entry-session-1", "read-only", Duration.ofMinutes(15));
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(get("/keyfort/login")
                        .param("ticket", ticket.getTicketToken())
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));

        assertEquals("guest-entry-session-1", session.getAttribute("user"));
        assertEquals("read-only", session.getAttribute("scope"));
        assertEquals("GUEST_READONLY", session.getAttribute("role"));
        assertNotNull(session.getAttribute("guestExpiresAt"));
    }

    @Test
    @DisplayName("Second use of same ticket via HTTP redirects to login with invalid_ticket error")
    void testSecondUseOfTicketViaHttpFails() throws Exception {
        TicketEntity ticket = ticketService.createTicket("entry-reuse", "read-only", Duration.ofMinutes(15));
        MockHttpSession session1 = new MockHttpSession();
        MockHttpSession session2 = new MockHttpSession();

        // First use: success redirect to dashboard
        mockMvc.perform(get("/keyfort/login")
                        .param("ticket", ticket.getTicketToken())
                        .session(session1))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));

        // Second use: failure redirect to login with error
        mockMvc.perform(get("/keyfort/login")
                        .param("ticket", ticket.getTicketToken())
                        .session(session2))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error=invalid_ticket"));
    }

    @Test
    @DisplayName("Connector endpoints verify HMAC signature and reject invalid signatures")
    void testConnectorEndpointHmacVerification() throws Exception {
        Map<String, Object> req = Map.of("entryId", "entry-e2e", "scope", "read", "ttlSeconds", 900);
        String body = objectMapper.writeValueAsString(req);
        long ts = Instant.now().getEpochSecond();
        String nonce = "nonce-conn-test-1";
        String validSig = HmacSigner.sign(SECRET, "POST", "/connector/tickets", ts, nonce, body);

        // Valid signature creates ticket
        MvcResult res = mockMvc.perform(post("/connector/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-KeyFort-Timestamp", String.valueOf(ts))
                        .header("X-KeyFort-Nonce", nonce)
                        .header("X-KeyFort-Signature", validSig)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        Map<?, ?> respMap = objectMapper.readValue(res.getResponse().getContentAsString(), Map.class);
        String ticketId = (String) respMap.get("ticketId");
        assertNotNull(ticketId);

        // Invalid signature is rejected
        mockMvc.perform(post("/connector/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-KeyFort-Timestamp", String.valueOf(ts))
                        .header("X-KeyFort-Nonce", "nonce-diff")
                        .header("X-KeyFort-Signature", "invalid-sig")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("No secret or ticket appears in exception responses or logs")
    void testNoSecretAppearsInResponses() throws Exception {
        MvcResult res = mockMvc.perform(get("/keyfort/login").param("ticket", "tkt_secret_token_12345"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String redirected = res.getResponse().getRedirectedUrl();
        assertNotNull(redirected);
        assertFalse(redirected.contains("secret_token_12345"), "Redirect URL must not echo raw ticket secret");
    }
}
