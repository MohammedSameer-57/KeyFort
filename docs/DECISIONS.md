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

