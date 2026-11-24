package org.keyfort.server.controller;

import org.keyfort.core.Crypto;
import org.keyfort.server.dto.CryptoFactsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes core cryptographic facts and parameters directly to clients.
 */
@RestController
@RequestMapping("/api/crypto-facts")
public class CryptoFactsController {

    @GetMapping
    public CryptoFactsResponse getCryptoFacts() {
        return new CryptoFactsResponse(
                "PBKDF2WithHmacSHA256",
                Crypto.PBKDF2_ITERATIONS,
                "AES-256-GCM",
                "HKDF-SHA256",
                "RSA-3072-OAEP",
                128,
                64,
                "SHA-256",
                "SHA-256",
                "HmacSHA256",
                300
        );
    }
}
