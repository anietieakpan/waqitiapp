# Mnemonic Seed Phrase Security Guidelines

**CRITICAL SECURITY DOCUMENT**
**Last Updated:** 2025-11-19
**Classification:** CONFIDENTIAL - SECURITY CRITICAL

---

## ⚠️ CRITICAL SECURITY RULES

### Rule #1: NEVER Store Mnemonics
**MNEMONICS MUST NEVER BE STORED IN:**
- ❌ Database tables
- ❌ Log files
- ❌ Configuration files
- ❌ Environment variables
- ❌ Cache systems (Redis, Memcached)
- ❌ Message queues (Kafka, RabbitMQ)
- ❌ Backup systems
- ❌ Monitoring/observability systems
- ❌ Any persistent storage whatsoever

**WHY**: A mnemonic seed phrase provides **COMPLETE ACCESS** to a crypto wallet. Anyone with the mnemonic can:
- Steal all funds
- Generate all private keys
- Access all derived addresses
- Recover the entire wallet

---

## 🔐 Proper Mnemonic Handling

### During Wallet Creation (ONE-TIME ONLY)

1. **Generate Mnemonic** (in memory only)
   ```java
   // HDWalletGenerator.generateHDWallet()
   // Generates 12-word BIP39 mnemonic
   ```

2. **Show to User IMMEDIATELY**
   - Display mnemonic in UI **once**
   - Require user to write it down on paper
   - Implement confirmation step (user re-enters words)
   - **NEVER email or SMS the mnemonic**

3. **Clear from Memory**
   - After showing to user, clear mnemonic from all variables
   - Do not include in API logs
   - Do not include in error messages
   - Do not store in session state

### What IS Stored

We store **ONLY**:
- ✅ **Encrypted private keys** (via AWS KMS with HSM backing)
- ✅ **Public keys** (safe to store, cannot derive private keys)
- ✅ **Wallet addresses** (public information)
- ✅ **Extended public key (xPub)** (for address derivation only)

**Private keys are encrypted with:**
- AWS KMS (Key Management Service)
- HSM (Hardware Security Module) backing
- Envelope encryption
- Per-user, per-currency encryption keys
- Automatic key rotation

---

## 📋 Implementation Checklist

### For API Developers

- [ ] Wallet creation endpoint returns mnemonic in response ONCE
- [ ] Mnemonic is NOT included in any other API responses
- [ ] Mnemonic is NOT logged (even at DEBUG level)
- [ ] API response includes clear security warnings
- [ ] Mnemonic is cleared from memory after response sent

###  For Frontend Developers

- [ ] Display mnemonic on dedicated "Backup Wallet" screen
- [ ] Require user acknowledgment: "I have written down my recovery phrase"
- [ ] Implement word-by-word confirmation
- [ ] Show security warnings prominently
- [ ] Do NOT allow screenshot/copy-paste (if possible)
- [ ] Clear mnemonic from JavaScript variables after user confirms
- [ ] Do NOT store in localStorage, sessionStorage, or cookies

### For Operations/DevOps

- [ ] Log sanitization rules in place (strip mnemonic patterns)
- [ ] Monitoring dashboards do NOT display mnemonics
- [ ] Error tracking (Sentry, etc.) configured to mask mnemonics
- [ ] Backup procedures do NOT include mnemonic recovery
- [ ] Incident response plan includes mnemonic compromise scenarios

---

## 🚨 Security Incident Response

### If Mnemonic is Compromised

**IMMEDIATE ACTIONS** (within minutes):

1. **Assume Wallet Compromise**
   - All funds in that wallet are at risk
   - Attacker has complete access

2. **Transfer Funds**
   - Create NEW wallet (new mnemonic)
   - Transfer all assets to new wallet immediately
   - Old wallet must be considered permanently compromised

3. **Notify User**
   - Alert user of security incident
   - Guide through wallet migration
   - Document how compromise occurred

4. **Root Cause Analysis**
   - Investigate how mnemonic was exposed
   - Fix vulnerability immediately
   - Review all similar code paths

### If Mnemonic Found in Logs/Database

**CRITICAL SEVERITY P0 INCIDENT**

1. **Immediate Actions**:
   - Stop all affected services
   - Purge logs/database containing mnemonics
   - Identify all affected wallets
   - Contact all affected users

2. **User Protection**:
   - Force wallet migration for all affected users
   - Provide clear instructions
   - Monitor for unauthorized transactions
   - Consider incident compensation policy

3. **Prevent Recurrence**:
   - Code review of ALL wallet creation flows
   - Add automated tests for mnemonic leakage
   - Implement log scrubbing rules
   - Add runtime mnemonic detection alerts

---

## 🔍 Audit & Verification

### Code Review Checklist

When reviewing wallet-related code:

```bash
# Search for potential mnemonic storage
grep -r "mnemonic" --include="*.java" services/crypto-service/

# Check entity classes (should return ZERO results)
grep -r "@Column.*mnemonic" services/crypto-service/src/main/java/com/waqiti/crypto/entity/

# Check database migrations (should return ZERO results)
grep -r "mnemonic" services/crypto-service/src/main/resources/db/migration/

# Check logging statements
grep -r "log.*mnemonic" services/crypto-service/src/
```

**Expected Results**:
- ✅ Mnemonic only in `HDWalletGenerator.java`
- ✅ Mnemonic only in `HDWalletKeys.java` (DTO)
- ✅ NO mnemonic in entity classes
- ✅ NO mnemonic in database schemas
- ✅ NO mnemonic in logs

### Automated Security Tests

Required tests:
- Unit test: Verify mnemonic NOT in wallet entity
- Integration test: Verify mnemonic NOT persisted to database
- Integration test: Verify mnemonic NOT in API logs
- Security test: Verify mnemonic cleared from memory after use

---

## 📚 Technical Implementation

### Current Architecture

```
User Request: Create Wallet
        ↓
CryptoWalletController
        ↓
CryptoWalletService
        ↓
HDWalletGenerator.generateHDWallet()
        ├─→ Generate mnemonic (memory only)
        ├─→ Derive master key from mnemonic
        ├─→ Derive wallet keys
        ├─→ Encrypt private key (AWS KMS)
        └─→ Return HDWalletKeys (includes mnemonic for ONE-TIME display)
        ↓
API Response (includes mnemonic - SHOWN ONCE TO USER)
        ↓
User writes down mnemonic on paper
        ↓
Mnemonic cleared from memory
        ↓
MNEMONIC NEVER STORED ANYWHERE
```

### HDWalletKeys DTO

```java
@Data
public class HDWalletKeys {
    private String publicKey;           // ✅ Safe to store
    private String encryptedPrivateKey; // ✅ Encrypted with KMS
    private String address;             // ✅ Safe to store (public)
    private String mnemonic;            // ⚠️  ONLY for initial display, NEVER stored
}
```

### Security Logging

```java
// ✅ CORRECT - No mnemonic in logs
log.info("Generated HD wallet for user: {} currency: {}", userId, currency);

// ❌ WRONG - Exposes mnemonic
log.debug("Mnemonic: {}", mnemonic); // NEVER DO THIS
```

---

## 🎓 User Education

### What Users Must Know

**Security Message Shown at Wallet Creation:**

```
🔐 CRITICAL: Backup Your Recovery Phrase

Your 12-word recovery phrase is the ONLY way to recover your wallet if:
- You lose your device
- You forget your password
- Your account is locked
- Our service is unavailable

⚠️  IMPORTANT SECURITY RULES:
✓ Write it down on paper (do NOT take screenshot)
✓ Store paper in secure location (safe, safety deposit box)
✓ NEVER share with anyone (including Waqiti support)
✓ NEVER enter it on any website except Waqiti wallet recovery
✓ Anyone with your recovery phrase can steal ALL your funds

❌ DO NOT:
- Email or text the recovery phrase
- Store in cloud (Google Drive, iCloud, Dropbox)
- Take a photo of it
- Store on your computer
- Share with anyone for any reason

Waqiti support will NEVER ask for your recovery phrase.
```

---

## 📞 Support Procedures

### When User Loses Mnemonic

**CANNOT BE RECOVERED**

If user loses their mnemonic and also loses device access:
- Funds are PERMANENTLY INACCESSIBLE
- No recovery possible
- This is by design (security vs. convenience trade-off)

Support response:
```
"We're sorry, but recovery phrases cannot be recovered if lost.
This is a fundamental security feature of cryptocurrency wallets.
For security reasons, Waqiti does not store recovery phrases.

If you still have access to your account on your current device, you can:
1. Access your wallet normally
2. Create a new wallet with a new recovery phrase
3. Transfer your funds to the new wallet
4. Securely backup the new recovery phrase

This security design protects your funds from theft, but means you
must securely backup your recovery phrase when first created."
```

---

## ✅ Compliance & Regulatory

### PCI-DSS Compliance
- Mnemonics are equivalent to "authentication data"
- Must NEVER be stored post-authorization (PCI-DSS 3.2)
- In-memory processing only

### GDPR Compliance
- Mnemonics are personal data
- User has right to control
- Data minimization: we don't store what we don't need

### SOC 2 Compliance
- Documented key management procedures
- Mnemonic handling in security policies
- Regular security audits

---

## 🔄 Key Rotation & Recovery

### Wallet Recovery (User-Initiated)

User can recover wallet with mnemonic:
1. User enters 12-word phrase
2. System derives same master key
3. System derives same addresses
4. Funds accessible again

### If Private Keys Compromised

If encrypted private keys (in database) are compromised:
- Mnemonic is still safe (not stored)
- Encryption via AWS KMS with HSM
- Attacker needs KMS access AND database access
- Defense-in-depth security model

---

## 📊 Monitoring & Alerts

### Security Monitoring

Set up alerts for:
- [ ] Mnemonic-like patterns in logs (12-word phrases)
- [ ] Unusual wallet creation volumes
- [ ] Failed wallet recovery attempts
- [ ] KMS encryption/decryption failures
- [ ] Database access to encrypted private key columns

### Log Sanitization Rules

```regex
# Detect and MASK 12-word mnemonic patterns
Pattern: \b([a-z]+\s+){11}[a-z]+\b

# BIP39 word list patterns
Pattern: \b(abandon|ability|able|about|above|absent|absorb|...)\s+
```

---

## 📝 Change Log

| Date | Change | Author |
|------|--------|--------|
| 2025-11-19 | Initial security guidelines created | Claude AI |
| 2025-11-19 | Added HD wallet mnemonic security warnings | Claude AI |

---

## 📚 References

- **BIP39**: Mnemonic code for generating deterministic keys
- **BIP32**: Hierarchical Deterministic Wallets
- **BIP44**: Multi-Account Hierarchy for Deterministic Wallets
- **PCI-DSS 3.2**: Payment Card Industry Data Security Standard
- **OWASP**: Cryptocurrency Security Guidelines
- **NIST**: Cryptographic Key Management Guidelines

---

**END OF SECURITY GUIDELINES**

*This document must be reviewed and acknowledged by all developers working on wallet functionality.*
