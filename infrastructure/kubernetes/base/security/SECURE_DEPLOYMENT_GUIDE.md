# Secure Deployment Guide for WAQITI Platform

## Overview

This guide provides step-by-step instructions for securely deploying the WAQITI platform with proper secrets management using HashiCorp Vault and External Secrets Operator.

## Prerequisites

- Kubernetes cluster (1.24+)
- kubectl configured
- Helm 3.x installed
- HashiCorp Vault deployed (or use the provided Vault deployment)
- AWS account (for Secrets Manager backup)

## Step 1: Deploy HashiCorp Vault

### Option A: Use Existing Vault
If you already have Vault deployed, skip to Step 2.

### Option B: Deploy Vault
```bash
# Add Helm repository
helm repo add hashicorp https://helm.releases.hashicorp.com
helm repo update

# Install Vault
helm install vault hashicorp/vault \
  --namespace vault \
  --create-namespace \
  -f infrastructure/kubernetes/vault/values.yaml

# Wait for Vault to be ready
kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=vault -n vault --timeout=300s

# Initialize Vault (save the output!)
kubectl exec -n vault vault-0 -- vault operator init

# Unseal Vault (repeat 3 times with different unseal keys)
kubectl exec -n vault vault-0 -- vault operator unseal <unseal-key-1>
kubectl exec -n vault vault-0 -- vault operator unseal <unseal-key-2>
kubectl exec -n vault vault-0 -- vault operator unseal <unseal-key-3>
```

## Step 2: Install External Secrets Operator

```bash
# Add External Secrets Helm repository
helm repo add external-secrets https://charts.external-secrets.io
helm repo update

# Install External Secrets Operator
helm install external-secrets \
  external-secrets/external-secrets \
  -n external-secrets-system \
  --create-namespace \
  --set installCRDs=true

# Verify installation
kubectl get pods -n external-secrets-system
```

## Step 3: Configure Vault for WAQITI

```bash
# Port-forward to Vault
kubectl port-forward -n vault vault-0 8200:8200 &

# Set Vault address
export VAULT_ADDR='http://127.0.0.1:8200'

# Login to Vault (use root token from initialization)
vault login <root-token>

# Run the Vault setup script
./infrastructure/kubernetes/base/security/vault-setup.sh
```

## Step 4: Create Kubernetes Namespace and RBAC

```bash
# Create namespace
kubectl create namespace waqiti-system

# Create service account for External Secrets
kubectl create serviceaccount external-secrets-sa -n waqiti-system

# Apply RBAC
kubectl apply -f - <<EOF
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: external-secrets-role
rules:
- apiGroups: [""]
  resources: ["secrets"]
  verbs: ["get", "list", "watch", "create", "update", "patch", "delete"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: external-secrets-binding
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: external-secrets-role
subjects:
- kind: ServiceAccount
  name: external-secrets-sa
  namespace: waqiti-system
EOF
```

## Step 5: Deploy External Secrets Configuration

```bash
# Apply External Secrets Operator configuration
kubectl apply -f infrastructure/kubernetes/base/security/external-secrets-operator.yaml

# Apply infrastructure secrets
kubectl apply -f infrastructure/kubernetes/base/security/infrastructure-secrets.yaml

# Verify secrets are being created
kubectl get externalsecrets -n waqiti-system
kubectl get secrets -n waqiti-system
```

## Step 6: Configure Production Secrets

### Payment Provider Secrets
```bash
# Update payment provider secrets in Vault with actual values
vault kv put waqiti/payment-providers/stripe \
  api-key="${STRIPE_API_KEY}" \
  webhook-secret="${STRIPE_WEBHOOK_SECRET}"

vault kv put waqiti/payment-providers/paypal \
  client-id="${PAYPAL_CLIENT_ID}" \
  client-secret="${PAYPAL_CLIENT_SECRET}"

vault kv put waqiti/payment-providers/plaid \
  client-id="${PLAID_CLIENT_ID}" \
  secret="${PLAID_SECRET}"
```

### Third-Party Service Credentials
```bash
# Twilio (for notifications)
vault kv put waqiti/notification-service \
  twilio-account-sid="${TWILIO_ACCOUNT_SID}" \
  twilio-auth-token="${TWILIO_AUTH_TOKEN}" \
  twilio-phone-number="${TWILIO_PHONE_NUMBER}"

# SendGrid (for emails)
vault kv put waqiti/notification-service \
  sendgrid-api-key="${SENDGRID_API_KEY}"

# AWS credentials (for S3, SES, etc.)
vault kv put waqiti/aws \
  access-key-id="${AWS_ACCESS_KEY_ID}" \
  secret-access-key="${AWS_SECRET_ACCESS_KEY}"
```

## Step 7: Deploy WAQITI Platform

```bash
# Deploy with Helm
helm upgrade --install waqiti-platform \
  ./infrastructure/kubernetes/helm/waqiti-platform \
  -n waqiti-system \
  --create-namespace \
  --values ./infrastructure/kubernetes/helm/waqiti-platform/values.yaml \
  --values ./infrastructure/kubernetes/helm/waqiti-platform/values-production.yaml

# Monitor deployment
kubectl get pods -n waqiti-system -w
```

## Step 8: Verify Deployment

### Check External Secrets Sync
```bash
# Check External Secret status
kubectl describe externalsecrets -n waqiti-system

# Verify secrets are created
kubectl get secrets -n waqiti-system | grep -E "(postgres|redis|grafana|payment)"
```

### Test Secret Access
```bash
# Test a service pod has access to secrets
kubectl exec -n waqiti-system deployment/payment-service -- env | grep -i secret
```

## Step 9: Configure Monitoring

```bash
# Get Grafana admin password
kubectl get secret grafana-credentials -n waqiti-system -o jsonpath='{.data.admin-password}' | base64 -d

# Access Grafana
kubectl port-forward -n waqiti-system svc/grafana 3000:80

# Login at http://localhost:3000 with admin/<password>
```

## Step 10: Enable API Server Encryption at Rest

```bash
# Apply encryption configuration to API server
# Note: This requires cluster admin access and API server restart

# For managed Kubernetes (EKS, GKE, AKS), use provider-specific methods
# For self-managed clusters:
sudo cp /etc/kubernetes/manifests/kube-apiserver.yaml /etc/kubernetes/manifests/kube-apiserver.yaml.backup
sudo kubectl apply -f infrastructure/kubernetes/production/security-policies.yaml
```

## Security Checklist

- [ ] All hardcoded secrets removed from manifests
- [ ] Vault initialized and unsealed
- [ ] External Secrets Operator installed and configured
- [ ] All External Secrets syncing successfully
- [ ] Payment provider credentials updated
- [ ] Monitoring credentials secured
- [ ] API server encryption at rest enabled
- [ ] Network policies applied
- [ ] RBAC configured correctly
- [ ] Backup encryption keys stored securely

## Troubleshooting

### External Secret Not Syncing
```bash
# Check External Secret events
kubectl describe externalsecret <name> -n waqiti-system

# Check External Secrets Operator logs
kubectl logs -n external-secrets-system deployment/external-secrets -f

# Verify Vault connectivity
kubectl exec -n vault vault-0 -- vault status
```

### Secret Not Available in Pod
```bash
# Check if secret exists
kubectl get secret <secret-name> -n waqiti-system

# Describe pod to see mounted secrets
kubectl describe pod <pod-name> -n waqiti-system

# Check service logs
kubectl logs <pod-name> -n waqiti-system
```

### Vault Sealed
```bash
# Check Vault status
kubectl exec -n vault vault-0 -- vault status

# Unseal if needed (requires 3 unseal keys)
kubectl exec -n vault vault-0 -- vault operator unseal <key>
```

## Backup and Recovery

### Backup Vault Data
```bash
# Create Vault snapshot
kubectl exec -n vault vault-0 -- vault operator raft snapshot save /tmp/vault-backup.snap

# Copy snapshot locally
kubectl cp vault/vault-0:/tmp/vault-backup.snap ./vault-backup-$(date +%Y%m%d).snap
```

### Backup to AWS Secrets Manager
```bash
# Script to backup critical secrets to AWS
./infrastructure/kubernetes/base/security/backup-secrets-to-aws.sh
```

## Regular Maintenance

### Rotate Secrets (Monthly)
```bash
# Rotate database passwords
vault kv put waqiti/infrastructure/postgres \
  password="$(openssl rand -base64 32)"

# Force External Secret refresh
kubectl annotate externalsecret postgres-credentials \
  force-sync=$(date +%s) --overwrite -n waqiti-system
```

### Update External Secrets Operator (Quarterly)
```bash
helm upgrade external-secrets \
  external-secrets/external-secrets \
  -n external-secrets-system
```

### Audit Secret Access (Weekly)
```bash
# Check Vault audit logs
kubectl exec -n vault vault-0 -- vault audit list
```

## Production Readiness

Before going to production, ensure:

1. **High Availability**
   - Vault running in HA mode (3+ nodes)
   - External Secrets Operator with multiple replicas
   - Database credentials with automatic rotation

2. **Monitoring**
   - Alerts for failed secret sync
   - Alerts for Vault seal status
   - Metrics for secret rotation age

3. **Compliance**
   - Audit logging enabled
   - Secret access tracked
   - Regular security scans

4. **Disaster Recovery**
   - Vault backup strategy
   - Unseal keys stored securely (separate locations)
   - Recovery procedures documented and tested