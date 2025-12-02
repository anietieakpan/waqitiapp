# HashiCorp Vault Deployment for Waqiti Banking Platform

This directory contains production-ready Kubernetes manifests for deploying a highly available HashiCorp Vault cluster.

## Architecture

- **High Availability**: 3-node Raft cluster with automatic leader election
- **Auto-Unseal**: AWS KMS-based automatic unsealing
- **Storage**: 500GB SSD persistent volumes per node
- **TLS**: Automatic certificate management via cert-manager
- **Monitoring**: Prometheus metrics and Grafana dashboards
- **Backup**: Automated Raft snapshots to S3 every 6 hours

## Prerequisites

Before deploying Vault, ensure you have:

1. **Kubernetes Cluster**: v1.24 or later
2. **kubectl**: Configured with cluster admin access
3. **AWS CLI**: Configured with appropriate IAM permissions
4. **cert-manager**: Installed in the cluster for TLS certificates
5. **Prometheus Operator**: (Optional) For monitoring

## Files

| File | Description |
|------|-------------|
| `vault-namespace.yaml` | Creates `vault-system` namespace |
| `vault-rbac.yaml` | Service account and RBAC permissions |
| `vault-config.yaml` | Vault server configuration (Raft, TLS, auto-unseal) |
| `vault-storage.yaml` | Persistent volume claims (500GB x 3) |
| `vault-service.yaml` | Internal headless service + external LoadBalancer |
| `vault-statefulset.yaml` | StatefulSet with 3 replicas |
| `vault-backup-cronjob.yaml` | Automated backup to S3 (every 6 hours) |
| `vault-monitoring.yaml` | Prometheus ServiceMonitor and alerts |
| `deploy-vault.sh` | Automated deployment script |

## Quick Start

### 1. Configure Environment

```bash
export AWS_REGION="us-east-1"
export VAULT_DOMAIN="vault.example.com"
export AWS_ACCOUNT_ID="123456789012"
```

### 2. Update Configuration

Edit the following files to replace placeholders:

**vault-service.yaml**:
- Replace `ACCOUNT_ID` with your AWS account ID
- Replace `CERT_ID` with your ACM certificate ID

**vault-config.yaml**:
- Verify AWS region matches your environment

### 3. Deploy Vault

```bash
cd infrastructure/vault
chmod +x deploy-vault.sh
./deploy-vault.sh
```

The script will:
1. Create AWS KMS key for auto-unseal
2. Create Kubernetes namespace
3. Generate TLS certificates
4. Deploy Vault StatefulSet
5. Initialize Vault cluster
6. Form Raft cluster
7. Run vault-setup.sh to populate secrets

### 4. Verify Deployment

```bash
# Check pod status
kubectl get pods -n vault-system

# Expected output:
# NAME      READY   STATUS    RESTARTS   AGE
# vault-0   1/1     Running   0          5m
# vault-1   1/1     Running   0          5m
# vault-2   1/1     Running   0          5m

# Check Vault status
kubectl exec -n vault-system vault-0 -- vault status

# Check Raft cluster
kubectl exec -n vault-system vault-0 -- vault operator raft list-peers
```

### 5. Access Vault UI

```bash
# Get LoadBalancer address
kubectl get svc vault -n vault-system

# Access UI at: https://vault.example.com:8200
```

## Post-Deployment Configuration

### 1. Store Root Token Securely

The deployment script saves initialization keys to `vault-init-keys.json`. **IMMEDIATELY** store these in AWS Secrets Manager:

```bash
aws secretsmanager create-secret \
  --name waqiti/vault/root-token \
  --secret-string file://vault-init-keys.json \
  --region us-east-1

# Delete local copy
shred -u vault-init-keys.json
```

### 2. Enable Audit Logging

```bash
export VAULT_ADDR="https://vault.example.com:8200"
export VAULT_TOKEN="<root-token>"

# Enable file audit
kubectl exec -n vault-system vault-0 -- \
  vault audit enable file file_path=/vault/logs/audit.log

# Enable syslog audit (for SIEM)
kubectl exec -n vault-system vault-0 -- \
  vault audit enable syslog \
    tag="vault" \
    facility="AUTH"
```

### 3. Configure Backup Validation

```bash
# Create backup service account token
BACKUP_TOKEN=$(kubectl exec -n vault-system vault-0 -- \
  vault token create \
    -policy=backup-policy \
    -period=24h \
    -format=json | jq -r '.auth.client_token')

# Store in Kubernetes secret
kubectl create secret generic vault-backup-token \
  --namespace=vault-system \
  --from-literal=token="$BACKUP_TOKEN"

# Apply backup CronJob
kubectl apply -f vault-backup-cronjob.yaml
```

### 4. Set Up Monitoring

```bash
# Apply ServiceMonitor and PrometheusRule
kubectl apply -f vault-monitoring.yaml

# Import Grafana dashboard
kubectl label configmap vault-dashboard -n vault-system grafana_dashboard=1
```

### 5. Revoke Root Token

After creating admin users:

```bash
export VAULT_TOKEN="<root-token>"
vault token revoke -self
```

## Service Configuration

After Vault is deployed, configure your microservices:

### 1. Add Dependencies

Add to each service's `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-vault-config</artifactId>
</dependency>
```

### 2. Update application.yml

```yaml
spring:
  cloud:
    vault:
      enabled: true
      host: vault.vault-system.svc.cluster.local
      port: 8200
      scheme: https
      authentication: TOKEN
      token: ${VAULT_TOKEN}
      kv:
        enabled: true
        backend: secret
        default-context: waqiti
```

### 3. Create Service Tokens

```bash
# Example for payment-service
vault policy write payment-service-policy - <<EOF
path "secret/data/waqiti/payment-service/*" {
  capabilities = ["read"]
}
path "secret/data/waqiti/jwt-secret" {
  capabilities = ["read"]
}
EOF

vault token create \
  -policy=payment-service-policy \
  -period=720h \
  -format=json
```

## Backup and Recovery

### Manual Backup

```bash
# Create snapshot
kubectl exec -n vault-system vault-0 -- \
  vault operator raft snapshot save /tmp/backup.snap

# Copy from pod
kubectl cp vault-system/vault-0:/tmp/backup.snap ./vault-backup.snap
```

### Recovery from Snapshot

```bash
# Copy snapshot to pod
kubectl cp ./vault-backup.snap vault-system/vault-0:/tmp/backup.snap

# Restore
kubectl exec -n vault-system vault-0 -- \
  vault operator raft snapshot restore /tmp/backup.snap
```

## Monitoring and Alerts

### Key Metrics

| Metric | Description | Threshold |
|--------|-------------|-----------|
| `vault_core_unsealed` | Vault seal status | Must be 1 (unsealed) |
| `vault_raft_leader` | Raft leader exists | Must be 1 |
| `vault_raft_peers` | Raft peer count | Must be 3 |
| `vault_core_handle_request{quantile="0.99"}` | p99 request latency | < 1000ms |
| `vault_token_count` | Active token count | Monitor trends |

### Alerts

Critical alerts configured in `vault-monitoring.yaml`:

- **VaultSealed**: Vault instance sealed for >5min
- **VaultDown**: Vault instance unreachable for >2min
- **VaultRaftLeaderMissing**: No Raft leader for >1min
- **VaultHighLatency**: p99 latency >1s for >10min

## Disaster Recovery

### Scenario 1: Single Node Failure

```bash
# Raft will automatically elect new leader
# No action required - Kubernetes will restart pod

# Verify cluster health
kubectl exec -n vault-system vault-0 -- vault operator raft list-peers
```

### Scenario 2: Complete Cluster Loss

```bash
# Restore from latest S3 backup
aws s3 ls s3://waqiti-vault-backups/raft-snapshots/ | tail -1

# Download snapshot
aws s3 cp s3://waqiti-vault-backups/raft-snapshots/vault-backup-LATEST.snap ./

# Restore (requires manual unsealing if auto-unseal is unavailable)
kubectl cp ./vault-backup-LATEST.snap vault-system/vault-0:/tmp/backup.snap
kubectl exec -n vault-system vault-0 -- vault operator raft snapshot restore /tmp/backup.snap
```

### Scenario 3: KMS Key Loss

If AWS KMS key is lost/deleted, you must manually unseal Vault using the unseal keys from `vault-init-keys.json` (stored in AWS Secrets Manager).

```bash
# Retrieve unseal keys
aws secretsmanager get-secret-value \
  --secret-id waqiti/vault/root-token \
  --region us-east-1 \
  --query SecretString \
  --output text > vault-keys.json

# Extract unseal keys
UNSEAL_KEY_1=$(jq -r '.unseal_keys_b64[0]' vault-keys.json)
UNSEAL_KEY_2=$(jq -r '.unseal_keys_b64[1]' vault-keys.json)
UNSEAL_KEY_3=$(jq -r '.unseal_keys_b64[2]' vault-keys.json)

# Unseal each instance (3 keys required)
for i in 0 1 2; do
  kubectl exec -n vault-system "vault-$i" -- vault operator unseal "$UNSEAL_KEY_1"
  kubectl exec -n vault-system "vault-$i" -- vault operator unseal "$UNSEAL_KEY_2"
  kubectl exec -n vault-system "vault-$i" -- vault operator unseal "$UNSEAL_KEY_3"
done
```

## Security Hardening

### 1. Network Policies

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: vault-network-policy
  namespace: vault-system
spec:
  podSelector:
    matchLabels:
      app: vault
  policyTypes:
    - Ingress
    - Egress
  ingress:
    # Allow from services
    - from:
        - namespaceSelector:
            matchLabels:
              name: waqiti-services
      ports:
        - protocol: TCP
          port: 8200
  egress:
    # Allow to AWS KMS
    - to:
        - namespaceSelector: {}
      ports:
        - protocol: TCP
          port: 443
```

### 2. Pod Security Standards

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: vault-system
  labels:
    pod-security.kubernetes.io/enforce: restricted
    pod-security.kubernetes.io/audit: restricted
    pod-security.kubernetes.io/warn: restricted
```

### 3. Enable MFA for Root Operations

```bash
vault auth enable userpass

vault write auth/userpass/users/admin \
  password="<strong-password>" \
  policies="admin-policy"

# Enable TOTP MFA
vault write sys/mfa/method/totp/admin \
  issuer="Waqiti Vault" \
  period=30 \
  algorithm=SHA256
```

## Troubleshooting

### Pods Not Starting

```bash
# Check events
kubectl describe pod vault-0 -n vault-system

# Check logs
kubectl logs vault-0 -n vault-system

# Common issues:
# - PVC not bound: Check storage class
# - TLS certificate not ready: Check cert-manager
# - KMS permissions: Check IAM role
```

### Vault Sealed After Restart

```bash
# Check seal status
kubectl exec -n vault-system vault-0 -- vault status

# If auto-unseal is working, should unseal automatically
# Check KMS permissions if not
```

### Raft Cluster Not Forming

```bash
# Check Raft status
kubectl exec -n vault-system vault-0 -- vault operator raft list-peers

# Re-join nodes if needed
kubectl exec -n vault-system vault-1 -- \
  vault operator raft join https://vault-0.vault-internal:8200
```

## Performance Tuning

### High Transaction Volume

If experiencing >10K req/s:

1. **Increase resources**:
```yaml
resources:
  requests:
    cpu: "2000m"
    memory: "8Gi"
  limits:
    cpu: "4000m"
    memory: "16Gi"
```

2. **Enable performance replication** (Enterprise):
```bash
vault write -f sys/replication/performance/primary/enable
```

3. **Increase concurrency**:
```yaml
# In vault.hcl
disable_mlock = true
default_max_request_duration = "90s"
```

## Compliance

### PCI DSS Requirements

- ✅ 3.4: Encryption keys protected (KMS auto-unseal)
- ✅ 3.5: Key management procedures (automated rotation)
- ✅ 8.2: Multi-factor authentication (TOTP MFA)
- ✅ 10.2: Audit logging (file + syslog audit)
- ✅ 10.7: Audit log retention (S3 backups)

### SOC 2 Requirements

- ✅ CC6.1: Logical access controls (RBAC policies)
- ✅ CC6.6: Encryption in transit (TLS 1.2+)
- ✅ CC7.2: System monitoring (Prometheus alerts)
- ✅ CC9.1: Risk mitigation (HA cluster, backups)

## Support

For issues or questions:
- Internal: #vault-support Slack channel
- Vault Docs: https://www.vaultproject.io/docs
- Kubernetes Vault: https://www.vaultproject.io/docs/platform/k8s

## Next Steps

1. ✅ Deploy Vault cluster (Task 1.1)
2. ⏳ Configure services to use Vault (Task 1.2)
3. ⏳ Run database migrations (Task 1.3)
4. ⏳ Create Kafka DLQ topics (Task 1.4)
