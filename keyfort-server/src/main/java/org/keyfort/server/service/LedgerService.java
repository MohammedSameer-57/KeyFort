package org.keyfort.server.service;

import org.keyfort.core.Crypto;
import org.keyfort.core.ledger.Ledger;
import org.keyfort.server.dto.LedgerBlockResponse;
import org.keyfort.server.dto.LedgerVerificationResponse;
import org.keyfort.server.entity.LedgerBlockEntity;
import org.keyfort.server.repository.LedgerBlockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class LedgerService {

    private final LedgerBlockRepository ledgerRepository;

    public LedgerService(LedgerBlockRepository ledgerRepository) {
        this.ledgerRepository = ledgerRepository;
    }

    @Transactional
    public synchronized LedgerBlockEntity append(String type, String payload) {
        Optional<LedgerBlockEntity> last = ledgerRepository.findTopByOrderBySeqDesc();
        long seq = last.map(b -> b.getSeq() + 1).orElse(0L);
        String prevHash = last.map(LedgerBlockEntity::getHash).orElse(Ledger.GENESIS);
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);

        String hash = Ledger.digest(seq, now, type, payload, prevHash);
        LedgerBlockEntity entity = new LedgerBlockEntity(seq, now, type, payload, prevHash, hash);
        return ledgerRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<LedgerBlockResponse> getAllBlocks() {
        return ledgerRepository.findAllByOrderBySeqAsc().stream()
                .map(b -> new LedgerBlockResponse(
                        b.getSeq(),
                        b.getAt(),
                        b.getType(),
                        b.getPayload(),
                        b.getPrevHash(),
                        b.getHash()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public LedgerVerificationResponse verify() {
        List<LedgerBlockEntity> chain = ledgerRepository.findAllByOrderBySeqAsc();
        String prev = Ledger.GENESIS;
        long expectedSeq = 0L;

        for (LedgerBlockEntity b : chain) {
            if (b.getSeq() != expectedSeq) {
                return new LedgerVerificationResponse(false, chain.size(),
                        "Sequence broken at block #" + b.getSeq() + " (expected #" + expectedSeq + ")");
            }
            if (!b.getPrevHash().equals(prev)) {
                return new LedgerVerificationResponse(false, chain.size(),
                        "Hash chain broken at block #" + b.getSeq() + ": prevHash mismatch");
            }
            String calculatedHash = Ledger.digest(b.getSeq(), b.getAt(), b.getType(), b.getPayload(), b.getPrevHash());
            if (!b.getHash().equals(calculatedHash)) {
                return new LedgerVerificationResponse(false, chain.size(),
                        "Tamper detected at block #" + b.getSeq() + ": block digest mismatch");
            }
            prev = b.getHash();
            expectedSeq++;
        }

        return new LedgerVerificationResponse(true, chain.size(), "Ledger intact and cryptographically verified");
    }
}
