package org.keyfort.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.keyfort.core.risk.DeviceState;

import java.time.Instant;

@Entity
@Table(name = "devices", indexes = {
        @Index(name = "idx_devices_user_id", columnList = "user_id")
})
public class DeviceEntity {

    @Id
    @Column(name = "id", nullable = false, length = 128)
    private String id; // composite key: userId + "/" + deviceId

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "device_id", nullable = false, length = 64)
    private String deviceId;

    @Column(name = "device_name", nullable = false, length = 128)
    private String deviceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 32)
    private DeviceState state = DeviceState.UNKNOWN;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    public DeviceEntity() {}

    public DeviceEntity(String userId, String deviceId, String deviceName, DeviceState state, Instant registeredAt, Instant lastSeenAt) {
        this.id = userId + "/" + deviceId;
        this.userId = userId;
        this.deviceId = deviceId;
        this.deviceName = deviceName;
        this.state = state;
        this.registeredAt = registeredAt;
        this.lastSeenAt = lastSeenAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }

    public DeviceState getState() { return state; }
    public void setState(DeviceState state) { this.state = state; }

    public Instant getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(Instant registeredAt) { this.registeredAt = registeredAt; }

    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }
}
