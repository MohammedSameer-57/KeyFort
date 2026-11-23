package org.keyfort.server.orchestration.ratelimit;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Component
public class RedeemRateLimiter {

    private static final int MAX_IP_REQUESTS_PER_MINUTE = 20;
    private static final int MAX_PASS_REQUESTS_PER_MINUTE = 5;
    private static final long WINDOW_SECONDS = 60;

    private final ConcurrentHashMap<String, ConcurrentLinkedDeque<Instant>> ipWindows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentLinkedDeque<Instant>> passWindows = new ConcurrentHashMap<>();

    public boolean allowRequest(String clientIp, String passId) {
        Instant now = Instant.now();
        Instant cutoff = now.minusSeconds(WINDOW_SECONDS);

        if (clientIp != null && !clientIp.isBlank()) {
            if (!checkRateLimit(ipWindows, clientIp, cutoff, now, MAX_IP_REQUESTS_PER_MINUTE)) {
                return false;
            }
        }

        if (passId != null && !passId.isBlank()) {
            if (!checkRateLimit(passWindows, passId, cutoff, now, MAX_PASS_REQUESTS_PER_MINUTE)) {
                return false;
            }
        }

        return true;
    }

    private boolean checkRateLimit(
            ConcurrentHashMap<String, ConcurrentLinkedDeque<Instant>> map,
            String key,
            Instant cutoff,
            Instant now,
            int maxLimit
    ) {
        ConcurrentLinkedDeque<Instant> timestamps = map.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        // Prune stale timestamps
        while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
            timestamps.pollFirst();
        }

        if (timestamps.size() >= maxLimit) {
            return false;
        }

        timestamps.addLast(now);
        return true;
    }
}
