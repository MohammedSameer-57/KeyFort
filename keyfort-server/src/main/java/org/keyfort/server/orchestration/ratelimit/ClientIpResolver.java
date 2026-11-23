package org.keyfort.server.orchestration.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ClientIpResolver {

    private final Set<String> trustedProxies;

    public ClientIpResolver(
            @Value("${keyfort.rate-limit.trusted-proxies:127.0.0.1,::1,0:0:0:0:0:0:0:1}") String trustedProxiesStr
    ) {
        this.trustedProxies = Arrays.stream(trustedProxiesStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    public String resolveClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (remoteAddr == null || !trustedProxies.contains(remoteAddr)) {
            // Peer is not a trusted proxy: discard X-Forwarded-For to prevent IP spoofing
            return remoteAddr != null ? remoteAddr : "unknown";
        }

        String xff = request.getHeader("X-Forwarded-For");
        if (xff == null || xff.isBlank()) {
            return remoteAddr;
        }

        // Return the client IP (first element in X-Forwarded-For chain)
        String[] parts = xff.split(",");
        if (parts.length > 0 && !parts[0].isBlank()) {
            return parts[0].trim();
        }

        return remoteAddr;
    }
}
