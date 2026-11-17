# Security Policy 🛡️

Security and user privacy are the core foundations of **KeyFort**. We take all security vulnerabilities seriously.

---

## 🔒 Security Commitments

1. **Zero-Knowledge Architecture:**  
   The KeyFort server is mathematically incapable of decrypting stored user vaults. The server only handles encrypted ciphertext blocks wrapped under user-derived keys.

2. **No Plaintext Logging:**  
   Neither `keyfort-core` nor `keyfort-server` logs sensitive secrets, master passwords, or decrypted entries under any circumstance.

3. **Tamper-Evident Ledger:**  
   All security audit logs use continuous SHA-256 hash chaining to ensure that unauthorized alterations to historical events are instantly detected.

---

## 🚨 Reporting a Security Vulnerability

If you believe you have discovered a security vulnerability or cryptographic flaw in KeyFort, please **do not open a public issue**. 

Instead, report it privately so we can evaluate and address the issue responsibly:

1. Reach out to the maintainers privately via email or GitHub Private Vulnerability Reporting.
2. Please include:
   - A description of the vulnerability.
   - Steps or proof-of-concept to reproduce the behavior.
   - The affected module (`keyfort-core`, `keyfort-server`, or `keyfort-android`).
   - Any suggested mitigations if you have them.

We appreciate your cooperation in keeping user data secure!
