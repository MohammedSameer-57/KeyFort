# 🛡️ KeyFort — Password Vault & Disposable Guest Passes

> **Remember just one master password. Share temporary access safely with self-destructing Guest Passes.**

---

## 👤 What is KeyFort?

**KeyFort** is a modern personal password manager that solves the biggest problem with sharing passwords: **what happens after you share them.**

Instead of giving away your real, permanent password (which a friend could write down or take a picture of with a camera), KeyFort lets you create **Disposable Guest Passes** that automatically expire after a set time!

---

## ✨ Standout Features

- ⏱️ **Disposable Guest Passes (New!):** Create temporary passcodes (`30 Mins`, `2 Hours`, `24 Hours`) for Netflix, Wi-Fi, or Google. Once expired, whatever they wrote down is permanently dead!
- 🚫 **Emergency Kill Switch:** Revoke any active guest pass immediately with 1 click.
- 🔑 **Simple Vault:** Store and organize all your personal accounts in one clean, encrypted space.
- 📋 **1-Click Copy:** Instant copy for usernames, passwords, and guest pass links.
- 🔒 **Zero-Knowledge Privacy:** All permanent passwords are encrypted on your device using AES-256-GCM.

---

## 🚀 How to Open & Test KeyFort

### Option 1: Direct in Your Browser (Instant)
Double-click **`preview.html`** in this folder to test the full password manager and Disposable Guest Pass system immediately!

### Option 2: Live Backend Server (Windows)
1. Double-click **`START_KEYFORT.bat`**.
2. Open **[http://localhost:8080](http://localhost:8080)** in your browser.
3. Click **Unlock Vault** and try creating a guest pass!

---

## 📁 Project Structure

```text
KeyFort/
├── keyfort-server/    # Spring Boot backend server
├── keyfort-android/   # Native Android mobile application
├── keyfort-core/      # AES-256 encryption engine & risk scoring
├── preview.html       # Instant visual password manager & guest pass simulator
└── START_KEYFORT.bat  # 1-click launcher for Windows
```

---

## 📄 License
This project is open-source and free to use under the [MIT License](LICENSE).
