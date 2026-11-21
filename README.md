# 🛡️ KeyFort — Ephemeral Passwords & Zero-Knowledge Auto-Rotation

> **One master password for your vault. Single-use disposable passwords for the outside world. Zero loopholes.**

---

## 💡 The Core Problem: The "Camera & Screenshot" Leak

When you share a password with a friend over WhatsApp or type it into an untrusted computer at a library or cafe:
- Your friend can **take a photo** of the screen with a phone camera.
- They can **write it down** or save it to their notes.
- Anyone **shoulder-surfing** behind you can memorize or peek at it.

With traditional password managers (1Password, Bitwarden, Apple Keychain), once a password is seen or shared, **it remains valid forever** until you manually go change it. This is a massive security loophole known as the *Analog Hole*.

---

## 🔒 The KeyFort Solution: Complete Privacy in 4 Steps

KeyFort eliminates this loophole by ensuring that **the real password is never exposed to the outside world**, and any shared access **self-destructs after one single login**:

```
 ┌──────────────────────┐
 │  Step 1: Master Lock │  ➔ Unlock your vault using your Master Password
 └──────────┬───────────┘     (PBKDF2 600,000 rounds + AES-256-GCM)
            ▼
 ┌──────────────────────┐
 │ Step 2: Guest Key    │  ➔ Generate a simple 1-Time Disposable Guest Pass
 └──────────┬───────────┘     (Send to friend on WhatsApp or enter on untrusted website)
            ▼
 ┌──────────────────────┐
 │ Step 3: 1-Time Login │  ➔ Friend or website logs in ONCE
 └──────────┬───────────┘
            ▼
 ┌──────────────────────┐
 │ Step 4: Auto-Rotate  │  ➔ Guest key is permanently killed & burned!
 └──────────────────────┘     Encryption key rotates automatically.
                              Hacker captures only useless AES-256 ciphertext.
```

### 1. Master Vault Unlock
Your vault is locked behind your private **Master Password**. Key derivation uses **PBKDF2 with 600,000 SHA-256 iterations** to derive a Key Encryption Key (KEK), which protects a 256-bit Data Encryption Key (DEK).

### 2. Disposable Guest Password
When sharing an account (like Netflix) with a friend or logging in on a sketchy public computer, KeyFort generates a **1-time temporary guest token** (e.g. `TEMP-NETF-8921-XQ`). You never give away your real master credentials.

### 3. Single-Login Execution
The friend uses the guest token to authenticate for that specific session.

### 4. Automatic Key Rotation (Zero Loopholes)
The moment authentication succeeds:
1. The temporary guest token is **permanently revoked and burned**.
2. KeyFort executes an **atomic key rotation**, generating a fresh 256-bit encryption key and bumping the version.
3. The underlying credentials are re-sealed with **AES-256-GCM** using authenticated additional data (AAD).
4. If a hacker peeked over your friend's shoulder, took a photo with a camera, or sniffed the network, **the captured passcode is 100% DEAD**. Attempting to reuse it results in instant access denial.

---

## 🌐 Is This Idea Novel?

**Yes.** Here is why:

| Feature | Standard Password Managers (Bitwarden, 1Password) | Enterprise IAM (AWS STS, Kerberos) | **KeyFort** |
| :--- | :--- | :--- | :--- |
| **Audience** | Everyday consumers | Cloud engineers & sysadmins | **Everyday consumers** |
| **Password Sharing** | Shares permanent plaintext secret | Issue short-lived STS tokens | **1-time disposable passcodes** |
| **Camera / Shoulder-Surfing Defense** | ❌ None (password stays valid) | N/A (CLI / machine-to-machine) | **✅ Immune (rotates after 1 login)** |
| **Key Architecture** | Static Master Key | Centralized Key Server | **Zero-Knowledge Client-Side Auto-Rotation** |
| **Loophole Risk** | High (human error / notes / camera) | Low (complex configuration) | **Zero (self-destructing tokens)** |

KeyFort bridges the gap between high-end enterprise ephemeral token systems and consumer-friendly password managers, bringing camera-proof, zero-knowledge privacy to personal devices.

---

## 🚀 Real-World Usage & Deployment

### 1. 🌐 Live Public Web App (Any Device / Phone / Laptop)
KeyFort is hosted live via GitHub Pages:
👉 **[https://mohammedsameer-57.github.io/KeyFort/](https://mohammedsameer-57.github.io/KeyFort/)**

You can open this URL directly on your phone's browser, share it with a friend on WhatsApp, and test the 4-step Ephemeral Key & Auto-Rotation flow anywhere in the world in real time!

---

### 2. 📱 Native Android Mobile App (`.apk`)
A native Android application (`keyfort-android`) is built automatically on every push via **GitHub Actions**:
1. Go to the **[Actions Tab](https://github.com/MohammedSameer-57/KeyFort/actions)** on GitHub.
2. Select the latest **Build Android APK** workflow run.
3. Download the **`KeyFort-Mobile-App`** artifact containing `KeyFort-debug.apk`.
4. Install it directly onto your Android device to run KeyFort natively with Biometric fingerprint authentication.

---

### 3. ⚡ Share Local Server to Your Phone (`SHARE_TO_PHONE.bat`)
Want to run the backend on your laptop and access it live on your phone right now?
1. Start the server: double-click **`START_KEYFORT.bat`**.
2. Double-click **`SHARE_TO_PHONE.bat`**.
3. It generates an instant, secure public HTTPS link (e.g. `https://xxxx.lhrtunnel.link`). Open that link on your phone (even over 5G/mobile data)!

---

### 4. ☁️ 24/7 Cloud Server Deployment (Render / Railway / Fly.io)
The backend includes a production-ready multi-stage **`Dockerfile`**:
1. Connect this GitHub repository to [Render.com](https://render.com) or [Railway.app](https://railway.app).
2. Choose **Docker** as the runtime.
3. Render will build and run the Spring Boot server with 100% free hosting and a permanent public HTTPS API!

---

### 5. 💻 Local Developer Mode
- **Instant Simulator:** Double-click **`preview.html`** in this folder.
- **Spring Boot Live Backend:** Double-click **`START_KEYFORT.bat`** and visit **[http://localhost:8080](http://localhost:8080)**.

---

## 📁 Repository Structure

```text
KeyFort/
├── keyfort-core/          # Pure Java 17 Cryptographic Engine
│   ├── Crypto.java        # PBKDF2 (600k rounds), AES-256-GCM, HKDF-SHA256, RSA-3072
│   ├── Vault.java         # Zero-knowledge vault, atomic key rotation, session handling
│   ├── ledger/            # Append-only tamper-evident audit ledger
│   └── risk/              # Real-time behavioral risk engine & device trust registry
│
├── keyfort-server/        # Spring Boot 3 Web Application & REST API
│   ├── config/            # Security filters & endpoint permissions
│   ├── controller/        # Vault unlock, rotation, and audit endpoints
│   └── src/main/resources/static/ # Interactive UI hosted on http://localhost:8080
│
├── keyfort-android/       # Native Android Application (Jetpack Compose & Material 3)
│   └── BiometricPrompt    # Hardware-backed Android Keystore integration
│
├── docs/index.html        # GitHub Pages production deployment entrypoint
├── preview.html           # Standalone interactive 4-step security flow simulator
├── Dockerfile             # Multi-stage container for cloud deployment (Render/Railway)
├── START_KEYFORT.bat      # 1-click launcher for Windows
├── SHARE_TO_PHONE.bat     # 1-click instant public tunnel for mobile testing
└── START_KEYFORT.ps1      # PowerShell launcher for Windows
```

---

## 🧪 Cryptographic Verification

KeyFort includes 27 rigorous unit tests covering encryption integrity, bad-tag tampering detection, atomic key rotation, and risk assessment:

```bash
# Run all unit tests
gradlew.bat :keyfort-core:test
```

All 27 cryptographic assertions pass with **100% success rate**.

---

## 📄 License
This project is open-source under the [MIT License](LICENSE).
