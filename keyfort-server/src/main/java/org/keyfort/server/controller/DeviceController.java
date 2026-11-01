package org.keyfort.server.controller;

import org.keyfort.server.dto.DeviceResponse;
import org.keyfort.server.entity.DeviceEntity;
import org.keyfort.server.service.DeviceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/devices")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @GetMapping
    public ResponseEntity<List<DeviceResponse>> listDevices(Authentication auth) {
        String userId = (String) auth.getPrincipal();
        return ResponseEntity.ok(deviceService.getUserDevices(userId));
    }

    @PostMapping("/{id}/revoke")
    public ResponseEntity<DeviceResponse> revokeDevice(@PathVariable("id") String deviceId, Authentication auth) {
        String userId = (String) auth.getPrincipal();
        DeviceEntity revoked = deviceService.revokeDevice(userId, deviceId);

        return ResponseEntity.ok(new DeviceResponse(
                revoked.getDeviceId(),
                revoked.getDeviceName(),
                revoked.getState(),
                revoked.getRegisteredAt(),
                revoked.getLastSeenAt()
        ));
    }
}
