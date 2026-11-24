package org.keyfort.server.dto;

/**
 * Technical cryptographic facts and parameters exposed directly from keyfort-core.
 */
public record CryptoFactsResponse(
        String kdfAlgorithm,
        int kdfIterations,
        String cipherAlgorithm,
        String hkdfAlgorithm,
        String recoveryKeyAlgorithm,
        int tokenSecretBits,
        int tokenIdBits,
        String tokenHashAlgorithm,
        String ledgerHashAlgorithm,
        String connectorHmacAlgorithm,
        long clockSkewAllowanceSeconds
) {}
