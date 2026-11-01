package org.keyfort.server.service;

import org.keyfort.core.orchestration.Orchestrator;
import org.keyfort.core.risk.Action;
import org.keyfort.core.risk.Decision;
import org.keyfort.core.risk.DeviceState;
import org.keyfort.server.dto.AccessEventRequest;
import org.keyfort.server.dto.DecisionResponse;
import org.keyfort.server.entity.AccessEventEntity;
import org.keyfort.server.entity.VaultEntity;
import org.keyfort.server.repository.AccessEventRepository;
import org.keyfort.server.repository.VaultRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class RiskAssessmentService {

    private static final Duration WINDOW = Duration.ofMinutes(10);

    private final AccessEventRepository accessEventRepository;
    private final DeviceService deviceService;
    private final VaultRepository vaultRepository;
    private final LedgerService ledgerService;

    public RiskAssessmentService(
            AccessEventRepository accessEventRepository,
            DeviceService deviceService,
            VaultRepository vaultRepository,
            LedgerService ledgerService
    ) {
        this.accessEventRepository = accessEventRepository;
        this.deviceService = deviceService;
        this.vaultRepository = vaultRepository;
        this.ledgerService = ledgerService;
    }

    @Transactional
    public DecisionResponse assess(AccessEventRequest req) {
        Instant at = req.at() != null ? req.at() : Instant.now();
        Instant cutoff = at.minus(WINDOW);

        // Fetch recent sliding-window events from DB
        List<AccessEventEntity> recentEntities = accessEventRepository
                .findByUserIdAndAtAfterOrderByAtAsc(req.userId(), cutoff);

        // Find last successful login for travel velocity check
        Optional<AccessEventEntity> lastSuccess = recentEntities.stream()
                .filter(AccessEventEntity::isSuccess)
                .reduce((first, second) -> second);

        DeviceState st = deviceService.getDeviceState(req.userId(), req.deviceId());
        List<String> reasons = new ArrayList<>();
        int score = 0;

        // Device-level signals
        if (st == DeviceState.UNKNOWN) {
            score += 25;
            reasons.add("unfamiliar device");
        }
        if (st == DeviceState.REVOKED) {
            score += 60;
            reasons.add("revoked device still trying");
        }

        // Count device-specific failures in sliding window (including current if unsuccessful)
        long devFails = recentEntities.stream()
                .filter(x -> !x.isSuccess() && x.getDeviceId().equals(req.deviceId()))
                .count() + (req.success() ? 0 : 1);

        if (devFails > 0) {
            score += (int) Math.min(40, devFails * 10);
            reasons.add(devFails + " failures on this device");
        }

        // Impossible travel check
        if (lastSuccess.isPresent()) {
            AccessEventEntity prev = lastSuccess.get();
            if (!prev.getGeo().equals(req.geo())
                    && Duration.between(prev.getAt(), at).compareTo(Duration.ofHours(1)) < 0) {
                score += 25;
                reasons.add("impossible travel " + prev.getGeo() + " -> " + req.geo());
            }
        }

        // User-level coordinated attack signals
        List<AccessEventEntity> fails = new ArrayList<>(recentEntities.stream().filter(x -> !x.isSuccess()).toList());
        if (!req.success()) {
            fails.add(new AccessEventEntity(at, req.userId(), req.deviceId(), req.ip(), req.geo(), false));
        }

        long ips = fails.stream().map(AccessEventEntity::getIp).distinct().count();
        long devs = fails.stream().map(AccessEventEntity::getDeviceId).distinct().count();
        boolean coordinated = fails.size() >= 5 && (ips >= 3 || devs >= 3);
        if (coordinated) {
            score += 30;
            reasons.add("coordinated failures: " + fails.size() + " from " + ips + " IPs / " + devs + " devices");
        }

        Action action = score >= 75 ? Action.REVOKE_DEVICE
                : score >= 55 ? Action.STEP_UP_AUTH
                : score >= 30 ? Action.ALERT
                : Action.ALLOW;

        // Anti-lockout: trusted device is never auto-revoked; challenged instead
        if (st == DeviceState.TRUSTED && action == Action.REVOKE_DEVICE) {
            action = Action.STEP_UP_AUTH;
        }

        // Coordinated attack raises at least an ALERT
        if (coordinated && action == Action.ALLOW) {
            action = Action.ALERT;
        }

        // Persist the access event
        accessEventRepository.save(new AccessEventEntity(at, req.userId(), req.deviceId(), req.ip(), req.geo(), req.success()));

        // Execute side effects based on risk decision
        if (action == Action.REVOKE_DEVICE) {
            deviceService.revokeDevice(req.userId(), req.deviceId());
        }

        if (coordinated) {
            vaultRepository.findById(req.userId()).ifPresent(v -> {
                if (!v.isRotationPending()) {
                    v.setRotationPending(true);
                    vaultRepository.save(v);
                    ledgerService.append("ROTATION_REQUESTED", Orchestrator.meta(req.userId(), req.deviceId(), "v" + v.getKeyVersion()));
                }
            });
        }

        return new DecisionResponse(action, score, coordinated, reasons);
    }
}
