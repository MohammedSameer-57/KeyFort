package org.keyfort.server.controller;

import jakarta.validation.Valid;
import org.keyfort.server.dto.VaultSyncRequest;
import org.keyfort.server.dto.VaultSyncResponse;
import org.keyfort.server.service.VaultSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/vault")
public class VaultController {

    private final VaultSyncService vaultSyncService;

    public VaultController(VaultSyncService vaultSyncService) {
        this.vaultSyncService = vaultSyncService;
    }

    @GetMapping("/sync")
    public ResponseEntity<VaultSyncResponse> getVaultSync(Authentication auth) {
        String userId = (String) auth.getPrincipal();
        return vaultSyncService.getVault(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/sync")
    public ResponseEntity<VaultSyncResponse> putVaultSync(
            @Valid @RequestBody VaultSyncRequest req,
            Authentication auth
    ) {
        String authenticatedUser = (String) auth.getPrincipal();
        if (!authenticatedUser.equals(req.userId())) {
            return ResponseEntity.status(403).build();
        }

        VaultSyncResponse synced = vaultSyncService.updateVault(req);
        return ResponseEntity.ok(synced);
    }

    public record RotationAckRequest(String deviceId, int newKeyVersion) {}

    @PostMapping("/rotation/ack")
    public ResponseEntity<Map<String, Object>> acknowledgeRotation(
            @RequestBody RotationAckRequest req,
            Authentication auth
    ) {
        String userId = (String) auth.getPrincipal();
        boolean acknowledged = vaultSyncService.acknowledgeRotation(userId, req.deviceId(), req.newKeyVersion());

        if (acknowledged) {
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "newKeyVersion", req.newKeyVersion()));
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}
