package org.keyfort.core.orchestration;

import org.keyfort.core.AuthException;
import org.keyfort.core.Crypto;
import org.keyfort.core.Vault;
import org.keyfort.core.ledger.Ledger;
import org.keyfort.core.risk.AccessEvent;
import org.keyfort.core.risk.Decision;
import org.keyfort.core.risk.DeviceRegistry;
import org.keyfort.core.risk.DeviceState;
import org.keyfort.core.risk.RiskEngine;

import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Server-side orchestrator coordinating devices, risk evaluation, and ledger auditing.
 * Never holds or processes the master secret or plaintext secrets.
 */
public final class Orchestrator {
    private final DeviceRegistry devices;
    private final RiskEngine engine;
    private final Ledger ledger;
    private final Map<String, Vault> vaults = new ConcurrentHashMap<>();
    private final Consumer<String> notifier;

    public Orchestrator(Consumer<String> notifier) {
        this(new DeviceRegistry(), new Ledger(), notifier);
    }

    public Orchestrator(DeviceRegistry devices, Ledger ledger, Consumer<String> notifier) {
        this.devices = Objects.requireNonNull(devices, "devices must not be null");
        this.ledger = Objects.requireNonNull(ledger, "ledger must not be null");
        this.engine = new RiskEngine(this.devices);
        this.notifier = notifier != null ? notifier : (msg -> {});
    }

    public void register(Vault v) {
        vaults.put(v.userId(), v);
    }

    public Vault getVault(String userId) {
        return vaults.get(userId);
    }

    public DeviceRegistry devices() {
        return devices;
    }

    public RiskEngine riskEngine() {
        return engine;
    }

    public Ledger ledger() {
        return ledger;
    }

    public void trustDevice(String user, String device) {
        devices.trust(user, device);
        ledger.append("DEVICE_TRUSTED", meta(user, device, null));
    }

    public void revokeDevice(String user, String device) {
        devices.revoke(user, device);
        ledger.append("DEVICE_REVOKED", meta(user, device, null));
        notifier.accept("[REVOKED] " + device + " blocked for " + user);
    }

    public Decision onAccess(AccessEvent e) {
        Decision d = engine.assess(e);
        switch (d.action()) {
            case ALLOW -> {}
            case ALERT -> notifier.accept("[ALERT] " + e.userId() + ": " + d.reasons());
            case STEP_UP_AUTH -> notifier.accept("[STEP-UP] " + e.userId() + "/" + e.deviceId() + " must re-verify");
            case REVOKE_DEVICE -> {
                devices.revoke(e.userId(), e.deviceId());
                ledger.append("DEVICE_REVOKED", meta(e.userId(), e.deviceId(), null));
                notifier.accept("[REVOKED] " + e.deviceId() + " blocked for " + e.userId());
            }
        }
        if (d.coordinatedAttack()) {
            Vault v = vaults.get(e.userId());
            if (v != null && v.requestRotation()) {
                ledger.append("ROTATION_REQUESTED", meta(e.userId(), e.deviceId(), "v" + v.keyVersion()));
                notifier.accept("[ROTATION] keys will rotate on next trusted unlock");
            }
        }
        return d;
    }

    /**
     * Unlock gate: revoked devices are refused; pending rotation completes client-side during session init.
     */
    public Vault.Session unlock(String user, String device, char[] secret)
            throws GeneralSecurityException, AuthException {
        if (devices.state(user, device) == DeviceState.REVOKED) {
            throw new AuthException("Device revoked");
        }
        Vault v = Objects.requireNonNull(vaults.get(user), "unknown user");
        int before = v.keyVersion();
        Vault.Session s = v.unlock(secret);
        if (v.keyVersion() != before) {
            ledger.append("KEY_VERSION_CHANGED", meta(user, device, "v" + before + "->v" + v.keyVersion()));
        }
        return s;
    }

    public static String meta(String user, String device, String extra) {
        // Pseudonymous metadata only: never secrets, never plaintext identifiers.
        String userHash = Crypto.sha256Hex(user);
        String prefix = userHash.length() >= 12 ? userHash.substring(0, 12) : userHash;
        return "user=" + prefix + ";device=" + device
                + (extra == null ? "" : ";" + extra);
    }
}
