# 📡 KeyFort REST API Documentation

KeyFort's backend provides a clean, secure REST API to synchronize encrypted vaults, register trusted devices, process security access events, and verify the cryptographic audit ledger.

---

## 🔑 Authentication

Most endpoints (except `/auth/*` and `/ledger/verify`) require a Bearer JWT token in the `Authorization` header:

```http
Authorization: Bearer <your_jwt_token>
```

---

## 📋 Endpoints Overview

### 1. Authentication Endpoints

#### Register a New Account
- **Endpoint:** `POST /auth/register`
- **Description:** Creates a new user account with their initial encrypted vault blob.
- **Request Body:**
  ```json
  {
    "username": "alice",
    "passwordHash": "<client_side_stretched_auth_hash>",
    "initialVault": {
      "wrappedDek": "base64_encoded_wrapped_dek",
      "recoveryWrappedDek": "base64_encoded_recovery_dek",
      "kdfSalt": "base64_encoded_salt"
    }
  }
  ```
- **Response (200 OK):**
  ```json
  {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "userId": "usr_94a7e2b1"
  }
  ```

#### User Login
- **Endpoint:** `POST /auth/login`
- **Description:** Authenticates a user and issues a JWT session token.
- **Request Body:**
  ```json
  {
    "username": "alice",
    "passwordHash": "<client_side_stretched_auth_hash>"
  }
  ```
- **Response (200 OK):**
  ```json
  {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "userId": "usr_94a7e2b1"
  }
  ```

---

### 2. Vault Synchronization Endpoints

#### Download Encrypted Vault
- **Endpoint:** `GET /vault`
- **Description:** Returns the user's latest encrypted vault ciphertext, wrapped DEK, and entries.
- **Response (200 OK):**
  ```json
  {
    "vaultId": "vlt_89234",
    "wrappedDek": "...",
    "recoveryWrappedDek": "...",
    "kdfSalt": "...",
    "rotationPending": false,
    "entries": [
      {
        "id": "ent_01",
        "title": "GitHub",
        "encryptedPayload": "...",
        "version": 1
      }
    ]
  }
  ```

#### Synchronize / Save Vault
- **Endpoint:** `POST /vault/sync`
- **Description:** Updates the server with newly added, edited, or re-encrypted vault entries.
- **Request Body:**
  ```json
  {
    "vaultId": "vlt_89234",
    "wrappedDek": "...",
    "entries": [ ... ]
  }
  ```
- **Response (200 OK):**
  ```json
  {
    "synced": true,
    "lastSyncTime": 1716000000000
  }
  ```

---

### 3. Device Management Endpoints

#### Register Device
- **Endpoint:** `POST /devices/register`
- **Description:** Registers a new phone or computer under the user's profile.
- **Request Body:**
  ```json
  {
    "deviceId": "dev_mobile_pixel8",
    "deviceFingerprint": "sha256_fingerprint_string",
    "publicKey": "base64_encoded_device_public_key"
  }
  ```
- **Response (200 OK):**
  ```json
  {
    "deviceId": "dev_mobile_pixel8",
    "state": "TRUSTED"
  }
  ```

#### List Registered Devices
- **Endpoint:** `GET /devices`
- **Description:** Returns all registered devices and their current trust status (`TRUSTED`, `STEP_UP_AUTH`, `REVOKED`).

---

### 4. Audit & Threat Ledger Endpoints

#### Verify Ledger Integrity
- **Endpoint:** `GET /ledger/verify`
- **Description:** Validates the complete cryptographic SHA-256 hash chain of the audit log to ensure no historical events were altered or deleted.
- **Response (200 OK):**
  ```json
  {
    "valid": true,
    "blockCount": 12,
    "message": "Ledger integrity verified successfully."
  }
  ```

#### Record Access Attempt (Risk Engine)
- **Endpoint:** `POST /events/access`
- **Description:** Submits an access event (success/failure, IP address, device ID) to the sliding-window threat detection engine.
