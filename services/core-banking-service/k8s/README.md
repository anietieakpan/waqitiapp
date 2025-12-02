# Core Banking Service - Kubernetes Deployment

## Production-Ready Kubernetes Manifests

This directory contains production-grade Kubernetes manifests for deploying the core-banking-service.

## Files

- `deployment.yaml` - Main Deployment, Service, and HorizontalPodAutoscaler
- `configmap.yaml` - Application configuration (non-sensitive)
- `secrets.yaml` - Secrets template (REPLACE BEFORE DEPLOYMENT)
- `serviceaccount.yaml` - ServiceAccount and RBAC
- `README.md` - This file

## Prerequisites

1. **Kubernetes Cluster**: v1.24+
2. **kubectl**: Configured with cluster access
3. **Namespace**: `waqiti-prod` namespace created
4. **Dependencies Running**:
   - PostgreSQL database (with `waqiti_core_banking` database)
   - Kafka cluster (3+ brokers)
   - Redis cache
   - Eureka discovery service
   - Keycloak authentication

## Deployment Instructions

### 1. Create Namespace (if not exists)

```bash
kubectl create namespace waqiti-prod
```

### 2. Create Secrets

**CRITICAL: Replace template values with actual secrets**

```bash
kubectl create secret generic core-banking-secrets \
  --from-literal=db.username='actual_db_username' \
  --from-literal=db.password='actual_db_password' \
  --from-literal=redis.password='actual_redis_password' \
  --from-literal=keycloak.client.secret='actual_keycloak_secret' \
  --from-literal=exchange.api.key='actual_exchange_api_key' \
  --from-literal=vault.token='actual_vault_token' \
  --from-literal=encryption.key.pii='actual_pii_encryption_key' \
  --from-literal=encryption.key.financial='actual_financial_encryption_key' \
  --namespace=waqiti-prod
```

### 3. Apply Configuration

```bash
# Apply in order
kubectl apply -f serviceaccount.yaml
kubectl apply -f configmap.yaml
kubectl apply -f deployment.yaml
```

### 4. Verify Deployment

```bash
# Check deployment status
kubectl get deployments -n waqiti-prod
kubectl get pods -n waqiti-prod -l app=core-banking-service

# Check service
kubectl get svc core-banking-service -n waqiti-prod

# Check HPA
kubectl get hpa core-banking-hpa -n waqiti-prod

# View logs
kubectl logs -f deployment/core-banking-service -n waqiti-prod
```

### 5. Health Checks

```bash
# Port-forward to test locally
kubectl port-forward svc/core-banking-service 8088:8088 -n waqiti-prod

# Test health endpoint
curl http://localhost:8088/actuator/health

# Test readiness
curl http://localhost:8088/actuator/health/readiness

# Test liveness
curl http://localhost:8088/actuator/health/liveness
```

## Configuration

### Resource Limits

**Current Settings:**
- Requests: 1Gi memory, 500m CPU
- Limits: 2Gi memory, 2000m CPU

**Tuning Recommendations:**
- Monitor actual usage via Prometheus
- Adjust based on load testing results
- Consider 4Gi memory for high-transaction environments

### Autoscaling

**HPA Configuration:**
- Min replicas: 3 (high availability)
- Max replicas: 10 (cost control)
- CPU target: 70%
- Memory target: 80%

**Scale-up:**
- Fast (100% increase every 30s or +2 pods)
- Stabilization: 60s

**Scale-down:**
- Conservative (50% decrease every 60s)
- Stabilization: 300s (5 minutes)

### Rolling Updates

**Strategy:**
- Type: RollingUpdate
- MaxSurge: 1 (one extra pod during update)
- MaxUnavailable: 0 (zero downtime deployment)

## Monitoring

### Prometheus Metrics

Service exposes metrics at:
```
http://<service-ip>:8088/actuator/prometheus
```

Prometheus scraping configured via annotations:
```yaml
prometheus.io/scrape: "true"
prometheus.io/port: "8088"
prometheus.io/path: "/actuator/prometheus"
```

### Key Metrics to Monitor

1. **Transaction Processing**:
   - `core_banking_transactions_total`
   - `core_banking_transaction_processing_duration_seconds`
   - `core_banking_transaction_reversal_total`

2. **Account Operations**:
   - `core_banking_account_balance_updates_total`
   - `core_banking_fund_reservations_active`

3. **Compliance**:
   - `core_banking_compliance_checks_total`
   - `core_banking_compliance_failures_total`

4. **JVM**:
   - `jvm_memory_used_bytes`
   - `jvm_gc_pause_seconds`
   - `jvm_threads_live`

5. **HTTP**:
   - `http_server_requests_seconds`
   - `http_server_requests_active`

## Troubleshooting

### Pod Not Starting

```bash
# Check pod events
kubectl describe pod <pod-name> -n waqiti-prod

# Check logs
kubectl logs <pod-name> -n waqiti-prod

# Check if secrets exist
kubectl get secrets core-banking-secrets -n waqiti-prod
```

### Database Connection Failures

```bash
# Verify database connectivity from pod
kubectl exec -it <pod-name> -n waqiti-prod -- /bin/sh
# Inside pod:
nc -zv postgres-primary.database.svc.cluster.local 5432
```

### High Memory Usage

```bash
# Check current memory usage
kubectl top pods -n waqiti-prod -l app=core-banking-service

# Heap dump (if JVM heap issues)
kubectl exec <pod-name> -n waqiti-prod -- jmap -dump:format=b,file=/tmp/heapdump.hprof 1

# Copy heap dump locally
kubectl cp waqiti-prod/<pod-name>:/tmp/heapdump.hprof ./heapdump.hprof
```

### Circuit Breaker Open

Check logs for:
```
WARN  CircuitBreaker 'ledger-service' is OPEN
```

Verify dependent services:
```bash
kubectl get pods -n waqiti-prod
kubectl logs deployment/ledger-service -n waqiti-prod --tail=100
```

## Security

### Network Policies

**TODO: Add NetworkPolicy for**:
- Allow ingress only from API Gateway
- Allow egress to PostgreSQL, Kafka, Redis
- Deny all other traffic

### Pod Security

**Current Settings:**
- Run as non-root user (UID 1000)
- Read-only root filesystem (emptyDir for logs/tmp)
- No privilege escalation
- Drop all capabilities

### Secret Management

**Production Recommendations:**
1. Use External Secrets Operator with Vault/AWS/Azure
2. Enable secret encryption at rest (etcd)
3. Rotate secrets every 90 days
4. Use separate secrets per environment

## Scaling Recommendations

### Horizontal Scaling (HPA)

**Low Traffic** (<100 TPS):
- Min: 3 replicas
- Max: 5 replicas

**Medium Traffic** (100-500 TPS):
- Min: 5 replicas
- Max: 10 replicas

**High Traffic** (500-1000 TPS):
- Min: 10 replicas
- Max: 20 replicas

### Vertical Scaling (Resources)

**Low Traffic**:
- Memory: 1Gi-2Gi
- CPU: 500m-1000m

**Medium Traffic**:
- Memory: 2Gi-4Gi
- CPU: 1000m-2000m

**High Traffic**:
- Memory: 4Gi-8Gi
- CPU: 2000m-4000m

## Disaster Recovery

### Backup Strategy

1. **Database**: Automated daily backups with 30-day retention
2. **ConfigMaps/Secrets**: Version controlled in GitOps repo
3. **Kafka**: Replication factor 3, min in-sync replicas 2

### Rollback Procedure

```bash
# Rollback to previous deployment
kubectl rollout undo deployment/core-banking-service -n waqiti-prod

# Rollback to specific revision
kubectl rollout undo deployment/core-banking-service -n waqiti-prod --to-revision=2

# Check rollout history
kubectl rollout history deployment/core-banking-service -n waqiti-prod
```

## Production Checklist

Before going live:

- [ ] Replace all secret template values
- [ ] Run database migrations (V1-V103)
- [ ] Verify Keycloak realm and client configuration
- [ ] Configure network policies
- [ ] Set up Prometheus alerts
- [ ] Configure log aggregation (ELK/Loki)
- [ ] Load test to 2x expected peak traffic
- [ ] Disaster recovery drill
- [ ] Document runbooks for common issues
- [ ] Security scan all container images
- [ ] Enable Pod Security Standards (restricted)

## Support

For issues or questions, contact:
- Platform Team: platform@example.com
- On-Call: Use PagerDuty rotation

---

**Last Updated:** 2025-11-19
**Maintained By:** Core Banking Team
