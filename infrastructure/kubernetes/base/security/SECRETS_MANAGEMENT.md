# Secrets Management for WAQITI Platform

## Overview

This document describes the secure secrets management implementation for the WAQITI platform using HashiCorp Vault and External Secrets Operator.

## Architecture

```
┌─────────────────────┐     ┌─────────────────────┐
│   HashiCorp Vault   │────▶│  External Secrets   │
│  (Secrets Storage)  │     │     Operator        │
└─────────────────────┘     └─────────────────────┘
           │                           │
           ▼                           ▼
┌─────────────────────┐     ┌─────────────────────┐
│   AWS Secrets       │     │   Kubernetes        │
│     Manager         │     │    Secrets          │
└─────────────────────┘     └─────────────────────┘
```

## Components

### 1. HashiCorp Vault
- Primary secrets storage backend
- Provides encryption at rest and in transit
- Supports dynamic secrets generation
- Audit logging for all secret access

### 2. External Secrets Operator
- Synchronizes secrets from Vault to Kubernetes
- Automatic secret rotation
- Minimal permissions per service

### 3. AWS Secrets Manager
- Backup storage for critical secrets
- TLS certificates management
- Cross-region replication

## Secret Categories

### Infrastructure Secrets
- **PostgreSQL**: Database passwords
- **Redis**: Cache passwords
- **Grafana**: Admin credentials
- **Monitoring**: Basic auth credentials

### Application Secrets
- **JWT**: Authentication tokens
- **Encryption Keys**: Data encryption keys
- **API Keys**: Third-party service credentials

### Payment Provider Secrets
- **Stripe**: API keys and webhook secrets
- **PayPal**: Client ID and secrets
- **Plaid**: Banking integration credentials

## Vault Path Structure

```
waqiti/
├── data/
│   ├── infrastructure/
│   │   ├── postgres/
│   │   ├── redis/
│   │   └── grafana/
│   ├── auth/
│   │   └── jwt-secret
│   ├── encryption/
│   │   ├── master-key
│   │   └── data-key
│   ├── payment-providers/
│   │   ├── stripe/
│   │   ├── paypal/
│   │   └── plaid/
│   ├── monitoring/
│   │   ├── basic-auth/
│   │   ├── kibana-auth/
│   │   └── jaeger-auth/
│   └── [service-name]/
│       ├── db-host
│       ├── db-username
│       ├── db-password
│       └── ...
└── tls/
    └── production/
```

## Implementation Steps

### 1. Initial Setup

```bash
# Install External Secrets Operator
helm repo add external-secrets https://charts.external-secrets.io
helm install external-secrets \
  external-secrets/external-secrets \
  -n external-secrets-system \
  --create-namespace

# Apply External Secrets configuration
kubectl apply -f infrastructure/kubernetes/base/security/external-secrets-operator.yaml
kubectl apply -f infrastructure/kubernetes/base/security/infrastructure-secrets.yaml
```

### 2. Vault Configuration

```bash
# Enable KV v2 secrets engine
vault secrets enable -path=waqiti kv-v2

# Create Kubernetes auth method
vault auth enable kubernetes

# Configure Kubernetes auth
vault write auth/kubernetes/config \
  kubernetes_host="https://$KUBERNETES_PORT_443_TCP_ADDR:443" \
  kubernetes_ca_cert=@/var/run/secrets/kubernetes.io/serviceaccount/ca.crt \
  token_reviewer_jwt=@/var/run/secrets/kubernetes.io/serviceaccount/token

# Create policy for WAQITI services
vault policy write waqiti-services - <<EOF
path "waqiti/data/*" {
  capabilities = ["read", "list"]
}
EOF

# Create role for External Secrets
vault write auth/kubernetes/role/waqiti-services \
  bound_service_account_names=external-secrets-sa \
  bound_service_account_namespaces=waqiti-system \
  policies=waqiti-services \
  ttl=24h
```

### 3. Populate Secrets in Vault

```bash
# Infrastructure secrets
vault kv put waqiti/infrastructure/postgres \
  password="$(openssl rand -base64 32)"

vault kv put waqiti/infrastructure/redis \
  password="$(openssl rand -base64 32)"

vault kv put waqiti/infrastructure/grafana \
  admin-user="admin" \
  admin-password="$(openssl rand -base64 32)"

# Auth secrets
vault kv put waqiti/auth \
  jwt-secret="$(openssl rand -base64 64)"

# Encryption keys
vault kv put waqiti/encryption \
  master-key="$(openssl rand -base64 32)" \
  data-key="$(openssl rand -base64 32)"

# Payment provider secrets (use actual values)
vault kv put waqiti/payment-providers/stripe \
  api-key="${STRIPE_API_KEY}" \
  webhook-secret="${STRIPE_WEBHOOK_SECRET}"

# Add other secrets as needed...
```

### 4. Deploy Applications

```bash
# Deploy with Helm using secure values
helm upgrade --install waqiti-platform \
  ./infrastructure/kubernetes/helm/waqiti-platform \
  -n waqiti-system \
  --create-namespace
```

## Secret Rotation

### Automatic Rotation
- External Secrets Operator refreshes secrets every 15 seconds
- Vault can be configured to rotate secrets automatically
- Applications should handle secret updates gracefully

### Manual Rotation

```bash
# Rotate a specific secret
vault kv put waqiti/infrastructure/postgres \
  password="$(openssl rand -base64 32)"

# Force refresh in Kubernetes
kubectl annotate externalsecret postgres-credentials \
  force-sync=$(date +%s) --overwrite
```

## Security Best Practices

1. **Least Privilege**: Each service only has access to its required secrets
2. **Encryption**: All secrets encrypted at rest and in transit
3. **Audit Logging**: All secret access is logged in Vault
4. **No Hardcoded Secrets**: All secrets managed through Vault
5. **Regular Rotation**: Secrets rotated on a regular schedule
6. **Backup**: Critical secrets backed up to AWS Secrets Manager

## Monitoring and Alerts

1. **Secret Expiry**: Alert when secrets are about to expire
2. **Failed Sync**: Alert when External Secrets fails to sync
3. **Unauthorized Access**: Alert on unauthorized secret access attempts
4. **Rotation Failures**: Alert when automatic rotation fails

## Troubleshooting

### External Secret Not Syncing

```bash
# Check External Secret status
kubectl describe externalsecret <secret-name>

# Check External Secrets Operator logs
kubectl logs -n external-secrets-system deployment/external-secrets

# Verify Vault connectivity
kubectl exec -it <vault-pod> -- vault status
```

### Secret Not Available in Pod

```bash
# Verify secret exists
kubectl get secret <secret-name>

# Check secret content (be careful!)
kubectl get secret <secret-name> -o jsonpath='{.data}'

# Check pod environment
kubectl exec <pod-name> -- env | grep -i secret
```

## Emergency Procedures

### Vault Sealed

```bash
# Unseal Vault (requires unseal keys)
vault operator unseal <unseal-key-1>
vault operator unseal <unseal-key-2>
vault operator unseal <unseal-key-3>
```

### Complete Secret Recovery

```bash
# Restore from AWS Secrets Manager backup
aws secretsmanager get-secret-value \
  --secret-id waqiti/backup/<secret-name> \
  --query SecretString --output text
```

## Compliance

- All secret access is audited
- Secrets are encrypted with AES-256
- Regular security assessments
- Compliance with PCI DSS for payment data
- GDPR compliance for user data encryption