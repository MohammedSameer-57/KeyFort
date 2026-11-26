# KeyFort 5-Minute Presentation Script

**Presentation Target**: Live interactive UI at `http://localhost:8080/demo`  
**Goal**: Demonstrate ephemeral guest pass delegation, single-use enforcement, owner session kill-switch, and client-side key renewal.

---

## Step-by-Step Presentation Flow (5 Minutes)

### Minute 0:00 – 1:00 | Architecture & The Problem
- **What to show**: The locked vault screen on `http://localhost:8080/demo`. Point to the three columns (You / Your friend / Someone who copied it).
- **What to say**:
  > "Permanent credential sharing is insecure. When people share streaming, SaaS, or family logins, they either reveal their master password or plaintext credentials, which are then stored in unencrypted chat logs or reused indefinitely.
  >
  > KeyFort solves this by brokering single-use, time-bounded guest access through cooperating site connectors without ever exposing the permanent password or the vault decryption key to the friend or the server."
- **Action**: Click the master key input field. The demo hint indicates `demo-vault-master-pass`. Click **Unlock vault**.
- **What to observe**: The vault unlocks via real PBKDF2 key derivation. The column displays the entry for `CinemaStream Family` with password hidden behind dots (`••••••••••••`).

---

### Minute 1:00 – 2:00 | Generating the One-Time Link
- **What to say**:
  > "The owner wants to grant a friend temporary read-only access to CinemaStream for 10 minutes. The owner taps 'Create one-time link'."
- **Action**: Click **Create one-time link** in the owner column.
- **What to observe**:
  - Owner status changes to `Link ready` with a 10-minute active countdown.
  - Friend column updates to `Ready to open` with an active **Sign in once** button.
  - Copier column updates to `Has a copy` with a **Try copied link** button.
  - Live cryptographic facts update showing the SHA-256 fingerprint of the ephemeral token.
- **What to say**:
  > "Notice that the server stores only the SHA-256 fingerprint of this link. The plaintext token exists only in the URL handed to the guest."

---

### Minute 2:00 – 3:00 | Single-Use Redemption & Target Site Login
- **What to say**:
  > "Now the invited friend opens their browser and clicks 'Sign in once'."
- **Action**: In the middle column (Your friend), click **Sign in once**.
- **What to observe**:
  - Friend column changes to `Signed in once` with active guest session countdown (`Read-only guest session`).
  - Owner column reflects `Guest signed in` and presents a red **End session now** button.
  - Timeline step 2 lights up.
- **What to say**:
  > "The target site validated the one-time ticket, established a guest session, and sent a signed HMAC webhook back to KeyFort. The ticket is immediately revoked on the target site. The link is now burned."

---

### Minute 3:00 – 3:45 | The Copier Attack (Strict Single-Use Rejection)
- **What to say**:
  > "Now suppose someone intercepted or copied that same link from a chat history and attempts to use it."
- **Action**: In the third column (Someone who copied it), click **Try copied link**.
- **What to observe**:
  - The request is immediately rejected.
  - Status chip displays `Refused · Already used`.
  - Explanatory banner states: *'Refused: this link was already used once. Every later attempt with a copied link is blocked.'*
  - The live crypto panel increments the rejected replay counter.
- **What to say**:
  > "The server's atomic Compare-And-Set state machine transitioned the pass to BURNED upon the first redemption. Any secondary attempt is rejected with HTTP 400. Replay is cryptographically impossible."

---

### Minute 3:45 – 4:30 | Owner Kill-Switch & Key Renewal
- **What to say**:
  > "The friend is still browsing CinemaStream. If the owner decides to terminate access early, they can invoke the kill-switch."
- **Action**: In the owner column, click **End session now**.
- **What to observe**:
  - Friend's session immediately transitions to `Session ended`.
  - Owner sees `Session ended · Vault key renewed`.
- **Action**: Click **Unlock & renew vault key** (or click **Lock vault**, then re-enter `demo-vault-master-pass` and unlock).
- **What to observe**:
  - Client-side rotation executes (`Vault.rotateKeys()`).
  - Cryptographic fact updates from `v1` to `v2`.
  - Audit ledger hash chain updates with block verification.
- **What to say**:
  > "The server flagged `rotationPending`. Because the server does not hold the master key or DEK, the rotation executes client-side upon unlock, generating a fresh DEK and re-encrypting all entries."

---

### Minute 4:30 – 5:00 | Viva Questions & Answers

#### Question 1: "Is this zero-knowledge?"
> **Answer**: "KeyFort provides zero-knowledge protection for **passwords and vault keys**, which the server never holds or sees. However, KeyFort does **not** claim full zero-knowledge over metadata: site names are visible to the server to route requests to the correct site connector SPI, and usernames are stored for client list performance. Full zero-knowledge metadata encryption is future work."

#### Question 2: "What if the user closes their browser?"
> **Answer**: "Closing the browser does not compromise security. The guest pass state machine is persisted in the server database. If the link was already redeemed, it remains BURNED. If it was never redeemed, its hard TTL expires, and the background sweeper job cleans up the target site ticket."

#### Question 3: "How does the cooperating site know who the user is?"
> **Answer**: "The cooperating site does not receive the user's permanent password. When the pass is created, KeyFort calls `connector.provisionTicket()` for the user's account identifier. The cooperating site issues a temporary single-use ticket handle bound to that account. On redemption, the ticket is exchanged for a short-lived read-only guest session cookie."
