package org.keyfort.server.service;

import org.keyfort.core.orchestration.Orchestrator;
import org.keyfort.server.dto.EntryDto;
import org.keyfort.server.dto.VaultSyncRequest;
import org.keyfort.server.dto.VaultSyncResponse;
import org.keyfort.server.entity.EntryEntity;
import org.keyfort.server.entity.VaultEntity;
import org.keyfort.server.repository.EntryRepository;
import org.keyfort.server.repository.VaultRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Manages zero-knowledge ciphertext synchronization between client and server.
 * THE SERVER NEVER HANDLES PLAINTEXT PASSWORDS OR MASTER KEYS.
 */
@Service
public class VaultSyncService {

    private final VaultRepository vaultRepository;
    private final EntryRepository entryRepository;
    private final LedgerService ledgerService;

    public VaultSyncService(
            VaultRepository vaultRepository,
            EntryRepository entryRepository,
            LedgerService ledgerService
    ) {
        this.vaultRepository = vaultRepository;
        this.entryRepository = entryRepository;
        this.ledgerService = ledgerService;
    }

    @Transactional(readOnly = true)
    public Optional<VaultSyncResponse> getVault(String userId) {
        return vaultRepository.findById(userId).map(v -> {
            List<EntryEntity> entries = entryRepository.findByUserId(userId);
            List<EntryDto> entryDtos = entries.stream()
                    .map(e -> new EntryDto(
                            e.getId(),
                            e.getSite(),
                            e.getUsername(),
                            Base64.getEncoder().encodeToString(e.getSealedPassword()),
                            e.getKeyVersion()
                    ))
                    .toList();

            return new VaultSyncResponse(
                    v.getUserId(),
                    v.getIterations(),
                    Base64.getEncoder().encodeToString(v.getRecoveryPub()),
                    Base64.getEncoder().encodeToString(v.getKdfSalt()),
                    Base64.getEncoder().encodeToString(v.getWrappedDek()),
                    Base64.getEncoder().encodeToString(v.getRecoveryWrappedDek()),
                    v.getKeyVersion(),
                    v.isRotationPending(),
                    entryDtos
            );
        });
    }

    @Transactional
    public VaultSyncResponse updateVault(VaultSyncRequest req) {
        Instant now = Instant.now();

        byte[] recoveryPub = Base64.getDecoder().decode(req.recoveryPubBase64());
        byte[] kdfSalt = Base64.getDecoder().decode(req.kdfSaltBase64());
        byte[] wrappedDek = Base64.getDecoder().decode(req.wrappedDekBase64());
        byte[] recoveryWrappedDek = Base64.getDecoder().decode(req.recoveryWrappedDekBase64());

        VaultEntity vault = vaultRepository.findById(req.userId())
                .orElseGet(() -> new VaultEntity(
                        req.userId(),
                        req.iterations(),
                        recoveryPub,
                        kdfSalt,
                        wrappedDek,
                        recoveryWrappedDek,
                        req.keyVersion(),
                        req.rotationPending(),
                        now
                ));

        vault.setIterations(req.iterations());
        vault.setRecoveryPub(recoveryPub);
        vault.setKdfSalt(kdfSalt);
        vault.setWrappedDek(wrappedDek);
        vault.setRecoveryWrappedDek(recoveryWrappedDek);
        vault.setKeyVersion(req.keyVersion());
        vault.setRotationPending(req.rotationPending());
        vault.setUpdatedAt(now);
        vaultRepository.save(vault);

        // Replace/update entries
        entryRepository.deleteByUserId(req.userId());
        if (req.entries() != null) {
            List<EntryEntity> entities = req.entries().stream()
                    .map(dto -> new EntryEntity(
                            dto.id(),
                            req.userId(),
                            dto.site(),
                            dto.username(),
                            Base64.getDecoder().decode(dto.sealedPasswordBase64()),
                            dto.keyVersion(),
                            now
                    ))
                    .toList();
            entryRepository.saveAll(entities);
        }

        ledgerService.append("VAULT_SYNCED", Orchestrator.meta(req.userId(), "sync", "v" + req.keyVersion()));

        return getVault(req.userId()).orElseThrow();
    }

    @Transactional
    public boolean acknowledgeRotation(String userId, String deviceId, int newKeyVersion) {
        return vaultRepository.findById(userId).map(v -> {
            int oldVersion = v.getKeyVersion();
            v.setKeyVersion(newKeyVersion);
            v.setRotationPending(false);
            v.setUpdatedAt(Instant.now());
            vaultRepository.save(v);

            ledgerService.append("KEY_VERSION_CHANGED",
                    Orchestrator.meta(userId, deviceId, "v" + oldVersion + "->v" + newKeyVersion));
            return true;
        }).orElse(false);
    }
}
