# KeyFort

> A secure password vault that lets you share temporary access with friends without revealing your real password.

---

## What is KeyFort?

Normally, if a friend wants to use your streaming account (like Netflix or Disney+), you have to text them your real password. That creates big problems:
- They can save your password forever.
- They might share it with other people.
- If you use that password on other websites, your accounts are at risk.

**KeyFort solves this.** Instead of sharing your real password, you create a **Single-Use Guest Pass Link** from your Android phone or laptop. 

1. You send the link to your friend.
2. They click it and get logged in directly.
3. They **never see your actual password**.
4. The link expires immediately after use.

---

## How It Works

```text
[Owner's Phone] ────(1. Generate Link)────► [KeyFort Server]
                                                    │
                                            (2. Friend clicks link)
                                                    ▼
[Friend's Browser] ◄──(3. Auto-Login)────── [CinemaStream Demo Site]
                                                    │
                                            (4. Pass Burned & Revoked)
```

1. **Create a Guest Pass**: Choose how long the pass should last (e.g., 5 or 15 minutes) and generate a link.
2. **Send the Link**: Share the link with your friend over chat.
3. **One-Time Login**: When your friend opens the link, KeyFort logs them into the target website automatically without exposing your password.
4. **Immediate Burn**: Once used (or once the timer expires), the pass self-destructs and cannot be used again.

---

## Key Features

- 🔒 **Zero Password Leak**: The server never sees or stores your master password or your site passwords. Everything is encrypted on your device using AES-256-GCM.
- ⏱️ **Ephemeral Guest Passes**: Single-use links that burn immediately after login.
- 🔄 **Automatic Rotation & Assisted Reminders**: Works with cooperating websites to revoke sessions, and reminds you to rotate passwords on standard sites.
- 📱 **Native Android App**: Manage your credentials, generate strong passwords, and create guest links directly on mobile.
- 💻 **Live 3-Pane Web Demo**: Test the entire workflow side-by-side (Owner, Friend, and an unauthorized Copier) in your browser.
- 📜 **Tamper-Proof Audit Ledger**: An append-only cryptographic ledger tracks every access attempt.

---

## Project Structure

```text
KeyFort/
├── keyfort-core/          # Pure Java 17 encryption engine (AES-256-GCM, PBKDF2, HKDF)
├── keyfort-server/        # Spring Boot 3 backend API & guest pass broker
├── keyfort-demo-site/     # Mock streaming service ("CinemaStream") testing guest logins
├── keyfort-android/       # Native Android client app (Java 17, XML layouts, Room DB)
├── web/                   # Interactive 3-pane web demo and dashboard
├── scripts/               # One-click Windows/PowerShell launchers and test scripts
├── presentation/          # 12-slide presentation deck (.pptx and .pdf)
└── docs/                  # Organized documentation (architecture, guides, design, reports)
```

---

## Quick Start: Running the Demo

### Prerequisites
- **Java 17** or newer installed.
- Windows PowerShell or Command Prompt.

### 1. Start the Demo Servers
Run the startup script from the root folder:

```cmd
scripts\RUN_DEMO.bat
```
*(Or in PowerShell: `.\scripts\RUN_DEMO.ps1`)*

This starts both the KeyFort server (`http://localhost:8080`) and the CinemaStream demo site (`http://localhost:9090`).

### 2. Open the Interactive Web Demo
Open your web browser and navigate to:
👉 **[http://localhost:8080/demo](http://localhost:8080/demo)**

Here you can test all three roles:
- **Left Panel (Owner)**: Select CinemaStream, create a guest pass, and copy the link.
- **Middle Panel (Friend)**: Redeem the link and get authenticated into CinemaStream.
- **Right Panel (Attacker / Copier)**: Try to reuse the burned link and watch it get rejected.

### 3. Stop the Servers
When you are finished testing, run:

```cmd
scripts\STOP_DEMO.bat
```

---

## Running the Android App

1. Open the project in **Android Studio**.
2. Start an Android Emulator (API 26+).
3. To install and run directly from the command line:
   ```cmd
   .\gradlew.bat :keyfort-android:installDebug
   ```
4. On the login screen, tap **Demo Login** (or use username `alice`, password `password123`).
5. Tap **CinemaStream Family** -> **Create guest pass** -> **Generate Link**.

---

## Running Automated Tests

KeyFort includes a comprehensive automated test suite (115 tests passing across all core, server, and Android modules):

```cmd
scripts\RUN_TESTS.bat
```
*(Or via Gradle: `.\gradlew.bat test`)*

---

## Documentation & Presentation

For technical reports, security proofs, and presentation slides, see `docs/` and `presentation/`:

- **[Presentation Deck (PDF)](presentation/KeyFort_Presentation.pdf)** / **[PowerPoint](presentation/KeyFort_Presentation.pptx)**: 12-slide project overview and presenter deck.
- **[System Architecture](docs/architecture/ARCHITECTURE.md)**: Module breakdown, data flow, and connector specifications.
- **[Threat Model & Security](docs/architecture/THREAT_MODEL.md)**: Security boundaries, attacker profiles, and protections.
- **[REST API Reference](docs/architecture/API.md)**: Endpoint contracts and JSON payloads.
- **[Viva & Technical Explainer](docs/guides/EXPLAINER.md)**: 20 technical viva questions and plain-English answers.
- **[Presentation Talk Track](docs/guides/DEMO_SCRIPT.md)**: Step-by-step 5-minute live demonstration script.
- **[Final Report](docs/reports/FINAL_REPORT.md)**: Complete implementation metrics and test verification results.

---

## License

This project is licensed under the [MIT License](LICENSE).
