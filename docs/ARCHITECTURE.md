# KeyFort System Architecture

KeyFort provides ephemeral, single-use credential access to cooperating web services without exposing the vault owner's master secret, permanent passwords, or encryption keys.

## 1. System Modules

- **keyfort-core**: Pure Java 17 cryptographic engine. Implements PBKDF2 key derivation (600,000 iterations), AES-256-GCM encryption, RFC 5869 HKDF per-entry key derivation, RSA-3072-OAEP recovery key wrapping, HMAC-SHA256 canonical request signing, and an append-only hash-chained ledger. Compatible with Android < API 34.
- **keyfort-demo-site**: Cooperating mock target site (e.g. CinemaStream Family). Implements the `SiteConnector` SPI, single-use login ticket verification, dual session lifetimes (10-minute hard TTL, 5-minute sliding idle timeout), and remote session kill-switch termination.
- **keyfort-server**: Spring Boot 3 orchestration server. Manages device risk analysis, optimistic-locking guest pass transitions (`CREATED -> ISSUED -> REDEEMED -> CONSUMED -> BURNED`), connector dispatch, and zero-knowledge vault sync with post-login rotation flagging.
- **keyfort-android**: Native Android application (Java 17, XML, Fragments, Room database). Performs client-side encryption, biometric authentication, and local key renewal at unlock when `rotationPending` is flagged.
- **web/**: Self-contained, accessible vanilla JS client implementing the live interactive proof at `/demo`.

---

## 2. Request Flow Architecture

```text
[Owner Device]             [KeyFort Server]            [Target Site]           [Friend / Copier]
      |                           |                          |                         |
 1. Create Link (10m TTL) -------->                          |                         |
      |                     Store Hash & Issue               |                         |
      |<-- Return URL ------------|                          |                         |
      |                           |                          |                         |
 2. Send Link to Friend -------------------------------------------------------------->|
      |                           |                          |                         |
 3. Present Link (Redeem) <------------------------------------------------------- Redeem Link
      |                     Validate & Broker Ticket -------->                         |
      |                           |                    Issue One-Time Ticket           |
      |                           |<-------------------------|                         |
      |                     Return Redirect URL -------------------------------------->|
      |                           |                          |                         |
 4. Confirm Login --------------------------------------> Webhook                      |
      |                           |<-- Signed Webhook -------|                         |
      |                     Revoke Ticket & Burn Pass ------>| Revoke Ticket           |
      |                     Flag Vault: rotationPending      |                         |
      |                           |                          | Establish Guest Session |
      |                           |                          |<----------------------->|
 5. Copier Attempts Replay <------------------------------------------------------ Replay Attempt
      |                     Check State (BURNED)             |                         |
      |                     REJECT: "Already Used" ----------------------------------->| Refused
      |                           |                          |                         |
 6. Owner Ends Session ----------->                          |                         |
      |                     Connector Kill-Switch ---------->| Terminate Session       |
      |                           |                          |                         |
 7. Next Vault Unlock ------------>                          |                         |
      |                     Fetch Vault (rotationPending)    |                         |
      |   Rotate DEK Locally      |                          |                         |
      |   Re-encrypt Entries      |                          |                         |
      |   Bump Version to 2       |                          |                         |
      |-- Upload Rotated Vault -->|                          |                         |
      |                     Clear rotationPending            |                         |
```

---

## 3. Core Architectural Boundaries

1. **Zero-Knowledge Password Sealing**: Passwords are encrypted exclusively on client endpoints using HKDF-derived keys. The server stores ciphertext blobs only.
2. **Metadata Transparency**: Site names remain readable to allow server-side connector routing. Usernames remain readable to facilitate client list views without decrypting all entries.
3. **Single-Use State Machine**: Ephemeral tokens transition once through an atomic compare-and-set sequence, preventing concurrency races and credential replay.
4. **Decoupled Live Session**: Burning a guest pass initiates client key renewal but preserves the friend's active browsing session until hard TTL, idle expiration, or owner kill-switch termination.
