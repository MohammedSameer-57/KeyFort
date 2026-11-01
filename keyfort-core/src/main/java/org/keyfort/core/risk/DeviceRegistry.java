package org.keyfort.core.risk;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks device trust states (UNKNOWN, TRUSTED, REVOKED).
 */
public final class DeviceRegistry {
    private final Map<String, DeviceState> states = new ConcurrentHashMap<>();

    private static String k(String user, String device) {
        return user + "/" + device;
    }

    public void trust(String user, String device) {
        states.put(k(user, device), DeviceState.TRUSTED);
    }

    public void revoke(String user, String device) {
        states.put(k(user, device), DeviceState.REVOKED);
    }

    public DeviceState state(String user, String device) {
        return states.getOrDefault(k(user, device), DeviceState.UNKNOWN);
    }

    public Map<String, DeviceState> allStates() {
        return Map.copyOf(states);
    }
}
