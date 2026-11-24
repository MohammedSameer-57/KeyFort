# KeyFort Architectural Explainer & Viva Preparation

## 1. End-to-End Guest Pass Lifecycle Walkthrough

```
Owner Client                    KeyFort Server               Site Connector             Target Site
    |                                 |                             |                        |
 1. | POST /api/guest-passes -------->|                             |                        |
    | (entryId, scope, ttl)           | (IssuedToken.create)        |                        |
    |                                 | 2. provisionTicket -------->|                        |
    |<-- 201 Created (id, rawToken) --|                             |                        |
    |                                 |                             |                        |
    |                                 | 3. POST /redeem (rawToken) -|                        |
    |                                 | (atomic CAS to CONSUMED)    |                        |
    |                                 |---------------------------->| 4. Redirect with ticket|
    |                                 |                             |----------------------->|
    |                                 |                             |<-- 5. Establish Session|
    |                                 | 6. POST /connector/events <-| (HMAC-SHA256 signed)   |
    |                                 | (atomic CAS to BURNED;      |                        |
    |                                 |  set rotationPending=true)  |                        |
    |                                 |                             |                        |
 7. | GET /vault/sync --------------->|                             |                        |
    |<-- rotationPending=true --------|                             |                        |
 8. | Vault.unlock() rotates keys     |                             |                        |
 9. | PUT /vault/sync (clears flag) ->|                             |                        |
```

1. **Pass Creation**: In `GuestPassDialog.java`, the user selects duration and calls `POST /api/guest-passes`. `GuestPassController.createGuestPass` invokes `GuestPassOrchestrationService.create()`. It verifies ownership via `EntryRepository`, invokes `GuestPassService.create()`, provisions a ticket on `SiteConnector.provisionTicket()`, persists `GuestPassEntity` (storing only SHA-256 of raw secret), records `GUEST_PASS_ISSUED` in `LedgerService`, and returns `CreateGuestPassResponse(id, rawToken, state, ...)`.
2. **Redemption**: Guest opens link `GET /g/{token}`. Browser invokes `POST /api/guest-passes/redeem`. `RedeemRateLimiter` enforces IP throttling. `GuestPassOrchestrationService.redeem()` verifies token hash, verifies state is `ISSUED` and not expired, performs atomic CAS transition to `CONSUMED`, and returns target redirect URL with ticket handle.
3. **Session Establishment & Webhook**: Target site (`DemoSiteController.consumeTicket()`) validates ticket against `DemoTicketService`, creates `GuestSession` (hard TTL 10m, idle 5m), and fires signed asynchronous webhook event (`POST /api/connector/events`).
4. **Pass Invalidation & Burn**: `GuestPassController.handleConnectorEvent` validates timestamp freshness, nonce uniqueness, and HMAC-SHA256 signature using `HmacSigner.verify()`. `GuestPassOrchestrationService.handleWebhookEvent()` revokes ticket via `connector.revokeTicket()`, performs atomic CAS transition from `CONSUMED` to `BURNED`, flags `rotationPending = true` on `VaultEntity`, records `GUEST_PASS_BURNED` in ledger, and stores `sessionId`.
5. **Client-Side Rotation**: When the owner unlocks (`Vault.unlock(password)`), `Vault.rotateKeys()` generates a fresh DEK, re-encrypts all entries with fresh random nonces and incremented `keyVersion`, re-wraps DEK under KEK, and clears `rotationPending`. Client uploads new ciphertext via `PUT /vault/sync`.

---

## 2. Top 15 Class and Method Map

| Class | Module | Role & Architectural Justification |
| :--- | :--- | :--- |
| `org.keyfort.core.Vault` | `keyfort-core` | Core cryptographic aggregate root; manages encrypted entries, DEK wrapping, session unlocking, and atomic key rotation. |
| `org.keyfort.core.Crypto` | `keyfort-core` | Low-level primitives (PBKDF2-HMAC-SHA256, AES-256-GCM AEAD, RSA key-wrapping, secure zeroization via `Crypto.wipe()`). |
| `org.keyfort.core.guestpass.GuestPass` | `keyfort-core` | Immutable domain record modeling state machine transitions (`ISSUED -> CONSUMED -> BURNED`) with optimistic locking versions. |
| `org.keyfort.core.guestpass.GuestPassService` | `keyfort-core` | Core business logic coordinating pass issuance, redemption validation, and state progression decoupled from HTTP or JPA. |
| `org.keyfort.core.connector.SiteConnector` | `keyfort-core` | SPI interface defining contract for target site ticket provisioning, revocation, session inspection, and remote termination. |
| `org.keyfort.core.connector.HmacSigner` | `keyfort-core` | Canonical request builder and HMAC-SHA256 signature verifier with clock-skew enforcement for webhook authentication. |
| `org.keyfort.server.orchestration.controller.GuestPassController` | `keyfort-server` | REST controller exposing `/api/guest-passes`, redemption, webhook ingestion, and session lifecycle endpoints. |
| `org.keyfort.server.orchestration.service.GuestPassOrchestrationService` | `keyfort-server` | Transactional orchestrator integrating core domain services, site connectors, rate limiters, audit ledger, and JPA repositories. |
| `org.keyfort.server.orchestration.ratelimit.RedeemRateLimiter` | `keyfort-server` | In-memory token bucket rate limiter with trusted reverse-proxy IP resolution to prevent brute-force attacks on redemption. |
| `org.keyfort.server.service.LedgerService` | `keyfort-server` | Append-only audit log maintaining a cryptographic SHA-256 hash chain of critical security events. |
| `org.keyfort.server.security.JwtService` | `keyfort-server` | Stateless authentication service enforcing fail-fast validation of 256-bit secrets on startup outside dev/test. |
| `org.keyfort.server.orchestration.service.DemoRunnerService` | `keyfort-server` | Demo profile presentation orchestrator managing simulated owner, friend, and copier lifecycles with real PBKDF2 unlock gate. |
| `org.keyfort.demo.controller.DemoSiteController` | `keyfort-demo-site` | Mock cooperating site simulating ticket validation, session tracking, kill-switch handling, and webhook delivery. |
| `org.keyfort.android.ui.GuestPassDialog` | `keyfort-android` | Android dialog fragment managing duration selection, link generation, `FLAG_SECURE` window binding, and 2s status polling. |
| `org.keyfort.android.ui.GuestPassStateResolver` | `keyfort-android` | Pure Java state machine mapper transforming server pass/session statuses into UI display states. |

---

## 3. Key Java and Software Engineering Concepts

- **Java Records (`record`)**: Immutable value objects with generated accessors, `equals()`, and `hashCode()`. Used extensively for DTOs (`IssuedToken`, `Entry`, `CreateGuestPassRequest`, `DemoStateDto`).
- **Atomic Compare-And-Set (CAS)**: Optimistic concurrency control via `GuestPassStore.compareAndSet(id, expectedVersion, updated)` preventing race conditions between concurrent redeemers.
- **Service Provider Interface (SPI)**: Decoupled target site integration via `SiteConnector` interface, enabling new site adapters without modifying server core.
- **Cryptographic Memory Zeroization**: Overwriting sensitive key material in memory (`Arrays.fill(key, (byte) 0)`) via `Crypto.wipe()` inside `finally` blocks to defeat heap dump inspections.
- **Fail-Fast Configuration**: `JwtService` validates secret presence and minimum 256-bit entropy at Spring container startup.
- **Spring `@Profile` Segregation**: Production endpoints are isolated; simulation harnesses (`DemoRunnerController`) only exist under `@Profile("demo")` and return 404 otherwise.
- **JPA Static Schema Validation**: Production uses `ddl-auto: validate` backed by version-controlled PostgreSQL `schema.sql` rather than runtime auto-generation.

---

## 4. Architectural Glossary

- **KEK (Key Encryption Key)**: 256-bit key derived client-side via PBKDF2 from user master password; wraps the DEK.
- **DEK (Data Encryption Key)**: Random 256-bit symmetric key that directly encrypts vault entries; never leaves client unencrypted.
- **AAD (Authenticated Additional Data)**: Non-secret metadata cryptographically bound to AES-GCM ciphertext (`id + keyVersion`); tampering triggers authentication tag failure.
- **PBKDF2**: Key derivation function with HMAC-SHA256 and 600,000 iterations to resist GPU-based brute-force attacks.
- **AES-GCM**: Galois/Counter Mode authenticated symmetric encryption providing both confidentiality and integrity.
- **TicketHandle**: Short-lived opaque reference issued by target site to exchange for an authenticated session.
- **GuestSession**: Ephemeral target site session bounded by hard TTL (10 min) and idle timeout (5 min).
- **CAS (Compare-And-Swap)**: Atomic concurrency primitive ensuring state transitions succeed only if version matches expected state.
- **Nonce**: Cryptographic number used once (96-bit for AES-GCM; random string for HMAC webhook replay prevention).
- **Idempotency**: Operation property where identical requests produce the same result without duplicate side-effects (webhook processing).
- **HMAC**: Hash-based Message Authentication Code verifying message integrity and authenticity between server and connector.
- **Zero-Knowledge**: Architectural property where the server never possesses master keys, vault DEK, or entry passwords.
- **Ephemeral Credential**: Single-use token that self-destructs after consumption or timeout.
- **Broker**: Intermediary coordinating access delegation between owner device and target site without storing permanent secrets.

---

## 5. Top 20 Viva Defense Questions & Answers

1. **Why not send the password directly?** Direct transmission exposes permanent credentials to the recipient, clipboard loggers, shoulder-surfing, and network transit. KeyFort delegates ephemeral, short-lived session tickets instead.
2. **What prevents someone who copied the link from using it?** Strict single-use CAS state transition. When the friend redeems, state transitions atomically to `CONSUMED`. Subsequent requests with copied links are rejected.
3. **What if the copier opens the link before the friend?** First use wins. The pass is consumed and burned, notifying the owner. The owner immediately terminates the session via the remote kill-switch.
4. **What prevents replay attacks on webhooks?** Signed webhook requests include a timestamp and unique nonce. Nonces are recorded in `processed_events`; duplicates are rejected, and timestamps outside clock skew (30s) fail verification.
5. **Why rotate the vault key if the guest pass was read-only?** Defense-in-depth: rotating the DEK and re-encrypting entries ensures that even if an entry secret leaked out-of-band during the session, the permanent stored key is refreshed.
6. **What if the KeyFort server database is completely compromised?** The server stores only ciphertext, KDF salt, and SHA-256 hashes of tokens. Without the owner's master password, the attacker cannot decrypt any stored password.
7. **How does KeyFort prevent timing attacks on token lookup?** `GuestPassService` performs constant-time comparisons (`MessageDigest.isEqual()`) and executes dummy hashing on unknown pass IDs to ensure identical response timing shapes.
8. **Why use H2 in test and PostgreSQL in production?** H2 enables fast, isolated in-memory integration testing without external dependencies. PostgreSQL provides robust transactional concurrency and persistent production durability.
9. **How does the server authenticate incoming webhooks?** The connector signs requests with HMAC-SHA256 over canonical method, URI, timestamp, nonce, and body. The server verifies this with a shared secret.
10. **Why are KEK and DEK decoupled?** Changing the master password requires only re-wrapping the 32-byte DEK under a new KEK (instantaneous), rather than re-encrypting thousands of vault entries.
11. **Why does the server see site names and usernames?** Site names are required for routing to the correct `SiteConnector` SPI. Usernames are stored for client list rendering. Only passwords are encrypted end-to-end. Full zero-knowledge over metadata is not claimed.
12. **What is the difference between CONSUMED and BURNED?** `CONSUMED` means the ticket was handed to the guest browser. `BURNED` means the target site verified login, the ticket was revoked, and vault rotation was flagged.
13. **Does BURNED terminate the friend's active browsing session?** No. BURNED revokes the one-time ticket and flags vault rotation, but the live session remains active until TTL expiry or explicit owner kill-switch termination.
14. **How does the owner kill-switch work?** Owner calls `DELETE /api/guest-passes/{id}/session`. The server looks up the connector and invokes `connector.terminateSession(sessionId)`.
15. **Why is cleartext traffic restricted in Android?** `network_security_config.xml` permits cleartext traffic exclusively to `10.0.2.2` (emulator host) and localhost for local development, blocking arbitrary insecure cleartext HTTP.
16. **How does client-side rotation execute when `rotationPending` is true?** The server cannot rotate keys because it lacks the DEK. When the owner unlocks, `Vault.unlock()` detects `rotationPending`, invokes `Vault.rotateKeys()`, and uploads the fresh vault.
17. **How does the ledger guarantee tamper evidence?** Each ledger block contains SHA-256(`blockIndex + prevHash + timestamp + eventType + payload`). If any historical entry is altered, the chain breaks.
18. **Why PBKDF2 with 600,000 iterations?** 600,000 rounds of HMAC-SHA256 align with current OWASP recommendations, significantly increasing the computational cost of offline dictionary attacks.
19. **How does rate limiting protect redemption?** `RedeemRateLimiter` restricts IP requests using a token bucket (max 5 burst, 1 req/sec refill) and resolves IPs only from configured trusted proxies.
20. **Why are test accessors in `Vault` package-private?** Methods like `lastCreatedSession()` and `keyVersion()` are restricted to package-private visibility to prevent production callers from violating zero-knowledge encapsulation while allowing strict verification in tests.
