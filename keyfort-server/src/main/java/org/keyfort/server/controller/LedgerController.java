package org.keyfort.server.controller;

import org.keyfort.server.dto.LedgerBlockResponse;
import org.keyfort.server.dto.LedgerVerificationResponse;
import org.keyfort.server.service.LedgerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/ledger")
public class LedgerController {

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @GetMapping
    public ResponseEntity<List<LedgerBlockResponse>> getLedgerBlocks() {
        return ResponseEntity.ok(ledgerService.getAllBlocks());
    }

    @GetMapping("/verify")
    public ResponseEntity<LedgerVerificationResponse> verifyLedger() {
        return ResponseEntity.ok(ledgerService.verify());
    }
}
