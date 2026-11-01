package org.keyfort.android;

import org.keyfort.core.Vault;

public class AppSession {

    private Vault vault;
    private Vault.Session session;
    private String userId;
    private String deviceId;
    private String jwtToken;

    public AppSession() {}

    public boolean isUnlocked() {
        return session != null;
    }

    public void lock() {
        if (session != null) {
            try {
                session.close();
            } catch (Exception ignored) {}
            session = null;
        }
    }

    public Vault getVault() {
        return vault;
    }

    public void setVault(Vault vault) {
        this.vault = vault;
    }

    public Vault.Session getSession() {
        return session;
    }

    public void setSession(Vault.Session session) {
        this.session = session;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getJwtToken() {
        return jwtToken;
    }

    public void setJwtToken(String jwtToken) {
        this.jwtToken = jwtToken;
    }

    public void clear() {
        lock();
        vault = null;
        userId = null;
        deviceId = null;
        jwtToken = null;
    }
}
