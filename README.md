# KeyFort

KeyFort is a credential vault featuring broker-assisted ephemeral guest passes, client-side encrypted passwords, and post-login key rotation.

The server can see site names and usernames (site names are required to route requests to the appropriate connector SPI), but never has access to plaintext passwords or vault encryption keys. KeyFort does not claim full zero-knowledge confidentiality over metadata.

## System Architecture

The project consists of three primary modules:

- **keyfort-core**: Pure Java 17 cryptographic engine. Implements PBKDF2-HMAC-SHA256 (600,000 iterations), AES-256-GCM authenticated encryption, RFC 5869 HKDF-SHA256 key derivation, RSA-3072-OAEP recovery key wrapping, and an append-only hash-chained ledger. The module maintains compatibility with Android runtimes below API 34 by avoiding `java.util.HexFormat`.
- **keyfort-server**: Spring Boot 3 web application providing REST endpoints for device registration, risk-based access evaluation, hash-chained ledger auditing, and encrypted vault payload synchronization.
- **keyfort-android**: Native Android application implemented using Java 17, XML layouts, AndroidX Fragments, Room persistence, and Android Keystore biometric authentication.

## Product Concept: Ephemeral Guest Passes

KeyFort provides a mechanism to grant temporary, single-use access to an account on a third-party service without revealing the owner's permanent password.

1. **Brokerage via Connector SPI**: For cooperating sites integrating with the KeyFort connector interface, the site provisions a short-lived temporary credential.
2. **Single-Use Execution**: The guest redeems a high-entropy ephemeral token to authenticate on the target service.
3. **Revocation and Rotation**: Upon receipt of a verified login webhook from the target site, the temporary credential is revoked, the token is burned, and KeyFort requests an atomic vault key rotation.
4. **Assisted Mode**: For target sites lacking connector integration, KeyFort operates in an assisted mode that prompts the owner with an explicit reminder to rotate the real password after sharing.

For threat boundaries, attacker profiles, and explicit limitations (such as active remote sessions and optical recording during live display), refer to [THREAT_MODEL.md](THREAT_MODEL.md).

## Prototype and Simulation Notice

The visual demonstration available in `preview.html` and the GitHub Pages deployment at `https://mohammedsameer-57.github.io/KeyFort/` are simulation prototypes only. They illustrate the target workflow and do not communicate with the backend server or execute cryptographic rotation. Integration of the live proof mode is scheduled for Phase 7.

## Repository Structure

```text
KeyFort/
├── keyfort-core/          # Java 17 cryptographic primitives and state machine
├── keyfort-server/        # Spring Boot 3 REST API and persistence layer
├── keyfort-android/       # Android application (Java, XML, Fragments)
├── docs/
│   ├── API.md             # REST API specification
│   ├── DECISIONS.md       # Architectural Decision Records (ADRs)
│   ├── RUNNING.md         # Compilation, test execution, and deployment instructions
│   └── index.html         # Hosted simulation prototype
├── scripts/
│   ├── START_KEYFORT.bat  # Windows Batch server launcher
│   ├── START_KEYFORT.ps1  # Windows PowerShell server launcher
│   ├── OPEN_DEMO.bat      # Browser launcher for simulation prototype
│   └── SHARE_TO_PHONE.bat # SSH reverse port forward for mobile network testing
├── preview.html           # Standalone browser simulation prototype
├── Dockerfile             # Multi-stage container definition for keyfort-server
├── THREAT_MODEL.md        # System threat model and non-goals
├── CONTRIBUTING.md        # Contribution guidelines
├── SECURITY.md            # Vulnerability reporting policy
└── LICENSE                # MIT License
```

## Build and Execution

Operational procedures for executing tests, starting the server, and building artifacts are documented in [docs/RUNNING.md](docs/RUNNING.md).

Quick test run:

```bash
# Windows
.\gradlew.bat test

# Linux / macOS
./gradlew test
```

Architectural decisions and trade-offs are logged in [docs/DECISIONS.md](docs/DECISIONS.md).

## License

This project is licensed under the [MIT License](LICENSE).
