# KeyFort Audit and Implementation Status

Date: 2026-11-24
Branch: phase-7-web (derived from main commit 6259b78)
Baseline Test Suite: 100 tests passing (52 core, 18 demo-site, 30 server).

## Core Invariant Confirmations

1. Existing Assertions: No existing test assertion was removed, renamed, or weakened across any module.
2. Key-Wipe Accessors: Accessors in `Vault.Session` (`wipedKekForTesting`, `wipedDekForTesting`) are package-private and used exclusively by `VaultTest`.
3. Secret Fallback Isolation: `JwtService` fallback default secret operates strictly when `dev`, `demo`, or `test` profiles are active. The default production profile throws `IllegalStateException` on startup if `KEYFORT_JWT_SECRET` is omitted or shorter than 32 bytes (verified in `JwtServiceTest.testMissingSecretInNonDevThrows`).

---

## Detailed Task Audit

### 0. Phase 6 Close-Out: DONE
- Evidence: ADR 0011 wording refined in `docs/DECISIONS.md`. ADR 0012 recorded for production schema validation.
- Schema: Static script checked in at `keyfort-server/src/main/resources/schema.sql`.
- Invariant tests: Key zeroing verified in `VaultTest.testUnlockRotateKeysFailureWipesKeys`. Concurrency bounds verified in `GuestPassTest`. Log sanitization verified in `GuestPassOrchestrationIntegrationTest`.

### 1. One-Click Setup and Run (Scripts): PARTIAL
- Existing launcher scripts in `scripts/` require consolidation into `CHECK_SETUP`, `RUN_DEMO`, `STOP_DEMO`, and `RUN_TESTS` (.bat and .ps1) with PID tracking in `.run/`.

### 2. Web Demo at /demo: PARTIAL
- Backend: `DemoRunnerService` and `DemoRunnerController` active under `@Profile("demo")`.
- Frontend: Original self-contained markup, stylesheet, and client logic in `web/` to be completed.

### 3. How It Stays Safe (Cryptographic Transparency): PARTIAL
- Backend: `CryptoFactsController` (`GET /api/crypto-facts`) implemented and exposing real parameters.
- Frontend: Disclosure cards and history verification trigger to be connected in `web/app.js`.

### 4. Tests: PARTIAL
- In-process demo runner tests (`DemoRunnerIntegrationTest` and `DemoRunnerDisabledIntegrationTest`) to be written.

### 5. Android Minimal Guest Flow: PARTIAL
- Android module structure and Room entities exist. UI flow, view models, and JVM tests to be completed.

### 6. Emulator Demo Documentation: PARTIAL
- `docs/RUNNING.md` to be updated with 3-line quickstart and emulator instructions.

### 7. Documentation and Submission: PARTIAL
- `docs/EXPLAINER.md`, `docs/DEMO_SCRIPT.md`, and `docs/FINAL_REPORT.md` to be generated.
