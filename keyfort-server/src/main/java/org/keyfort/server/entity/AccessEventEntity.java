package org.keyfort.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "access_events", indexes = {
        @Index(name = "idx_events_user_at", columnList = "user_id, at")
})
public class AccessEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "at", nullable = false)
    private Instant at;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "device_id", nullable = false, length = 64)
    private String deviceId;

    @Column(name = "ip", nullable = false, length = 64)
    private String ip;

    @Column(name = "geo", nullable = false, length = 64)
    private String geo;

    @Column(name = "success", nullable = false)
    private boolean success;

    public AccessEventEntity() {}

    public AccessEventEntity(Instant at, String userId, String deviceId, String ip, String geo, boolean success) {
        this.at = at;
        this.userId = userId;
        this.deviceId = deviceId;
        this.ip = ip;
        this.geo = geo;
        this.success = success;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Instant getAt() { return at; }
    public void setAt(Instant at) { this.at = at; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }

    public String getGeo() { return geo; }
    public void setGeo(String geo) { this.geo = geo; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
}
