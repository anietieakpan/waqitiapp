# PCI DSS Automated Key Rotation - Implementation Guide

**CRITICAL: Cryptographic Key Management for Payment Card Data**

**Version**: 3.0.0
**Last Updated**: 2025-10-04
**Owner**: Waqiti Security Team

---

## Table of Contents

1. [Overview](#overview)
2. [PCI DSS Requirements](#pci-dss-requirements)
3. [Architecture](#architecture)
4. [Key Hierarchy](#key-hierarchy)
5. [Rotation Process](#rotation-process)
6. [Zero-Downtime Strategy](#zero-downtime-strategy)
7. [Monitoring & Alerts](#monitoring--alerts)
8. [Compliance Verification](#compliance-verification)
9. [Disaster Recovery](#disaster-recovery)
10. [Audit Trail](#audit-trail)

---

## Overview

### Why Automated Key Rotation?

**PCI DSS Requirement 3.6.4 states:**
> Cryptographic keys must be changed when the key has reached the end of its defined cryptoperiod or when the integrity of the key has been weakened.

**Manual key rotation risks:**
- Human error (forget to rotate)
- Downtime during rotation
- Incomplete rotation (some data not re-encrypted)
- Poor audit trail

**Automated key rotation benefits:**
- ✅ Guaranteed rotation every 90 days (PCI DSS compliant)
- ✅ Zero downtime
- ✅ Complete audit trail
- ✅ Immediate emergency rotation on compromise
- ✅ Metrics and alerting

### Scope

**Keys Rotated:**
- Data Encryption Keys (DEK) - Encrypt payment data
- Database-level encryption keys
- Field-level encryption keys
- API encryption keys

**Data Protected:**
- Credit card numbers (PAN)
- CVV/CVC codes
- Bank account numbers
- SSN/TIN
- Date of birth
- Full names and addresses

**NOT Rotated (AWS KMS Managed):**
- Customer Master Keys (CMK) - AWS KMS rotates automatically every 365 days

---

## PCI DSS Requirements

### Requirement 3.6: Protect cryptographic keys

**3.6.1 - Generation of strong cryptographic keys** ✅
- Algorithm: AES-256-GCM (NIST approved)
- Key generation: AWS KMS `GenerateDataKey` API
- Randomness: FIPS 140-2 Level 3 HSM

**3.6.2 - Secure cryptographic key distribution** ✅
- Keys never transmitted in plaintext
- Envelope encryption pattern
- TLS 1.3 for all key material transport

**3.6.3 - Secure cryptographic key storage** ✅
- Master keys: AWS KMS (FIPS 140-2 Level 3)
- Data keys: Encrypted at rest in database
- Access controls: IAM policies + database ACLs

**3.6.4 - Key changes for keys at end of cryptoperiod** ✅
- **THIS IMPLEMENTATION**
- Automated rotation every 90 days
- Scheduled daily check at 2 AM
- Emergency rotation capability

**3.6.5 - Retirement or replacement of keys** ✅
- Old keys marked as RETIRED
- Kept for historical decryption only
- Cannot be used for new encryptions

**3.6.6 - Split knowledge and dual control** N/A
- Fully automated (no manual key operations)

**3.6.7 - Prevention of unauthorized substitution** ✅
- Cryptographic signing of key metadata
- Audit trail of all key operations
- IAM policy enforcement

**3.6.8 - Replacement if integrity compromised** ✅
- Emergency rotation API
- Automatic re-encryption
- Security team alerting

---

## Architecture

### High-Level Design

```
┌─────────────────────────────────────────────────────────────┐
│                    AWS KMS (Master Keys)                     │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  CMK (Customer Master Key)                            │   │
│  │  - FIPS 140-2 Level 3 HSM                            │   │
│  │  - Auto-rotates every 365 days                       │   │
│  │  - Never leaves AWS KMS                              │   │
│  └──────────────────────────────────────────────────────┘   │
└───────────────────────┬─────────────────────────────────────┘
                        │ Encrypts/Decrypts
                        ▼
┌─────────────────────────────────────────────────────────────┐
│           Data Encryption Keys (DEK) - In Database           │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  Encrypted DEK (stored)                              │   │
│  │  - Encrypted by CMK                                   │   │
│  │  - Rotated every 90 days                             │   │
│  │  - AES-256-GCM                                       │   │
│  └──────────────────────────────────────────────────────┘   │
└───────────────────────┬─────────────────────────────────────┘
                        │ Encrypts/Decrypts
                        ▼
┌─────────────────────────────────────────────────────────────┐
│                Application Data (Encrypted)                  │
│  - Credit card numbers                                       │
│  - Bank account numbers                                      │
│  - SSN, DOB, addresses                                       │
└─────────────────────────────────────────────────────────────┘
```

### Envelope Encryption Pattern

```
1. Generate DEK in AWS KMS
   ├─> Plaintext DEK (256-bit AES key)
   └─> Encrypted DEK (encrypted by CMK)

2. Encrypt data with plaintext DEK
   └─> Encrypted data

3. Store encrypted DEK + encrypted data
   ├─> Database: encrypted_data_key column
   └─> Database: encrypted_value column

4. To decrypt:
   ├─> Fetch encrypted DEK from database
   ├─> Decrypt DEK using AWS KMS
   ├─> Decrypt data using plaintext DEK
   └─> Return plaintext data
```

---

## Key Hierarchy

### Key Levels

**Level 1: Customer Master Key (CMK)**
- Managed by: AWS KMS
- Location: AWS CloudHSM (FIPS 140-2 Level 3)
- Rotation: Automatic (every 365 days)
- Purpose: Encrypt/decrypt Data Encryption Keys
- Access: IAM policies only

**Level 2: Data Encryption Keys (DEK)**
- Managed by: Our Application + This Service
- Location: PostgreSQL database (encrypted at rest)
- Rotation: **Every 90 days (PCI DSS requirement)**
- Purpose: Encrypt/decrypt application data
- Access: Application code only

**Level 3: Application Data**
- Encrypted by: DEKs
- Location: PostgreSQL database
- Examples: Credit cards, bank accounts, SSN, DOB

### Key Purposes

| Purpose | Rotation Frequency | Data Protected |
|---------|-------------------|----------------|
| `PAYMENT_ENCRYPTION` | 90 days | Credit cards, bank accounts |
| `PII_ENCRYPTION` | 90 days | SSN, DOB, addresses |
| `WALLET_ENCRYPTION` | 90 days | Wallet balances, private keys |
| `API_ENCRYPTION` | 90 days | API tokens, session keys |

---

## Rotation Process

### Automated Rotation (Every 90 Days)

**Schedule:**
```
Daily check at 2:00 AM UTC
Query: SELECT * FROM encryption_keys
       WHERE rotation_enabled = true
       AND next_rotation_at <= NOW()
```

**Rotation Steps:**

**Step 1: Generate New DEK**
```java
// Call AWS KMS to generate new 256-bit AES key
GenerateDataKeyResponse response = kmsClient.generateDataKey(
    GenerateDataKeyRequest.builder()
        .keyId(masterKeyId)         // CMK ARN
        .keySpec(DataKeySpec.AES_256)
        .build()
);

// We get back:
// - Plaintext DEK (for immediate use)
// - Encrypted DEK (to store in database)
```

**Step 2: Create New Key Entity**
```sql
INSERT INTO encryption_keys (
    key_id,
    alias,
    encrypted_data_key,
    status,
    rotation_enabled,
    rotation_frequency_days,
    last_rotated_at,
    next_rotation_at
) VALUES (
    'key-20250104-abc123',
    'payment_encryption_v1704384000',
    '<encrypted_dek_base64>',
    'ACTIVE',
    true,
    90,
    '2025-01-04 02:00:00',
    '2025-04-04 02:00:00'  -- 90 days later
);
```

**Step 3: Mark Old Key as RETIRED**
```sql
UPDATE encryption_keys
SET status = 'RETIRED',
    updated_at = NOW()
WHERE key_id = 'old-key-id';
```

**Key Status Transition:**
```
ACTIVE -> RETIRED -> ARCHIVED (after 7 years for compliance)
```

**Step 4: Start Background Re-Encryption**
```java
CompletableFuture.runAsync(() -> {
    reEncryptAllData(oldKey, newKey);
});
```

**Step 5: Audit Log**
```json
{
  "event": "KEY_ROTATED",
  "timestamp": "2025-01-04T02:00:00Z",
  "oldKeyId": "key-20241004-xyz789",
  "newKeyId": "key-20250104-abc123",
  "reason": "SCHEDULED_ROTATION",
  "pciDSSRequirement": "3.6.4",
  "rotatedBy": "SYSTEM:AUTOMATED"
}
```

**Step 6: Alert Security Team**
```
Subject: Encryption Key Rotated Successfully

A scheduled encryption key rotation has completed successfully.

Old Key: key-20241004-xyz789 (created 2024-10-04)
New Key: key-20250104-abc123 (created 2025-01-04)
Purpose: PAYMENT_ENCRYPTION
Age of old key: 92 days

Background re-encryption started.
Estimated completion: 2 hours

Next rotation: 2025-04-04 02:00:00 UTC (90 days)
```

---

## Zero-Downtime Strategy

### The Challenge

**Problem:**
- Application has encrypted data in database
- Need to rotate encryption key
- Cannot afford downtime
- Must re-encrypt all data

**Naive Approach (WRONG):**
```
1. Stop application ❌
2. Rotate key ❌
3. Re-encrypt all data ❌ (takes hours)
4. Start application ❌
Result: Hours of downtime!
```

### Our Solution: Dual-Key Period

**Strategy:**
```
1. Generate new key (NEW_KEY)
2. Keep old key active for decryption (OLD_KEY)
3. Use NEW_KEY for all NEW encryptions
4. Use OLD_KEY for decrypting existing data
5. Gradually re-encrypt data in background
6. Once complete, retire OLD_KEY
```

**Timeline:**
```
T+0:   Generate NEW_KEY
       Mark OLD_KEY as RETIRED (but still usable for decryption)

T+1min: Application starts using NEW_KEY for new data
        Application can still decrypt old data using OLD_KEY

T+1hr:  Background job re-encrypting data
        Progress: 25% (500,000 records)

T+2hr:  Background job complete
        Progress: 100% (2,000,000 records)
        All data now encrypted with NEW_KEY

T+2hr+1min: OLD_KEY archived (no longer in use)
```

**Code Example:**
```java
// Writing new data - use ACTIVE key
public void encryptAndStore(String data) {
    EncryptionKey activeKey = getActiveKey();  // NEW_KEY
    String encrypted = encrypt(data, activeKey);
    database.save(encrypted, activeKey.getId());
}

// Reading existing data - check which key was used
public String decryptAndRead(UUID recordId) {
    Record record = database.findById(recordId);

    // Find the key that was used to encrypt this record
    EncryptionKey key = keyRepository.findByKeyId(record.getKeyId());

    // Decrypt using appropriate key (could be OLD or NEW)
    String decrypted = decrypt(record.getEncryptedData(), key);

    // If data encrypted with old key, re-encrypt with new key
    if ("RETIRED".equals(key.getStatus())) {
        EncryptionKey newKey = getActiveKey();
        String reEncrypted = encrypt(decrypted, newKey);
        record.setEncryptedData(reEncrypted);
        record.setKeyId(newKey.getId());
        database.save(record);  // Lazy re-encryption
    }

    return decrypted;
}
```

**Result: ZERO DOWNTIME!** ✅

---

## Monitoring & Alerts

### Prometheus Metrics

```yaml
# Key rotation attempts
key_rotation.attempts{purpose="payment_encryption"}

# Key rotation successes
key_rotation.successes{purpose="payment_encryption"}

# Key rotation failures
key_rotation.failures{purpose="payment_encryption",reason="kms_unavailable"}

# Key rotation duration
key_rotation.duration_seconds{quantile="0.99"}

# Records re-encrypted
key_rotation.re_encryptions{purpose="payment_encryption"}

# Keys requiring rotation
encryption_keys.requiring_rotation{purpose="payment_encryption"}

# Active keys count
encryption_keys.active{purpose="payment_encryption"}

# Retired keys count
encryption_keys.retired{purpose="payment_encryption"}
```

### Grafana Dashboard

**Panel 1: Key Rotation Health**
- Last rotation time (time series)
- Rotation success rate (gauge)
- Keys requiring rotation (stat)

**Panel 2: Re-Encryption Progress**
- Records re-encrypted (counter)
- Re-encryption rate (records/sec)
- Estimated completion time

**Panel 3: Key Inventory**
- Active keys (stat)
- Retired keys (stat)
- Keys by purpose (pie chart)

### Alerts

**Critical Alerts (PagerDuty)**

```yaml
# Key rotation overdue
- alert: KeyRotationOverdue
  expr: |
    encryption_keys_requiring_rotation > 0
    AND
    time() - encryption_keys_last_rotation_timestamp > 93*24*3600
  for: 1h
  labels:
    severity: critical
    pci_dss: "3.6.4"
  annotations:
    summary: "Encryption key rotation is overdue (>93 days)"
    description: "{{ $value }} keys require rotation. PCI DSS violation!"

# Key rotation failed
- alert: KeyRotationFailed
  expr: key_rotation_failures > 0
  for: 5m
  labels:
    severity: critical
  annotations:
    summary: "Key rotation failed"
    description: "Automated key rotation failed. Manual intervention required."

# Re-encryption stalled
- alert: ReEncryptionStalled
  expr: |
    rate(key_rotation_re_encryptions[10m]) == 0
    AND
    key_rotation_re_encryption_pending > 0
  for: 30m
  labels:
    severity: warning
  annotations:
    summary: "Re-encryption process stalled"
    description: "Background re-encryption has made no progress for 30 minutes"
```

**Warning Alerts (Slack)**

```yaml
# Key approaching rotation
- alert: KeyRotationApproaching
  expr: encryption_keys_days_until_rotation < 7
  labels:
    severity: warning
  annotations:
    summary: "Key rotation due in less than 7 days"
    description: "Key {{ $labels.key_id }} will rotate on {{ $labels.next_rotation_date }}"
```

---

## Compliance Verification

### Automated Compliance Check

**Daily Compliance Report:**
```bash
# Run daily compliance check
./scripts/pci-dss-compliance-check.sh

# Output:
┌─────────────────────────────────────────────────────────┐
│         PCI DSS 3.6.4 Compliance Report                 │
│                 2025-01-04 08:00:00 UTC                 │
└─────────────────────────────────────────────────────────┘

✅ Requirement 3.6.1: Strong key generation
   - Algorithm: AES-256-GCM (NIST approved)
   - Key source: AWS KMS (FIPS 140-2 Level 3)

✅ Requirement 3.6.2: Secure key distribution
   - Transport: TLS 1.3
   - Plaintext keys never stored

✅ Requirement 3.6.3: Secure key storage
   - Master keys: AWS KMS CloudHSM
   - Data keys: Encrypted at rest in PostgreSQL

✅ Requirement 3.6.4: Key rotation
   - Rotation frequency: 90 days
   - Last rotation: 2025-01-04 02:00:00 (0 days ago)
   - Next rotation: 2025-04-04 02:00:00 (90 days)
   - Keys overdue: 0

✅ Requirement 3.6.5: Key retirement
   - Retired keys: 12
   - Archived keys: 45

✅ Requirement 3.6.7: Unauthorized substitution prevention
   - IAM policies: Enforced
   - Audit logs: Complete

✅ Requirement 3.6.8: Compromise response
   - Emergency rotation: Configured
   - Alert system: Active

─────────────────────────────────────────────────────────
COMPLIANCE STATUS: ✅ COMPLIANT
─────────────────────────────────────────────────────────
```

### Audit Queries

**Query 1: Verify all keys rotated within 90 days**
```sql
SELECT
    key_id,
    alias,
    purpose,
    CURRENT_DATE - last_rotated_at::date as days_since_rotation,
    status
FROM encryption_keys
WHERE rotation_enabled = true
AND status = 'ACTIVE'
AND (CURRENT_DATE - last_rotated_at::date) > 90;

-- Expected result: 0 rows (all keys rotated within 90 days)
```

**Query 2: Verify rotation schedule is correct**
```sql
SELECT
    key_id,
    alias,
    last_rotated_at,
    next_rotation_at,
    next_rotation_at - last_rotated_at as rotation_interval_days
FROM encryption_keys
WHERE rotation_enabled = true
AND status = 'ACTIVE';

-- Expected: rotation_interval_days = 90 for all keys
```

**Query 3: Audit trail of rotations**
```sql
SELECT
    event_timestamp,
    old_key_id,
    new_key_id,
    reason,
    rotated_by
FROM audit_logs
WHERE event_type = 'KEY_ROTATED'
ORDER BY event_timestamp DESC
LIMIT 100;
```

---

## Disaster Recovery

### Scenario 1: Key Rotation Failure

**Detection:**
- Alert fires: `KeyRotationFailed`
- Security team paged

**Response:**
1. Check logs for error details
2. Verify AWS KMS is available
3. Retry rotation manually:
   ```java
   keyRotationService.rotateKey(oldKey);
   ```
4. If still failing, escalate to AWS Support

**Rollback:**
- Old key remains ACTIVE if rotation fails
- No impact to application
- No data loss

### Scenario 2: AWS KMS Unavailable

**Detection:**
- Multiple KMS API call failures
- Alert: `KMSUnavailable`

**Impact:**
- Cannot decrypt data keys
- Cannot encrypt new data
- **Application outage**

**Response:**
1. Check AWS Service Health Dashboard
2. Use cached DEKs (if available)
3. Escalate to AWS Support (Priority 1)
4. Consider failover to secondary region

**Prevention:**
- Multi-region KMS keys
- DEK caching (limited time)
- Circuit breaker on KMS calls

### Scenario 3: Compromised Key

**Detection:**
- Security alert
- Unusual access patterns
- External report

**Response:**
1. **IMMEDIATE**: Call emergency rotation API
   ```java
   keyRotationService.emergencyKeyRotation(
       keyId,
       "SECURITY_INCIDENT_2025_01_04"
   );
   ```
2. Mark old key as COMPROMISED
3. Re-encrypt ALL data immediately
4. Audit all access logs
5. File incident report
6. Notify affected customers (if PII exposed)

**Timeline:**
- T+0: Compromise detected
- T+5min: Emergency rotation initiated
- T+10min: New key active
- T+2hr: All data re-encrypted
- T+24hr: Forensic analysis complete
- T+72hr: Incident report filed

---

## Audit Trail

### Audit Log Schema

```sql
CREATE TABLE key_rotation_audit (
    id UUID PRIMARY KEY,
    event_timestamp TIMESTAMP NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    old_key_id VARCHAR(100),
    new_key_id VARCHAR(100),
    purpose VARCHAR(100),
    reason VARCHAR(200),
    rotated_by VARCHAR(100),
    rotation_duration_ms BIGINT,
    success BOOLEAN NOT NULL,
    error_message TEXT,
    metadata JSONB,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_key_rotation_audit_timestamp
    ON key_rotation_audit(event_timestamp DESC);
CREATE INDEX idx_key_rotation_audit_key_id
    ON key_rotation_audit(old_key_id, new_key_id);
```

### Event Types

| Event Type | Description | PCI DSS Requirement |
|------------|-------------|-------------------|
| `KEY_GENERATED` | New key created | 3.6.1 |
| `KEY_ROTATED` | Scheduled rotation | 3.6.4 |
| `KEY_RETIRED` | Key marked as retired | 3.6.5 |
| `KEY_COMPROMISED` | Emergency rotation | 3.6.8 |
| `KEY_ARCHIVED` | Old key archived | 3.6.5 |
| `RE_ENCRYPTION_STARTED` | Background re-encryption | 3.6.4 |
| `RE_ENCRYPTION_COMPLETED` | Re-encryption done | 3.6.4 |
| `KEY_ACCESS` | Key used for encrypt/decrypt | 3.6.7 |

### Sample Audit Log Entry

```json
{
  "id": "aud-20250104-abc123",
  "event_timestamp": "2025-01-04T02:00:00Z",
  "event_type": "KEY_ROTATED",
  "old_key_id": "key-20241004-xyz789",
  "new_key_id": "key-20250104-abc123",
  "purpose": "PAYMENT_ENCRYPTION",
  "reason": "SCHEDULED_ROTATION_90_DAYS",
  "rotated_by": "SYSTEM:KEY_ROTATION_SERVICE",
  "rotation_duration_ms": 4523,
  "success": true,
  "error_message": null,
  "metadata": {
    "old_key_age_days": 92,
    "old_key_created": "2024-10-04T02:00:00Z",
    "new_key_algorithm": "AES-256-GCM",
    "kms_key_arn": "arn:aws:kms:us-east-1:123456789:key/abc-def-ghi",
    "records_requiring_re_encryption": 2847593,
    "pci_dss_requirement": "3.6.4"
  }
}
```

### Retention Policy

**Audit logs must be retained for:**
- Active period: 3 years (online access)
- Archival period: 7 years (cold storage)
- Total retention: 10 years (PCI DSS requirement)

---

## Summary

**✅ PCI DSS 3.6 Fully Compliant**

| Requirement | Status | Implementation |
|-------------|--------|----------------|
| 3.6.1 - Strong key generation | ✅ | AWS KMS, AES-256-GCM |
| 3.6.2 - Secure distribution | ✅ | TLS 1.3, envelope encryption |
| 3.6.3 - Secure storage | ✅ | AWS KMS CloudHSM |
| 3.6.4 - **Key rotation** | ✅ | **This Service - 90 days** |
| 3.6.5 - Key retirement | ✅ | Automated retirement |
| 3.6.6 - Split knowledge | N/A | Fully automated |
| 3.6.7 - Substitution prevention | ✅ | IAM + Audit |
| 3.6.8 - Compromise response | ✅ | Emergency rotation API |

**Key Features:**
- ✅ Automated rotation every 90 days
- ✅ Zero-downtime rotation
- ✅ Emergency rotation capability
- ✅ Complete audit trail
- ✅ Metrics and alerting
- ✅ Background re-encryption
- ✅ Multi-region support

**Next Steps:**
1. Deploy to production
2. Monitor rotation metrics
3. Review audit logs weekly
4. Annual PCI DSS audit

---

**Questions?** Contact: security@example.com

