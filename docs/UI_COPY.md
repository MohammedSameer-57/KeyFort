# KeyFort UI Copy and Plain-Language Standards

This document establishes the user-facing voice, mandatory vocabulary, and exact copy strings for all KeyFort client applications (Web, Android, and Documentation).

## 1. Core Vocabulary and Strict Rules

To maintain clarity for non-technical users and avoid intimidating jargon, all user-facing interfaces must adhere to the following terminology rules:

| Forbidden Term | Mandatory Plain-Language Equivalent | Reason |
| :--- | :--- | :--- |
| `token` / `ephemeral token` | **link** / **one-time link** | Users share web links, not cryptographic tokens. |
| `burned` / `consumed` | **already used** | Communicates that single-use has completed without technical jargon. |
| `rotation` / `key rotation` | **vault key renewed** / **renewed** | Describes the outcome (fresh protection) rather than internal mechanisms. |
| `attacker` / `adversary` / `hacker` | **someone who copied it** | Accurately describes the threat without cinematic or aggressive phrasing. |

---

## 2. Screen Copy Specifications

### Screen: Live Demo (`/demo`)

- **Top Bar**:
  - Logo label: `KeyFort`
  - Tagline: `Let someone in once, without giving away the key`
  - Cooperating site badge: `CinemaStream Family · Cooperating site`
  - Reset action: `Start over`
  - Lock action: `Lock vault`

- **Master Key Unlock Gate**:
  - Heading: `Unlock your vault`
  - Description: `Enter your master key to open the vault. Without it, nothing inside can be read.`
  - Input label: `Master key`
  - Action button: `Unlock vault`
  - Show/Hide button: `Show` / `Hide`
  - Incorrect key error: `That master key did not open the vault. Try again.`
  - Rate limit lockout: `Too many tries. Wait a moment, then try again.`
  - Demo hint label: `Demo vault · demo master key: {hint}`

- **Introduction**:
  - Heading: `See what happens when you share a login once`
  - Description: `Follow the same one-time link across three views: your vault, your friend's browser, and someone who copied the link.`

- **Three Columns**:
  1. **You (Vault Owner)**:
     - Heading: `You`
     - Role: `Vault owner`
     - Status badges: `Link ready` (green), `Guest signed in` (amber), `Session ended` (amber), `Vault key renewed` (green), `No active link` (neutral)
     - Vault item: `CinemaStream Family` · `family@household.org` · `••••••••••••`
     - Countdown: `{time} remaining` | `Already used`
     - Helper text: `Your friend can sign in once without seeing or typing the real password.`
     - Actions: `Create one-time link` | `Cancel link` | `End session now` | `Unlock & renew vault key` | `Create another link`
  2. **Your friend (Invited Guest)**:
     - Heading: `Your friend`
     - Role: `Invited guest`
     - Status badges: `Ready to open` | `Signed in once` | `Session ended` | `Waiting for link`
     - Helper text: `You were invited to sign in once. The password stays hidden inside KeyFort.`
     - Session indicator: `Read-only guest session` · `{time} left`
     - Action button: `Sign in once` (active) | `Link already used` (disabled) | `Session ended` (disabled)
  3. **Someone who copied it (Forwarded Link)**:
     - Heading: `Someone who copied it`
     - Role: `Forwarded link`
     - Status badges: `Has a copy` | `Refused · Already used` | `No link yet`
     - Helper text: `Once your friend signs in, this copied link is marked already used and will be refused.`
     - Refused helper: `Refused: this link was already used once. Every later attempt with a copied link is blocked.`
     - Action button: `Try copied link` | `Try copied link again`
     - Copier-first edge case: `First use wins. If this happens, end the session.`

- **Four-Step Timeline**:
  - Heading: `How this link moves through KeyFort`
  - Step 1: `Link created` — `Single-use link issued with a 10-minute countdown.`
  - Step 2: `Signed in once` — `Friend opens the site; link is marked already used.`
  - Step 3: `Copied link refused` — `Every later attempt with the copied link is blocked.`
  - Step 4: `Vault key renewed` — `Owner's vault is flagged and renewed at next unlock.`

- **How It Stays Safe (Cryptographic Verification Card)**:
  - Heading: `How it stays safe`
  - Item 1: `Your password is locked on your device.` (Live: Vault key version `v1` / `v2`)
  - Item 2: `The link is a one-time secret we don't keep.` (Live: SHA-256 fingerprint prefix `#{fingerprint}`)
  - Item 3: `The site proves it was really the site.` (Live: `Signature verified` · `{count} replays refused`)
  - Item 4: `History can't be quietly changed.` (Live: Hash chain head `#{hash}` · Button: `Check history`)
  - Item 5: `A fresh key after every use.` (Live: `Renewal pending at next unlock` | `Key renewed`)

- **Honest Limits (Scope and Non-Goals)**:
  - Heading: `Honest limits`
  - Summary:
    1. *Filming during the session*: KeyFort cannot stop someone from filming their screen or taking photos during the one allowed session.
    2. *Action inside live session*: KeyFort cannot prevent actions inside the live session before you end it.
    3. *First use wins*: If a copied link is used before your friend opens it, the copied link consumes the access. End the session immediately.
    4. *Sites without a connector*: Sites that have not integrated KeyFort operate in assisted mode, prompting you to change your real password manually after sharing.
    5. *Server visibility*: The server can see site names and usernames, never passwords.

---

### Screen: Create a Link

- Heading: `Let a friend in, once.`
- Description: `Choose what to share and for how long. Your friend never sees your password.`
- Form labels: `Sharing` | `Link works for` (Options: `5 min`, `15 min`, `1 hour`)
- Submit button: `Create one-time link`
- Footnote: `Nothing is shared until you send the link.`

---

### Screen: Link Ready

- Eyebrow: `Waiting for your friend`
- Heading: `Your link is ready.`
- Countdown label: `left to use it`
- Explanatory note: `It works once. After it's used, your vault gets a fresh key.`
- Action buttons: `Share link` | `Copy` | `Cancel link`
- Clipboard notification: `Link copied to clipboard (clears automatically in 30 seconds).`

---

### Screen: Friend Page (`/g/{link}`)

- Eyebrow: `Shared with you`
- Heading: `{Site Name}`
- Description: `A friend is letting you in. You'll be signed in without typing a password.`
- Primary button: `Open {Site Name}`
- Footnote: `This link works once. Your session ends after 10 minutes.`
- Error/Refused message: `This link was already used or has expired.`

---

### Screen: Vault List & Activity

- Vault item status labels:
  - `Protected` (Standard zero-knowledge encrypted state)
  - `Link active` (Ephemeral one-time link currently valid)
  - `Change this password at the site` (Assisted mode recommendation for non-integrated sites)
- Activity log status:
  - Verified message: `History checked: nothing was changed.`
  - Tamper alert: `Verification failed: audit history has been tampered with.`
