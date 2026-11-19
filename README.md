# 🛡️ KeyFort

> **A minimal, zero-knowledge password manager with built-in threat protection.**

<p align="center">
  <img src="https://img.shields.io/badge/Java-17-orange?style=flat-square&logo=openjdk" alt="Java 17" />
  <img src="https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen?style=flat-square&logo=springboot" alt="Spring Boot" />
  <img src="https://img.shields.io/badge/Android-Native-green?style=flat-square&logo=android" alt="Android" />
  <img src="https://img.shields.io/badge/Security-Zero--Knowledge-blueviolet?style=flat-square" alt="Zero-Knowledge" />
  <img src="https://img.shields.io/badge/License-MIT-blue?style=flat-square" alt="MIT" />
</p>

---

## 💡 What is KeyFort?

KeyFort is a private digital vault for your passwords where **only you hold the key**. 

Your master password never leaves your device. Everything is encrypted into scrambled ciphertext before syncing to the cloud, meaning even the server cannot read your data.

---

## ✨ Core Features

- 🔒 **Zero-Knowledge Privacy** — Data is encrypted on your device with AES-256-GCM. The backend only stores locked ciphertexts.
- 🛡️ **Threat Defense** — Automatically detects bot attacks and password guessing across multiple IPs.
- 📜 **Tamper-Proof Ledger** — Every security event is logged in a cryptographically chained audit log.
- 📱 **Minimal Mobile App** — Clean, distraction-free interface for your passwords and security settings.

---

## 📁 Project Structure

```text
KeyFort/
├── keyfort-core/       # Encryption engine & threat detection (Pure Java 17)
├── keyfort-server/     # Backend REST API (Spring Boot 3 + H2 Database)
├── keyfort-android/    # Native Android mobile app (Material 3)
├── preview.html        # Interactive browser preview & control center
└── START_KEYFORT.bat   # 1-click launcher for Windows
```

---

## 🚀 How to Run

### Option 1: One-Click (Windows)
Double-click **`START_KEYFORT.bat`** in this folder.

### Option 2: Terminal
Run this single command:

```bash
# Windows
gradlew.bat :keyfort-server:run

# macOS / Linux
./gradlew :keyfort-server:run
```

Once started, open:
- **Live Audit Ledger:** [http://localhost:8080/ledger/verify](http://localhost:8080/ledger/verify)
- **Interactive UI Preview:** Double-click **`preview.html`** in your browser.

---

## 🧪 Running Tests

To verify cryptographic rules and security checks:

```bash
gradlew.bat test       # Windows
./gradlew test         # macOS / Linux
```

---

## 📄 License
Licensed under the [MIT License](LICENSE).
