# KeyFort

KeyFort is a credential vault featuring broker-assisted ephemeral guest passes, target site connectors, and automatic post-login key rotation. The server can see site names and usernames, never passwords. KeyFort does not claim full zero-knowledge: site names stay readable so the server can select the appropriate site connector, and username encryption is planned as future work. Master secrets and entry passwords remain strictly client-side and encrypted under keys the server never holds.

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

For threat boundaries, attacker profiles, and explicit limitations (such as active remote sessions and optical recording during live display), refer to [docs/THREAT_MODEL.md](docs/THREAT_MODEL.md).

## Repository Structure

```text
KeyFort/
├── keyfort-core/          # Pure Java 17 cryptographic engine and state machine
├── keyfort-demo-site/     # Cooperating mock service implementing connector SPI
├── keyfort-server/        # Spring Boot 3 REST API, persistence, and orchestration
├── keyfort-android/       # Native Android client (Java 17, XML, Fragments)
├── web/                   # Self-contained vanilla JS Web UI
├── scripts/               # One-click verification, execution, and test runners
├── docs/                  # Architectural specs, threat models, and viva documentation
├── build.gradle           # Multi-project root build definition
├── settings.gradle        # Gradle project structure configuration
├── gradle.properties      # Build flags and JVM configurations
├── gradlew / gradlew.bat  # Gradle wrapper
├── .gitattributes         # Line-ending and attribute configurations
├── .gitignore             # Git exclusion rules
├── README.md              # Project overview
├── SECURITY.md            # Vulnerability disclosure policy
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

Architectural decisions and trade-offs are logged in [docs/DECISIONS.md](docs/DECISIONS.md). Technical deep-dive and viva preparation questions are available in [docs/EXPLAINER.md](docs/EXPLAINER.md), live presentation guidance in [docs/DEMO_SCRIPT.md](docs/DEMO_SCRIPT.md), and final verification proofs in [docs/FINAL_REPORT.md](docs/FINAL_REPORT.md).

## License

This project is licensed under the [MIT License](LICENSE).
