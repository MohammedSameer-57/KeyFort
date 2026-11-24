# Architectural Decision Records

## 0001: Android SDK build guard and baseline test recording

### Status
Accepted

### Context
Running `./gradlew test` from the repository root failed on development environments lacking an Android SDK installation. The build failed with:
`Could not determine the dependencies of task ':keyfort-android:testDebugUnitTest'. SDK location not found.`

### Options Considered
1. Require all contributors and build environments to install the full Android SDK and configure `ANDROID_HOME` or `local.properties`.
2. Conditionally evaluate and include `:keyfort-android` in `settings.gradle` only when an Android SDK is detected via environment variables (`ANDROID_HOME`, `ANDROID_SDK_ROOT`) or `local.properties` (`sdk.dir`).

### Decision
Option 2. In `settings.gradle`, inspect environment variables and `local.properties`. If no valid Android SDK directory exists, log a single notice and omit `:keyfort-android` from the project tree. Environments with an Android SDK continue to build and test `:keyfort-android`.

### Trade-offs
Developers lacking the Android SDK cannot execute Android unit tests locally. Baseline test execution without Android SDK passes 32 tests across `keyfort-core` (27 tests) and `keyfort-server` (5 tests).

## 0002: Token format using composite identifier and unpadded Base64URL

### Status
Accepted

### Context
Guest pass tokens must be easily transmitted across chat applications, URLs, and QR codes without character escaping, while allowing O(1) store lookup without searching or decrypting all stored passes.

### Options Considered
1. Monolithic random token where the whole token is hashed; looking up requires either an inverted index or linear scans.
2. Composite `<id>.<secret>` format with a 64-bit random ID and a 128-bit random secret, both encoded as unpadded Base64URL strings.

### Decision
Option 2. The token consists of `<id>.<secret>`. The 64-bit ID serves as a public lookup key in storage. The 128-bit secret serves as the authentication credential.

### Trade-offs
The ID is exposed in URLs and transmission, but authentication relies strictly on the 128-bit secret. Only the SHA-256 hash of the secret is persisted.

## 0003: Plain SHA-256 hashing for 128-bit secret verification

### Status
Accepted

### Context
Stored token secrets must be protected at rest against database dumps, but token verification occurs on performance-sensitive login and redemption paths.

### Options Considered
1. Salted memory-hard key derivation functions (PBKDF2, Argon2, bcrypt).
2. Plain cryptographic SHA-256 hash without salt.

### Decision
Option 2. The secret is generated using 128 bits of cryptographic randomness (`SecureRandom`). Because 128 bits of entropy ($2^{128}$) renders rainbow tables and brute-force pre-image attacks computationally infeasible, slow memory-hard key stretching is unnecessary. Verification uses constant-time comparison via `MessageDigest.isEqual`.

### Trade-offs
Plain hashing is acceptable only for high-entropy machine-generated tokens ($>= 128$ bits), not for human-selected passwords.

## 0004: Immutable record design with explicit transition matrix

### Status
Accepted

### Context
Guest passes move through multiple states across their lifecycle. State transitions must be atomic, thread-safe, and prevent illegal lifecycle jumps.

### Options Considered
1. Mutable entity class with state-setting methods.
2. Immutable Java record where each transition validates the move against an explicit transition matrix, verifies clock expiry, and returns a new record with an incremented version.

### Decision
Option 2. `GuestPass` is an immutable record. Allowed transitions are defined in `ALLOWED_TRANSITIONS`. Illegal transitions throw typed exceptions (`IllegalTransitionException`, `GuestPassExpiredException`).

### Trade-offs
Creates a new record instance per transition, but eliminates race conditions and mutable state leaks.

## 0005: GuestPassStore interface with compareAndSet concurrency control

### Status
Accepted

### Context
When multiple concurrent callers attempt to redeem, revoke, or consume a guest pass simultaneously, exactly one caller must succeed and all others must be rejected.

### Options Considered
1. Synchronized blocks on in-memory collections or database table locks.
2. Optimistic locking via `compareAndSet(id, expectedVersion, newPass)`.

### Decision
Option 2. `GuestPassStore` defines `compareAndSet`. `InMemoryGuestPassStore` implements atomic CAS via `ConcurrentHashMap.compute`. In Phase 4, the server will implement this using JPA `@Version`.

### Trade-offs
Concurrent mutations result in `ConcurrentMutationException`, requiring callers to handle concurrency conflicts explicitly.

## 0006: Domain state lifecycle semantics and rotation trigger on BURNED

### Status
Accepted

### Context
Cooperating sites must broker single-use credentials and confirm login without exposing the vault Data Encryption Key (DEK). Vault rotation must be triggered at the right moment.

### Options Considered
1. Request vault key rotation upon token redemption (`REDEEMED`).
2. Request vault key rotation upon credential revocation (`BURNED`), after the site confirms consumption.

### Decision
Option 2. Lifecycle progression is `CREATED -> ISSUED -> REDEEMED -> CONSUMED -> BURNED`. Vault key rotation is requested through `Vault.requestRotation()` strictly when entering `BURNED`.

### Trade-offs
If a site fails to send a consumption confirmation or credential revocation, vault key rotation is deferred until the pass expires or is manually revoked.

## 0007: One-time login tickets via SiteConnector SPI and AssistedConnector fallback

### Status
Accepted

### Context
Sharing credentials directly exposes plaintext passwords to the recipient's device, enabling retention, screenshotting, or shoulder-surfing. The guest credential must never equal the owner's permanent password or expose the vault DEK.

### Options Considered
1. Provision temporary synthetic usernames and passwords injected into the target site via automated browser extensions or scraping.
2. Broker a cryptographic one-time login ticket (`TicketHandle`) directly with cooperating sites via a standardized SPI (`SiteConnector`), with an explicit assisted reminder mode (`AssistedConnector`) for unintegrated sites.

### Decision
Option 2. The guest credential is a single-use login ticket bound to an account and scope with a strict TTL. The recipient's browser receives only a ticketed redirect URL, never a plaintext password. This flow works end-to-end strictly on services implementing the connector SPI (e.g. `keyfort-demo-site`). Services lacking connector integration fall back to `AssistedConnector`, which generates no remote credentials and instructs the owner to rotate the permanent password manually.

### Trade-offs
End-to-end zero-knowledge guest authentication requires target site cooperation via the connector SPI. Unintegrated sites cannot automatically enforce single-use revocation or automated login webhooks.



## 0008: Server-side rotation-pending signaling for zero-knowledge client vault rotation

### Status
Accepted

### Context
When a guest pass reaches `BURNED`, the vault key must rotate to invalidate any previous state. However, the KeyFort server adheres to a zero-knowledge security architecture: the server never possesses the master password, KEK, or unencrypted DEK. Therefore, the server cannot re-encrypt vault entries directly.

### Options Considered
1. Store an escrow copy of the DEK on the server to allow server-side re-encryption.
2. Set a persistent `rotation_pending` flag on the vault entity; the owner's client observes this flag upon next unlock or sync, rotates the DEK client-side, re-encrypts all entries under the new DEK, updates the wrapped DEKs, and uploads the new vault state.

### Decision
Option 2. The server strictly sets `rotationPending = true` on the owner's `VaultEntity` when a guest pass transitions to `BURNED`. The owner's client application detects `rotationPending` during the next local unlock or sync, rotates the key locally using the master secret in memory, and submits the rotated vault bundle via `PUT /vault/sync`.

### Trade-offs
Vault rotation is deferred until the owner next opens their vault client. Until then, the server records the pending rotation status and refuses conflicting operations until the client synchronizes the new key version.

## 0009: Client IP resolution via trusted proxy verification for rate limiting

### Status
Accepted

### Context
`POST /api/guest-passes/redeem` is an unauthenticated endpoint rate-limited per IP and per pass ID to prevent brute-force attacks against high-entropy tokens. If `X-Forwarded-For` is accepted unconditionally from arbitrary HTTP clients, an attacker can trivially forge header values to bypass rate limiting or conduct denial-of-service against legitimate IP addresses.

### Options Considered
1. Always use `request.getRemoteAddr()`. Fails when deployed behind reverse proxies or load balancers as all traffic appears from internal proxy addresses.
2. Unconditionally trust `X-Forwarded-For`. Vulnerable to trivial spoofing by untrusted clients.
3. Validate immediate peer address (`request.getRemoteAddr()`) against configured trusted proxies (`keyfort.rate-limit.trusted-proxies`). Only extract client IP from `X-Forwarded-For` when arriving from a trusted proxy; otherwise, strictly use `request.getRemoteAddr()`.

### Decision
Option 3. The server inspects `request.getRemoteAddr()`. If and only if the peer IP is explicitly listed in `keyfort.rate-limit.trusted-proxies`, the client IP from `X-Forwarded-For` is extracted. Otherwise, `X-Forwarded-For` is discarded, and the direct socket address is used as the client identifier.

### Trade-offs
Deployments behind reverse proxies must configure `KEYFORT_TRUSTED_PROXIES` environment variable with proxy IP addresses or subnets.

## 0010: Database-backed guest session control, dual timeout model, and BURNED state decoupling

### Status
Accepted

### Context
When a guest completes single-use ticket login on a cooperating site, the site issues a guest session. If the session exists only in an in-memory browser cookie or servlet session without persistent database checks, an owner has no mechanism to revoke access before standard cookie expiration. Furthermore, when the KeyFort server observes login and transitions the pass to `BURNED` to initiate vault rotation, abruptly terminating the guest's active browser session would corrupt the user experience during a legitimate single-use visit.

### Options Considered
1. Store session status purely in signed JWT cookies or in-memory servlet sessions with no database check; kill-switch cannot immediately invalidate live sessions across instances.
2. Invalidate and terminate the live guest session immediately upon the pass reaching `BURNED`. Breaks the legitimate friend access workflow, as login instantly burns the pass and would prematurely kill the session before the friend can use the site.
3. Decouple pass rotation from live session lifecycle; enforce dual timeout (hard TTL maximum bound and idle sliding window timeout) with every request validated directly against `GuestSessionRepository`. Provide an authenticated owner kill switch (`POST /api/guest-passes/{id}/end-session`) invoking `DELETE /connector/sessions/{id}` with HMAC signatures for immediate session revocation.

### Decision
Option 3. The guest pass transition to `BURNED` signals post-login single-use consumption and client key rotation, but explicitly does NOT terminate the live session on the target site. The live session is governed by a hard TTL (default 10 minutes) and an idle timeout (default 5 minutes, sliding on access). Every request to the target site validates the session against the database repository (`GuestSessionRepository`) for immediate kill-switch effect. The vault owner can query live session status (`GET /api/guest-passes/{id}/session`) and immediately terminate it (`POST /api/guest-passes/{id}/end-session`) via connector HMAC API, appending an audit record to the ledger.

### Trade-offs
Target sites incur a database lookup on protected requests, offset by immediate kill-switch enforcement and precise session lifetime boundaries.

## 0011: Server hardening, fail-fast JWT validation, explicit CORS, and client-side rotation

### Status
Accepted

### Context
Zero-knowledge architectures require strict boundary enforcement: JWT secrets must not rely on production defaults, CORS policies must reject arbitrary origins, and server databases must be segregated between ephemeral in-memory engines for testing/development and production databases. Furthermore, client-side cryptographic rotation workflows must ensure that if key rotation encounters corrupted entry ciphertext during unlock, sensitive key material (`kek`, `dek`) is immediately zeroed in memory.

### Options Considered
1. Retain default hardcoded fallback for JWT secrets and open wildcard CORS to ease deployment.
2. Enforce fail-fast startup validation for `KEYFORT_JWT_SECRET` (requiring >= 32 bytes in production, allowing explicit dev-only default in dev/test profiles); configure an explicit origin-bound CORS policy; configure separate datasource profiles (PostgreSQL production vs H2 dev/test); wrap `Vault.unlock` rotation in try-catch with immediate key wiping on failure; and preserve plaintext `site`/`username` metadata with explicit threat documentation rather than breaking cross-module contracts.

### Decision
Option 2. The server fails fast on startup if `KEYFORT_JWT_SECRET` is missing or under 32 bytes (256 bits), reserving default secrets strictly for `dev` and `test` environments. CORS explicitly restricts allowed origins and headers. Datasource configuration defaults to PostgreSQL via environment variables (`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`), utilizing H2 only under `dev` and `test` profiles. In `keyfort-core`, `Vault.unlock` ensures that any failure in `rotateKeys()` immediately invokes `session.close()` to zero out `kek` and `dek` arrays. When `rotationPending` is observed during vault sync, the client unlocks and rotates locally, uploading the updated version 2 vault and clearing the server flag. Site stays readable because the server uses it to choose the connector; username encryption is future work.

### Trade-offs
Production environments require explicit environment variable provisioning (`KEYFORT_JWT_SECRET`, database credentials, and CORS origins). The server can see site names and usernames, never passwords. Full zero-knowledge is not claimed; site is necessary for connector dispatch and username encryption remains future work, as documented in `THREAT_MODEL.md`.

## 0012: Production database schema validation and static DDL script

### Status
Accepted

### Context
Using `hibernate.ddl-auto: update` in production environments introduces reliability and migration hazards, as uncontrolled schema changes can corrupt production data or mask breaking entity evolutions.

### Options Considered
1. Retain `hibernate.ddl-auto: update` across all profiles.
2. Enforce `hibernate.ddl-auto: validate` in the default/production profile backed by a checked-in, version-controlled schema script (`schema.sql`), while retaining ephemeral `update` strictly in `dev` and `test` profiles.

### Decision
Option 2. The production profile sets `spring.jpa.hibernate.ddl-auto: validate` and requires PostgreSQL tables to match the checked-in schema at `keyfort-server/src/main/resources/schema.sql`. Ephemeral H2 databases in `dev` and `test` profiles continue using automatic schema creation.

### Trade-offs
Production database migrations require manual execution of static DDL scripts, ensuring strict operational control and auditability before services start up.


