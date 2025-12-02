# Vault TLS Migration Guide

## Overview
This guide provides step-by-step instructions for migrating from non-TLS Vault configuration to production-grade TLS 1.3 with mTLS.

**Security Impact**: CRITICAL - This fixes the HIGH priority security vulnerability where Vault API traffic was unencrypted.

## Prerequisites
- Kubernetes cluster with kubectl access
- OpenSSL 1.1.1+ installed
- Vault CLI installed
- AWS KMS key created for auto-unseal

## Migration Steps

### Phase 1: Generate TLS Certificates (30 minutes)

#### Step 1.1: Run Certificate Generation Script
```bash
cd /Users/anietieakpan/git/waqiti-app/infrastructure/vault/scripts
./generate-vault-tls-certs.sh production
```

**What this does**:
- Generates 4096-bit RSA CA certificate (10-year validity)
- Creates Vault server certificate with proper SANs (1-year validity)
- Creates Consul client certificates for storage backend
- Creates Kubernetes secret `vault-tls` in `vault-production` namespace

#### Step 1.2: Verify Certificate Creation
```bash
kubectl get secret vault-tls -n vault-production
kubectl describe secret vault-tls -n vault-production
```

Expected output:
```
Name:         vault-tls
Namespace:    vault-production
Labels:       app=vault
              component=tls
              environment=production
Type:         Opaque

Data
====
ca.pem:            2041 bytes
consul-ca.pem:     2041 bytes
consul-cert.pem:   2017 bytes
consul-key.pem:    3243 bytes
vault-cert.pem:    2017 bytes
vault-key.pem:     3243 bytes
```

#### Step 1.3: Backup Existing Vault Data
```bash
# Export current Vault secrets
kubectl exec -it vault-0 -n vault-production -- vault operator raft snapshot save /tmp/vault-backup.snap
kubectl cp vault-production/vault-0:/tmp/vault-backup.snap ./vault-backup-$(date +%Y%m%d).snap
```

### Phase 2: Deploy Vault with TLS (20 minutes)

#### Step 2.1: Create Consul Token Secret (if using Consul storage)
```bash
# Generate secure Consul token
CONSUL_TOKEN=$(openssl rand -base64 32)

# Create Kubernetes secret
kubectl create secret generic vault-consul-token \
  --from-literal=token="${CONSUL_TOKEN}" \
  -n vault-production

# Configure Consul to use this token
# (See Consul documentation for ACL configuration)
```

#### Step 2.2: Apply Vault Deployment
```bash
cd /Users/anietieakpan/git/waqiti-app/k8s/vault
kubectl apply -f vault-deployment-tls.yaml
```

#### Step 2.3: Verify Deployment
```bash
# Wait for pods to be ready
kubectl wait --for=condition=ready pod -l app=vault -n vault-production --timeout=300s

# Check pod status
kubectl get pods -n vault-production -l app=vault

# Expected output:
# NAME      READY   STATUS    RESTARTS   AGE
# vault-0   1/1     Running   0          2m
# vault-1   1/1     Running   0          2m
# vault-2   1/1     Running   0          2m
```

#### Step 2.4: Verify TLS Configuration
```bash
# Test TLS connection
kubectl exec -it vault-0 -n vault-production -- sh -c 'vault status'

# Should show:
# - Sealed: true (expected on first start)
# - TLS: enabled
# - Cluster Name: waqiti-vault-prod
```

### Phase 3: Initialize Vault (if new deployment) (15 minutes)

#### Step 3.1: Initialize Vault
```bash
# Initialize Vault (only on first deployment)
kubectl exec -it vault-0 -n vault-production -- vault operator init \
  -key-shares=5 \
  -key-threshold=3 \
  -format=json > vault-init-keys.json

# CRITICAL: Store vault-init-keys.json in a secure location (e.g., AWS Secrets Manager)
```

#### Step 3.2: Unseal Vault (AWS KMS handles this automatically)
With AWS KMS auto-unseal configured, Vault will automatically unseal on restart.

Verify:
```bash
kubectl exec -it vault-0 -n vault-production -- vault status
# Sealed should be: false
```

### Phase 4: Update Vault Clients (30 minutes)

All services connecting to Vault must be updated to use TLS.

#### Step 4.1: Update Spring Cloud Vault Configuration

**Before (HTTP)**:
```yaml
spring:
  cloud:
    vault:
      uri: http://vault:8200
      token: ${VAULT_TOKEN}
```

**After (HTTPS with TLS)**:
```yaml
spring:
  cloud:
    vault:
      uri: https://vault:8200
      token: ${VAULT_TOKEN}
      ssl:
        trust-store: classpath:vault-ca.jks
        trust-store-password: ${VAULT_TRUSTSTORE_PASSWORD}
        # Alternatively, trust all certificates from CA
      connection-timeout: 5000
      read-timeout: 15000
```

#### Step 4.2: Create Java Truststore with Vault CA

```bash
# Export CA certificate from Kubernetes secret
kubectl get secret vault-tls -n vault-production \
  -o jsonpath='{.data.ca\.pem}' | base64 -d > vault-ca.pem

# Create Java truststore
keytool -importcert \
  -alias vault-ca \
  -file vault-ca.pem \
  -keystore vault-ca.jks \
  -storepass changeit \
  -noprompt

# Distribute vault-ca.jks to all services
```

#### Step 4.3: Update Service Deployment

**Example: payment-service**

```yaml
# k8s/payment-service/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: payment-service
spec:
  template:
    spec:
      containers:
        - name: payment-service
          env:
            - name: SPRING_CLOUD_VAULT_URI
              value: "https://vault.vault-production.svc.cluster.local:8200"
            - name: SPRING_CLOUD_VAULT_SSL_TRUST_STORE
              value: "/etc/vault/vault-ca.jks"
            - name: SPRING_CLOUD_VAULT_SSL_TRUST_STORE_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: vault-truststore-password
                  key: password
          volumeMounts:
            - name: vault-ca
              mountPath: /etc/vault
              readOnly: true
      volumes:
        - name: vault-ca
          secret:
            secretName: vault-ca-truststore
```

**Create Truststore Secret**:
```bash
kubectl create secret generic vault-ca-truststore \
  --from-file=vault-ca.jks \
  -n payment-service
```

#### Step 4.4: Rolling Update Services

Update services one namespace at a time:

```bash
# List all services using Vault
kubectl get deployments --all-namespaces \
  -o jsonpath='{range .items[?(@.spec.template.spec.containers[*].env[?(@.name=="VAULT_ADDR")])]}......{.metadata.name}{"\n"}{end}'

# Update each service
for service in payment-service wallet-service user-service; do
  kubectl set env deployment/${service} \
    SPRING_CLOUD_VAULT_URI=https://vault.vault-production.svc.cluster.local:8200

  kubectl rollout status deployment/${service}
done
```

### Phase 5: Verification & Testing (15 minutes)

#### Step 5.1: Verify TLS Connections
```bash
# Test from within cluster
kubectl run vault-tls-test --rm -it --image=alpine -- sh

# Inside pod:
apk add curl openssl
curl --cacert /path/to/ca.pem https://vault.vault-production.svc.cluster.local:8200/v1/sys/health

# Should return JSON with Vault health status
```

#### Step 5.2: Verify Service Connectivity
```bash
# Check payment-service logs
kubectl logs -l app=payment-service --tail=100 | grep -i vault

# Should see successful Vault connections:
# "Successfully authenticated with Vault"
# "Retrieved secrets from Vault path: secret/payment-service"
```

#### Step 5.3: Verify mTLS
```bash
# Connect to Vault with client certificate
kubectl exec -it vault-0 -n vault-production -- \
  vault login -method=cert -client-cert=/vault/tls/vault-cert.pem

# Should successfully authenticate
```

### Phase 6: Certificate Rotation Setup (10 minutes)

#### Step 6.1: Create Certificate Rotation CronJob

```yaml
# k8s/vault/vault-cert-rotation-cronjob.yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: vault-cert-rotation
  namespace: vault-production
spec:
  schedule: "0 0 1 */3 *"  # Every 3 months
  jobTemplate:
    spec:
      template:
        spec:
          serviceAccountName: vault-cert-rotator
          containers:
            - name: cert-rotator
              image: alpine/openssl:latest
              command:
                - /bin/sh
                - -c
                - |
                  # Download rotation script
                  wget https://raw.githubusercontent.com/waqiti/vault-tools/main/rotate-certs.sh
                  chmod +x rotate-certs.sh
                  ./rotate-certs.sh production
          restartPolicy: OnFailure
```

Apply:
```bash
kubectl apply -f k8s/vault/vault-cert-rotation-cronjob.yaml
```

#### Step 6.2: Set Up Certificate Expiry Alerts

**Prometheus Alert Rule**:
```yaml
# monitoring/prometheus/rules/vault-certs.yaml
groups:
  - name: vault-tls
    interval: 1h
    rules:
      - alert: VaultCertificateExpiringSoon
        expr: (vault_certificate_expiry_seconds - time()) / 86400 < 30
        for: 1h
        labels:
          severity: warning
        annotations:
          summary: "Vault TLS certificate expiring in {{ $value }} days"
          description: "Vault certificate for {{ $labels.instance }} expires in less than 30 days"

      - alert: VaultCertificateExpired
        expr: (vault_certificate_expiry_seconds - time()) < 0
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "Vault TLS certificate EXPIRED"
          description: "Vault certificate for {{ $labels.instance }} has EXPIRED - immediate action required"
```

Apply:
```bash
kubectl apply -f monitoring/prometheus/rules/vault-certs.yaml
```

## Rollback Procedure

If issues occur, rollback to non-TLS configuration:

### Step 1: Revert to Previous Deployment
```bash
# Scale down TLS-enabled Vault
kubectl scale statefulset vault -n vault-production --replicas=0

# Deploy old configuration (temporary)
kubectl apply -f k8s/vault/vault-deployment-old.yaml

# Wait for pods
kubectl wait --for=condition=ready pod -l app=vault -n vault-production
```

### Step 2: Restore Services
```bash
# Revert service configurations to HTTP
for service in payment-service wallet-service user-service; do
  kubectl set env deployment/${service} \
    SPRING_CLOUD_VAULT_URI=http://vault:8200
done
```

### Step 3: Restore Vault Data
```bash
# Restore from snapshot
kubectl cp ./vault-backup-YYYYMMDD.snap vault-production/vault-0:/tmp/
kubectl exec -it vault-0 -n vault-production -- \
  vault operator raft snapshot restore /tmp/vault-backup-YYYYMMDD.snap
```

## Post-Migration Checklist

- [ ] All 3 Vault pods running and unsealed
- [ ] TLS verification successful from test pod
- [ ] All 50+ services successfully connecting to Vault over HTTPS
- [ ] No authentication errors in service logs
- [ ] Certificate expiry monitoring configured
- [ ] Certificate rotation CronJob created
- [ ] Vault init keys securely stored in AWS Secrets Manager
- [ ] Truststore distributed to all services
- [ ] Documentation updated with new Vault URI
- [ ] Team trained on new TLS configuration

## Troubleshooting

### Issue: "x509: certificate signed by unknown authority"
**Solution**: Ensure vault-ca.jks is mounted in service pods and configured in Spring Cloud Vault

### Issue: "connection refused" when accessing Vault
**Solution**: Verify service can resolve `vault.vault-production.svc.cluster.local`

```bash
kubectl run -it dns-test --image=busybox --rm -- nslookup vault.vault-production.svc.cluster.local
```

### Issue: Vault pods in CrashLoopBackOff
**Solution**: Check logs for TLS certificate issues

```bash
kubectl logs vault-0 -n vault-production
```

Common causes:
- Missing TLS secret
- Incorrect file permissions (should be 0400)
- Invalid certificate format

## Security Best Practices

1. **Certificate Storage**: Store CA private key in AWS Secrets Manager or Hardware Security Module (HSM)
2. **Access Control**: Limit access to `vault-tls` secret to Vault service account only
3. **Rotation**: Rotate server certificates every 90 days (automated via CronJob)
4. **Monitoring**: Set up alerts for certificate expiry (30 days before)
5. **Audit**: Enable Vault audit logs and send to centralized logging (ELK)
6. **Backup**: Regularly backup Vault snapshots to S3 with encryption
7. **mTLS**: Consider enabling mTLS for client authentication (already configured)

## Performance Impact

Expected performance changes after TLS enablement:
- Latency increase: +2-5ms per request (TLS handshake overhead)
- CPU usage increase: +5-10% (encryption/decryption)
- Memory usage: No significant change
- Throughput: Minimal impact (<3% reduction)

**Mitigation**: Connection pooling and session resumption handle performance efficiently.

## Compliance

This TLS implementation satisfies:
- ✅ PCI DSS Requirement 4.1 (Strong cryptography for transmission)
- ✅ NIST 800-52 Rev. 2 (TLS 1.3 minimum)
- ✅ SOC 2 Trust Principle (Encryption in transit)
- ✅ ISO 27001 A.10.1 (Cryptographic controls)

## Next Steps

After successful migration:
1. ✅ Enable Vault audit logging
2. ✅ Configure Vault replication for disaster recovery
3. ✅ Implement Vault namespaces for multi-tenancy
4. ✅ Set up Vault performance replication across regions
5. ✅ Implement Vault Agent for automatic secret injection

## Support

For issues or questions:
- **DevOps Team**: #devops-vault Slack channel
- **Security Team**: security@example.com
- **Emergency**: On-call SRE (PagerDuty)

---

**Migration Completed By**: _________________
**Date**: _________________
**Verified By**: _________________
**Sign-off**: _________________
