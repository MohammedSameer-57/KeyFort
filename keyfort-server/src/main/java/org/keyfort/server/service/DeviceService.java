package org.keyfort.server.service;

import org.keyfort.core.orchestration.Orchestrator;
import org.keyfort.core.risk.DeviceState;
import org.keyfort.server.dto.DeviceResponse;
import org.keyfort.server.entity.DeviceEntity;
import org.keyfort.server.repository.DeviceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final LedgerService ledgerService;

    public DeviceService(DeviceRepository deviceRepository, LedgerService ledgerService) {
        this.deviceRepository = deviceRepository;
        this.ledgerService = ledgerService;
    }

    @Transactional
    public DeviceEntity registerDevice(String userId, String deviceId, String deviceName) {
        Instant now = Instant.now();
        return deviceRepository.findByUserIdAndDeviceId(userId, deviceId)
                .map(existing -> {
                    existing.setDeviceName(deviceName);
                    existing.setLastSeenAt(now);
                    return deviceRepository.save(existing);
                })
                .orElseGet(() -> {
                    DeviceEntity created = new DeviceEntity(userId, deviceId, deviceName, DeviceState.UNKNOWN, now, now);
                    return deviceRepository.save(created);
                });
    }

    @Transactional
    public DeviceEntity verifyDevice(String userId, String deviceId) {
        Instant now = Instant.now();
        DeviceEntity device = deviceRepository.findByUserIdAndDeviceId(userId, deviceId)
                .orElseGet(() -> new DeviceEntity(userId, deviceId, "Verified Device", DeviceState.UNKNOWN, now, now));

        device.setState(DeviceState.TRUSTED);
        device.setLastSeenAt(now);
        DeviceEntity saved = deviceRepository.save(device);

        ledgerService.append("DEVICE_TRUSTED", Orchestrator.meta(userId, deviceId, null));
        return saved;
    }

    @Transactional
    public DeviceEntity revokeDevice(String userId, String deviceId) {
        Instant now = Instant.now();
        DeviceEntity device = deviceRepository.findByUserIdAndDeviceId(userId, deviceId)
                .orElseGet(() -> new DeviceEntity(userId, deviceId, "Revoked Device", DeviceState.UNKNOWN, now, now));

        device.setState(DeviceState.REVOKED);
        device.setLastSeenAt(now);
        DeviceEntity saved = deviceRepository.save(device);

        ledgerService.append("DEVICE_REVOKED", Orchestrator.meta(userId, deviceId, null));
        return saved;
    }

    @Transactional(readOnly = true)
    public DeviceState getDeviceState(String userId, String deviceId) {
        return deviceRepository.findByUserIdAndDeviceId(userId, deviceId)
                .map(DeviceEntity::getState)
                .orElse(DeviceState.UNKNOWN);
    }

    @Transactional(readOnly = true)
    public List<DeviceResponse> getUserDevices(String userId) {
        return deviceRepository.findByUserId(userId).stream()
                .map(d -> new DeviceResponse(
                        d.getDeviceId(),
                        d.getDeviceName(),
                        d.getState(),
                        d.getRegisteredAt(),
                        d.getLastSeenAt()
                ))
                .toList();
    }
}
