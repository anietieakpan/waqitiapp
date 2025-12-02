# Cryptocurrency Service - Secure Credential Management

**Document Version:** 1.0
**Last Updated:** 2025-11-08
**Status:** Production-Ready
**Compliance:** PCI DSS 8.2, SOX 404, NIST 800-53 IA-5

---

## 🚨 CRITICAL SECURITY FIX IMPLEMENTED

**Issue:** CRITICAL-001 - Hardcoded cryptocurrency node credentials
**Previous State:** Passwords set to "password" in configuration files
**Risk:** Complete compromise of crypto wallet infrastructure, unlimited financial loss
**Fix Status:** ✅ **RESOLVED**

---

## Table of Contents

1. [Overview](#overview)
2. [Security Architecture](#security-architecture)
3. [Quick Start](#quick-start)
4. [Vault Integration](#vault-integration)
5. [Credential Rotation](#credential-rotation)
6. [Deployment Guide](#deployment-guide)
7. [Troubleshooting](#troubleshooting)
8. [Compliance](#compliance)
9. [Incident Response](#incident-response)

---

## Overview

This document describes the secure credential management system for Waqiti's cryptocurrency services, including Bitcoin, Litecoin, Ethereum, and Lightning Network integrations.

### What Changed?

| Component | Before (❌ Insecure) | After (✅ Secure) |
|-----------|---------------------|------------------|
| **Bitcoin RPC** | Hardcoded `password` | Environment variable + Vault |
| **Litecoin RPC** | Hardcoded `password` | Environment variable + Vault |
| **LND Macaroon** | Embedded in config | File-based + Vault |
| **Ethereum API** | Hardcoded key | Environment variable + Vault |
| **Credential Rotation** | Manual | Automated script + 90-day policy |

### Security Improvements

- ✅ **No hardcoded credentials** anywhere in codebase
- ✅ **Vault integration** for centralized secret management
- ✅ **Automated rotation** with zero-downtime script
- ✅ **Strong password enforcement** (64-character minimum)
- ✅ **Credential validation** at application startup
- ✅ **Audit logging** for all credential access
- ✅ **Backup and recovery** procedures

---

## Security Architecture

### Credential Flow

```
┌─────────────────────────────────────────────────────────────────────┐
│                   APPLICATION STARTUP                                │
└─────────────────────────────────────────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────────┐
│  Is Vault Enabled?                                                   │
│  (VAULT_ENABLED=true)                                                │
└─────────────────────────────────────────────────────────────────────┘
                    YES │                        │ NO
                        ▼                        ▼
        ┌───────────────────────────┐  ┌──────────────────────────┐
        │  Fetch from Vault         │  │  Load from Environment   │
        │  - Bitcoin credentials    │  │  - .env.crypto file      │
        │  - Litecoin credentials   │  │  - Environment variables │
        │  - Ethereum API keys      │  └──────────────────────────┘
        │  - Lightning macaroons    │
        └───────────────────────────┘
                        │
                        ▼
        ┌───────────────────────────────────────────────────────────┐
        │  VaultCryptoCredentialsConfig                              │
        │  - Validates all credentials present                       │
        │  - Rejects default "password" values                       │
        │  - Logs credential loading (without exposing values)       │
        │  - Fails fast if invalid                                   │
        └───────────────────────────────────────────────────────────┘
                        │
                        ▼
        ┌───────────────────────────────────────────────────────────┐
        │  Inject into Services                                      │
        │  - BitcoinRpcClient                                        │
        │  - LitecoinRpcClient                                       │
        │  - EthereumRpcClient                                       │
        │  - LightningNetworkService                                 │
        └───────────────────────────────────────────────────────────┘
```

### Multi-Layer Security

1. **Layer 1: Environment Variables** (Development)
   - Credentials loaded from `.env.crypto` file
   - File never committed to Git (in `.gitignore`)
   - Secure file permissions (600)

2. **Layer 2: HashiCorp Vault** (Production Recommended)
   - Centralized secret storage
   - Dynamic secret generation
   - Automatic rotation policies
   - Audit logging built-in

3. **Layer 3: Kubernetes Secrets** (Cloud Production)
   - Encrypted at rest
   - RBAC-controlled access
   - Automatic mounting as environment variables

4. **Layer 4: Application Validation**
   - Startup validation of all credentials
   - Rejection of weak/default passwords
   - Health checks for connectivity

---

## Quick Start

### Step 1: Copy Environment Template

```bash
cd /Users/anietieakpan/git/waqiti-app/services/crypto-service
cp .env.crypto.template .env.crypto
```

### Step 2: Generate Strong Passwords

```bash
# Generate Bitcoin RPC password
openssl rand -base64 48 | tr -d "=+/" | cut -c1-64

# Generate Litecoin RPC password
openssl rand -base64 48 | tr -d "=+/" | cut -c1-64

# Generate database password
openssl rand -base64 48 | tr -d "=+/" | cut -c1-64
```

### Step 3: Update .env.crypto

```bash
# Edit the file
nano .env.crypto

# Update these critical values:
BITCOIN_RPC_PASSWORD=<your-generated-bitcoin-password>
LITECOIN_RPC_PASSWORD=<your-generated-litecoin-password>
CRYPTO_DB_PASSWORD=<your-generated-db-password>
ETHEREUM_RPC_API_KEY=<your-infura-api-key>
```

### Step 4: Secure the File

```bash
chmod 600 .env.crypto  # Owner read/write only
```

### Step 5: Start Services

```bash
# Using Docker Compose with secure configuration
docker-compose -f docker-compose.yml -f docker-compose.secure.yml up -d

# Verify health
docker-compose ps
docker-compose logs crypto-service | grep "CRYPTO_CREDENTIALS_VALIDATION"
```

### Step 6: Validate Credentials

```bash
# Check Bitcoin connectivity
docker-compose exec bitcoin-core bitcoin-cli -regtest \
  -rpcuser=$BITCOIN_RPC_USER -rpcpassword=$BITCOIN_RPC_PASSWORD getblockchaininfo

# Check Litecoin connectivity
docker-compose exec litecoin-core litecoin-cli -regtest \
  -rpcuser=$LITECOIN_RPC_USER -rpcpassword=$LITECOIN_RPC_PASSWORD getblockchaininfo

# Check application logs
docker-compose logs crypto-service | grep "status=valid"
```

---

## Vault Integration

### Production Setup (Recommended)

#### 1. Install Vault

```bash
# Start Vault container
docker-compose --profile vault up -d vault

# Initialize Vault (first time only)
docker-compose exec vault vault operator init -key-shares=5 -key-threshold=3
# SAVE the unseal keys and root token securely!

# Unseal Vault
docker-compose exec vault vault operator unseal <unseal-key-1>
docker-compose exec vault vault operator unseal <unseal-key-2>
docker-compose exec vault vault operator unseal <unseal-key-3>
```

#### 2. Configure Vault Secrets

```bash
# Login to Vault
export VAULT_ADDR=http://localhost:8200
export VAULT_TOKEN=<your-root-token>
vault login $VAULT_TOKEN

# Enable KV secrets engine
vault secrets enable -path=secret kv-v2

# Store Bitcoin credentials
vault kv put secret/crypto-service/bitcoin \
  rpc_user="waqiti_bitcoin" \
  rpc_password="<strong-password-here>"

# Store Litecoin credentials
vault kv put secret/crypto-service/litecoin \
  rpc_user="waqiti_litecoin" \
  rpc_password="<strong-password-here>"

# Store Ethereum credentials
vault kv put secret/crypto-service/ethereum \
  provider="infura" \
  rpc_api_key="<your-infura-api-key>"

# Store Lightning credentials
vault kv put secret/crypto-service/lightning \
  macaroon_hex="<lnd-macaroon-hex>"
```

#### 3. Enable AppRole Authentication (Production)

```bash
# Enable AppRole
vault auth enable approle

# Create policy for crypto-service
vault policy write crypto-service-policy - <<EOF
path "secret/data/crypto-service/*" {
  capabilities = ["read"]
}
EOF

# Create AppRole
vault write auth/approle/role/crypto-service \
  token_policies="crypto-service-policy" \
  token_ttl=1h \
  token_max_ttl=4h

# Get RoleID and SecretID
vault read auth/approle/role/crypto-service/role-id
vault write -f auth/approle/role/crypto-service/secret-id
```

#### 4. Update Application Configuration

```bash
# Update .env.crypto
VAULT_ENABLED=true
VAULT_ADDR=https://vault.example.com:8200
VAULT_ROLE_ID=<your-role-id>
VAULT_SECRET_ID=<your-secret-id>
```

---

## Credential Rotation

### Automated Rotation Script

We provide an automated script for safe credential rotation:

```bash
# Rotate Bitcoin credentials only
./scripts/rotate-crypto-credentials.sh bitcoin

# Rotate Litecoin credentials only
./scripts/rotate-crypto-credentials.sh litecoin

# Rotate all credentials
./scripts/rotate-crypto-credentials.sh all
```

### What the Script Does

1. ✅ **Generates** cryptographically secure 64-character password
2. ✅ **Backs up** current credentials with timestamp
3. ✅ **Updates** Vault (if enabled) or `.env.crypto`
4. ✅ **Restarts** affected services gracefully
5. ✅ **Validates** new credentials work correctly
6. ✅ **Logs** all activities for audit trail
7. ✅ **Rolls back** if validation fails

### Manual Rotation (Emergency)

If you need to rotate manually:

```bash
# 1. Generate new password
NEW_PASSWORD=$(openssl rand -base64 48 | tr -d "=+/" | cut -c1-64)

# 2. Update Vault (if using Vault)
vault kv put secret/crypto-service/bitcoin rpc_password="$NEW_PASSWORD"

# 3. Update .env.crypto
sed -i "s/^BITCOIN_RPC_PASSWORD=.*/BITCOIN_RPC_PASSWORD=$NEW_PASSWORD/" .env.crypto

# 4. Restart services
docker-compose restart bitcoin-core crypto-service

# 5. Validate
docker-compose exec bitcoin-core bitcoin-cli -regtest -rpcpassword="$NEW_PASSWORD" getblockchaininfo
```

### Rotation Schedule

| Environment | Frequency | Method | Responsibility |
|-------------|-----------|--------|----------------|
| **Development** | On-demand | Manual or script | Developer |
| **Staging** | Monthly | Automated script | DevOps |
| **Production** | Every 90 days | Vault auto-rotation | Security Team |

---

## Deployment Guide

### Development Environment

```bash
# 1. Clone repository
git clone https://github.com/waqiti/waqiti-app.git
cd waqiti-app/services/crypto-service

# 2. Create .env.crypto from template
cp .env.crypto.template .env.crypto

# 3. Generate and set passwords (see Quick Start)

# 4. Start services
docker-compose -f docker-compose.yml -f docker-compose.secure.yml up -d

# 5. Verify
docker-compose logs -f crypto-service
```

### Staging Environment

```bash
# 1. Enable Vault
export VAULT_ENABLED=true
export VAULT_ADDR=https://vault-staging.example.com

# 2. Configure Vault secrets (see Vault Integration section)

# 3. Deploy with Vault integration
docker-compose -f docker-compose.yml -f docker-compose.secure.yml --profile vault up -d

# 4. Verify Vault connection
docker-compose logs crypto-service | grep "vault_source=vault"
```

### Production Environment (Kubernetes)

```yaml
# kubernetes/crypto-service/secrets.yaml
apiVersion: v1
kind: Secret
metadata:
  name: crypto-service-secrets
  namespace: waqiti
type: Opaque
stringData:
  BITCOIN_RPC_USER: waqiti_bitcoin
  BITCOIN_RPC_PASSWORD: <vault:secret/crypto-service/bitcoin#rpc_password>
  LITECOIN_RPC_USER: waqiti_litecoin
  LITECOIN_RPC_PASSWORD: <vault:secret/crypto-service/litecoin#rpc_password>
  ETHEREUM_RPC_API_KEY: <vault:secret/crypto-service/ethereum#rpc_api_key>
```

```bash
# Deploy to Kubernetes
kubectl apply -f kubernetes/crypto-service/secrets.yaml
kubectl apply -f kubernetes/crypto-service/deployment.yaml

# Verify
kubectl get pods -n waqiti
kubectl logs -n waqiti deployment/crypto-service | grep "CRYPTO_CREDENTIALS_VALIDATION"
```

---

## Troubleshooting

### Issue: Application fails to start with "password is not configured"

**Cause:** Environment variables not loaded correctly

**Solution:**
```bash
# Check .env.crypto exists
ls -la .env.crypto

# Verify file permissions
chmod 600 .env.crypto

# Check Docker Compose loads environment
docker-compose config | grep BITCOIN_RPC_PASSWORD

# Restart with explicit env file
docker-compose --env-file .env.crypto up -d crypto-service
```

### Issue: "SECURITY VIOLATION: password is set to default 'password'"

**Cause:** Default password not changed

**Solution:**
```bash
# Generate new password
openssl rand -base64 48 | tr -d "=+/" | cut -c1-64

# Update .env.crypto
nano .env.crypto  # Replace BITCOIN_RPC_PASSWORD value

# Restart
docker-compose restart crypto-service
```

### Issue: Bitcoin/Litecoin RPC connection refused

**Cause:** Credentials mismatch or service not started

**Solution:**
```bash
# Check Bitcoin Core is running
docker-compose ps bitcoin-core

# Verify credentials match
docker-compose exec bitcoin-core cat /home/bitcoin/.bitcoin/bitcoin.conf | grep rpcpassword

# Compare with application config
docker-compose exec crypto-service env | grep BITCOIN_RPC_PASSWORD

# Restart both services
docker-compose restart bitcoin-core crypto-service
```

### Issue: Vault connection failed

**Cause:** Vault not unsealed or network issue

**Solution:**
```bash
# Check Vault status
docker-compose exec vault vault status

# Unseal if needed
docker-compose exec vault vault operator unseal <unseal-key>

# Verify network connectivity
docker-compose exec crypto-service ping vault

# Check Vault token
echo $VAULT_TOKEN
```

---

## Compliance

### PCI DSS 8.2 - Credential Management

✅ **Requirement 8.2.1:** User passwords must contain both numeric and alphabetic characters
✅ **Requirement 8.2.3:** Passwords/passphrases must meet minimum strength (64 characters)
✅ **Requirement 8.2.4:** Change user passwords at least every 90 days (automated rotation)
✅ **Requirement 8.2.5:** Do not allow an individual to submit a new password that is the same as any of the last four passwords used (Vault rotation history)

### SOX 404 - IT General Controls

✅ **Access Controls:** Only authorized personnel can access credentials (Vault RBAC)
✅ **Change Management:** All credential changes logged and auditable
✅ **Monitoring:** Real-time alerts on credential access failures

### NIST 800-53 - IA-5 Authenticator Management

✅ **IA-5(1)(a):** Minimum password complexity enforced (64-character cryptographic)
✅ **IA-5(1)(d):** Password minimum/maximum lifetime enforced (90-day rotation)
✅ **IA-5(1)(e):** Prohibition of password reuse (Vault tracks history)
✅ **IA-5(7):** Unencrypted static authenticators are not embedded in applications (no hardcoded passwords)

---

## Incident Response

### Scenario: Credentials Compromised

**Immediate Actions (within 1 hour):**

1. **Rotate ALL credentials:**
   ```bash
   ./scripts/rotate-crypto-credentials.sh all
   ```

2. **Check audit logs:**
   ```bash
   grep "CRYPTO_CREDENTIALS" logs/credential-rotation.log
   docker-compose logs crypto-service | grep "RPC"
   ```

3. **Verify no unauthorized transactions:**
   ```bash
   docker-compose exec bitcoin-core bitcoin-cli -regtest listtransactions "*" 1000
   docker-compose exec litecoin-core litecoin-cli -regtest listtransactions "*" 1000
   ```

4. **Notify security team:**
   - Email: security@example.com
   - Slack: #security-incidents
   - PagerDuty: Trigger critical alert

**Follow-up Actions (within 24 hours):**

5. **Conduct forensic analysis:**
   - Review all access logs
   - Identify breach timeline
   - Determine scope of exposure

6. **Update security measures:**
   - Implement additional monitoring
   - Review and strengthen access controls
   - Update incident response procedures

7. **Document incident:**
   - Create incident report
   - Update compliance documentation
   - Share lessons learned

---

## Support

**Security Team:** security@example.com
**DevOps Team:** devops@example.com
**On-Call:** +1-xxx-xxx-xxxx (PagerDuty)

**Documentation Updated:** 2025-11-08
**Next Review:** 2026-02-08 (90 days)
