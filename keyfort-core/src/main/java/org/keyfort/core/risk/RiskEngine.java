package org.keyfort.core.risk;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window multi-signal risk engine with anti-lockout protection for trusted devices.
 */
public final class RiskEngine {
    private static final Duration WINDOW = Duration.ofMinutes(10);
    private final DeviceRegistry devices;
    private final Map<String, Deque<AccessEvent>> history = new ConcurrentHashMap<>();
    private final Map<String, AccessEvent> lastSuccess = new ConcurrentHashMap<>();

    public RiskEngine(DeviceRegistry devices) {
        this.devices = Objects.requireNonNull(devices, "devices must not be null");
    }

    public Decision assess(AccessEvent e) {
        Deque<AccessEvent> q = history.computeIfAbsent(e.userId(), k -> new ArrayDeque<>());
        List<AccessEvent> recent;
        AccessEvent prevSuccess;
        synchronized (q) {
            Instant cutoff = e.at().minus(WINDOW);
            while (!q.isEmpty() && q.peekFirst().at().isBefore(cutoff)) {
                q.removeFirst();
            }
            q.addLast(e);
            recent = new ArrayList<>(q);
            prevSuccess = lastSuccess.get(e.userId());
            if (e.success()) {
                lastSuccess.put(e.userId(), e);
            }
        }

        DeviceState st = devices.state(e.userId(), e.deviceId());
        List<String> reasons = new ArrayList<>();
        int score = 0;

        // Device-level signals: determine what happens to THIS device.
        if (st == DeviceState.UNKNOWN) {
            score += 25;
            reasons.add("unfamiliar device");
        }
        if (st == DeviceState.REVOKED) {
            score += 60;
            reasons.add("revoked device still trying");
        }

        long devFails = recent.stream()
                .filter(x -> !x.success() && x.deviceId().equals(e.deviceId()))
                .count();
        if (devFails > 0) {
            score += (int) Math.min(40, devFails * 10);
            reasons.add(devFails + " failures on this device");
        }

        if (prevSuccess != null && !prevSuccess.geo().equals(e.geo())
                && Duration.between(prevSuccess.at(), e.at()).compareTo(Duration.ofHours(1)) < 0) {
            score += 25;
            reasons.add("impossible travel " + prevSuccess.geo() + " -> " + e.geo());
        }

        // User-level signals: determine whether an attack is coordinated.
        List<AccessEvent> fails = recent.stream().filter(x -> !x.success()).toList();
        long ips = fails.stream().map(AccessEvent::ip).distinct().count();
        long devs = fails.stream().map(AccessEvent::deviceId).distinct().count();
        boolean coordinated = fails.size() >= 5 && (ips >= 3 || devs >= 3);
        if (coordinated) {
            score += 30;
            reasons.add("coordinated failures: " + fails.size() + " from " + ips + " IPs / " + devs + " devices");
        }

        Action action = score >= 75 ? Action.REVOKE_DEVICE
                : score >= 55 ? Action.STEP_UP_AUTH
                : score >= 30 ? Action.ALERT
                : Action.ALLOW;

        // Anti-lockout: a trusted device is never auto-revoked; it is challenged instead.
        if (st == DeviceState.TRUSTED && action == Action.REVOKE_DEVICE) {
            action = Action.STEP_UP_AUTH;
        }

        // A coordinated attack must at least raise an alert for everyone, including the owner.
        if (coordinated && action == Action.ALLOW) {
            action = Action.ALERT;
        }

        return new Decision(action, score, coordinated, List.copyOf(reasons));
    }
}
