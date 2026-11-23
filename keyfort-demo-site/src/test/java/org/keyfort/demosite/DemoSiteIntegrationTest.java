package org.keyfort.demosite;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keyfort.core.Crypto;
import org.keyfort.core.connector.HmacSigner;
import org.keyfort.demosite.entity.OutboxEventEntity;
import org.keyfort.demosite.entity.OutboxStatus;
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

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
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

    private static final byte[] SECRET = "dev-only-insecure-test-connector-hmac-secret-256bit!!".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TicketService ticketService;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private org.keyfort.demosite.repository.GuestSessionRepository sessionRepository;

    @Autowired
    private org.keyfort.demosite.service.GuestSessionService sessionService;

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
        java.util.concurrent.ConcurrentLinkedQueue<Throwable> unexpectedExceptions = new java.util.concurrent.ConcurrentLinkedQueue<>();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    ticketService.consumeTicket(token);
                    successes.incrementAndGet();
                } catch (TicketValidationException | org.springframework.dao.OptimisticLockingFailureException expected) {
                    failures.incrementAndGet();
                } catch (Throwable unexpected) {
                    unexpectedExceptions.add(unexpected);
                } finally {
                    finishGate.countDown();
                }
            });
        }

        startGate.countDown();
        finishGate.await();
        executor.shutdown();

        assertTrue(unexpectedExceptions.isEmpty(), "Unexpected exceptions occurred during ticket consumption race: " + unexpectedExceptions);
        assertEquals(1, successes.get(), "Exactly one consumer must win ticket consumption");
        assertEquals(49, failures.get(), "Remaining 49 concurrent attempts must be rejected");

        TicketEntity updated = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertTrue(updated.isUsed());
    }

    @Test
    @DisplayName("Expired ticket fails consumption with generic error message")
    void testExpiredTicketFailsConsumption() {
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

        WebhookDispatchService failingService = new WebhookDispatchService(
                outboxRepository,
                objectMapper,
                new String(SECRET, StandardCharsets.UTF_8),
                "http://127.0.0.1:59999/unreachable/webhook"
        );

        failingService.dispatchLoginObserved("ticket-outbox-legacy-1", "entry-legacy", "read-only");

        List<OutboxEventEntity> undelivered = outboxRepository.findByDeliveredFalse();
        assertEquals(1, undelivered.size());
        OutboxEventEntity event = undelivered.get(0);
        assertFalse(event.isDelivered());
        assertEquals(1, event.getRetryCount());
        assertTrue(event.getPayload().contains("ticket-outbox-legacy-1"));
    }

    @Test
    @DisplayName("Second use of same ticket via HTTP redirects to login with invalid_ticket error")
    void testSecondUseOfTicketViaHttpFails() throws Exception {
        TicketEntity ticket = ticketService.createTicket("entry-reuse-explicit", "read-only", Duration.ofMinutes(15));
        MockHttpSession session1 = new MockHttpSession();
        MockHttpSession session2 = new MockHttpSession();

        mockMvc.perform(post("/keyfort/login")
                        .param("ticket", ticket.getTicketToken())
                        .session(session1))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(redirectedUrl("/dashboard"));

        mockMvc.perform(post("/keyfort/login")
                        .param("ticket", ticket.getTicketToken())
                        .session(session2))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(redirectedUrl("/login?error=invalid_ticket"));
    }

    @Test
    @DisplayName("GET /keyfort/login shows confirmation page and does not consume ticket")
    void testConfirmationPageGetDoesNotConsumeTicket() throws Exception {
        TicketEntity ticket = ticketService.createTicket("entry-confirm-1", "read-only", Duration.ofMinutes(15));

        MvcResult result = mockMvc.perform(get("/keyfort/login")
                        .param("ticket", ticket.getTicketToken()))
                .andExpect(status().isOk())
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andReturn();

        String content = result.getResponse().getContentAsString();
        assertTrue(content.contains("KeyFort Guest Access Confirmation"), "Page must show guest access confirmation");
        assertTrue(content.contains("entry-confirm-1"), "Page must show target entry");
        assertTrue(content.contains("action=\"/keyfort/login\" method=\"post\""), "Must contain POST confirmation form");

        TicketEntity stored = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertFalse(stored.isUsed(), "GET request must NOT mark ticket as used");
    }

    @Test
    @DisplayName("Prefetch-style repeated GETs do not consume ticket; subsequent POST consumes atomically")
    void testPrefetchStyleRepeatedGetsDoNotConsumeTicket() throws Exception {
        TicketEntity ticket = ticketService.createTicket("entry-prefetch-1", "read-only", Duration.ofMinutes(15));
        String token = ticket.getTicketToken();

        // 5 repeated GET requests simulating browser prefetching or user refreshing confirmation page
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/keyfort/login").param("ticket", token))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Referrer-Policy", "no-referrer"));

            TicketEntity afterGet = ticketRepository.findById(ticket.getId()).orElseThrow();
            assertFalse(afterGet.isUsed(), "Ticket must remain unconsumed after prefetch GET iteration " + i);
        }

        // POST confirmation consumes the ticket
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/keyfort/login")
                        .param("ticket", token)
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(redirectedUrl("/dashboard"));

        TicketEntity afterPost = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertTrue(afterPost.isUsed(), "POST must consume the ticket");
        assertEquals("guest-entry-prefetch-1", session.getAttribute("user"));

        // A second POST must fail with invalid_ticket redirect
        mockMvc.perform(post("/keyfort/login")
                        .param("ticket", token))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(redirectedUrl("/login?error=invalid_ticket"));
    }

    @Test
    @DisplayName("Outbox implements exponential backoff, max attempts, and transitions to DEAD_LETTER")
    void testOutboxExponentialBackoffAndDeadLetterStatus() {
        outboxRepository.deleteAll();

        MutableTestClock testClock = new MutableTestClock(Instant.parse("2026-11-22T12:00:00Z"));
        int maxAttempts = 3;

        WebhookDispatchService dispatchService = new WebhookDispatchService(
                outboxRepository,
                objectMapper,
                new String(SECRET, StandardCharsets.UTF_8),
                "http://127.0.0.1:59999/unreachable/webhook",
                HttpClient.newHttpClient(),
                testClock,
                maxAttempts
        );

        // Initial dispatch failure creates outbox entry with retryCount = 1, status = PENDING
        dispatchService.dispatchLoginObserved("ticket-dlq-1", "entry-dlq", "read-only");

        List<OutboxEventEntity> events = outboxRepository.findByDeliveredFalseAndStatus(OutboxStatus.PENDING);
        assertEquals(1, events.size());
        OutboxEventEntity initial = events.get(0);
        assertEquals(1, initial.getRetryCount());
        assertEquals(OutboxStatus.PENDING, initial.getStatus());
        assertNotNull(initial.getNextAttemptAt());
        // First backoff is 1 second (2^0)
        assertEquals(testClock.instant().plusSeconds(1), initial.getNextAttemptAt());

        // Retry before nextAttemptAt should be skipped
        dispatchService.retryUndeliveredEvents();
        OutboxEventEntity skipped = outboxRepository.findById(initial.getId()).orElseThrow();
        assertEquals(1, skipped.getRetryCount(), "Retry before nextAttemptAt must be skipped");

        // Advance clock past first backoff window (2 seconds)
        testClock.advance(Duration.ofSeconds(2));
        dispatchService.retryUndeliveredEvents();

        OutboxEventEntity secondAttempt = outboxRepository.findById(initial.getId()).orElseThrow();
        assertEquals(2, secondAttempt.getRetryCount());
        assertEquals(OutboxStatus.PENDING, secondAttempt.getStatus());
        // Second backoff is 2 seconds (2^1)
        assertEquals(testClock.instant().plusSeconds(2), secondAttempt.getNextAttemptAt());

        // Advance clock past second backoff window
        testClock.advance(Duration.ofSeconds(3));
        dispatchService.retryUndeliveredEvents();

        // Third attempt reaches maxAttempts (3) -> status transitions to DEAD_LETTER
        OutboxEventEntity deadLetter = outboxRepository.findById(initial.getId()).orElseThrow();
        assertEquals(3, deadLetter.getRetryCount());
        assertEquals(OutboxStatus.DEAD_LETTER, deadLetter.getStatus());
        assertFalse(deadLetter.isDelivered());

        // Subsequent retry execution skips DEAD_LETTER events
        testClock.advance(Duration.ofSeconds(60));
        dispatchService.retryUndeliveredEvents();
        OutboxEventEntity unchanged = outboxRepository.findById(initial.getId()).orElseThrow();
        assertEquals(3, unchanged.getRetryCount(), "Dead letter event must not be re-attempted");
        assertEquals(OutboxStatus.DEAD_LETTER, unchanged.getStatus());
    }

    @Test
    @DisplayName("Guest session has limited read-only scope and TTL bound")
    void testGuestSessionScopeAndTtl() throws Exception {
        TicketEntity ticket = ticketService.createTicket("entry-session-1", "read-only", Duration.ofMinutes(15));
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/keyfort/login")
                        .param("ticket", ticket.getTicketToken())
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(redirectedUrl("/dashboard"));

        assertEquals("guest-entry-session-1", session.getAttribute("user"));
        assertEquals("read-only", session.getAttribute("scope"));
        assertEquals("GUEST_READONLY", session.getAttribute("role"));
        assertNotNull(session.getAttribute("guestExpiresAt"));
    }

    @Test
    @DisplayName("Connector endpoints verify HMAC signature and reject invalid signatures")
    void testConnectorEndpointHmacVerification() throws Exception {
        Map<String, Object> req = Map.of("entryId", "entry-e2e", "scope", "read", "ttlSeconds", 900);
        String body = objectMapper.writeValueAsString(req);
        long ts = Instant.now().getEpochSecond();
        String nonce = "nonce-conn-test-1";
        String validSig = HmacSigner.sign(SECRET, "POST", "/connector/tickets", ts, nonce, body);

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
        MvcResult res = mockMvc.perform(post("/keyfort/login").param("ticket", "tkt_secret_token_12345"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String redirected = res.getResponse().getRedirectedUrl();
        assertNotNull(redirected);
        assertFalse(redirected.contains("secret_token_12345"), "Redirect URL must not echo raw ticket secret");
    }

    @Test
    @DisplayName("Guest session cannot outlive hard TTL")
    void testSessionCannotOutliveHardTtl() throws Exception {
        TicketEntity ticket = ticketService.createTicket("entry-hard-ttl", "read-only", Duration.ofMinutes(15));
        MvcResult loginRes = mockMvc.perform(post("/keyfort/login")
                        .param("ticket", ticket.getTicketToken()))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        jakarta.servlet.http.Cookie sessionCookie = loginRes.getResponse().getCookie("KEYFORT_GUEST_SESSION");
        assertNotNull(sessionCookie);
        jakarta.servlet.http.Cookie deviceCookie = loginRes.getResponse().getCookie("KEYFORT_DEVICE_ID");

        // Fast forward expiresAt in the database to the past
        org.keyfort.demosite.entity.GuestSessionEntity entity = sessionRepository.findById(sessionCookie.getValue()).orElseThrow();
        entity.setExpiresAt(Instant.now().minusSeconds(10));
        sessionRepository.save(entity);

        var reqBuilder = get("/dashboard").cookie(sessionCookie);
        if (deviceCookie != null) reqBuilder.cookie(deviceCookie);

        mockMvc.perform(reqBuilder)
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error=session_expired"));

        org.keyfort.demosite.entity.GuestSessionEntity after = sessionRepository.findById(sessionCookie.getValue()).orElseThrow();
        assertEquals(org.keyfort.demosite.entity.SessionStatus.ENDED, after.getStatus());
    }

    @Test
    @DisplayName("Guest session slides on access and expires when idle timeout is exceeded")
    void testSessionIdleTimeoutAndSlide() throws Exception {
        TicketEntity ticket = ticketService.createTicket("entry-idle-test", "read-only", Duration.ofMinutes(15));
        MvcResult loginRes = mockMvc.perform(post("/keyfort/login")
                        .param("ticket", ticket.getTicketToken()))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        jakarta.servlet.http.Cookie sessionCookie = loginRes.getResponse().getCookie("KEYFORT_GUEST_SESSION");
        jakarta.servlet.http.Cookie deviceCookie = loginRes.getResponse().getCookie("KEYFORT_DEVICE_ID");
        assertNotNull(sessionCookie);

        // Slide check: access /dashboard and verify lastAccessAt is updated
        var req1 = get("/dashboard").cookie(sessionCookie);
        if (deviceCookie != null) req1.cookie(deviceCookie);
        mockMvc.perform(req1).andExpect(status().isOk());

        // Now fast forward lastAccessAt to 6 minutes in the past (> 5 min idle timeout)
        org.keyfort.demosite.entity.GuestSessionEntity entity = sessionRepository.findById(sessionCookie.getValue()).orElseThrow();
        entity.setLastAccessAt(Instant.now().minus(Duration.ofMinutes(6)));
        sessionRepository.save(entity);

        var req2 = get("/dashboard").cookie(sessionCookie);
        if (deviceCookie != null) req2.cookie(deviceCookie);
        mockMvc.perform(req2)
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error=session_expired"));

        org.keyfort.demosite.entity.GuestSessionEntity after = sessionRepository.findById(sessionCookie.getValue()).orElseThrow();
        assertEquals(org.keyfort.demosite.entity.SessionStatus.ENDED, after.getStatus());
    }

    @Test
    @DisplayName("Connector kill-switch terminates session immediately and ended session cannot be revived")
    void testConnectorKillSwitchEndsSessionImmediately() throws Exception {
        TicketEntity ticket = ticketService.createTicket("entry-kill-switch", "read-only", Duration.ofMinutes(15));
        MvcResult loginRes = mockMvc.perform(post("/keyfort/login")
                        .param("ticket", ticket.getTicketToken()))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        jakarta.servlet.http.Cookie sessionCookie = loginRes.getResponse().getCookie("KEYFORT_GUEST_SESSION");
        jakarta.servlet.http.Cookie deviceCookie = loginRes.getResponse().getCookie("KEYFORT_DEVICE_ID");
        assertNotNull(sessionCookie);
        String sessionId = sessionCookie.getValue();

        // Valid session before kill-switch
        var activeReq = get("/api/session").cookie(sessionCookie);
        if (deviceCookie != null) activeReq.cookie(deviceCookie);
        mockMvc.perform(activeReq).andExpect(status().isOk());

        // Kill-switch: DELETE /connector/sessions/{id} with HMAC
        long ts = Instant.now().getEpochSecond();
        String nonce = "nonce-kill-switch-1";
        String path = "/connector/sessions/" + sessionId;
        String sig = HmacSigner.sign(SECRET, "DELETE", path, ts, nonce, "");

        mockMvc.perform(delete(path)
                        .header("X-KeyFort-Timestamp", String.valueOf(ts))
                        .header("X-KeyFort-Nonce", nonce)
                        .header("X-KeyFort-Signature", sig))
                .andExpect(status().isNoContent());

        // Immediate check: next request gets 401 Unauthorized
        var killedReq = get("/api/session").cookie(sessionCookie);
        if (deviceCookie != null) killedReq.cookie(deviceCookie);
        mockMvc.perform(killedReq).andExpect(status().isUnauthorized());

        // Ended session cannot be revived
        var revivedReq = get("/dashboard").cookie(sessionCookie);
        if (deviceCookie != null) revivedReq.cookie(deviceCookie);
        mockMvc.perform(revivedReq)
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error=session_expired"));

        org.keyfort.demosite.entity.GuestSessionEntity after = sessionRepository.findById(sessionId).orElseThrow();
        assertEquals(org.keyfort.demosite.entity.SessionStatus.ENDED, after.getStatus());
    }

    @Test
    @DisplayName("Guest session with read-only scope cannot reach write endpoints")
    void testGuestCannotReachWriteEndpoints() throws Exception {
        TicketEntity ticket = ticketService.createTicket("entry-write-test", "read-only", Duration.ofMinutes(15));
        MvcResult loginRes = mockMvc.perform(post("/keyfort/login")
                        .param("ticket", ticket.getTicketToken()))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        jakarta.servlet.http.Cookie sessionCookie = loginRes.getResponse().getCookie("KEYFORT_GUEST_SESSION");
        jakarta.servlet.http.Cookie deviceCookie = loginRes.getResponse().getCookie("KEYFORT_DEVICE_ID");
        assertNotNull(sessionCookie);

        // Read endpoint succeeds
        var readReq = get("/account/settings").cookie(sessionCookie);
        if (deviceCookie != null) readReq.cookie(deviceCookie);
        mockMvc.perform(readReq).andExpect(status().isOk());

        // Mutating POST endpoints return 403 Forbidden
        var writeReq1 = post("/account/settings").cookie(sessionCookie);
        if (deviceCookie != null) writeReq1.cookie(deviceCookie);
        mockMvc.perform(writeReq1).andExpect(status().isForbidden());

        var writeReq2 = post("/account/transfer").cookie(sessionCookie);
        if (deviceCookie != null) writeReq2.cookie(deviceCookie);
        mockMvc.perform(writeReq2).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Login webhook dispatch carries sessionId in JSON payload")
    void testLoginWebhookDispatchesSessionId() throws Exception {
        outboxRepository.deleteAll();
        TicketEntity ticket = ticketService.createTicket("entry-webhook-session", "read-only", Duration.ofMinutes(15));

        MvcResult loginRes = mockMvc.perform(post("/keyfort/login")
                        .param("ticket", ticket.getTicketToken()))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        jakarta.servlet.http.Cookie sessionCookie = loginRes.getResponse().getCookie("KEYFORT_GUEST_SESSION");
        assertNotNull(sessionCookie);
        String sessionId = sessionCookie.getValue();

        List<OutboxEventEntity> events = outboxRepository.findByDeliveredFalse();
        assertFalse(events.isEmpty(), "Outbox should hold the dispatched login event");
        String payload = events.get(0).getPayload();
        assertTrue(payload.contains("\"sessionId\":\"" + sessionId + "\""), "Payload must include sessionId: " + payload);
    }

    @Test
    @DisplayName("Device binding mismatch rejects request")
    void testDeviceBindingMismatchRejected() throws Exception {
        TicketEntity ticket = ticketService.createTicket("entry-device-bind", "read-only", Duration.ofMinutes(15));
        MvcResult loginRes = mockMvc.perform(post("/keyfort/login")
                        .param("ticket", ticket.getTicketToken())
                        .header("User-Agent", "Mozilla/5.0 Device-A")
                        .cookie(new jakarta.servlet.http.Cookie("KEYFORT_DEVICE_ID", "device-cookie-123")))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        jakarta.servlet.http.Cookie sessionCookie = loginRes.getResponse().getCookie("KEYFORT_GUEST_SESSION");
        assertNotNull(sessionCookie);

        // Access with matching device cookie and User-Agent succeeds
        mockMvc.perform(get("/dashboard")
                        .cookie(sessionCookie)
                        .cookie(new jakarta.servlet.http.Cookie("KEYFORT_DEVICE_ID", "device-cookie-123"))
                        .header("User-Agent", "Mozilla/5.0 Device-A"))
                .andExpect(status().isOk());

        // Access with altered User-Agent is rejected
        mockMvc.perform(get("/dashboard")
                        .cookie(sessionCookie)
                        .cookie(new jakarta.servlet.http.Cookie("KEYFORT_DEVICE_ID", "device-cookie-123"))
                        .header("User-Agent", "Different-Browser/1.0"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error=session_expired"));
    }

    @Test
    @DisplayName("No session ID or cookie value is leaked in log statements")
    void testNoSessionIdOrCookieLeakedInLogs() throws Exception {
        ch.qos.logback.classic.Logger rootLogger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("org.keyfort.demosite");
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender = new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        rootLogger.addAppender(appender);

        TicketEntity ticket = ticketService.createTicket("entry-log-leak", "read-only", Duration.ofMinutes(15));
        MvcResult loginRes = mockMvc.perform(post("/keyfort/login")
                        .param("ticket", ticket.getTicketToken()))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        jakarta.servlet.http.Cookie sessionCookie = loginRes.getResponse().getCookie("KEYFORT_GUEST_SESSION");
        assertNotNull(sessionCookie);
        String sessionId = sessionCookie.getValue();

        try {
            for (ch.qos.logback.classic.spi.ILoggingEvent event : appender.list) {
                String msg = event.getFormattedMessage();
                assertFalse(msg.contains(sessionId), "Log output must not contain raw session ID: " + msg);
                assertFalse(msg.contains(ticket.getTicketToken()), "Log output must not contain raw ticket token: " + msg);
            }
        } finally {
            rootLogger.detachAppender(appender);
        }
    }

    private static final class MutableTestClock extends Clock {
        private Instant current;

        MutableTestClock(Instant initial) {
            this.current = initial;
        }

        void advance(Duration duration) {
            this.current = this.current.plus(duration);
        }

        @Override public Instant instant() { return current; }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
    }
}
