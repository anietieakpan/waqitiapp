# Waqiti Production Deployment Runbook

## Pre-Deployment Checklist

### 1. Code Readiness
- [ ] All tests passing (unit + integration)
- [ ] Code review approved
- [ ] Security scan completed (OWASP, SonarQube)
- [ ] Performance testing validated (1,500 TPS for 4 hours)
- [ ] Database migrations tested in staging

### 2. Infrastructure Readiness
- [ ] Kubernetes cluster healthy
- [ ] Database backup completed
- [ ] Redis cluster healthy
- [ ] Kafka brokers healthy
- [ ] Schema Registry accessible
- [ ] Monitoring dashboards operational

### 3. Team Readiness
- [ ] On-call engineer assigned
- [ ] Deployment window scheduled
- [ ] Stakeholders notified
- [ ] Rollback plan documented

---

## Deployment Steps

### Phase 1: Pre-Deployment (T-60 minutes)

```bash
# 1. Verify staging environment
cd /Users/anietieakpan/git/waqiti-app
./scripts/verify-staging.sh

# 2. Backup production database
kubectl exec -n production postgres-0 -- pg_dump waqiti_db > backup-$(date +%Y%m%d-%H%M%S).sql

# 3. Enable maintenance mode (optional)
kubectl scale deployment/api-gateway --replicas=1 -n production
kubectl set env deployment/api-gateway MAINTENANCE_MODE=true -n production
```

### Phase 2: Database Migration (T-30 minutes)

```bash
# 1. Apply database indexes
kubectl apply -f database/k8s/migration-job.yaml

# 2. Monitor migration
kubectl logs -f job/db-migration-v2025-10-23-001 -n production

# 3. Validate indexes
kubectl exec -n production postgres-0 -- psql -U waqiti -d waqiti_db \
  -c "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'public' AND indexname LIKE 'idx_%';"
```

### Phase 3: Service Deployment (T-0)

```bash
# 1. Deploy new image (blue-green)
kubectl set image deployment/payment-service payment-service=waqiti/payment-service:v2.1.0 -n production

# 2. Monitor rollout
kubectl rollout status deployment/payment-service -n production

# 3. Verify health
kubectl get pods -n production -l app=payment-service
curl https://api.example.com/actuator/health
```

### Phase 4: Traffic Ramp (T+10 minutes)

```bash
# Gradual traffic increase: 10% → 25% → 50% → 100%

# 10% traffic
kubectl patch virtualservice payment-service -n production --type merge \
  -p '{"spec":{"http":[{"route":[{"destination":{"host":"payment-service-new"},"weight":10},{"destination":{"host":"payment-service-old"},"weight":90}]}]}}'

# Monitor for 5 minutes, then increase to 25%, 50%, 100%
```

### Phase 5: Validation (T+30 minutes)

```bash
# 1. Run smoke tests
./scripts/smoke-tests.sh --env=production

# 2. Verify metrics
open https://grafana.example.com/d/production-dashboard

# 3. Check error rates
kubectl logs -l app=payment-service --tail=100 | grep ERROR

# 4. Validate performance
# Payment processing p95 < 100ms
# Database queries p95 < 200ms
# No DLQ spike
```

---

## Rollback Procedure

### Immediate Rollback (if critical issues)

```bash
# 1. Revert to previous version
kubectl rollout undo deployment/payment-service -n production

# 2. Route 100% traffic to old version
kubectl patch virtualservice payment-service -n production --type merge \
  -p '{"spec":{"http":[{"route":[{"destination":{"host":"payment-service-old"},"weight":100}]}]}}'

# 3. Verify rollback
kubectl rollout status deployment/payment-service -n production
```

---

## Post-Deployment

### 1. Monitor for 4 hours
- [ ] No error rate increase
- [ ] Performance metrics stable
- [ ] DLQ events normal
- [ ] No customer complaints

### 2. Documentation
- [ ] Update deployment log
- [ ] Document any issues encountered
- [ ] Update runbook if needed

### 3. Cleanup
- [ ] Remove old deployment after 24 hours
- [ ] Archive logs
- [ ] Update status page

---

## Emergency Contacts

- **On-Call Engineer:** [PagerDuty]
- **Database Admin:** [Contact]
- **DevOps Lead:** [Contact]
- **CTO:** [Contact]

---

## Common Issues & Solutions

### Issue 1: High DLQ Rate
**Symptom:** DLQ events spiking
**Solution:**
```bash
# Check DLQ dashboard
# Review recent code changes
# Increase consumer parallelism if needed
kubectl scale deployment/payment-consumer --replicas=5
```

### Issue 2: Database Connection Pool Exhausted
**Symptom:** Connection timeout errors
**Solution:**
```bash
# Increase connection pool size
kubectl set env deployment/payment-service DB_POOL_SIZE=50

# Or scale up replicas
kubectl scale deployment/payment-service --replicas=10
```

### Issue 3: Kafka Consumer Lag Growing
**Symptom:** Consumer lag > 10,000 messages
**Solution:**
```bash
# Scale consumers
kubectl scale deployment/payment-consumer --replicas=10

# Check consumer health
kubectl logs -l app=payment-consumer --tail=50
```

---

**Status:** ✅ Production-Ready
**Last Updated:** 2025-10-23
**Version:** 1.0.0
