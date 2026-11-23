package org.keyfort.demosite.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.keyfort.core.Crypto;
import org.keyfort.core.connector.HmacSigner;
import org.keyfort.demosite.entity.OutboxEventEntity;
import org.keyfort.demosite.entity.OutboxStatus;
import org.keyfort.demosite.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WebhookDispatchService {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatchService.class);

    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final byte[] secret;
    private final String webhookUrl;
    private final Clock clock;
    private final int maxAttempts;

    @org.springframework.beans.factory.annotation.Autowired
    public WebhookDispatchService(
            OutboxEventRepository outboxRepository,
            ObjectMapper objectMapper,
            @Value("${keyfort.connector.secret:}") String secretStr,
            @Value("${keyfort.connector.webhook-url:http://localhost:8080/api/webhooks/login}") String webhookUrl
    ) {
        this(outboxRepository, objectMapper, secretStr, webhookUrl,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(),
                Clock.systemUTC(),
                OutboxEventEntity.DEFAULT_MAX_ATTEMPTS);
    }


    public WebhookDispatchService(
            OutboxEventRepository outboxRepository,
            ObjectMapper objectMapper,
            String secretStr,
            String webhookUrl,
            HttpClient httpClient,
            Clock clock,
            int maxAttempts
    ) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        if (secretStr == null || secretStr.isBlank()) {
            throw new IllegalStateException("KEYFORT_CONNECTOR_SECRET environment variable is not configured");
        }
        this.secret = secretStr.getBytes(StandardCharsets.UTF_8);
        this.webhookUrl = webhookUrl;
        this.httpClient = httpClient;
        this.clock = clock;
        this.maxAttempts = maxAttempts;
    }

    public void dispatchLoginObserved(String ticketId, String entryId, String scope) {
        dispatchLoginObserved(ticketId, entryId, scope, null);
    }

    public void dispatchLoginObserved(String ticketId, String entryId, String scope, String sessionId) {
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> payloadMap = new java.util.HashMap<>();
        payloadMap.put("eventId", eventId);
        payloadMap.put("ticketId", ticketId);
        payloadMap.put("entryId", entryId);
        payloadMap.put("scope", scope);
        if (sessionId != null) {
            payloadMap.put("sessionId", sessionId);
        }
        payloadMap.put("timestamp", clock.instant().toString());

        String jsonPayload;
        try {
            jsonPayload = objectMapper.writeValueAsString(payloadMap);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize webhook payload", e);
        }

        boolean delivered = sendWebhook(jsonPayload);
        if (!delivered) {
            OutboxEventEntity outbox = new OutboxEventEntity(
                    eventId,
                    "LOGIN_OBSERVED",
                    jsonPayload,
                    clock.instant(),
                    maxAttempts
            );
            outbox.setLastAttemptAt(clock.instant());
            outbox.incrementRetry();
            // First failure backoff: 2^0 = 1 second
            long backoffSeconds = computeBackoffSeconds(outbox.getRetryCount());
            outbox.setNextAttemptAt(clock.instant().plusSeconds(backoffSeconds));
            outboxRepository.save(outbox);
            log.info("Webhook dispatch deferred to outbox for ticketId={}", ticketId);
        }
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void retryUndeliveredEvents() {
        List<OutboxEventEntity> pendingEvents = outboxRepository.findByDeliveredFalseAndStatus(OutboxStatus.PENDING);
        for (OutboxEventEntity event : pendingEvents) {
            if (event.getNextAttemptAt() != null && clock.instant().isBefore(event.getNextAttemptAt())) {
                continue;
            }

            boolean success = sendWebhook(event.getPayload());
            event.setLastAttemptAt(clock.instant());
            event.incrementRetry();

            if (success) {
                event.setDelivered(true);
                event.setStatus(OutboxStatus.DELIVERED);
            } else {
                if (event.getRetryCount() >= event.getMaxAttempts()) {
                    event.setStatus(OutboxStatus.DEAD_LETTER);
                    log.warn("Outbox event id={} reached max attempts ({}) and marked DEAD_LETTER",
                            event.getId(), event.getMaxAttempts());
                } else {
                    long backoffSeconds = computeBackoffSeconds(event.getRetryCount());
                    event.setNextAttemptAt(clock.instant().plusSeconds(backoffSeconds));
                }
            }
            outboxRepository.save(event);
        }
    }

    public static long computeBackoffSeconds(int retryCount) {
        // Exponential backoff: 1s, 2s, 4s, 8s, 16s... capped at 300s (5 minutes)
        int exponent = Math.max(0, retryCount - 1);
        long backoff = (long) Math.pow(2, Math.min(exponent, 8));
        return Math.min(300L, backoff);
    }

    public boolean sendWebhook(String jsonPayload) {
        try {
            long ts = clock.instant().getEpochSecond();
            String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(Crypto.random(12));
            URI uri = URI.create(webhookUrl);
            String path = uri.getPath() == null || uri.getPath().isEmpty() ? "/" : uri.getPath();

            String signature = HmacSigner.sign(secret, "POST", path, ts, nonce, jsonPayload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(3))
                    .header("Content-Type", "application/json")
                    .header("X-KeyFort-Timestamp", String.valueOf(ts))
                    .header("X-KeyFort-Nonce", nonce)
                    .header("X-KeyFort-Signature", signature)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception e) {
            return false;
        }
    }
}
