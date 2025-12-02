# Config Service Operational Runbook

**Service**: Config Service
**Team**: Platform Infrastructure
**On-Call Escalation**: platform-oncall@example.com
**Last Updated**: 2025-01-20

## Table of Contents
1. [Service Overview](#service-overview)
2. [Architecture](#architecture)
3. [Health Checks & Monitoring](#health-checks--monitoring)
4. [Common Issues & Solutions](#common-issues--solutions)
5. [Emergency Procedures](#emergency-procedures)
6. [Deployment](#deployment)
7. [Rollback Procedures](#rollback-procedures)
8. [Database Operations](#database-operations)
9. [Troubleshooting Guide](#troubleshooting-guide)

---

## Service Overview

### Purpose
Centralized configuration management and feature flag service for the Waqiti fintech platform. Manages dynamic configuration, feature toggles, and environment-specific settings for all microservices.

### Critical Dependencies
- **PostgreSQL**: Configuration storage (CRITICAL)
- **Kafka**: Configuration change events (HIGH)
- **Vault**: Secrets and encryption keys (CRITICAL)
- **Eureka**: Service discovery (HIGH)
- **Keycloak**: Authentication/Authorization (CRITICAL)
- **Notification Service**: Change notifications (LOW)

### SLA
- **Availability**: 99.95% (< 4.38 hours downtime/year)
- **Response Time**: p95 < 200ms, p99 < 500ms
- **Error Rate**: < 0.1%

---

## Architecture

### Endpoints
- **Health**: `GET /actuator/health`
- **Metrics**: `GET /actuator/prometheus`
- **Configuration API**: `/api/v1/config/*`
- **Feature Flags API**: `/api/v1/config/feature-flags/*`

### Resource Requirements
- **CPU**: 250m (request), 1000m (limit)
- **Memory**: 512Mi (request), 1Gi (limit)
- **Replicas**: 3 (min), 10 (max via HPA)

---

## Health Checks & Monitoring

### Health Check Endpoints
```bash
# Overall health
curl https://config-service.example.com/actuator/health

# Liveness (Kubernetes uses this)
curl https://config-service.example.com/actuator/health/liveness

# Readiness (Kubernetes uses this)
curl https://config-service.example.com/actuator/health/readiness
```

### Key Metrics to Monitor

| Metric | Alert Threshold | Description |
|--------|----------------|-------------|
| `config_read_duration` | p95 > 200ms | Configuration read latency |
| `config_write_duration` | p95 > 500ms | Configuration write latency |
| `config_cache_hit_rate` | < 80% | Cache hit rate dropping |
| `config_encryption_error` | > 0 | Encryption failures |
| `config_decryption_error` | > 0 | Decryption failures |
| `http_server_requests_total` (5xx) | > 1% | Server error rate |
| `jvm_memory_used_bytes` / `jvm_memory_max_bytes` | > 85% | Memory usage |

### Grafana Dashboards
- **Main Dashboard**: https://grafana.example.com/d/config-service
- **JVM Dashboard**: https://grafana.example.com/d/config-service-jvm

### Logs
```bash
# View logs
kubectl logs -f deployment/config-service -n waqiti

# Search for errors
kubectl logs deployment/config-service -n waqiti | grep ERROR

# Follow logs from all pods
kubectl logs -f -l app=config-service -n waqiti --tail=100
```

---

## Common Issues & Solutions

### Issue 1: Service Health Check Failing

**Symptoms**:
- Kubernetes killing pods
- `/actuator/health` returning DOWN
- Pods in CrashLoopBackOff

**Diagnosis**:
```bash
# Check health details
kubectl exec -it deployment/config-service -n waqiti -- \
  curl localhost:8888/actuator/health | jq

# Check which dependency is failing
kubectl logs deployment/config-service -n waqiti | grep "Health check failed"
```

**Solutions**:

1. **Database Connection Failure**:
   ```bash
   # Verify database connectivity
   kubectl run -it --rm debug --image=postgres:15 --restart=Never -- \
     psql -h postgres.waqiti.svc.cluster.local -U config_user -d configdb

   # Check database secret
   kubectl get secret config-service-secrets -n waqiti -o yaml
   ```

2. **Vault Connection Failure**:
   ```bash
   # Check Vault status
   kubectl exec -it vault-0 -n waqiti -- vault status

   # Verify Vault token
   kubectl get secret config-service-secrets -n waqiti -o jsonpath='{.data.vault-token}' | base64 -d
   ```

3. **Kafka Connection Failure**:
   ```bash
   # Check Kafka brokers
   kubectl get pods -n waqiti | grep kafka

   # Test Kafka connectivity
   kubectl exec -it kafka-0 -n waqiti -- kafka-broker-api-versions.sh \
     --bootstrap-server localhost:9092
   ```

### Issue 2: High Response Latency

**Symptoms**:
- API requests taking > 500ms
- p95 latency exceeding threshold

**Diagnosis**:
```bash
# Check metrics
curl https://config-service.example.com/actuator/prometheus | grep config_read_duration

# Check database query performance
kubectl logs deployment/config-service -n waqiti | grep "Slow query"
```

**Solutions**:

1. **Database Slow Queries**:
   ```sql
   -- Connect to database and check slow queries
   SELECT * FROM pg_stat_statements ORDER BY mean_time DESC LIMIT 10;
   ```

2. **Cache Miss Rate High**:
   ```bash
   # Check cache statistics
   curl https://config-service.example.com/actuator/metrics/config.cache.hit.rate

   # Increase cache size if needed (edit configmap)
   kubectl edit configmap config-service-config -n waqiti
   ```

3. **Vault Latency**:
   ```bash
   # Check encryption service health
   curl https://config-service.example.com/actuator/health | jq .components.configService.details.encryption
   ```

### Issue 3: Out of Memory (OOM)

**Symptoms**:
- Pods being killed by Kubernetes
- `OOMKilled` in pod status
- Memory usage > 90%

**Diagnosis**:
```bash
# Check memory usage
kubectl top pods -n waqiti | grep config-service

# Get heap dump (if pod is running)
kubectl exec deployment/config-service -n waqiti -- \
  jmap -dump:format=b,file=/tmp/heapdump.hprof 1
```

**Solutions**:

1. **Memory Leak**:
   ```bash
   # Restart affected pods
   kubectl rollout restart deployment/config-service -n waqiti

   # Increase memory limits if genuine usage
   kubectl edit deployment config-service -n waqiti
   # Update: resources.limits.memory: "2Gi"
   ```

2. **Cache Size Too Large**:
   ```yaml
   # Edit application config
   config:
     cache:
       max-size: 5000  # Reduce from 10000
   ```

### Issue 4: Encryption/Decryption Failures

**Symptoms**:
- Configuration values not decrypting
- `config.decryption.error` metric > 0
- Errors in logs: "Failed to decrypt"

**Diagnosis**:
```bash
# Check encryption health
curl https://config-service.example.com/actuator/health | \
  jq .components.configService.details.encryption

# Verify Vault key exists
kubectl exec -it vault-0 -n waqiti -- \
  vault kv get secret/config
```

**Solutions**:

1. **Vault Key Missing**:
   ```bash
   # Regenerate encryption key
   ./ssl-setup.sh  # Run key generation script
   ```

2. **Vault Token Expired**:
   ```bash
   # Rotate Vault token
   kubectl delete secret config-service-secrets -n waqiti
   kubectl create secret generic config-service-secrets \
     --from-literal=vault-token='NEW_TOKEN' \
     -n waqiti

   # Restart pods
   kubectl rollout restart deployment/config-service -n waqiti
   ```

---

## Emergency Procedures

### Emergency Contacts
- **On-Call Engineer**: platform-oncall@example.com (PagerDuty)
- **Platform Lead**: platform-lead@example.com
- **CTO**: cto@example.com

### Emergency Rollback
```bash
# 1. Identify last known good version
kubectl rollout history deployment/config-service -n waqiti

# 2. Rollback to previous version
kubectl rollout undo deployment/config-service -n waqiti

# 3. Verify rollback
kubectl rollout status deployment/config-service -n waqiti

# 4. Check health
kubectl get pods -n waqiti | grep config-service
```

### Emergency Scale Down
```bash
# If service is overwhelmed, temporarily reduce load
kubectl scale deployment config-service --replicas=1 -n waqiti
```

### Circuit Breaker Manual Trigger
```bash
# If notification service is causing cascading failures
kubectl set env deployment/config-service \
  NOTIFICATIONS_ENABLED=false -n waqiti
```

---

## Deployment

### Pre-Deployment Checklist
- [ ] Database migrations tested
- [ ] Configuration changes reviewed
- [ ] Secrets updated (if needed)
- [ ] Smoke tests passing
- [ ] Rollback plan documented

### Standard Deployment
```bash
# 1. Update image
kubectl set image deployment/config-service \
  config-service=waqiti/config-service:v1.2.3 -n waqiti

# 2. Watch rollout
kubectl rollout status deployment/config-service -n waqiti

# 3. Verify health
kubectl get pods -n waqiti | grep config-service
curl https://config-service.example.com/actuator/health
```

### Blue-Green Deployment
```bash
# 1. Deploy new version (green)
kubectl apply -f k8s/deployment-green.yaml

# 2. Test green deployment
kubectl port-forward svc/config-service-green 8888:8888 -n waqiti
curl localhost:8888/actuator/health

# 3. Switch traffic
kubectl patch service config-service -n waqiti \
  -p '{"spec":{"selector":{"version":"green"}}}'

# 4. Monitor for issues (keep blue running)
# 5. Decommission blue after 24 hours
```

---

## Rollback Procedures

### Automatic Rollback (Kubernetes)
Kubernetes will automatically rollback if:
- Liveness probes fail
- Readiness probes don't pass within timeout
- Pods crash repeatedly

### Manual Rollback
```bash
# 1. Check deployment history
kubectl rollout history deployment/config-service -n waqiti

# 2. Rollback to specific revision
kubectl rollout undo deployment/config-service -n waqiti --to-revision=5

# 3. Verify
kubectl rollout status deployment/config-service -n waqiti
```

### Database Rollback
```bash
# 1. Connect to database
kubectl run -it --rm psql --image=postgres:15 --restart=Never -- \
  psql -h postgres.waqiti.svc.cluster.local -U config_user -d configdb

# 2. Check Flyway history
SELECT * FROM flyway_schema_history ORDER BY installed_on DESC;

# 3. Rollback migration (if available)
# Note: Manual rollback may be required depending on migration
```

---

## Database Operations

### Backup
```bash
# Manual backup
kubectl exec -it postgres-0 -n waqiti -- \
  pg_dump -U config_user configdb > configdb-backup-$(date +%Y%m%d).sql
```

### Restore
```bash
# Restore from backup
kubectl exec -i postgres-0 -n waqiti -- \
  psql -U config_user configdb < configdb-backup-20250120.sql
```

### Check Connection Pool
```sql
SELECT count(*) as connections,
       state
FROM pg_stat_activity
WHERE datname = 'configdb'
GROUP BY state;
```

---

## Troubleshooting Guide

### Pod Won't Start
```bash
# Check pod events
kubectl describe pod <pod-name> -n waqiti

# Check init containers
kubectl logs <pod-name> -c init-container-name -n waqiti

# Check startup logs
kubectl logs <pod-name> -n waqiti --previous
```

### High CPU Usage
```bash
# Get thread dump
kubectl exec deployment/config-service -n waqiti -- \
  jstack 1 > thread-dump.txt

# Profile with async-profiler (if available)
kubectl exec deployment/config-service -n waqiti -- \
  /opt/async-profiler/profiler.sh -d 30 -f /tmp/profile.html 1
```

### Network Issues
```bash
# Test service connectivity
kubectl run -it --rm debug --image=curlimages/curl --restart=Never -- \
  curl -v http://config-service.waqiti.svc.cluster.local:8888/actuator/health

# Check service endpoints
kubectl get endpoints config-service -n waqiti

# Check network policies
kubectl get networkpolicies -n waqiti
```

---

## Related Documentation
- [Architecture Documentation](ARCHITECTURE.md)
- [API Documentation](API_DOCUMENTATION.md)
- [Disaster Recovery Plan](DISASTER_RECOVERY.md)
- [Security Guidelines](SECURITY.md)

---

## Revision History
| Date | Author | Changes |
|------|--------|---------|
| 2025-01-20 | Platform Team | Initial version |
