package org.keyfort.demosite.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.keyfort.core.Crypto;
import org.keyfort.core.connector.HmacSigner;
import org.keyfort.demosite.entity.OutboxEventEntity;
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
import java.time.Instant;
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

    @org.springframework.beans.factory.annotation.Autowired
    public WebhookDispatchService(
            OutboxEventRepository outboxRepository,
            ObjectMapper objectMapper,
            @Value("${keyfort.connector.secret:demo-site-connector-hmac-shared-secret-key-material-256bits!!}") String secretStr,
            @Value("${keyfort.connector.webhook-url:http://localhost:8080/api/webhooks/login}") String webhookUrl
    ) {
        this(outboxRepository, objectMapper, secretStr, webhookUrl, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(), Clock.systemUTC());
    }

    public WebhookDispatchService(
            OutboxEventRepository outboxRepository,
            ObjectMapper objectMapper,
            String secretStr,
            String webhookUrl,
            HttpClient httpClient,
            Clock clock
    ) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.secret = secretStr.getBytes(StandardCharsets.UTF_8);
        this.webhookUrl = webhookUrl;
        this.httpClient = httpClient;
        this.clock = clock;
    }

    public void dispatchLoginObserved(String ticketId, String entryId, String scope) {
        Map<String, Object> payloadMap = Map.of(
                "ticketId", ticketId,
                "entryId", entryId,
                "scope", scope,
                "timestamp", clock.instant().toString()
        );

        String jsonPayload;
        try {
            jsonPayload = objectMapper.writeValueAsString(payloadMap);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize webhook payload", e);
        }

        boolean delivered = sendWebhook(jsonPayload);
        if (!delivered) {
            OutboxEventEntity outbox = new OutboxEventEntity(
                    UUID.randomUUID().toString(),
                    "LOGIN_OBSERVED",
                    jsonPayload,
                    clock.instant()
            );
            outbox.setLastAttemptAt(clock.instant());
            outbox.incrementRetry();
            outboxRepository.save(outbox);
            log.info("Webhook dispatch deferred to outbox for ticketId={}", ticketId);
        }
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void retryUndeliveredEvents() {
        List<OutboxEventEntity> undelivered = outboxRepository.findByDeliveredFalse();
        for (OutboxEventEntity event : undelivered) {
            boolean success = sendWebhook(event.getPayload());
            event.setLastAttemptAt(clock.instant());
            event.incrementRetry();
            if (success) {
                event.setDelivered(true);
            }
            outboxRepository.save(event);
        }
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
