# VAULT SECRETS MIGRATION GUIDE

**Waqiti Fintech Platform - Enterprise Secrets Management**

**Version:** 1.0
**Last Updated:** October 2025
**Author:** Platform Security Team
**Status:** Implementation Ready

---

## Executive Summary

### Current State
The Waqiti platform currently manages secrets through a hybrid approach:
- **233 application configuration files** across 103 microservices
- **427+ Vault placeholder references** already in configuration files
- **Partial Vault integration** with infrastructure in place but incomplete adoption
- **Hardcoded secrets** in environment variables and YAML files
- **Mixed secret types**: Database credentials, API keys, payment providers, blockchain wallets, JWT secrets, and more

### Target State
Complete centralized secrets management using HashiCorp Vault:
- All secrets stored and managed exclusively in Vault
- Dynamic database credentials with automatic rotation
- AppRole-based authentication for all services
- Environment-specific secret isolation (dev/staging/production)
- Automated secret rotation and audit logging
- Zero hardcoded credentials in version control

### Benefits
1. **Security**: Centralized access control, audit trails, and encryption at rest
2. **Compliance**: PCI-DSS, SOC2, and GDPR compliance requirements
3. **Operational**: Automated rotation reduces manual overhead
4. **Incident Response**: Rapid secret revocation and rotation capabilities
5. **Developer Experience**: Simplified local development with secure secret access

### Timeline
- **Phase 1 (Week 1)**: Vault cluster setup and configuration - 5 days
- **Phase 2 (Week 2)**: Infrastructure secrets migration - 5 days
- **Phase 3 (Week 3)**: Application secrets migration - 5 days
- **Phase 4 (Week 4)**: Validation, rollout, and hardened production - 5 days
- **Total Duration**: 4 weeks (20 business days)

---

## 1. Secret Inventory

### 1.1 Infrastructure Secrets

#### Database Credentials (Priority: P0)
- **Count**: 103 services
- **Current State**: Mix of `${DATABASE_PASSWORD}`, `${VAULT_DATABASE_PASSWORD}`, and Vault placeholders
- **Services**: All microservices (payment-service, core-banking-service, account-service, etc.)
- **Vault Path**: `{env}/database/{service-name}`
- **Rotation**: Automatic every 24 hours
- **Examples**:
  - `spring.datasource.password: ${DB_PASSWORD:${vault.database.password}}`
  - `jdbc:postgresql://postgres-payment:5432/waqiti_payments`

#### Redis Credentials (Priority: P0)
- **Count**: 95+ services
- **Current State**: `${REDIS_PASSWORD}` with Vault fallbacks
- **Vault Path**: `{env}/infrastructure/redis`
- **Rotation**: Manual/90 days
- **Connection Pattern**: `spring.data.redis.password: ${vault.redis.password}`

#### Kafka Credentials (Priority: P0)
- **Count**: 80+ services
- **Current State**: SSL keystore/truststore passwords
- **Vault Path**: `{env}/infrastructure/kafka`
- **Rotation**: Manual/90 days
- **Secrets**:
  - `kafka.ssl.truststore-password`
  - `kafka.ssl.keystore-password`

#### Elasticsearch Credentials (Priority: P1)
- **Count**: 15 services
- **Current State**: `${ELASTICSEARCH_PASSWORD}`
- **Services**: analytics-service, support-service, reporting-service, etc.
- **Vault Path**: `{env}/infrastructure/elasticsearch`

### 1.2 Authentication & Authorization Secrets

#### JWT Secrets (Priority: P0)
- **Count**: All services with JWT validation
- **Current State**: Mix of `${JWT_SECRET}` and Vault references
- **Vault Path**: `{env}/common/jwt`
- **Rotation**: Manual/180 days
- **Keys**:
  - Primary signing key
  - Refresh token key
  - Service-specific keys

#### Keycloak Client Secrets (Priority: P0)
- **Count**: 103 services
- **Current State**: `${KEYCLOAK_CLIENT_SECRET}` with service-specific defaults
- **Vault Path**: `{env}/keycloak/{service-name}`
- **Pattern**: Each service has dedicated Keycloak client
- **Examples**:
  - `keycloak.credentials.secret: ${KEYCLOAK_CLIENT_SECRET:CHANGE_ME_SECURE_SECRET_REQUIRED}`
  - Services: payment-service, core-banking-service, account-service, etc.

#### Encryption Keys (Priority: P0)
- **Master Encryption Key**: Platform-wide data encryption
- **Service-Specific Keys**: Per-service encryption for PII
- **Transit Keys**: Vault Transit engine for encryption-as-a-service
- **Vault Paths**:
  - `{env}/common/encryption/master-key`
  - `{env}/encryption/{service-name}`
  - Transit engine: `transit/keys/waqiti-encryption-key`

### 1.3 Payment Provider Credentials

#### Stripe (Priority: P0)
- **Services**: payment-service, bank-integration-service, subscription services
- **Secrets**:
  - API Key: `${STRIPE_API_KEY:${VAULT_STRIPE_API_KEY}}`
  - Secret Key: `${STRIPE_SECRET_KEY:${VAULT_STRIPE_SECRET_KEY}}`
  - Webhook Secret: `${STRIPE_WEBHOOK_SECRET:${VAULT_STRIPE_WEBHOOK_SECRET}}`
- **Vault Path**: `{env}/payment-providers/stripe`
- **Rotation**: Manual on breach/90 days

#### PayPal (Priority: P0)
- **Services**: payment-service, bank-integration-service
- **Secrets**:
  - Client Secret: `${PAYPAL_CLIENT_SECRET:${VAULT_PAYPAL_CLIENT_SECRET}}`
  - Webhook ID
- **Vault Path**: `{env}/payment-providers/paypal`

#### Plaid (Priority: P0)
- **Services**: bank-integration-service
- **Secrets**:
  - Secret: `${PLAID_SECRET:${VAULT_PLAID_SECRET}}`
  - Client ID
- **Vault Path**: `{env}/payment-providers/plaid`

#### Other Payment Providers (Priority: P1)
- **Adyen**: `${ADYEN_API_KEY}`
- **Dwolla**: `${DWOLLA_KEY}`, `${DWOLLA_SECRET}`
- **Wise (TransferWise)**: `${WISE_API_TOKEN}`
- **Yodlee**: `${YODLEE_SECRET}`
- **Figo**: `${FIGO_SECRET}`
- **Vault Path**: `{env}/payment-providers/{provider-name}`

### 1.4 KYC/Compliance Provider Credentials

#### Jumio (Priority: P0)
- **Services**: kyc-service
- **Secrets**:
  - API Token: `${JUMIO_API_TOKEN}`
  - API Secret: `${JUMIO_API_SECRET}`
- **Vault Path**: `{env}/kyc-providers/jumio`

#### Onfido (Priority: P0)
- **Services**: kyc-service
- **Secrets**:
  - API Token: `${ONFIDO_API_TOKEN}`
- **Vault Path**: `{env}/kyc-providers/onfido`

#### OFAC (Priority: P1)
- **Services**: compliance-service
- **Secrets**:
  - API Key: `${OFAC_API_KEY}`
- **Vault Path**: `{env}/compliance/ofac`

### 1.5 Communication & Notification Secrets

#### Twilio (Priority: P0)
- **Services**: notification-service, sms-banking-service, voice-payment-service
- **Secrets**:
  - Auth Token: `${TWILIO_AUTH_TOKEN}`
  - Account SID: `${TWILIO_ACCOUNT_SID}`
- **Vault Path**: `{env}/communication/twilio`

#### SendGrid (Priority: P0)
- **Services**: notification-service, messaging-service
- **Secrets**:
  - API Key: `${SENDGRID_API_KEY}`
- **Vault Path**: `{env}/communication/sendgrid`

#### Firebase/Push Notifications (Priority: P1)
- **Services**: notification-service
- **Secrets**:
  - Server Key
  - Service Account JSON
- **Vault Path**: `{env}/communication/firebase`

### 1.6 Blockchain & Crypto Credentials

#### Bitcoin (Priority: P0)
- **Services**: crypto-service, nft-service, wallet-service
- **Secrets**:
  - RPC Username: `${vault.blockchain.bitcoin.rpc-username}`
  - RPC Password: `${vault.blockchain.bitcoin.rpc-password}`
  - Hot Wallet Private Keys
- **Vault Path**: `{env}/blockchain/bitcoin`
- **Rotation**: Never (wallet keys), RPC credentials every 90 days

#### Ethereum (Priority: P0)
- **Services**: crypto-service, nft-service, layer2-service, investment-service
- **Secrets**:
  - Infura Project Secret: `${vault.blockchain.ethereum.infura-secret}`
  - API Keys: `${vault.blockchain.ethereum.api-key}`
  - Private Keys: `${vault.blockchain.hot-wallet.private-key}`
- **Vault Path**: `{env}/blockchain/ethereum`

#### IPFS/Pinata (Priority: P1)
- **Services**: nft-service
- **Secrets**:
  - API Key: `${vault.ipfs.pinata.api-key}`
  - Secret Key: `${vault.ipfs.pinata.secret-key}`
- **Vault Path**: `{env}/blockchain/ipfs`

### 1.7 Cloud Provider Credentials

#### AWS (Priority: P0)
- **Services**: Multiple (S3 storage, SQS, SNS)
- **Secrets**:
  - Access Key: `${vault.aws.s3.access-key}`
  - Secret Key: `${vault.aws.s3.secret-key}`
- **Vault Path**: `{env}/cloud/aws`
- **Rotation**: Automatic via Vault AWS secrets engine

#### Azure (Priority: P1)
- **Services**: Integration services
- **Secrets**:
  - Client Secret
- **Vault Path**: `{env}/cloud/azure`

#### GCP (Priority: P1)
- **Services**: Integration services
- **Secrets**:
  - Service Account JSON
- **Vault Path**: `{env}/cloud/gcp`

### 1.8 Monitoring & Observability Credentials

#### DataDog (Priority: P1)
- **Services**: All services (APM)
- **Secrets**:
  - API Key: `${DATADOG_API_KEY}`
  - App Key
- **Vault Path**: `{env}/monitoring/datadog`

#### Sentry (Priority: P1)
- **Services**: All services (error tracking)
- **Secrets**:
  - DSN: `${SENTRY_DSN}`
- **Vault Path**: `{env}/monitoring/sentry`

#### OpenAI (Priority: P1)
- **Services**: support-service (AI chatbot), ml-service
- **Secrets**:
  - API Key: `${OPENAI_API_KEY:PRODUCTION_OPENAI_KEY_REQUIRED}`
- **Vault Path**: `{env}/ml/openai`

### 1.9 Social Commerce Credentials

#### Instagram (Priority: P1)
- **Services**: social-commerce-service
- **Secrets**:
  - App Secret: `${INSTAGRAM_APP_SECRET}`
  - Webhook Verify Token: `${INSTAGRAM_WEBHOOK_TOKEN}`
- **Vault Path**: `{env}/social/instagram`

#### TikTok (Priority: P1)
- **Services**: social-commerce-service
- **Secrets**:
  - Client Secret: `${TIKTOK_CLIENT_SECRET}`
  - Webhook Secret: `${TIKTOK_WEBHOOK_SECRET}`
- **Vault Path**: `{env}/social/tiktok`

#### YouTube (Priority: P1)
- **Services**: social-commerce-service
- **Secrets**:
  - API Key: `${YOUTUBE_API_KEY}`
  - OAuth Client Secret: `${YOUTUBE_OAUTH_CLIENT_SECRET}`
- **Vault Path**: `{env}/social/youtube`

### 1.10 SSL/TLS Certificates

#### Keystore Passwords (Priority: P0)
- **Services**: All services with SSL enabled
- **Secrets**:
  - Keystore Password: `${SSL_KEYSTORE_PASSWORD}`
  - Truststore Password: `${SSL_TRUSTSTORE_PASSWORD}`
  - Key Password
- **Vault Path**: `{env}/common/ssl`
- **Rotation**: Every 90 days

#### PKI Certificates (Priority: P1)
- **Services**: config-service, api-gateway
- **Vault Path**: `pki/{service-name}`
- **Managed by**: Vault PKI secrets engine

### 1.11 Specialized Service Credentials

#### Tax Service (Priority: P0)
- **Secrets**:
  - Avalara License Key: `${AVALARA_LICENSE_KEY}`
  - IRS API Key: `${IRS_API_KEY}`
  - Tax Encryption Key: `${TAX_ENCRYPTION_KEY}`
- **Vault Path**: `{env}/tax-service`

#### Currency Exchange (Priority: P0)
- **Services**: core-banking-service, currency-service
- **Secrets**:
  - Exchange API Key: `${EXCHANGE_API_KEY}`
  - Fallback API Key: `${FALLBACK_EXCHANGE_API_KEY}`
- **Vault Path**: `{env}/currency/exchange-apis`

#### Config Service (Priority: P0)
- **Secrets**:
  - Vault Token: `${VAULT_TOKEN}`
  - Git Password: `${GIT_PASSWORD}`
  - Config Server Password: `${CONFIG_SERVER_PASSWORD}`
  - Encrypt Key: `${ENCRYPT_KEY}`
- **Vault Path**: `{env}/config-service`

---

## 2. Vault Path Structure

### 2.1 Recommended Path Hierarchy

```
vault/
├── development/
│   ├── common/
│   │   ├── jwt/                    # Platform-wide JWT secrets
│   │   ├── encryption/             # Master encryption keys
│   │   ├── redis/                  # Shared Redis credentials
│   │   └── ssl/                    # SSL/TLS certificates
│   ├── database/
│   │   ├── payment-service/        # Dynamic DB credentials
│   │   ├── core-banking-service/
│   │   └── {service-name}/
│   ├── keycloak/
│   │   ├── payment-service/        # Service-specific client secrets
│   │   ├── core-banking-service/
│   │   └── {service-name}/
│   ├── payment-providers/
│   │   ├── stripe/
│   │   ├── paypal/
│   │   ├── plaid/
│   │   ├── adyen/
│   │   └── {provider-name}/
│   ├── kyc-providers/
│   │   ├── jumio/
│   │   └── onfido/
│   ├── blockchain/
│   │   ├── bitcoin/
│   │   ├── ethereum/
│   │   └── ipfs/
│   ├── communication/
│   │   ├── twilio/
│   │   ├── sendgrid/
│   │   └── firebase/
│   ├── cloud/
│   │   ├── aws/
│   │   ├── azure/
│   │   └── gcp/
│   ├── monitoring/
│   │   ├── datadog/
│   │   └── sentry/
│   ├── social/
│   │   ├── instagram/
│   │   ├── tiktok/
│   │   └── youtube/
│   └── infrastructure/
│       ├── kafka/
│       └── elasticsearch/
├── staging/
│   └── [same structure as development]
└── production/
    └── [same structure as development]
```

### 2.2 Environment Separation Strategy

**Namespace-Based Isolation**:
- Each environment (dev/staging/prod) uses separate Vault namespaces
- Prevents cross-environment secret access
- Allows per-environment access policies

**Path Prefix Convention**:
```
{environment}/{category}/{service-or-provider}
```

**Examples**:
- `development/database/payment-service`
- `production/payment-providers/stripe`
- `staging/keycloak/core-banking-service`

### 2.3 Secret Key Naming Conventions

**Standard Keys**:
- `password` - Database/service passwords
- `username` - Database/service usernames
- `api-key` - API keys for external services
- `secret-key` - Secret keys for signing/encryption
- `client-secret` - OAuth2 client secrets
- `token` - Authentication tokens
- `private-key` - Private keys (blockchain, SSH, etc.)

**Compound Keys**:
- `keystore-password`
- `truststore-password`
- `webhook-secret`
- `refresh-secret`

---

## 3. Implementation Steps

### Phase 1: Vault Setup (Week 1, Days 1-5)

#### Day 1-2: Vault Cluster Deployment

**1.1 Deploy Vault Cluster (HA Setup)**

**Option A: Docker Compose (Development/Staging)**
```yaml
# docker-compose-vault.yml
version: '3.8'

services:
  vault-server:
    image: hashicorp/vault:1.15.0
    container_name: waqiti-vault
    ports:
      - "8200:8200"
    environment:
      VAULT_ADDR: 'http://0.0.0.0:8200'
      VAULT_API_ADDR: 'http://0.0.0.0:8200'
      VAULT_LOCAL_CONFIG: |
        {
          "backend": {
            "consul": {
              "address": "consul:8500",
              "path": "vault/"
            }
          },
          "listener": {
            "tcp": {
              "address": "0.0.0.0:8200",
              "tls_disable": 1
            }
          },
          "ui": true,
          "default_lease_ttl": "168h",
          "max_lease_ttl": "720h"
        }
    cap_add:
      - IPC_LOCK
    volumes:
      - ./vault/config:/vault/config
      - ./vault/logs:/vault/logs
      - vault-data:/vault/file
    command: server
    networks:
      - waqiti-network

  consul:
    image: consul:1.16
    container_name: waqiti-consul
    ports:
      - "8500:8500"
    command: agent -server -ui -bootstrap-expect=1 -client=0.0.0.0
    volumes:
      - consul-data:/consul/data
    networks:
      - waqiti-network

volumes:
  vault-data:
  consul-data:

networks:
  waqiti-network:
    driver: bridge
```

**Option B: Kubernetes (Production)**
```yaml
# vault-helm-values.yaml
server:
  ha:
    enabled: true
    replicas: 3
    raft:
      enabled: true
      setNodeId: true
      config: |
        ui = true

        listener "tcp" {
          tls_disable = 0
          address = "[::]:8200"
          cluster_address = "[::]:8201"
          tls_cert_file = "/vault/userconfig/vault-tls/tls.crt"
          tls_key_file  = "/vault/userconfig/vault-tls/tls.key"
        }

        storage "raft" {
          path = "/vault/data"
        }

        seal "awskms" {
          region     = "us-east-1"
          kms_key_id = "alias/waqiti-vault-unseal"
        }

        service_registration "kubernetes" {}

  ingress:
    enabled: true
    hosts:
      - host: vault.example.com
        paths: [/]
    tls:
      - secretName: vault-tls
        hosts:
          - vault.example.com

  resources:
    requests:
      memory: 2Gi
      cpu: 1000m
    limits:
      memory: 4Gi
      cpu: 2000m

  dataStorage:
    enabled: true
    size: 50Gi
    storageClass: gp3-encrypted

  auditStorage:
    enabled: true
    size: 20Gi
```

**Deploy Commands**:
```bash
# Docker Compose
docker-compose -f docker-compose-vault.yml up -d

# Kubernetes
helm repo add hashicorp https://helm.releases.hashicorp.com
helm install vault hashicorp/vault -f vault-helm-values.yaml -n vault --create-namespace
```

**1.2 Initialize and Unseal Vault**

```bash
# Initialize Vault (produces unseal keys and root token)
export VAULT_ADDR='http://localhost:8200'
vault operator init -key-shares=5 -key-threshold=3 > vault-init-output.txt

# Store unseal keys securely (use 1Password, AWS Secrets Manager, or Azure Key Vault)
# Extract unseal keys from vault-init-output.txt

# Unseal Vault (requires 3 of 5 keys)
vault operator unseal <UNSEAL_KEY_1>
vault operator unseal <UNSEAL_KEY_2>
vault operator unseal <UNSEAL_KEY_3>

# Verify status
vault status

# Login with root token (from vault-init-output.txt)
vault login <ROOT_TOKEN>
```

**Security Note**: Store unseal keys and root token in separate secure locations (split custody).

#### Day 2-3: Secrets Engine Configuration

**2.1 Enable Secrets Engines**

```bash
# KV v2 for general secrets
vault secrets enable -path=secret kv-v2

# Database dynamic credentials
vault secrets enable database

# Transit encryption engine
vault secrets enable transit

# PKI for certificates
vault secrets enable pki
vault secrets tune -max-lease-ttl=87600h pki

# AWS dynamic credentials
vault secrets enable -path=aws aws
```

**2.2 Configure Database Secrets Engine (PostgreSQL)**

```bash
# Configure PostgreSQL connection
vault write database/config/waqiti-postgres \
    plugin_name=postgresql-database-plugin \
    allowed_roles="payment-service-db-role,core-banking-db-role,account-db-role" \
    connection_url="postgresql://{{username}}:{{password}}@postgres:5432/waqiti?sslmode=require" \
    username="vault_admin" \
    password="<VAULT_ADMIN_PASSWORD>"

# Create dynamic role for payment-service
vault write database/roles/payment-service-db-role \
    db_name=waqiti-postgres \
    creation_statements="CREATE ROLE \"{{name}}\" WITH LOGIN PASSWORD '{{password}}' VALID UNTIL '{{expiration}}'; \
        GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO \"{{name}}\"; \
        GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO \"{{name}}\";" \
    default_ttl="1h" \
    max_ttl="24h"

# Repeat for all 103 services (automate with script)
```

**2.3 Configure Transit Encryption Engine**

```bash
# Create encryption key for platform-wide use
vault write -f transit/keys/waqiti-encryption-key

# Enable convergent encryption for deterministic results
vault write transit/keys/waqiti-encryption-key/config \
    convergent_encryption=true \
    derived=true
```

**2.4 Configure PKI for TLS Certificates**

```bash
# Generate root CA
vault write pki/root/generate/internal \
    common_name="Waqiti Fintech Root CA" \
    ttl=87600h

# Configure CA and CRL URLs
vault write pki/config/urls \
    issuing_certificates="http://vault.example.com:8200/v1/pki/ca" \
    crl_distribution_points="http://vault.example.com:8200/v1/pki/crl"

# Create role for service certificates
vault write pki/roles/waqiti-services \
    allowed_domains="example.com,*.example.com" \
    allow_subdomains=true \
    max_ttl="720h"
```

#### Day 3-4: Authentication Configuration

**3.1 Enable AppRole Authentication**

```bash
# Enable AppRole auth method
vault auth enable approle

# Create policy for payment-service
vault policy write payment-service-policy - <<EOF
path "development/database/payment-service/*" {
  capabilities = ["read"]
}
path "development/keycloak/payment-service/*" {
  capabilities = ["read"]
}
path "development/common/*" {
  capabilities = ["read"]
}
path "development/payment-providers/*" {
  capabilities = ["read"]
}
path "database/creds/payment-service-db-role" {
  capabilities = ["read"]
}
path "transit/encrypt/waqiti-encryption-key" {
  capabilities = ["update"]
}
path "transit/decrypt/waqiti-encryption-key" {
  capabilities = ["update"]
}
EOF

# Create AppRole for payment-service
vault write auth/approle/role/payment-service \
    token_policies="payment-service-policy" \
    token_ttl=1h \
    token_max_ttl=4h \
    secret_id_ttl=24h

# Get RoleID and SecretID for payment-service
vault read auth/approle/role/payment-service/role-id
vault write -f auth/approle/role/payment-service/secret-id

# Repeat for all 103 services (automate with script)
```

**3.2 Service Policy Template**

Create script to generate policies for all services:

```bash
#!/bin/bash
# generate-service-policies.sh

SERVICES=(
  "payment-service"
  "core-banking-service"
  "account-service"
  "wallet-service"
  "transaction-service"
  "user-service"
  "notification-service"
  # ... all 103 services
)

for SERVICE in "${SERVICES[@]}"; do
  vault policy write ${SERVICE}-policy - <<EOF
# Database credentials
path "development/database/${SERVICE}/*" {
  capabilities = ["read"]
}
path "database/creds/${SERVICE}-db-role" {
  capabilities = ["read"]
}

# Service-specific Keycloak secrets
path "development/keycloak/${SERVICE}/*" {
  capabilities = ["read"]
}

# Common secrets (JWT, encryption, Redis)
path "development/common/*" {
  capabilities = ["read"]
}

# Infrastructure secrets
path "development/infrastructure/*" {
  capabilities = ["read"]
}

# Transit encryption
path "transit/encrypt/waqiti-encryption-key" {
  capabilities = ["update"]
}
path "transit/decrypt/waqiti-encryption-key" {
  capabilities = ["update"]
}

# Payment providers (if applicable)
path "development/payment-providers/*" {
  capabilities = ["read"]
}

# Blockchain (for crypto services)
path "development/blockchain/*" {
  capabilities = ["read"]
}
EOF

  # Create AppRole
  vault write auth/approle/role/${SERVICE} \
      token_policies="${SERVICE}-policy" \
      token_ttl=1h \
      token_max_ttl=4h \
      secret_id_ttl=24h

  echo "Created policy and AppRole for ${SERVICE}"
done
```

**3.3 Configure Kubernetes Authentication (for K8s deployments)**

```bash
# Enable Kubernetes auth
vault auth enable kubernetes

# Configure Kubernetes auth
vault write auth/kubernetes/config \
    kubernetes_host="https://kubernetes.default.svc:443" \
    kubernetes_ca_cert=@/var/run/secrets/kubernetes.io/serviceaccount/ca.crt \
    token_reviewer_jwt=@/var/run/secrets/kubernetes.io/serviceaccount/token

# Create role for services in Kubernetes
vault write auth/kubernetes/role/waqiti-services \
    bound_service_account_names=payment-service,core-banking-service,account-service \
    bound_service_account_namespaces=waqiti \
    policies=payment-service-policy,core-banking-policy,account-policy \
    ttl=1h
```

#### Day 4-5: Monitoring and Audit Setup

**4.1 Enable Audit Logging**

```bash
# Enable file audit device
vault audit enable file file_path=/vault/logs/audit.log

# Enable syslog audit (for centralized logging)
vault audit enable syslog tag="vault" facility="LOCAL7"
```

**4.2 Configure Prometheus Metrics**

```hcl
# vault.hcl
telemetry {
  prometheus_retention_time = "30s"
  disable_hostname = false
}
```

**4.3 Health Check Endpoints**

```bash
# Configure health check
curl http://vault.example.com:8200/v1/sys/health

# Expected response:
# {
#   "initialized": true,
#   "sealed": false,
#   "standby": false,
#   "replication_performance_mode": "disabled",
#   "replication_dr_mode": "disabled",
#   "server_time_utc": 1697000000,
#   "version": "1.15.0"
# }
```

**4.4 Set Up Monitoring Dashboard**

Create Grafana dashboard for Vault metrics:
- Unseal status
- Audit log volume
- Secret access patterns
- Database credential leases
- Failed authentication attempts

---

### Phase 2: Infrastructure Secrets Migration (Week 2, Days 6-10)

#### Day 6-7: Database Credentials Migration

**5.1 Populate Static Database Secrets**

For services not using dynamic credentials initially:

```bash
#!/bin/bash
# populate-database-secrets.sh

# Development environment
vault kv put development/database/payment-service \
    username="payment_service" \
    password="<GENERATED_STRONG_PASSWORD>" \
    url="jdbc:postgresql://postgres-payment:5432/waqiti_payments"

vault kv put development/database/core-banking-service \
    username="core_banking_service" \
    password="<GENERATED_STRONG_PASSWORD>" \
    url="jdbc:postgresql://postgres-banking:5432/waqiti_banking"

# Repeat for all 103 services...

# Production environment (different passwords!)
vault kv put production/database/payment-service \
    username="payment_service" \
    password="<PRODUCTION_STRONG_PASSWORD>" \
    url="jdbc:postgresql://postgres-payment-prod.rds.amazonaws.com:5432/waqiti_payments"
```

**5.2 Update Service Configurations**

Update `application.yml` to use Vault:

```yaml
# services/payment-service/src/main/resources/application.yml
spring:
  datasource:
    url: ${vault.database.url}
    username: ${vault.database.username}
    password: ${vault.database.password}

  cloud:
    vault:
      enabled: true
      uri: ${VAULT_URI:https://vault.example.com}
      authentication: APPROLE
      app-role:
        role-id: ${VAULT_ROLE_ID}
        secret-id: ${VAULT_SECRET_ID}
      kv:
        enabled: true
        backend: secret
        profile-separator: /
        application-name: ${spring.application.name}
```

**5.3 Test Database Connectivity**

```bash
# Start service with Vault configuration
export VAULT_ROLE_ID="<payment-service-role-id>"
export VAULT_SECRET_ID="<payment-service-secret-id>"
export VAULT_URI="http://localhost:8200"

./gradlew :payment-service:bootRun

# Verify successful database connection in logs
```

#### Day 7-8: Redis and Kafka Credentials

**6.1 Migrate Redis Secrets**

```bash
# Common Redis credentials (shared across services)
vault kv put development/infrastructure/redis \
    password="<REDIS_PASSWORD>" \
    host="redis" \
    port="6379"

vault kv put production/infrastructure/redis \
    password="<PRODUCTION_REDIS_PASSWORD>" \
    host="redis-cluster.example.com" \
    port="6379"
```

**6.2 Migrate Kafka Secrets**

```bash
# Kafka SSL credentials
vault kv put development/infrastructure/kafka \
    bootstrap-servers="kafka:9092" \
    truststore-password="<TRUSTSTORE_PASSWORD>" \
    keystore-password="<KEYSTORE_PASSWORD>" \
    security-protocol="SSL"

# Store truststore/keystore files in Vault
vault kv put development/infrastructure/kafka/ssl \
    truststore=@kafka.truststore.jks \
    keystore=@kafka.keystore.jks
```

**6.3 Update Service Configurations**

```yaml
spring:
  data:
    redis:
      host: ${vault.redis.host}
      port: ${vault.redis.port}
      password: ${vault.redis.password}

  kafka:
    bootstrap-servers: ${vault.kafka.bootstrap-servers}
    ssl:
      trust-store-password: ${vault.kafka.truststore-password}
      key-store-password: ${vault.kafka.keystore-password}
```

#### Day 8-10: SSL/TLS Certificates

**7.1 Migrate Keystore/Truststore Passwords**

```bash
# Common SSL secrets
vault kv put development/common/ssl \
    keystore-password="<KEYSTORE_PASSWORD>" \
    truststore-password="<TRUSTSTORE_PASSWORD>" \
    key-password="<KEY_PASSWORD>"

vault kv put production/common/ssl \
    keystore-password="<PRODUCTION_KEYSTORE_PASSWORD>" \
    truststore-password="<PRODUCTION_TRUSTSTORE_PASSWORD>" \
    key-password="<PRODUCTION_KEY_PASSWORD>"
```

**7.2 Test Infrastructure Secrets**

Validate each service can:
- Connect to database
- Connect to Redis
- Connect to Kafka
- Load SSL certificates

---

### Phase 3: Application Secrets Migration (Week 3, Days 11-15)

#### Day 11-12: JWT and Encryption Keys

**8.1 Migrate JWT Secrets**

```bash
# Generate strong JWT secrets
JWT_SECRET=$(openssl rand -base64 64)
JWT_REFRESH_SECRET=$(openssl rand -base64 64)

# Store in Vault
vault kv put development/common/jwt \
    secret="${JWT_SECRET}" \
    refresh-secret="${JWT_REFRESH_SECRET}" \
    algorithm="HS512" \
    expiration="3600"

vault kv put production/common/jwt \
    secret="<DIFFERENT_PRODUCTION_JWT_SECRET>" \
    refresh-secret="<DIFFERENT_PRODUCTION_REFRESH_SECRET>" \
    algorithm="HS512" \
    expiration="3600"
```

**8.2 Migrate Encryption Keys**

```bash
# Platform-wide encryption key
MASTER_KEY=$(openssl rand -base64 32)

vault kv put development/common/encryption \
    master-key="${MASTER_KEY}" \
    algorithm="AES-256-GCM"

# Service-specific encryption keys
vault kv put development/encryption/tax-service \
    data-key="<TAX_DATA_ENCRYPTION_KEY>" \
    algorithm="AES-256-GCM"
```

#### Day 12-13: Keycloak Client Secrets

**9.1 Migrate Keycloak Secrets**

```bash
# Script to migrate all service client secrets
#!/bin/bash
# migrate-keycloak-secrets.sh

SERVICES=(
  "payment-service"
  "core-banking-service"
  "account-service"
  # ... all 103 services
)

for SERVICE in "${SERVICES[@]}"; do
  # Generate unique client secret
  CLIENT_SECRET=$(openssl rand -base64 32)

  # Store in Vault
  vault kv put development/keycloak/${SERVICE} \
      client-id="${SERVICE}" \
      client-secret="${CLIENT_SECRET}" \
      realm="waqiti-fintech" \
      issuer-uri="https://keycloak.example.com/realms/waqiti-fintech"

  echo "Migrated Keycloak secret for ${SERVICE}"

  # Update Keycloak client configuration via REST API
  curl -X PUT "https://keycloak.example.com/admin/realms/waqiti-fintech/clients/${SERVICE}" \
      -H "Authorization: Bearer ${KEYCLOAK_ADMIN_TOKEN}" \
      -H "Content-Type: application/json" \
      -d "{\"secret\": \"${CLIENT_SECRET}\"}"
done
```

**9.2 Update Service Configurations**

```yaml
keycloak:
  credentials:
    secret: ${vault.keycloak.client-secret}
  resource: ${vault.keycloak.client-id}
  realm: ${vault.keycloak.realm}
  auth-server-url: ${vault.keycloak.issuer-uri}
```

#### Day 13-14: Payment Provider Credentials

**10.1 Migrate Stripe Secrets**

```bash
# Development/Sandbox Stripe credentials
vault kv put development/payment-providers/stripe \
    api-key="sk_test_..." \
    secret-key="sk_test_..." \
    webhook-secret="whsec_..." \
    publishable-key="pk_test_..."

# Production Stripe credentials
vault kv put production/payment-providers/stripe \
    api-key="sk_live_..." \
    secret-key="sk_live_..." \
    webhook-secret="whsec_..." \
    publishable-key="pk_live_..."
```

**10.2 Migrate PayPal Secrets**

```bash
vault kv put development/payment-providers/paypal \
    client-id="<PAYPAL_SANDBOX_CLIENT_ID>" \
    client-secret="<PAYPAL_SANDBOX_CLIENT_SECRET>" \
    webhook-id="<PAYPAL_WEBHOOK_ID>" \
    mode="sandbox"

vault kv put production/payment-providers/paypal \
    client-id="<PAYPAL_PRODUCTION_CLIENT_ID>" \
    client-secret="<PAYPAL_PRODUCTION_CLIENT_SECRET>" \
    webhook-id="<PAYPAL_WEBHOOK_ID>" \
    mode="live"
```

**10.3 Migrate Plaid Secrets**

```bash
vault kv put development/payment-providers/plaid \
    client-id="<PLAID_CLIENT_ID>" \
    secret="<PLAID_SANDBOX_SECRET>" \
    environment="sandbox"

vault kv put production/payment-providers/plaid \
    client-id="<PLAID_CLIENT_ID>" \
    secret="<PLAID_PRODUCTION_SECRET>" \
    environment="production"
```

**10.4 Migrate Other Payment Providers**

```bash
# Adyen
vault kv put development/payment-providers/adyen \
    api-key="<ADYEN_API_KEY>" \
    merchant-account="<MERCHANT_ACCOUNT>"

# Dwolla
vault kv put development/payment-providers/dwolla \
    key="<DWOLLA_KEY>" \
    secret="<DWOLLA_SECRET>" \
    environment="sandbox"

# Wise (TransferWise)
vault kv put development/payment-providers/wise \
    api-token="<WISE_API_TOKEN>" \
    profile-id="<PROFILE_ID>"

# Yodlee
vault kv put development/payment-providers/yodlee \
    client-id="<YODLEE_CLIENT_ID>" \
    secret="<YODLEE_SECRET>"

# Figo
vault kv put development/payment-providers/figo \
    client-id="<FIGO_CLIENT_ID>" \
    secret="<FIGO_SECRET>"
```

#### Day 14-15: KYC, Communication, and Blockchain Secrets

**11.1 Migrate KYC Provider Secrets**

```bash
# Jumio
vault kv put development/kyc-providers/jumio \
    api-token="<JUMIO_API_TOKEN>" \
    api-secret="<JUMIO_API_SECRET>" \
    base-url="https://netverify.com"

# Onfido
vault kv put development/kyc-providers/onfido \
    api-token="<ONFIDO_API_TOKEN>" \
    region="us"

# OFAC
vault kv put development/compliance/ofac \
    api-key="<OFAC_API_KEY>"
```

**11.2 Migrate Communication Secrets**

```bash
# Twilio
vault kv put development/communication/twilio \
    account-sid="<TWILIO_ACCOUNT_SID>" \
    auth-token="<TWILIO_AUTH_TOKEN>" \
    phone-number="<TWILIO_PHONE_NUMBER>"

# SendGrid
vault kv put development/communication/sendgrid \
    api-key="<SENDGRID_API_KEY>" \
    from-email="noreply@example.com"

# Firebase
vault kv put development/communication/firebase \
    server-key="<FIREBASE_SERVER_KEY>" \
    service-account=@firebase-service-account.json
```

**11.3 Migrate Blockchain Secrets**

```bash
# Bitcoin
vault kv put development/blockchain/bitcoin \
    rpc-username="<BITCOIN_RPC_USERNAME>" \
    rpc-password="<BITCOIN_RPC_PASSWORD>" \
    rpc-url="http://bitcoin-node:8332" \
    network="testnet"

# Ethereum
vault kv put development/blockchain/ethereum \
    infura-project-id="<INFURA_PROJECT_ID>" \
    infura-secret="<INFURA_PROJECT_SECRET>" \
    api-key="<ETHERSCAN_API_KEY>" \
    network="goerli"

# Hot Wallet (store with extreme caution!)
vault kv put development/blockchain/hot-wallet \
    private-key="<ENCRYPTED_PRIVATE_KEY>" \
    address="0x..."

# IPFS/Pinata
vault kv put development/blockchain/ipfs \
    pinata-api-key="<PINATA_API_KEY>" \
    pinata-secret-key="<PINATA_SECRET_KEY>"
```

**11.4 Migrate Social Commerce Secrets**

```bash
# Instagram
vault kv put development/social/instagram \
    app-id="<INSTAGRAM_APP_ID>" \
    app-secret="<INSTAGRAM_APP_SECRET>" \
    webhook-verify-token="<INSTAGRAM_WEBHOOK_TOKEN>"

# TikTok
vault kv put development/social/tiktok \
    client-id="<TIKTOK_CLIENT_ID>" \
    client-secret="<TIKTOK_CLIENT_SECRET>" \
    webhook-secret="<TIKTOK_WEBHOOK_SECRET>"

# YouTube
vault kv put development/social/youtube \
    api-key="<YOUTUBE_API_KEY>" \
    oauth-client-id="<YOUTUBE_OAUTH_CLIENT_ID>" \
    oauth-client-secret="<YOUTUBE_OAUTH_CLIENT_SECRET>"
```

**11.5 Migrate Cloud Provider Secrets**

```bash
# AWS
vault kv put development/cloud/aws \
    access-key="<AWS_ACCESS_KEY>" \
    secret-key="<AWS_SECRET_KEY>" \
    region="us-east-1"

# Azure
vault kv put development/cloud/azure \
    client-id="<AZURE_CLIENT_ID>" \
    client-secret="<AZURE_CLIENT_SECRET>" \
    tenant-id="<AZURE_TENANT_ID>"

# GCP
vault kv put development/cloud/gcp \
    service-account=@gcp-service-account.json \
    project-id="waqiti-platform"
```

**11.6 Migrate Monitoring Secrets**

```bash
# DataDog
vault kv put development/monitoring/datadog \
    api-key="<DATADOG_API_KEY>" \
    app-key="<DATADOG_APP_KEY>"

# Sentry
vault kv put development/monitoring/sentry \
    dsn="<SENTRY_DSN>" \
    environment="development"

# OpenAI
vault kv put development/ml/openai \
    api-key="<OPENAI_API_KEY>" \
    organization="<OPENAI_ORG_ID>"
```

---

### Phase 4: Validation & Rollout (Week 4, Days 16-20)

#### Day 16-17: Service-by-Service Testing

**12.1 Create Test Plan Template**

For each service:
```markdown
# Service Test Checklist: {service-name}

## Pre-Test
- [ ] Vault AppRole credentials configured
- [ ] All required secrets populated in Vault
- [ ] Service configuration updated to use Vault

## Database Tests
- [ ] Service starts successfully
- [ ] Database connection established
- [ ] Dynamic credentials obtained (if applicable)
- [ ] CRUD operations work
- [ ] Connection pool healthy

## Cache Tests
- [ ] Redis connection established
- [ ] Cache read/write operations work
- [ ] Session management functions

## Message Queue Tests
- [ ] Kafka producer/consumer connected
- [ ] Messages published successfully
- [ ] Messages consumed successfully
- [ ] DLQ processing works

## External API Tests
- [ ] Payment provider API calls succeed
- [ ] KYC provider API calls succeed
- [ ] Notification provider API calls succeed
- [ ] Proper error handling for API failures

## Security Tests
- [ ] JWT validation works
- [ ] Keycloak authentication successful
- [ ] Authorization rules enforced
- [ ] Encryption/decryption functions

## Logging & Monitoring
- [ ] Vault secret access logged
- [ ] Application logs show no secret leakage
- [ ] Metrics exported to monitoring
- [ ] Health check passes

## Rollback Plan
- [ ] Document rollback procedure
- [ ] Verify fallback credentials available
```

**12.2 Automated Testing Script**

```bash
#!/bin/bash
# test-vault-integration.sh

SERVICE_NAME=$1
ENVIRONMENT=${2:-development}

echo "Testing Vault integration for ${SERVICE_NAME} in ${ENVIRONMENT}..."

# Check Vault connectivity
if ! curl -s -o /dev/null -w "%{http_code}" http://vault.example.com:8200/v1/sys/health | grep -q "200"; then
    echo "ERROR: Vault is not accessible"
    exit 1
fi

# Verify AppRole exists
if ! vault read auth/approle/role/${SERVICE_NAME} &>/dev/null; then
    echo "ERROR: AppRole for ${SERVICE_NAME} does not exist"
    exit 1
fi

# Verify required secrets exist
REQUIRED_PATHS=(
    "${ENVIRONMENT}/database/${SERVICE_NAME}"
    "${ENVIRONMENT}/keycloak/${SERVICE_NAME}"
    "${ENVIRONMENT}/common/jwt"
    "${ENVIRONMENT}/common/encryption"
)

for PATH in "${REQUIRED_PATHS[@]}"; do
    if ! vault kv get ${PATH} &>/dev/null; then
        echo "ERROR: Secret at ${PATH} does not exist"
        exit 1
    fi
done

echo "✓ Vault integration test passed for ${SERVICE_NAME}"
```

**12.3 Execute Tests for Priority Services**

```bash
# Test high-priority services first
PRIORITY_SERVICES=(
    "payment-service"
    "core-banking-service"
    "account-service"
    "transaction-service"
    "wallet-service"
)

for SERVICE in "${PRIORITY_SERVICES[@]}"; do
    ./test-vault-integration.sh ${SERVICE} development
done
```

#### Day 17-18: Update Deployment Configurations

**13.1 Update Docker Compose**

```yaml
# docker-compose.yml
version: '3.8'

services:
  payment-service:
    image: waqiti/payment-service:latest
    environment:
      VAULT_URI: http://vault:8200
      VAULT_ROLE_ID: ${PAYMENT_SERVICE_ROLE_ID}
      VAULT_SECRET_ID: ${PAYMENT_SERVICE_SECRET_ID}
      SPRING_PROFILES_ACTIVE: development
      SPRING_CLOUD_VAULT_ENABLED: true
    depends_on:
      - vault-server
      - postgres-payment
      - redis
      - kafka
    networks:
      - waqiti-network
    secrets:
      - vault_role_id
      - vault_secret_id

secrets:
  vault_role_id:
    external: true
  vault_secret_id:
    external: true
```

**13.2 Update Kubernetes Manifests**

```yaml
# k8s/payment-service-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: payment-service
  namespace: waqiti
spec:
  replicas: 3
  selector:
    matchLabels:
      app: payment-service
  template:
    metadata:
      labels:
        app: payment-service
      annotations:
        vault.hashicorp.com/agent-inject: "true"
        vault.hashicorp.com/role: "payment-service"
        vault.hashicorp.com/agent-inject-secret-database: "development/database/payment-service"
        vault.hashicorp.com/agent-inject-template-database: |
          {{- with secret "development/database/payment-service" -}}
          spring.datasource.username={{ .Data.data.username }}
          spring.datasource.password={{ .Data.data.password }}
          spring.datasource.url={{ .Data.data.url }}
          {{- end }}
    spec:
      serviceAccountName: payment-service
      containers:
      - name: payment-service
        image: waqiti/payment-service:latest
        ports:
        - containerPort: 8083
        env:
        - name: VAULT_URI
          value: "https://vault.example.com"
        - name: SPRING_PROFILES_ACTIVE
          value: "production"
        - name: SPRING_CLOUD_VAULT_ENABLED
          value: "true"
        - name: SPRING_CLOUD_VAULT_AUTHENTICATION
          value: "KUBERNETES"
        - name: SPRING_CLOUD_VAULT_KUBERNETES_ROLE
          value: "payment-service"
        volumeMounts:
        - name: vault-secrets
          mountPath: /vault/secrets
          readOnly: true
      volumes:
      - name: vault-secrets
        emptyDir:
          medium: Memory
---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: payment-service
  namespace: waqiti
---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: payment-service-tokenreview
  namespace: waqiti
rules:
- apiGroups: [""]
  resources: ["serviceaccounts/token"]
  verbs: ["create"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: payment-service-tokenreview
  namespace: waqiti
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: Role
  name: payment-service-tokenreview
subjects:
- kind: ServiceAccount
  name: payment-service
  namespace: waqiti
```

**13.3 Update CI/CD Pipelines**

```yaml
# .github/workflows/deploy-payment-service.yml
name: Deploy Payment Service

on:
  push:
    branches: [ main ]
    paths:
      - 'services/payment-service/**'

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Build with Gradle
        run: ./gradlew :payment-service:build

      - name: Get Vault AppRole credentials
        id: vault-secrets
        uses: hashicorp/vault-action@v2
        with:
          url: https://vault.example.com
          method: approle
          roleId: ${{ secrets.VAULT_ROLE_ID }}
          secretId: ${{ secrets.VAULT_SECRET_ID }}
          secrets: |
            development/database/payment-service username | DB_USERNAME ;
            development/database/payment-service password | DB_PASSWORD

      - name: Run integration tests
        run: ./gradlew :payment-service:integrationTest
        env:
          VAULT_URI: https://vault.example.com
          VAULT_ROLE_ID: ${{ secrets.VAULT_ROLE_ID }}
          VAULT_SECRET_ID: ${{ secrets.VAULT_SECRET_ID }}

      - name: Build Docker image
        run: docker build -t waqiti/payment-service:${{ github.sha }} ./services/payment-service

      - name: Push to registry
        run: docker push waqiti/payment-service:${{ github.sha }}

      - name: Deploy to Kubernetes
        run: |
          kubectl set image deployment/payment-service \
            payment-service=waqiti/payment-service:${{ github.sha }} \
            -n waqiti
          kubectl rollout status deployment/payment-service -n waqiti
```

#### Day 18-19: Production Environment Setup

**14.1 Production Vault Configuration**

```bash
# Use separate Vault cluster for production
export VAULT_ADDR='https://vault-prod.example.com'

# Initialize with higher security parameters
vault operator init -key-shares=7 -key-threshold=4

# Enable enhanced audit logging
vault audit enable file file_path=/vault/logs/audit.log
vault audit enable syslog tag="vault-prod" facility="AUTH"

# Configure stricter policies
vault write sys/policies/password/production \
    policy=@/vault/config/production-password-policy.hcl
```

**Production Password Policy**:
```hcl
# production-password-policy.hcl
length = 32
rule "charset" {
  charset = "abcdefghijklmnopqrstuvwxyz"
  min-chars = 1
}
rule "charset" {
  charset = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
  min-chars = 1
}
rule "charset" {
  charset = "0123456789"
  min-chars = 1
}
rule "charset" {
  charset = "!@#$%^&*"
  min-chars = 1
}
```

**14.2 Populate Production Secrets**

```bash
#!/bin/bash
# populate-production-secrets.sh

# NEVER copy development secrets to production!
# Generate new, strong credentials for production

# Database secrets
for SERVICE in payment-service core-banking-service account-service; do
    DB_PASSWORD=$(openssl rand -base64 48)
    vault kv put production/database/${SERVICE} \
        username="${SERVICE}_prod" \
        password="${DB_PASSWORD}" \
        url="jdbc:postgresql://postgres-${SERVICE}.rds.amazonaws.com:5432/waqiti_${SERVICE}"
done

# JWT secrets (production must be different!)
PROD_JWT_SECRET=$(openssl rand -base64 64)
PROD_REFRESH_SECRET=$(openssl rand -base64 64)

vault kv put production/common/jwt \
    secret="${PROD_JWT_SECRET}" \
    refresh-secret="${PROD_REFRESH_SECRET}" \
    algorithm="HS512" \
    expiration="3600"

# Payment providers (live credentials!)
vault kv put production/payment-providers/stripe \
    api-key="${STRIPE_LIVE_API_KEY}" \
    secret-key="${STRIPE_LIVE_SECRET_KEY}" \
    webhook-secret="${STRIPE_LIVE_WEBHOOK_SECRET}"

vault kv put production/payment-providers/paypal \
    client-id="${PAYPAL_LIVE_CLIENT_ID}" \
    client-secret="${PAYPAL_LIVE_CLIENT_SECRET}" \
    mode="live"

# Continue for all providers...
```

**14.3 Production AppRole Configuration**

```bash
# Stricter TTLs for production
vault write auth/approle/role/payment-service \
    token_policies="payment-service-production-policy" \
    token_ttl=30m \
    token_max_ttl=1h \
    secret_id_ttl=6h \
    secret_id_num_uses=0
```

#### Day 19-20: Remove Hardcoded Secrets and Final Validation

**15.1 Audit Configuration Files**

```bash
# Search for remaining hardcoded secrets
#!/bin/bash
# audit-hardcoded-secrets.sh

echo "Searching for potential hardcoded secrets..."

# Patterns to check
PATTERNS=(
    "password.*=.*[^$\{]"
    "secret.*=.*[^$\{]"
    "key.*=.*[^$\{]"
    "token.*=.*[^$\{]"
)

for PATTERN in "${PATTERNS[@]}"; do
    echo "Checking pattern: ${PATTERN}"
    grep -r -E "${PATTERN}" services/*/src/main/resources/application*.yml \
        | grep -v "vault\." \
        | grep -v "\${" \
        | grep -v "CHANGE_ME"
done

echo "Audit complete. Review results above."
```

**15.2 Update Configuration Files to Remove Defaults**

Before:
```yaml
spring:
  datasource:
    password: ${DATABASE_PASSWORD:default_password}  # BAD: fallback exposes secret
```

After:
```yaml
spring:
  datasource:
    password: ${vault.database.password}  # GOOD: fail if Vault unavailable
```

**15.3 Remove Environment Variable Placeholders**

Create script to clean up configs:

```bash
#!/bin/bash
# cleanup-configs.sh

# Remove insecure fallback values
find services -name "application*.yml" -exec sed -i '' \
    -e 's/${DATABASE_PASSWORD:.*}/${vault.database.password}/g' \
    -e 's/${REDIS_PASSWORD:.*}/${vault.redis.password}/g' \
    -e 's/${KEYCLOAK_CLIENT_SECRET:.*}/${vault.keycloak.client-secret}/g' \
    -e 's/${JWT_SECRET:.*}/${vault.jwt.secret}/g' \
    {} \;

echo "Cleaned up configuration files"
```

**15.4 Final Validation Checklist**

```markdown
# Final Pre-Production Validation

## Vault Infrastructure
- [ ] Vault cluster is HA (3+ nodes)
- [ ] Auto-unseal configured (AWS KMS/Azure Key Vault)
- [ ] Audit logging enabled and forwarded to SIEM
- [ ] Backup strategy in place
- [ ] Disaster recovery tested

## Secrets Population
- [ ] All 103 services have AppRoles configured
- [ ] All database credentials in Vault
- [ ] All API keys in Vault
- [ ] All encryption keys in Vault
- [ ] Production secrets different from dev/staging

## Service Configuration
- [ ] All services updated to use Vault
- [ ] No hardcoded secrets in YAML files
- [ ] No insecure fallback values
- [ ] Proper error handling for Vault failures

## Security
- [ ] AppRole secret IDs rotated
- [ ] Database credentials use dynamic secrets (where applicable)
- [ ] Transit encryption enabled
- [ ] TLS enabled for Vault communication
- [ ] Network segmentation in place

## Testing
- [ ] All services tested in staging
- [ ] Load testing completed
- [ ] Failover scenarios tested
- [ ] Secret rotation tested
- [ ] Vault backup/restore tested

## Monitoring
- [ ] Vault metrics in Grafana
- [ ] Alerts configured for Vault seal
- [ ] Alerts configured for failed auth
- [ ] Alerts configured for secret access anomalies
- [ ] Audit log monitoring active

## Documentation
- [ ] Runbooks updated
- [ ] On-call procedures updated
- [ ] Secret rotation procedures documented
- [ ] Emergency access procedures documented
- [ ] Developer onboarding guide updated

## Rollback Plan
- [ ] Fallback credentials available (encrypted)
- [ ] Rollback procedure tested
- [ ] Communication plan for rollback
```

**15.5 Production Rollout Strategy**

Gradual rollout approach:

1. **Canary Deployment** (10% of traffic)
   - Deploy 1-2 instances with Vault integration
   - Monitor for 24 hours
   - Validate metrics: latency, error rates, Vault access patterns

2. **Staged Rollout** (50% of traffic)
   - Deploy to half of production instances
   - Monitor for 48 hours
   - Validate secret rotation works

3. **Full Rollout** (100% of traffic)
   - Deploy to all instances
   - Final monitoring for 72 hours
   - Sign off on migration complete

---

## 4. Configuration Examples

### 4.1 Service Application.yml Updates

**Complete Example: payment-service**

```yaml
# services/payment-service/src/main/resources/application.yml
server:
  port: 8083

spring:
  application:
    name: payment-service
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:development}

  # Vault Configuration
  config:
    import: "vault://"

  cloud:
    vault:
      enabled: true
      uri: ${VAULT_URI:https://vault.example.com}
      authentication: ${VAULT_AUTH_METHOD:APPROLE}

      # AppRole authentication
      app-role:
        role-id: ${VAULT_ROLE_ID}
        secret-id: ${VAULT_SECRET_ID}
        app-role-path: auth/approle

      # Kubernetes authentication (alternative)
      kubernetes:
        enabled: ${VAULT_K8S_ENABLED:false}
        role: payment-service
        service-account-token-file: /var/run/secrets/kubernetes.io/serviceaccount/token

      # KV secrets
      kv:
        enabled: true
        backend: secret
        profile-separator: /
        default-context: ${spring.profiles.active}
        application-name: ${spring.application.name}

      # Database dynamic credentials
      database:
        enabled: ${VAULT_DB_DYNAMIC:true}
        role: payment-service-db-role
        backend: database
        username-property: spring.datasource.username
        password-property: spring.datasource.password

      # Connection settings
      connection-timeout: 5000
      read-timeout: 15000
      fail-fast: true

  # Database configuration (populated from Vault)
  datasource:
    url: ${vault.database.url}
    username: ${vault.database.username}
    password: ${vault.database.password}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 50
      minimum-idle: 10
      connection-timeout: 10000
      leak-detection-threshold: 30000

  # Redis configuration (from Vault)
  data:
    redis:
      host: ${vault.redis.host:redis}
      port: ${vault.redis.port:6379}
      password: ${vault.redis.password}
      timeout: 2000ms

  # Kafka configuration (from Vault)
  kafka:
    bootstrap-servers: ${vault.kafka.bootstrap-servers}
    ssl:
      trust-store-password: ${vault.kafka.truststore-password}
      key-store-password: ${vault.kafka.keystore-password}

# Keycloak configuration (from Vault)
keycloak:
  realm: ${vault.keycloak.realm}
  resource: ${vault.keycloak.client-id}
  credentials:
    secret: ${vault.keycloak.client-secret}
  auth-server-url: ${vault.keycloak.auth-server-url}

# Payment provider configuration (from Vault)
payment:
  providers:
    stripe:
      api-key: ${vault.stripe.api-key}
      secret-key: ${vault.stripe.secret-key}
      webhook-secret: ${vault.stripe.webhook-secret}

    paypal:
      client-id: ${vault.paypal.client-id}
      client-secret: ${vault.paypal.client-secret}
      mode: ${vault.paypal.mode}

    plaid:
      client-id: ${vault.plaid.client-id}
      secret: ${vault.plaid.secret}
      environment: ${vault.plaid.environment}

# JWT configuration (from Vault)
jwt:
  secret: ${vault.jwt.secret}
  refresh-secret: ${vault.jwt.refresh-secret}
  expiration: ${vault.jwt.expiration:3600}

# Encryption configuration (from Vault)
encryption:
  master-key: ${vault.encryption.master-key}
  algorithm: AES-256-GCM

# Management endpoints
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,vault
  health:
    vault:
      enabled: true

# Logging
logging:
  level:
    org.springframework.vault: INFO
    com.waqiti.payment: DEBUG
```

### 4.2 Vault Policy Examples

**Payment Service Policy**:
```hcl
# payment-service-policy.hcl

# Database dynamic credentials
path "database/creds/payment-service-db-role" {
  capabilities = ["read"]
}

# Service-specific secrets
path "development/database/payment-service/*" {
  capabilities = ["read"]
}

path "development/keycloak/payment-service/*" {
  capabilities = ["read"]
}

# Common secrets (read-only)
path "development/common/jwt" {
  capabilities = ["read"]
}

path "development/common/encryption" {
  capabilities = ["read"]
}

path "development/common/ssl" {
  capabilities = ["read"]
}

path "development/infrastructure/redis" {
  capabilities = ["read"]
}

path "development/infrastructure/kafka" {
  capabilities = ["read"]
}

# Payment provider secrets
path "development/payment-providers/*" {
  capabilities = ["read"]
}

# Transit encryption
path "transit/encrypt/waqiti-encryption-key" {
  capabilities = ["update"]
}

path "transit/decrypt/waqiti-encryption-key" {
  capabilities = ["update"]
}

# Health check
path "sys/health" {
  capabilities = ["read", "sudo"]
}

# Renew own token
path "auth/token/renew-self" {
  capabilities = ["update"]
}

path "auth/token/lookup-self" {
  capabilities = ["read"]
}
```

**Admin Policy** (for operations team):
```hcl
# admin-policy.hcl

# Full access to all secrets
path "*" {
  capabilities = ["create", "read", "update", "delete", "list", "sudo"]
}

# System operations
path "sys/*" {
  capabilities = ["create", "read", "update", "delete", "list", "sudo"]
}

# Auth methods
path "auth/*" {
  capabilities = ["create", "read", "update", "delete", "list", "sudo"]
}
```

**Read-Only Policy** (for auditors):
```hcl
# auditor-policy.hcl

# Read all secrets metadata (not values)
path "+/metadata/*" {
  capabilities = ["read", "list"]
}

# Read audit logs
path "sys/audit" {
  capabilities = ["read", "list"]
}

# Read policies
path "sys/policies/acl/*" {
  capabilities = ["read", "list"]
}

# Health and status
path "sys/health" {
  capabilities = ["read"]
}

path "sys/seal-status" {
  capabilities = ["read"]
}
```

### 4.3 Docker Compose with Vault

```yaml
# docker-compose-services.yml
version: '3.8'

services:
  # Vault server
  vault:
    image: hashicorp/vault:1.15.0
    container_name: waqiti-vault
    ports:
      - "8200:8200"
    environment:
      VAULT_ADDR: 'http://0.0.0.0:8200'
      VAULT_DEV_ROOT_TOKEN_ID: 'dev-root-token'
      VAULT_DEV_LISTEN_ADDRESS: '0.0.0.0:8200'
    cap_add:
      - IPC_LOCK
    networks:
      - waqiti-network

  # Payment service
  payment-service:
    image: waqiti/payment-service:latest
    container_name: payment-service
    ports:
      - "8083:8083"
    environment:
      VAULT_URI: http://vault:8200
      VAULT_ROLE_ID: ${PAYMENT_SERVICE_ROLE_ID}
      VAULT_SECRET_ID: ${PAYMENT_SERVICE_SECRET_ID}
      SPRING_PROFILES_ACTIVE: development
      SPRING_CLOUD_VAULT_ENABLED: true
      SPRING_CLOUD_VAULT_AUTHENTICATION: APPROLE
    depends_on:
      - vault
      - postgres-payment
      - redis
      - kafka
    networks:
      - waqiti-network
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8083/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3

  # Core banking service
  core-banking-service:
    image: waqiti/core-banking-service:latest
    container_name: core-banking-service
    ports:
      - "8082:8082"
    environment:
      VAULT_URI: http://vault:8200
      VAULT_ROLE_ID: ${CORE_BANKING_ROLE_ID}
      VAULT_SECRET_ID: ${CORE_BANKING_SECRET_ID}
      SPRING_PROFILES_ACTIVE: development
      SPRING_CLOUD_VAULT_ENABLED: true
    depends_on:
      - vault
      - postgres-banking
      - redis
      - kafka
    networks:
      - waqiti-network

  # PostgreSQL
  postgres-payment:
    image: postgres:15
    container_name: postgres-payment
    environment:
      POSTGRES_DB: waqiti_payments
      POSTGRES_USER: payment_service
      POSTGRES_PASSWORD: dev_password
    ports:
      - "5432:5432"
    volumes:
      - postgres-payment-data:/var/lib/postgresql/data
    networks:
      - waqiti-network

  # Redis
  redis:
    image: redis:7-alpine
    container_name: redis
    command: redis-server --requirepass redis_dev_password
    ports:
      - "6379:6379"
    networks:
      - waqiti-network

  # Kafka
  kafka:
    image: confluentinc/cp-kafka:7.4.0
    container_name: kafka
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
    depends_on:
      - zookeeper
    networks:
      - waqiti-network

  zookeeper:
    image: confluentinc/cp-zookeeper:7.4.0
    container_name: zookeeper
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000
    networks:
      - waqiti-network

volumes:
  postgres-payment-data:

networks:
  waqiti-network:
    driver: bridge
```

### 4.4 Kubernetes Manifest Examples

**Vault Agent Sidecar Injection**:

```yaml
# k8s/payment-service-vault-injection.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: payment-service
  namespace: waqiti
  labels:
    app: payment-service
spec:
  replicas: 3
  selector:
    matchLabels:
      app: payment-service
  template:
    metadata:
      labels:
        app: payment-service
      annotations:
        # Enable Vault Agent Injector
        vault.hashicorp.com/agent-inject: "true"
        vault.hashicorp.com/role: "payment-service"
        vault.hashicorp.com/agent-inject-status: "update"

        # Database secrets
        vault.hashicorp.com/agent-inject-secret-database.properties: "development/database/payment-service"
        vault.hashicorp.com/agent-inject-template-database.properties: |
          {{- with secret "development/database/payment-service" -}}
          spring.datasource.url={{ .Data.data.url }}
          spring.datasource.username={{ .Data.data.username }}
          spring.datasource.password={{ .Data.data.password }}
          {{- end -}}

        # Keycloak secrets
        vault.hashicorp.com/agent-inject-secret-keycloak.properties: "development/keycloak/payment-service"
        vault.hashicorp.com/agent-inject-template-keycloak.properties: |
          {{- with secret "development/keycloak/payment-service" -}}
          keycloak.resource={{ .Data.data.client-id }}
          keycloak.credentials.secret={{ .Data.data.client-secret }}
          keycloak.realm={{ .Data.data.realm }}
          {{- end -}}

        # Stripe secrets
        vault.hashicorp.com/agent-inject-secret-stripe.properties: "development/payment-providers/stripe"
        vault.hashicorp.com/agent-inject-template-stripe.properties: |
          {{- with secret "development/payment-providers/stripe" -}}
          payment.providers.stripe.api-key={{ .Data.data.api-key }}
          payment.providers.stripe.secret-key={{ .Data.data.secret-key }}
          payment.providers.stripe.webhook-secret={{ .Data.data.webhook-secret }}
          {{- end -}}

        # JWT secrets
        vault.hashicorp.com/agent-inject-secret-jwt.properties: "development/common/jwt"
        vault.hashicorp.com/agent-inject-template-jwt.properties: |
          {{- with secret "development/common/jwt" -}}
          jwt.secret={{ .Data.data.secret }}
          jwt.refresh-secret={{ .Data.data.refresh-secret }}
          {{- end -}}
    spec:
      serviceAccountName: payment-service
      containers:
      - name: payment-service
        image: waqiti/payment-service:1.0.0
        ports:
        - containerPort: 8083
          name: http
          protocol: TCP
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "production"
        - name: SPRING_CONFIG_ADDITIONAL_LOCATION
          value: "file:/vault/secrets/"
        volumeMounts:
        - name: vault-secrets
          mountPath: /vault/secrets
          readOnly: true
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "2Gi"
            cpu: "2000m"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8083
          initialDelaySeconds: 60
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8083
          initialDelaySeconds: 30
          periodSeconds: 5
      volumes:
      - name: vault-secrets
        emptyDir:
          medium: Memory
---
apiVersion: v1
kind: Service
metadata:
  name: payment-service
  namespace: waqiti
spec:
  selector:
    app: payment-service
  ports:
  - protocol: TCP
    port: 80
    targetPort: 8083
  type: ClusterIP
---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: payment-service
  namespace: waqiti
```

**Vault ConfigMap for Init Container**:

```yaml
# k8s/vault-init-configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: vault-init-script
  namespace: waqiti
data:
  init-vault.sh: |
    #!/bin/sh
    set -e

    echo "Authenticating with Vault..."
    export VAULT_ADDR=https://vault.example.com

    # Login using Kubernetes auth
    VAULT_TOKEN=$(vault write -field=token auth/kubernetes/login \
      role=payment-service \
      jwt=@/var/run/secrets/kubernetes.io/serviceaccount/token)

    export VAULT_TOKEN

    echo "Fetching secrets..."
    vault kv get -format=json development/database/payment-service > /secrets/database.json
    vault kv get -format=json development/keycloak/payment-service > /secrets/keycloak.json

    echo "Secrets fetched successfully"
```

---

## 5. Secret Rotation Strategy

### 5.1 Automatic Rotation for Database Credentials

**Dynamic Database Credentials**:

Vault automatically rotates database credentials:

```bash
# Configure rotation for payment-service database role
vault write database/roles/payment-service-db-role \
    db_name=waqiti-postgres \
    creation_statements="CREATE ROLE \"{{name}}\" WITH LOGIN PASSWORD '{{password}}' VALID UNTIL '{{expiration}}'; \
        GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO \"{{name}}\"; \
        GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO \"{{name}}\";" \
    revocation_statements="REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA public FROM \"{{name}}\"; \
        DROP ROLE IF EXISTS \"{{name}}\";" \
    default_ttl="1h" \
    max_ttl="24h"
```

**Rotation Process**:
1. Service requests database credentials from Vault
2. Vault creates temporary PostgreSQL role with 1-hour TTL
3. Service uses credentials for database operations
4. After 1 hour, Vault automatically revokes the role
5. Service requests new credentials before expiration

**Service Implementation**:

```java
@Configuration
public class VaultDatabaseConfig {

    @Value("${spring.cloud.vault.database.enabled:true}")
    private boolean dynamicCredentialsEnabled;

    @Scheduled(fixedRate = 3000000) // 50 minutes (before 1-hour expiration)
    public void renewDatabaseCredentials() {
        if (dynamicCredentialsEnabled) {
            log.info("Renewing database credentials from Vault...");
            // Spring Cloud Vault automatically handles renewal
            // Just trigger datasource recreation
            dataSource.close();
            dataSource = createDataSource();
            log.info("Database credentials renewed successfully");
        }
    }
}
```

### 5.2 Manual Rotation Procedures

**JWT Secret Rotation**:

```bash
#!/bin/bash
# rotate-jwt-secret.sh

ENVIRONMENT=$1

echo "Rotating JWT secret for ${ENVIRONMENT}..."

# Generate new JWT secret
NEW_JWT_SECRET=$(openssl rand -base64 64)
NEW_REFRESH_SECRET=$(openssl rand -base64 64)

# Get current secrets (for rollback)
vault kv get -format=json ${ENVIRONMENT}/common/jwt > /tmp/jwt-backup-$(date +%s).json

# Write new secrets
vault kv put ${ENVIRONMENT}/common/jwt \
    secret="${NEW_JWT_SECRET}" \
    refresh-secret="${NEW_REFRESH_SECRET}" \
    algorithm="HS512" \
    expiration="3600"

echo "JWT secret rotated. Restart services to pick up new secret."
echo "Backup saved to /tmp/jwt-backup-*.json"
```

**Keycloak Client Secret Rotation**:

```bash
#!/bin/bash
# rotate-keycloak-secret.sh

SERVICE_NAME=$1
ENVIRONMENT=$2

echo "Rotating Keycloak secret for ${SERVICE_NAME} in ${ENVIRONMENT}..."

# Generate new client secret
NEW_CLIENT_SECRET=$(openssl rand -base64 32)

# Update Vault
vault kv patch ${ENVIRONMENT}/keycloak/${SERVICE_NAME} \
    client-secret="${NEW_CLIENT_SECRET}"

# Update Keycloak via API
KEYCLOAK_TOKEN=$(curl -s -X POST "https://keycloak.example.com/realms/master/protocol/openid-connect/token" \
    -d "client_id=admin-cli" \
    -d "username=${KEYCLOAK_ADMIN}" \
    -d "password=${KEYCLOAK_ADMIN_PASSWORD}" \
    -d "grant_type=password" | jq -r '.access_token')

curl -X PUT "https://keycloak.example.com/admin/realms/waqiti-fintech/clients/${SERVICE_NAME}" \
    -H "Authorization: Bearer ${KEYCLOAK_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "{\"secret\": \"${NEW_CLIENT_SECRET}\"}"

echo "Keycloak secret rotated for ${SERVICE_NAME}"
echo "Restart ${SERVICE_NAME} to apply changes"
```

**Payment Provider API Key Rotation**:

```bash
#!/bin/bash
# rotate-payment-provider-key.sh

PROVIDER=$1  # stripe, paypal, plaid, etc.
ENVIRONMENT=$2

echo "Rotating ${PROVIDER} API key for ${ENVIRONMENT}..."

# Prompt for new key (obtained from provider dashboard)
read -s -p "Enter new ${PROVIDER} API key: " NEW_API_KEY
echo

# Backup current key
vault kv get -format=json ${ENVIRONMENT}/payment-providers/${PROVIDER} > /tmp/${PROVIDER}-backup-$(date +%s).json

# Update Vault
case ${PROVIDER} in
    stripe)
        read -s -p "Enter new Stripe secret key: " NEW_SECRET_KEY
        echo
        read -s -p "Enter new Stripe webhook secret: " NEW_WEBHOOK_SECRET
        echo
        vault kv patch ${ENVIRONMENT}/payment-providers/stripe \
            api-key="${NEW_API_KEY}" \
            secret-key="${NEW_SECRET_KEY}" \
            webhook-secret="${NEW_WEBHOOK_SECRET}"
        ;;
    paypal)
        read -s -p "Enter new PayPal client secret: " NEW_CLIENT_SECRET
        echo
        vault kv patch ${ENVIRONMENT}/payment-providers/paypal \
            client-id="${NEW_API_KEY}" \
            client-secret="${NEW_CLIENT_SECRET}"
        ;;
    plaid)
        read -s -p "Enter new Plaid secret: " NEW_SECRET
        echo
        vault kv patch ${ENVIRONMENT}/payment-providers/plaid \
            client-id="${NEW_API_KEY}" \
            secret="${NEW_SECRET}"
        ;;
esac

echo "${PROVIDER} credentials rotated successfully"
echo "Backup saved to /tmp/${PROVIDER}-backup-*.json"
echo "Rolling restart required for payment-service and bank-integration-service"
```

### 5.3 Emergency Rotation Procedures

**Immediate Rotation (Suspected Breach)**:

```bash
#!/bin/bash
# emergency-rotation.sh

ENVIRONMENT=$1
REASON=$2

echo "EMERGENCY ROTATION INITIATED"
echo "Environment: ${ENVIRONMENT}"
echo "Reason: ${REASON}"
echo "Timestamp: $(date -u +%Y-%m-%dT%H:%M:%SZ)"

# Log to audit system
logger -t vault-emergency "Emergency rotation initiated for ${ENVIRONMENT}: ${REASON}"

# Rotate all critical secrets immediately
echo "Rotating JWT secrets..."
./rotate-jwt-secret.sh ${ENVIRONMENT}

echo "Rotating database credentials..."
for SERVICE in payment-service core-banking-service account-service wallet-service; do
    NEW_PASSWORD=$(openssl rand -base64 48)
    vault kv patch ${ENVIRONMENT}/database/${SERVICE} password="${NEW_PASSWORD}"

    # Update database directly
    psql -h postgres-${SERVICE} -U postgres -c "ALTER ROLE ${SERVICE} WITH PASSWORD '${NEW_PASSWORD}';"
done

echo "Rotating encryption keys..."
MASTER_KEY=$(openssl rand -base64 32)
vault kv patch ${ENVIRONMENT}/common/encryption master-key="${MASTER_KEY}"

echo "Revoking all AppRole secret IDs..."
for ROLE in $(vault list -format=json auth/approle/role | jq -r '.[]'); do
    vault write -f auth/approle/role/${ROLE}/secret-id-accessor/destroy
done

echo "Emergency rotation complete"
echo "ACTION REQUIRED: Restart all services with new Vault secret IDs"
echo "ACTION REQUIRED: Contact payment providers to rotate API keys"
echo "ACTION REQUIRED: Notify security team and file incident report"
```

**Rotation Schedule**:

| Secret Type | Rotation Frequency | Method | Owner |
|-------------|-------------------|--------|-------|
| Database Credentials (Dynamic) | 1 hour | Automatic | Vault |
| Database Credentials (Static) | 90 days | Manual | DevOps |
| JWT Secrets | 180 days | Manual | Security |
| Encryption Keys | 365 days | Manual | Security |
| Payment Provider API Keys | On breach or provider notification | Manual | FinOps |
| Keycloak Client Secrets | 90 days | Manual | DevOps |
| Redis Password | 90 days | Manual | DevOps |
| Kafka SSL Passwords | 90 days | Manual | DevOps |
| Cloud Provider Credentials | 60 days | Automatic (via Vault) | DevOps |
| Monitoring API Keys | 365 days | Manual | SRE |
| Blockchain RPC Credentials | 90 days | Manual | Blockchain Team |
| Blockchain Private Keys | Never (only on breach) | Manual | Security + Blockchain Team |

---

## 6. Monitoring & Audit

### 6.1 Vault Audit Logging

**Enable Comprehensive Audit Logging**:

```bash
# File-based audit log
vault audit enable file file_path=/vault/logs/audit.log

# Syslog audit (for centralized logging)
vault audit enable syslog \
    tag="vault-audit" \
    facility="AUTH" \
    address="syslog.example.com:514"

# Socket audit (for real-time analysis)
vault audit enable socket \
    address="audit-collector.example.com:9090" \
    socket_type="tcp"
```

**Audit Log Format**:

```json
{
  "time": "2025-10-13T10:30:45.123456Z",
  "type": "response",
  "auth": {
    "client_token": "hmac-sha256:abc123...",
    "accessor": "hmac-sha256:def456...",
    "display_name": "approle-payment-service",
    "policies": ["payment-service-policy"],
    "token_type": "service",
    "metadata": {
      "role_name": "payment-service"
    }
  },
  "request": {
    "id": "req-uuid-123",
    "operation": "read",
    "path": "development/database/payment-service",
    "data": null,
    "remote_address": "10.0.1.45"
  },
  "response": {
    "data": {
      "username": "hmac-sha256:user123...",
      "password": "hmac-sha256:pass456..."
    }
  }
}
```

### 6.2 Secret Access Monitoring

**Grafana Dashboard for Vault**:

```yaml
# grafana-vault-dashboard.json
{
  "dashboard": {
    "title": "Vault Secrets Access",
    "panels": [
      {
        "title": "Secret Access Rate",
        "targets": [
          {
            "expr": "rate(vault_core_handle_request_count[5m])",
            "legendFormat": "{{path}}"
          }
        ]
      },
      {
        "title": "Failed Authentication Attempts",
        "targets": [
          {
            "expr": "vault_core_handle_login_request_count{status=\"failed\"}",
            "legendFormat": "{{method}}"
          }
        ]
      },
      {
        "title": "Database Credential Leases",
        "targets": [
          {
            "expr": "vault_expire_num_leases{namespace=\"database\"}",
            "legendFormat": "Active Leases"
          }
        ]
      },
      {
        "title": "Token TTL Distribution",
        "targets": [
          {
            "expr": "histogram_quantile(0.95, vault_token_create_duration_seconds)",
            "legendFormat": "p95 Token Creation Time"
          }
        ]
      }
    ]
  }
}
```

**Prometheus Metrics Configuration**:

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'vault'
    metrics_path: /v1/sys/metrics
    params:
      format: ['prometheus']
    bearer_token: '<VAULT_PROMETHEUS_TOKEN>'
    static_configs:
      - targets: ['vault.example.com:8200']
    relabel_configs:
      - source_labels: [__address__]
        target_label: __param_target
      - source_labels: [__param_target]
        target_label: instance
      - target_label: __address__
        replacement: vault.example.com:8200
```

### 6.3 Alerting on Failed Access

**Alertmanager Rules**:

```yaml
# alertmanager-vault-rules.yml
groups:
  - name: vault_alerts
    interval: 30s
    rules:
      # Vault sealed
      - alert: VaultSealed
        expr: vault_core_unsealed == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Vault is sealed"
          description: "Vault cluster {{ $labels.instance }} has been sealed for more than 1 minute"

      # High rate of failed auth
      - alert: VaultHighFailedAuth
        expr: rate(vault_core_handle_login_request_count{status="failed"}[5m]) > 10
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High rate of failed Vault authentication"
          description: "{{ $value }} failed auth attempts per second on {{ $labels.instance }}"

      # Unusual secret access
      - alert: VaultUnusualSecretAccess
        expr: |
          (
            rate(vault_core_handle_request_count[5m])
            >
            avg_over_time(vault_core_handle_request_count[1h]) * 2
          )
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "Unusual Vault secret access pattern"
          description: "Secret access rate is 2x normal on {{ $labels.instance }}"

      # Vault token expiring soon
      - alert: VaultTokenExpiringSoon
        expr: vault_token_lookup_self_ttl < 300
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Vault token expiring soon"
          description: "Token for {{ $labels.accessor }} expires in {{ $value }} seconds"

      # Database lease exhaustion
      - alert: VaultDatabaseLeaseExhaustion
        expr: vault_expire_num_leases{namespace="database"} > 1000
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "High number of database leases"
          description: "{{ $value }} active database leases, possible leak"

      # Vault storage near capacity
      - alert: VaultStorageNearCapacity
        expr: vault_raft_fsm_store_duration_seconds > 0.5
        for: 15m
        labels:
          severity: warning
        annotations:
          summary: "Vault storage performance degraded"
          description: "Raft store operations taking {{ $value }}s on {{ $labels.instance }}"
```

**Splunk/ELK Queries for Anomaly Detection**:

```spl
# Splunk query for unauthorized access attempts
index=vault sourcetype=vault:audit
| search request.path="*database*" response.status=403
| stats count by auth.display_name, request.path, request.remote_address
| where count > 5

# Splunk query for secret access outside business hours
index=vault sourcetype=vault:audit
| search request.operation="read"
| eval hour=strftime(_time, "%H")
| where hour < 6 OR hour > 22
| stats count by auth.display_name, request.path

# Splunk query for unusual secret volume by service
index=vault sourcetype=vault:audit request.operation="read"
| timechart span=1h count by auth.display_name
| anomalydetection count
```

### 6.4 Security Information and Event Management (SIEM) Integration

**Forward Vault Audit Logs to SIEM**:

```bash
# rsyslog configuration for Vault audit forwarding
# /etc/rsyslog.d/vault-audit.conf

# Parse Vault JSON audit logs
$ModLoad imfile
$InputFileName /vault/logs/audit.log
$InputFileTag vault-audit:
$InputFileStateFile vault-audit-state
$InputFileSeverity info
$InputFileFacility local7
$InputRunFileMonitor

# Forward to SIEM
*.* @@siem.example.com:514;RSYSLOG_SyslogProtocol23Format
```

**SIEM Correlation Rules**:

1. **Brute Force Detection**: >20 failed auth attempts from same IP in 5 minutes
2. **Privilege Escalation**: Access to admin paths by non-admin roles
3. **Data Exfiltration**: >100 secret reads by single service in 1 hour
4. **Geographic Anomaly**: Secret access from unexpected geographic regions
5. **Time-based Anomaly**: Production secret access during maintenance windows

---

## 7. Troubleshooting

### 7.1 Common Issues

#### Issue 1: Service Cannot Connect to Vault

**Symptoms**:
```
ERROR: Failed to connect to Vault at https://vault.example.com:8200
Connection refused
```

**Diagnosis**:
```bash
# Check Vault health
curl https://vault.example.com:8200/v1/sys/health

# Check network connectivity
ping vault.example.com
telnet vault.example.com 8200

# Check DNS resolution
nslookup vault.example.com

# Check firewall rules
iptables -L | grep 8200
```

**Resolution**:
1. Verify Vault is running: `docker ps | grep vault` or `kubectl get pods -n vault`
2. Check Vault is unsealed: `vault status`
3. Verify network policies allow traffic to Vault
4. Check service configuration for correct Vault URI

#### Issue 2: Authentication Failed - Invalid RoleID/SecretID

**Symptoms**:
```
ERROR: Vault authentication failed
Permission denied
```

**Diagnosis**:
```bash
# Test AppRole authentication manually
export VAULT_ADDR=https://vault.example.com

# Get RoleID
vault read auth/approle/role/payment-service/role-id

# Generate new SecretID
vault write -f auth/approle/role/payment-service/secret-id

# Test login
vault write auth/approle/login \
    role_id="<ROLE_ID>" \
    secret_id="<SECRET_ID>"
```

**Resolution**:
1. Verify RoleID and SecretID are correct
2. Check if SecretID has expired (default TTL: 24h)
3. Generate new SecretID: `vault write -f auth/approle/role/payment-service/secret-id`
4. Update service environment variables with new credentials

#### Issue 3: Secret Not Found

**Symptoms**:
```
ERROR: Secret not found at path development/database/payment-service
404 Not Found
```

**Diagnosis**:
```bash
# List secrets at path
vault kv list development/database

# Check if path exists
vault kv get development/database/payment-service

# Check KV version (v1 vs v2)
vault secrets list -detailed
```

**Resolution**:
1. Verify secret path is correct (check for typos)
2. Ensure KV v2 syntax: `vault kv get secret/data/path` (note `/data/` in path)
3. Create missing secret:
   ```bash
   vault kv put development/database/payment-service \
       username="payment_service" \
       password="<PASSWORD>" \
       url="jdbc:postgresql://..."
   ```

#### Issue 4: Database Credentials Expired

**Symptoms**:
```
ERROR: FATAL: password authentication failed for user "v-approle-payment-12345"
```

**Diagnosis**:
```bash
# Check lease status
vault list sys/leases/lookup/database/creds/payment-service-db-role

# Check if credentials are still valid
vault read database/creds/payment-service-db-role
```

**Resolution**:
1. Service should automatically renew credentials before expiration
2. If manual intervention needed:
   ```bash
   # Generate new credentials
   vault read database/creds/payment-service-db-role
   ```
3. Configure automatic renewal in service:
   ```java
   @Scheduled(fixedRate = 3000000) // 50 minutes
   public void renewDatabaseCredentials() {
       // Trigger datasource refresh
   }
   ```

#### Issue 5: Token Expired

**Symptoms**:
```
ERROR: Vault token expired
403 permission denied
```

**Diagnosis**:
```bash
# Check token TTL
vault token lookup

# Check token capabilities
vault token capabilities <TOKEN>
```

**Resolution**:
1. Renew token:
   ```bash
   vault token renew
   ```
2. If token cannot be renewed, re-authenticate:
   ```bash
   vault write auth/approle/login role_id=<ROLE_ID> secret_id=<SECRET_ID>
   ```
3. Configure automatic token renewal in service

#### Issue 6: Insufficient Permissions

**Symptoms**:
```
ERROR: Permission denied
Code: 403, Errors: [1 error occurred:
* permission denied]
```

**Diagnosis**:
```bash
# Check token policies
vault token lookup -format=json | jq '.data.policies'

# Check policy contents
vault policy read payment-service-policy

# Test capabilities
vault token capabilities <TOKEN> development/database/payment-service
```

**Resolution**:
1. Update policy to grant required permissions:
   ```bash
   vault policy write payment-service-policy - <<EOF
   path "development/database/payment-service/*" {
     capabilities = ["read", "list"]
   }
   EOF
   ```
2. Re-authenticate service to pick up new policy
3. Verify permissions: `vault token capabilities development/database/payment-service`

#### Issue 7: Vault Sealed After Restart

**Symptoms**:
```
ERROR: Vault is sealed
Code: 503
```

**Diagnosis**:
```bash
vault status
# Output shows: Sealed = true
```

**Resolution**:
1. Unseal Vault with unseal keys:
   ```bash
   vault operator unseal <UNSEAL_KEY_1>
   vault operator unseal <UNSEAL_KEY_2>
   vault operator unseal <UNSEAL_KEY_3>
   ```
2. For production, configure auto-unseal with AWS KMS:
   ```hcl
   seal "awskms" {
     region     = "us-east-1"
     kms_key_id = "alias/waqiti-vault-unseal"
   }
   ```

### 7.2 Debug Commands

**Enable Debug Logging**:

```bash
# Service-side (Spring Boot)
export LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_VAULT=DEBUG
export LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_VAULT=DEBUG

# Vault server
vault audit enable file file_path=/vault/logs/audit.log log_raw=true
```

**Vault Troubleshooting Commands**:

```bash
# Check Vault status
vault status

# List enabled auth methods
vault auth list

# List enabled secrets engines
vault secrets list

# List policies
vault policy list

# Read policy
vault policy read payment-service-policy

# List AppRoles
vault list auth/approle/role

# Read AppRole configuration
vault read auth/approle/role/payment-service

# Generate new SecretID with metadata
vault write -f auth/approle/role/payment-service/secret-id \
    metadata="generated_at=$(date)"

# List database roles
vault list database/roles

# Test database credentials generation
vault read database/creds/payment-service-db-role

# List active leases
vault list sys/leases/lookup/database/creds/payment-service-db-role

# Revoke specific lease
vault lease revoke database/creds/payment-service-db-role/<LEASE_ID>

# Revoke all leases for a role
vault lease revoke -prefix database/creds/payment-service-db-role

# Check seal status
vault operator seal-status

# Check raft peer list (HA cluster)
vault operator raft list-peers

# Check audit device status
vault audit list

# Read secret
vault kv get -format=json development/database/payment-service

# List secrets at path
vault kv list development/database

# Check secret metadata
vault kv metadata get development/database/payment-service
```

**Network Troubleshooting**:

```bash
# Test Vault connectivity
curl -k https://vault.example.com:8200/v1/sys/health

# Test with authentication
curl -k -H "X-Vault-Token: <TOKEN>" \
    https://vault.example.com:8200/v1/development/database/payment-service

# Check TLS certificate
openssl s_client -connect vault.example.com:8200 -showcerts

# DNS resolution
dig vault.example.com

# Trace route
traceroute vault.example.com
```

### 7.3 Recovery Procedures

#### Recovery Scenario 1: Lost Vault Root Token

**Procedure**:
1. Generate new root token using unseal keys:
   ```bash
   vault operator generate-root -init
   # Output: Nonce and OTP

   vault operator generate-root -nonce=<NONCE>
   # Repeat for threshold number of unseal keys

   vault operator generate-root -nonce=<NONCE> -decode=<ENCODED_TOKEN> -otp=<OTP>
   # Output: New root token
   ```

2. Login with new root token:
   ```bash
   vault login <NEW_ROOT_TOKEN>
   ```

3. Revoke old root token (if known):
   ```bash
   vault token revoke <OLD_ROOT_TOKEN>
   ```

#### Recovery Scenario 2: Vault Cluster Failure

**Procedure**:
1. Check cluster status:
   ```bash
   vault operator raft list-peers
   ```

2. If quorum lost, restore from backup:
   ```bash
   # On new Vault instance
   vault operator raft snapshot restore /backups/vault-snapshot-latest.snap
   ```

3. Verify data integrity:
   ```bash
   vault kv list development/database
   ```

4. Rejoin cluster members if needed

#### Recovery Scenario 3: All Unseal Keys Lost

**Procedure** (requires re-initialization - **LAST RESORT**):

1. **WARNING**: This will destroy all data in Vault
2. Backup Vault data if any nodes are accessible
3. Re-initialize Vault:
   ```bash
   vault operator init -key-shares=5 -key-threshold=3
   ```
4. Store new unseal keys securely
5. Re-populate secrets from secure backup:
   ```bash
   ./scripts/populate-production-secrets.sh
   ```
6. Update all services with new AppRole credentials

#### Recovery Scenario 4: Database Secrets Compromised

**Procedure**:
1. Immediately revoke all database leases:
   ```bash
   vault lease revoke -prefix database/creds/
   ```

2. Rotate master database password:
   ```bash
   psql -h postgres -U postgres -c "ALTER USER vault_admin WITH PASSWORD '<NEW_PASSWORD>';"
   ```

3. Update Vault database config:
   ```bash
   vault write database/config/waqiti-postgres password="<NEW_PASSWORD>"
   ```

4. Force all services to re-authenticate and get new credentials:
   ```bash
   kubectl rollout restart deployment -n waqiti
   ```

5. Review audit logs for unauthorized access:
   ```bash
   vault audit-log query -filter="path contains 'database'"
   ```

---

## 8. Checklist

### 8.1 Pre-Migration Checklist

**Infrastructure Readiness**:
- [ ] Vault cluster deployed (HA configuration for production)
- [ ] Vault initialized and unsealed
- [ ] Unseal keys stored in separate secure locations (3+ custodians)
- [ ] Root token securely stored (break-glass procedure documented)
- [ ] Audit logging enabled and forwarded to SIEM
- [ ] Backup strategy configured (automated daily snapshots)
- [ ] Disaster recovery plan documented and tested
- [ ] Network connectivity verified (all services can reach Vault)
- [ ] TLS certificates configured (production uses valid certificates)
- [ ] Monitoring and alerting configured (Grafana + Prometheus)

**Secrets Engine Configuration**:
- [ ] KV v2 secrets engine enabled
- [ ] Database secrets engine enabled and configured
- [ ] Transit encryption engine enabled
- [ ] PKI secrets engine enabled (if using internal certificates)
- [ ] AWS/Azure/GCP secrets engines enabled (if using cloud credentials)

**Authentication Configuration**:
- [ ] AppRole auth method enabled
- [ ] Policies created for all 103 services
- [ ] AppRoles created for all services with appropriate policies
- [ ] RoleID and SecretID generated for each service
- [ ] Kubernetes auth configured (for K8s deployments)
- [ ] Token TTL and renewal configured appropriately

**Secrets Population**:
- [ ] Inventory of all secrets completed (see Section 1)
- [ ] Secrets categorized by priority (P0/P1/P2)
- [ ] Development environment secrets populated
- [ ] Staging environment secrets populated
- [ ] Production environment secrets populated (different from dev/staging!)
- [ ] All secrets follow naming conventions
- [ ] Secret versions tracked in documentation

**Team Readiness**:
- [ ] Operations team trained on Vault administration
- [ ] Development team trained on Vault integration
- [ ] Security team reviewed Vault configuration
- [ ] On-call runbooks updated with Vault procedures
- [ ] Emergency contact list updated
- [ ] Rollback plan documented and communicated

### 8.2 Per-Service Migration Checklist

**For each service** (repeat 103 times):

**Service**: _______________________________
**Owner**: _________________________________
**Priority**: P0 / P1 / P2
**Migration Date**: _______________________

**Configuration Updates**:
- [ ] application.yml updated to use Vault placeholders
- [ ] No hardcoded secrets remain in configuration files
- [ ] No insecure fallback values (e.g., `${PASSWORD:default}`)
- [ ] Vault URI configured correctly
- [ ] AppRole authentication configured
- [ ] Required Vault paths specified

**Vault Setup**:
- [ ] AppRole created for this service
- [ ] Policy created with appropriate permissions
- [ ] RoleID and SecretID generated
- [ ] Database role created (if using dynamic credentials)
- [ ] All required secrets populated in Vault:
  - [ ] Database credentials
  - [ ] Redis password
  - [ ] Kafka credentials
  - [ ] Keycloak client secret
  - [ ] JWT secrets (if service generates tokens)
  - [ ] Encryption keys (if service encrypts data)
  - [ ] External API credentials (payment providers, KYC, etc.)
- [ ] Secret paths verified

**Testing**:
- [ ] Service starts successfully with Vault integration
- [ ] Database connection established
- [ ] Redis connection established
- [ ] Kafka producer/consumer functional
- [ ] External API calls succeed
- [ ] JWT validation works
- [ ] Keycloak authentication works
- [ ] Health checks pass
- [ ] Integration tests pass
- [ ] Load tests pass

**Deployment**:
- [ ] Docker Compose / Kubernetes manifests updated
- [ ] CI/CD pipeline updated
- [ ] Secrets injected securely in deployment
- [ ] Deployed to development environment
- [ ] Deployed to staging environment
- [ ] Canary deployment to production (10%)
- [ ] Full production deployment (100%)

**Validation**:
- [ ] Service operational in production
- [ ] No errors in application logs
- [ ] No Vault authentication failures
- [ ] Metrics normal (latency, throughput, error rate)
- [ ] Audit logs show successful secret access
- [ ] 24-hour burn-in period completed

**Documentation**:
- [ ] Service documentation updated with Vault details
- [ ] Runbook updated with troubleshooting steps
- [ ] Secret rotation procedures documented
- [ ] Emergency access procedures documented

### 8.3 Post-Migration Validation

**System-Wide Validation**:
- [ ] All 103 services migrated successfully
- [ ] Zero hardcoded secrets in version control
- [ ] All services authenticate with Vault via AppRole
- [ ] Database credentials dynamically generated (where applicable)
- [ ] Secret access audit trail captured in SIEM
- [ ] No service outages during migration
- [ ] Performance metrics within acceptable ranges

**Security Validation**:
- [ ] Penetration testing completed
- [ ] Vulnerability scanning passed
- [ ] Secrets rotation tested
- [ ] Emergency revocation tested
- [ ] Audit log review completed
- [ ] No secrets in application logs
- [ ] No secrets in monitoring dashboards
- [ ] Access control policies reviewed

**Operational Validation**:
- [ ] Backup and restore tested
- [ ] High availability tested (Vault node failure)
- [ ] Service recovery tested (re-authentication after token expiry)
- [ ] Monitoring dashboards operational
- [ ] Alerting rules functional and tested
- [ ] On-call team trained and ready
- [ ] Documentation complete and accessible

**Compliance Validation**:
- [ ] PCI-DSS requirements met
- [ ] SOC 2 Type II requirements met
- [ ] GDPR requirements met
- [ ] Audit trails retained for required period
- [ ] Encryption at rest verified
- [ ] Encryption in transit verified
- [ ] Access control meets least privilege principle

**Business Validation**:
- [ ] All payment transactions processing successfully
- [ ] All KYC verifications functional
- [ ] All notifications sending successfully
- [ ] All blockchain operations functional
- [ ] Customer-facing features operational
- [ ] SLA targets met
- [ ] Zero revenue impact from migration

**Sign-off**:
- [ ] Platform Owner: ______________________ Date: __________
- [ ] Security Lead: _______________________ Date: __________
- [ ] DevOps Lead: ________________________ Date: __________
- [ ] Engineering Lead: ____________________ Date: __________

---

## 9. Rollback Plan

### 9.1 Rollback Triggers

Execute rollback if any of the following occur:
- **Service Outage**: More than 3 critical services unavailable for >5 minutes
- **Data Loss**: Unable to retrieve secrets from Vault
- **Performance Degradation**: System-wide latency increase >50%
- **Security Incident**: Unauthorized access to Vault or secrets
- **Failed Validation**: Post-migration tests fail for >20% of services

### 9.2 Emergency Fallback Credentials

**Prepare encrypted fallback file** (before migration):

```bash
#!/bin/bash
# create-fallback-credentials.sh

# Extract all secrets from Vault and encrypt
vault kv get -format=json development/database/payment-service > /tmp/secrets/payment-service-db.json
vault kv get -format=json development/keycloak/payment-service > /tmp/secrets/payment-service-keycloak.json
# ... repeat for all services

# Encrypt with GPG (use secure password stored in 1Password)
tar -czf /tmp/vault-secrets-backup.tar.gz /tmp/secrets/
gpg --symmetric --cipher-algo AES256 /tmp/vault-secrets-backup.tar.gz

# Store encrypted backup in secure location
aws s3 cp /tmp/vault-secrets-backup.tar.gz.gpg s3://waqiti-secrets-backup/fallback-$(date +%Y%m%d).tar.gz.gpg
```

### 9.3 Rollback Procedure

**Step 1: Assess Situation** (5 minutes)
1. Identify affected services
2. Determine root cause
3. Decide: Fix forward or rollback?
4. Notify stakeholders

**Step 2: Activate Fallback Credentials** (10 minutes)
1. Retrieve encrypted backup:
   ```bash
   aws s3 cp s3://waqiti-secrets-backup/fallback-latest.tar.gz.gpg /tmp/
   gpg --decrypt /tmp/fallback-latest.tar.gz.gpg > /tmp/fallback.tar.gz
   tar -xzf /tmp/fallback.tar.gz -C /tmp/secrets/
   ```

2. Deploy fallback credentials as environment variables:
   ```bash
   export DATABASE_PASSWORD=$(jq -r '.data.data.password' /tmp/secrets/payment-service-db.json)
   export KEYCLOAK_CLIENT_SECRET=$(jq -r '.data.data."client-secret"' /tmp/secrets/payment-service-keycloak.json)
   # ... repeat for all required secrets
   ```

**Step 3: Revert Service Configurations** (15 minutes)
1. Checkout previous git commit (before Vault integration):
   ```bash
   git checkout <PRE_VAULT_COMMIT_HASH>
   ```

2. Redeploy services with environment variables:
   ```bash
   kubectl set env deployment/payment-service \
       DATABASE_PASSWORD=$DATABASE_PASSWORD \
       KEYCLOAK_CLIENT_SECRET=$KEYCLOAK_CLIENT_SECRET

   kubectl rollout restart deployment/payment-service
   ```

**Step 4: Validate Service Recovery** (10 minutes)
1. Check service health:
   ```bash
   kubectl get pods -n waqiti
   curl https://payment-service.example.com/actuator/health
   ```

2. Verify critical operations:
   - Payment processing
   - Authentication
   - Database connectivity

3. Monitor metrics for 10 minutes

**Step 5: Root Cause Analysis and Re-planning** (post-incident)
1. Document what went wrong
2. Identify gaps in migration process
3. Plan remediation
4. Schedule retry with improvements

### 9.4 Rollback Communication Plan

**Immediate Notification** (within 5 minutes of rollback decision):
- [ ] Page on-call engineering team
- [ ] Notify platform leadership
- [ ] Post in #incidents Slack channel

**Status Updates** (every 15 minutes during rollback):
- [ ] Update status page: https://status.example.com
- [ ] Post updates in #incidents
- [ ] Email customer support team

**Post-Rollback Communication** (within 1 hour):
- [ ] Post-mortem meeting scheduled
- [ ] RCA document started
- [ ] Customers notified (if impacted)
- [ ] Regulatory reporting (if required)

---

## 10. Additional Resources

### 10.1 Vault Documentation
- Official Vault Docs: https://www.vaultproject.io/docs
- Vault API Reference: https://www.vaultproject.io/api-docs
- Spring Cloud Vault: https://spring.io/projects/spring-cloud-vault

### 10.2 Security Best Practices
- HashiCorp Vault Production Hardening: https://learn.hashicorp.com/tutorials/vault/production-hardening
- OWASP Secrets Management Cheat Sheet: https://cheatsheetseries.owasp.org/cheatsheets/Secrets_Management_Cheat_Sheet.html

### 10.3 Training Materials
- Vault Certification: https://www.hashicorp.com/certification/vault-associate
- Internal Vault Training: [Link to internal training portal]

### 10.4 Support Contacts
- **Vault Admin Team**: vault-admins@example.com
- **Security Team**: security@example.com
- **On-Call Engineer**: Use PagerDuty
- **HashiCorp Support**: [Enterprise support portal link]

---

## Appendix A: Automated Scripts

All migration scripts referenced in this guide are available at:
```
/Users/anietieakpan/git/waqiti-app/services/scripts/vault-migration/
```

**Scripts Inventory**:
- `populate-database-secrets.sh` - Populate database credentials for all services
- `generate-service-policies.sh` - Generate Vault policies for all services
- `create-approles.sh` - Create AppRoles for all services
- `rotate-jwt-secret.sh` - Rotate JWT signing secret
- `rotate-keycloak-secret.sh` - Rotate Keycloak client secret
- `emergency-rotation.sh` - Emergency rotation for all critical secrets
- `test-vault-integration.sh` - Test Vault integration for a service
- `audit-hardcoded-secrets.sh` - Find remaining hardcoded secrets
- `cleanup-configs.sh` - Remove insecure fallback values from configs
- `create-fallback-credentials.sh` - Create encrypted backup of all secrets

---

## Appendix B: Service Priority Matrix

| Priority | Services | Reason | Migration Order |
|----------|----------|--------|----------------|
| P0 | payment-service, core-banking-service, account-service, wallet-service, transaction-service, user-service | Revenue-critical, customer-facing | Week 3, Days 11-12 |
| P0 | bank-integration-service, kyc-service, notification-service | Essential integrations | Week 3, Day 13 |
| P1 | crypto-service, nft-service, investment-service, lending-service | High-value features | Week 3, Day 14 |
| P1 | fraud-detection-service, compliance-service, security-service, audit-service | Security and compliance | Week 3, Day 15 |
| P2 | analytics-service, reporting-service, monitoring-service | Internal tools | Week 4, Day 16 |
| P2 | All other services | Lower priority | Week 4, Days 17-19 |

---

## Appendix C: Glossary

- **AppRole**: Machine-oriented authentication method in Vault
- **Dynamic Credentials**: Short-lived credentials generated on-demand by Vault
- **HSM**: Hardware Security Module for key storage
- **KV**: Key-Value secrets engine in Vault
- **Lease**: Time-bound credential in Vault with automatic expiration
- **PKI**: Public Key Infrastructure for certificate management
- **RoleID**: Identifier for an AppRole (similar to username)
- **SecretID**: Credential for an AppRole (similar to password)
- **Seal/Unseal**: Process of encrypting/decrypting Vault's master key
- **Transit**: Vault's encryption-as-a-service engine
- **TTL**: Time To Live - duration before credential expires

---

## Document Control

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2025-10-13 | Platform Security Team | Initial release |

**Next Review Date**: 2025-11-13

**Document Classification**: CONFIDENTIAL - Internal Use Only

---

**END OF VAULT SECRETS MIGRATION GUIDE**
