package org.keyfort.server.connector;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.keyfort.core.Crypto;
import org.keyfort.core.connector.ConnectorException;
import org.keyfort.core.connector.HmacSigner;
import org.keyfort.core.connector.LoginEvent;
import org.keyfort.core.connector.SignedEvent;
import org.keyfort.core.connector.SignedRequest;
import org.keyfort.core.connector.SiteConnector;
import org.keyfort.core.connector.TicketHandle;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

@Component
public class HttpSiteConnector implements SiteConnector {

    private final String baseUrl;
    private final byte[] secret;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final HmacSigner hmacSigner;

    @org.springframework.beans.factory.annotation.Autowired
    public HttpSiteConnector(
            @Value("${keyfort.connector.base-url:http://localhost:9090}") String baseUrl,
            @Value("${keyfort.connector.secret:demo-site-connector-hmac-shared-secret-key-material-256bits!!}") String secretStr,
            ObjectMapper objectMapper
    ) {
        this(
                baseUrl,
                secretStr.getBytes(StandardCharsets.UTF_8),
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                objectMapper,
                Clock.systemUTC()
        );
    }

    public HttpSiteConnector(
            String baseUrl,
            byte[] secret,
            HttpClient httpClient,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl must not be null");
        this.secret = Objects.requireNonNull(secret, "secret must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.hmacSigner = new HmacSigner(clock);
    }

    @Override
    public TicketHandle provisionTicket(String entryId, String scope, Duration ttl) throws ConnectorException {
        try {
            Map<String, Object> reqMap = Map.of(
                    "entryId", entryId,
                    "scope", scope,
                    "ttlSeconds", ttl.toSeconds()
            );
            String jsonBody = objectMapper.writeValueAsString(reqMap);

            long ts = clock.instant().getEpochSecond();
            String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(Crypto.random(12));
            String path = "/connector/tickets";

            String signature = HmacSigner.sign(secret, "POST", path, ts, nonce, jsonBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .header("X-KeyFort-Timestamp", String.valueOf(ts))
                    .header("X-KeyFort-Nonce", nonce)
                    .header("X-KeyFort-Signature", signature)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 201) {
                throw new ConnectorException("Target site failed to provision ticket. Status: " + response.statusCode());
            }

            Map<?, ?> respMap = objectMapper.readValue(response.body(), Map.class);
            String ticketId = (String) respMap.get("ticketId");
            String redirectUrl = (String) respMap.get("redirectUrl");

            return TicketHandle.of(ticketId, redirectUrl);
        } catch (ConnectorException e) {
            throw e;
        } catch (Exception e) {
            throw new ConnectorException("Failed to provision ticket via HTTP connector", e);
        }
    }

    @Override
    public void revokeTicket(String ticketId) throws ConnectorException {
        try {
            long ts = clock.instant().getEpochSecond();
            String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(Crypto.random(12));
            String path = "/connector/tickets/" + ticketId;

            String signature = HmacSigner.sign(secret, "DELETE", path, ts, nonce, "");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(5))
                    .header("X-KeyFort-Timestamp", String.valueOf(ts))
                    .header("X-KeyFort-Nonce", nonce)
                    .header("X-KeyFort-Signature", signature)
                    .DELETE()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 204 && response.statusCode() != 200) {
                throw new ConnectorException("Failed to revoke ticket on target site. Status: " + response.statusCode());
            }
        } catch (ConnectorException e) {
            throw e;
        } catch (Exception e) {
            throw new ConnectorException("Failed to revoke ticket via HTTP connector", e);
        }
    }

    @Override
    public LoginEvent verifyLoginEvent(SignedEvent signedEvent) throws ConnectorException {
        try {
            SignedRequest req = new SignedRequest("POST", "/api/webhooks/login", signedEvent.timestamp(), signedEvent.nonce(), signedEvent.payload(), signedEvent.signature());
            hmacSigner.verify(secret, req);

            Map<?, ?> map = objectMapper.readValue(signedEvent.payload(), Map.class);
            String ticketId = (String) map.get("ticketId");
            String entryId = (String) map.get("entryId");
            String tsStr = (String) map.get("timestamp");
            Instant ts = tsStr != null ? Instant.parse(tsStr) : clock.instant();

            Object scopeVal = map.get("scope");
            String scope = scopeVal != null ? scopeVal.toString() : "read";

            return new LoginEvent(ticketId, entryId, ts, "cred-" + ticketId, Map.of("scope", scope));
        } catch (Exception e) {
            throw new ConnectorException("Failed to verify signed login event", e);
        }
    }
}
