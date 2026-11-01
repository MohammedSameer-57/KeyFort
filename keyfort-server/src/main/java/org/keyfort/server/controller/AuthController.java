package org.keyfort.server.controller;

import jakarta.validation.Valid;
import org.keyfort.core.risk.DeviceState;
import org.keyfort.server.dto.AuthResponse;
import org.keyfort.server.dto.DeviceRegistrationRequest;
import org.keyfort.server.dto.DeviceVerificationRequest;
import org.keyfort.server.entity.DeviceEntity;
import org.keyfort.server.security.JwtService;
import org.keyfort.server.service.DeviceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final DeviceService deviceService;
    private final JwtService jwtService;

    public AuthController(DeviceService deviceService, JwtService jwtService) {
        this.deviceService = deviceService;
        this.jwtService = jwtService;
    }

    @PostMapping("/device/register")
    public ResponseEntity<AuthResponse> registerDevice(@Valid @RequestBody DeviceRegistrationRequest req) {
        DeviceEntity device = deviceService.registerDevice(req.userId(), req.deviceId(), req.deviceName());
        String token = jwtService.generateToken(req.userId(), req.deviceId(), device.getState());

        return ResponseEntity.ok(new AuthResponse(
                token,
                req.userId(),
                req.deviceId(),
                device.getState(),
                900000L
        ));
    }

    @PostMapping("/device/verify")
    public ResponseEntity<AuthResponse> verifyDevice(@Valid @RequestBody DeviceVerificationRequest req) {
        // Step-up verification: upon successful challenge completion, mark device as TRUSTED
        DeviceEntity device = deviceService.verifyDevice(req.userId(), req.deviceId());
        String token = jwtService.generateToken(req.userId(), req.deviceId(), DeviceState.TRUSTED);

        return ResponseEntity.ok(new AuthResponse(
                token,
                req.userId(),
                req.deviceId(),
                DeviceState.TRUSTED,
                900000L
        ));
    }
}
