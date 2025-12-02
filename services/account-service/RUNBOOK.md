# Account Service Operational Runbook

**Service:** account-service
**Criticality:** Tier 1 (Critical - Revenue Impact)
**On-Call:** PagerDuty - Account Service Team
**Last Updated:** 2025-11-10

---

## Quick Reference

| Metric | Normal | Warning | Critical |
|--------|--------|---------|----------|
| Response Time (p95) | <200ms | 200-500ms | >500ms |
| Error Rate | <0.1% | 0.1-1% | >1% |
| CPU Usage | <50% | 50-70% | >70% |
| Memory Usage | <70% | 70-90% | >90% |
| Database Connections | <15 | 15-19 | 20 (max) |
| Kafka Consumer Lag | <100 | 100-1000 | >1000 |

**SLA:** 99.9% uptime, <200ms p95 latency

---

## Common Scenarios & Resolutions

### 1. Service is Down (P0)

**Symptoms:**
- Health check failing
- 5xx error rate >5%
- No response from service

**Diagnosis:**
```bash
# Check pod status
kubectl get pods -l app=account-service -n production

# Check recent logs
kubectl logs -f deployment/account-service -n production --tail=100

# Check resource usage
kubectl top pods -l app=account-service -n production
```

**Common Causes & Fixes:**

#### Cause A: OOMKilled (Memory Limit)
**Signs:** `kubectl describe pod` shows `OOMKilled`

**Immediate Fix:**
```bash
# Increase memory limit temporarily
kubectl set resources deployment/account-service -n production --limits=memory=2Gi

# Restart deployment
kubectl rollout restart deployment/account-service -n production
```

**Root Cause Investigation:**
- Check for memory leaks in recent deploys
- Review heap dump if available
- Check for connection pool leaks

#### Cause B: Database Connection Pool Exhausted
**Signs:** Logs show `HikariPool - Connection is not available`

**Immediate Fix:**
```bash
# Check active connections
kubectl exec -it deployment/account-service -n production -- curl http://localhost:8070/api/v1/actuator/metrics/hikaricp.connections.active

# If all 20 connections used, restart service
kubectl rollout restart deployment/account-service -n production
```

**Root Cause:** Check for:
- Long-running transactions
- Missing try-with-resources
- Database performance issues

#### Cause C: Cannot Connect to Dependencies
**Signs:** Logs show circuit breaker open, fallback activated

**Check Dependencies:**
```bash
# Check ledger service
curl http://ledger-service.production.svc.cluster.local/actuator/health

# Check compliance service
curl http://compliance-service.production.svc.cluster.local/actuator/health

# Check database
kubectl exec -it deployment/account-service -n production -- pg_isready -h postgres-host -U waqiti
```

**Fix:** Resolve dependency issue or wait for circuit breaker auto-recovery (60s)

---

### 2. High Latency (P1)

**Symptoms:**
- p95 response time >500ms
- User complaints about slow account operations

**Diagnosis:**
```bash
# Check metrics
curl http://account-service/actuator/metrics/http.server.requests | jq '.measurements[] | select(.statistic=="MAX")'

# Check slow queries
kubectl logs deployment/account-service -n production | grep "SlowQuery"

# Check database performance
kubectl exec -it postgres-account-0 -- psql -U waqiti -c "SELECT pid, now() - pg_stat_activity.query_start AS duration, query FROM pg_stat_activity WHERE state = 'active' ORDER BY duration DESC;"
```

**Common Causes:**

#### Cause A: Database Slow Queries
**Fix:**
- Check query plans for missing indexes
- Review recent schema changes
- Check database CPU/memory

#### Cause B: Cache Misses
**Fix:**
```bash
# Check Redis connectivity
kubectl exec -it deployment/account-service -n production -- redis-cli -h redis-host PING

# Check cache hit rate
curl http://account-service/actuator/metrics/cache.gets | jq .
```

#### Cause C: Circuit Breaker Open (Cascading Latency)
**Fix:**
- Identify which service is slow
- Check circuit breaker metrics
- Consider increasing timeout or fallback optimization

---

### 3. Kafka Consumer Lag Growing (P1)

**Symptoms:**
- Consumer lag >1000 messages
- Events not processing in real-time

**Diagnosis:**
```bash
# Check consumer lag
kubectl exec -it kafka-0 -- kafka-consumer-groups.sh \
  --bootstrap-server kafka:9092 \
  --describe \
  --group account-service

# Check consumer logs
kubectl logs deployment/account-service -n production | grep "Kafka.*Error"
```

**Common Causes:**

#### Cause A: Consumer Processing Too Slow
**Signs:** Lag growing steadily

**Fix:**
```bash
# Increase number of replicas temporarily
kubectl scale deployment account-service -n production --replicas=6

# After lag clears
kubectl scale deployment account-service -n production --replicas=3
```

#### Cause B: Consumer Exception Loop
**Signs:** Same message failing repeatedly

**Fix:**
```bash
# Check DLQ
kubectl exec -it kafka-0 -- kafka-console-consumer.sh \
  --bootstrap-server kafka:9092 \
  --topic account-events-dlq \
  --from-beginning \
  --max-messages 10

# If poison pill message, skip offset manually (last resort)
# Or fix consumer code and redeploy
```

#### Cause C: Kafka Broker Issues
**Signs:** All consumers lagging

**Fix:** Escalate to platform team to check Kafka cluster health

---

### 4. High Error Rate (P1)

**Symptoms:**
- Error rate >1%
- Increased 4xx/5xx responses

**Diagnosis:**
```bash
# Check error breakdown
kubectl logs deployment/account-service -n production | grep "ERROR" | tail -50

# Check specific error types
curl http://account-service/actuator/metrics/http.server.requests | jq '.tags[] | select(.tag=="status")'
```

**Common Error Types:**

#### Error A: 401 Unauthorized (Auth Issues)
**Cause:** Keycloak token validation failing

**Fix:**
```bash
# Check Keycloak connectivity
curl https://keycloak.example.com/realms/waqiti/.well-known/openid-configuration

# Check client secret is correct
kubectl get secret account-service-secrets -n production -o jsonpath='{.data.KEYCLOAK_CLIENT_SECRET}' | base64 -d
```

#### Error B: 500 Internal Server Error
**Cause:** Application exceptions

**Fix:**
1. Check logs for stack traces
2. Identify root cause
3. If recent deploy, consider rollback:
   ```bash
   kubectl rollout undo deployment/account-service -n production
   ```

#### Error C: 503 Service Unavailable
**Cause:** Circuit breaker open for dependencies

**Fix:** Check dependency health and wait for auto-recovery

---

### 5. Database Connection Failures (P0)

**Symptoms:**
- `Connection refused` or `Connection timeout` errors
- Service cannot start

**Diagnosis:**
```bash
# Check database pod
kubectl get pods -l app=postgresql -n production

# Test connection
kubectl exec -it deployment/account-service -n production -- \
  psql postgresql://waqiti:PASSWORD@postgres-host:5432/waqiti_accounts -c "SELECT 1"
```

**Fixes:**

#### Cause A: Database Pod Down
```bash
kubectl get pods -l app=postgresql -n production
# If not running, check events
kubectl describe pod postgresql-0 -n production
```

#### Cause B: Connection Pool Exhausted
```bash
# Restart service to reset pool
kubectl rollout restart deployment/account-service -n production
```

#### Cause C: Wrong Credentials
```bash
# Verify database secret
kubectl get secret postgres-credentials -n production -o yaml
```

---

### 6. Circuit Breaker Open (Warning)

**Symptoms:**
- Logs show `FALLBACK ACTIVATED`
- Degraded functionality

**Diagnosis:**
```bash
# Check which service has open circuit breaker
kubectl logs deployment/account-service -n production | grep "FALLBACK ACTIVATED" | head -20
```

**Response:**
1. Identify affected dependency (ledger, compliance, user, notification)
2. Check dependency health
3. Wait for auto-recovery (default 60s)
4. If persists >5 minutes, escalate to dependency team

**Note:** Fallback behavior is intentional for graceful degradation. Only escalate if:
- Critical operations blocked (e.g., cannot create accounts)
- Circuit breaker open >5 minutes
- Multiple dependencies affected

---

### 7. DLQ Processing Issues (P2)

**Symptoms:**
- DLQ topics growing
- Slack alerts for failed events

**Diagnosis:**
```bash
# Check DLQ size
kubectl exec -it kafka-0 -- kafka-run-class.sh kafka.tools.GetOffsetShell \
  --broker-list kafka:9092 \
  --topic account-events-dlq

# Sample DLQ messages
kubectl exec -it kafka-0 -- kafka-console-consumer.sh \
  --bootstrap-server kafka:9092 \
  --topic account-events-dlq \
  --max-messages 5
```

**Response:**
1. Review DLQ handler logs for error patterns
2. If systematic issue (e.g., schema change), fix and redeploy
3. If isolated failures, review and manually process if needed

**Manual DLQ Message Processing:**
See `DLQ_HANDLER_TEMPLATE.java` for implementation details.

---

## Deployment Procedures

### Standard Deployment

```bash
# 1. Pull latest image
docker pull waqiti/account-service:1.0.1

# 2. Update deployment
kubectl set image deployment/account-service account-service=waqiti/account-service:1.0.1 -n production

# 3. Monitor rollout
kubectl rollout status deployment/account-service -n production

# 4. Verify health
curl https://api.example.com/account-service/actuator/health

# 5. Check metrics for anomalies
# Monitor for 15 minutes before considering deploy successful
```

### Emergency Rollback

```bash
# Rollback to previous version
kubectl rollout undo deployment/account-service -n production

# Verify rollback
kubectl rollout status deployment/account-service -n production

# Check health
curl https://api.example.com/account-service/actuator/health

# Notify team
# Post in #incidents Slack channel
```

### Database Migration

**CRITICAL:** Always test migrations on staging first!

```bash
# 1. Backup database
kubectl exec -it postgres-account-0 -- pg_dump -U waqiti waqiti_accounts > backup-$(date +%Y%m%d).sql

# 2. Deploy service with migration
kubectl apply -f k8s/production/account-service-deployment.yaml

# 3. Monitor migration logs
kubectl logs -f deployment/account-service -n production | grep "Liquibase"

# 4. Verify migration success
kubectl exec -it postgres-account-0 -- psql -U waqiti -d waqiti_accounts -c "SELECT * FROM databasechangelog ORDER BY dateexecuted DESC LIMIT 5;"
```

**If migration fails:**
```bash
# 1. Rollback deployment
kubectl rollout undo deployment/account-service -n production

# 2. Restore database from backup (if data corruption)
kubectl exec -i postgres-account-0 -- psql -U waqiti waqiti_accounts < backup-YYYYMMDD.sql

# 3. Investigate and fix migration script
```

---

## Monitoring & Alerts

### Critical Dashboards

1. **Grafana - Account Service Overview**
   - URL: https://grafana.example.com/d/account-service
   - Metrics: Response time, error rate, throughput, resource usage

2. **Grafana - Account Service Database**
   - URL: https://grafana.example.com/d/account-service-db
   - Metrics: Connection pool, query performance, transaction rate

3. **Prometheus - Account Service Alerts**
   - URL: https://prometheus.example.com/alerts?search=account-service
   - Active alerts and firing rules

### Alert Thresholds

**P0 Alerts (PagerDuty):**
- Service down (health check fails)
- Error rate >5%
- Database connection failures
- Memory usage >95%

**P1 Alerts (Slack):**
- High latency (p95 >500ms)
- Error rate >1%
- Kafka consumer lag >1000
- Circuit breaker open >5 minutes

**P2 Alerts (Slack):**
- DLQ message count growing
- Cache hit rate <50%
- CPU usage >70%

### Key Metrics to Monitor

```bash
# Response time
curl http://account-service/actuator/metrics/http.server.requests

# Error rate
curl http://account-service/actuator/metrics/http.server.requests | jq '.measurements[] | select(.statistic=="COUNT")'

# JVM memory
curl http://account-service/actuator/metrics/jvm.memory.used

# Database connections
curl http://account-service/actuator/metrics/hikaricp.connections.active

# Kafka lag
kubectl exec kafka-0 -- kafka-consumer-groups.sh --bootstrap-server kafka:9092 --describe --group account-service
```

---

## Escalation

### Level 1: On-Call Engineer
**Response Time:** 15 minutes
**Responsibilities:**
- Respond to P0/P1 alerts
- Perform initial diagnosis
- Apply standard fixes from this runbook
- Escalate if unable to resolve in 30 minutes

### Level 2: Senior Engineer
**Contact:** Via PagerDuty escalation
**Responsibilities:**
- Deep diagnosis of complex issues
- Code-level investigation
- Database performance tuning
- Decide on rollback vs. hotfix

### Level 3: Platform/Database Team
**Contact:** Via #platform or #database Slack channels
**Responsibilities:**
- Infrastructure issues (K8s, networking)
- Database cluster issues
- Kafka cluster issues
- Cross-service coordination

### Level 4: Engineering Director
**Contact:** Via PagerDuty for prolonged outages
**When:**
- Outage >1 hour
- Data integrity concerns
- Multiple services affected

---

## Post-Incident

### Incident Report Template

**Incident:** [Brief title]
**Date:** YYYY-MM-DD
**Duration:** X hours
**Severity:** P0/P1/P2

**Timeline:**
- HH:MM - Alert fired
- HH:MM - Engineer responded
- HH:MM - Root cause identified
- HH:MM - Fix applied
- HH:MM - Service recovered

**Root Cause:**
[Detailed explanation]

**Impact:**
- Users affected: X
- Revenue impact: $X
- SLA breach: Yes/No

**Resolution:**
[What was done to fix]

**Action Items:**
1. [ ] Update runbook with new scenario
2. [ ] Add monitoring for early detection
3. [ ] Code fix to prevent recurrence
4. [ ] Load test to verify fix

---

## Useful Commands Cheat Sheet

```bash
# Get pod logs
kubectl logs -f deployment/account-service -n production

# Get previous pod logs (after crash)
kubectl logs deployment/account-service -n production --previous

# Execute command in pod
kubectl exec -it deployment/account-service -n production -- bash

# Port forward for local access
kubectl port-forward deployment/account-service 8070:8070 -n production

# Scale replicas
kubectl scale deployment account-service -n production --replicas=5

# Restart deployment (zero downtime)
kubectl rollout restart deployment/account-service -n production

# Check deployment history
kubectl rollout history deployment/account-service -n production

# Rollback deployment
kubectl rollout undo deployment/account-service -n production

# Check resource usage
kubectl top pods -l app=account-service -n production

# Check events
kubectl get events -n production --sort-by='.lastTimestamp' | grep account-service

# Check service endpoints
kubectl get endpoints account-service -n production

# Check configmap
kubectl get configmap account-service-config -n production -o yaml

# Check secrets
kubectl get secret account-service-secrets -n production -o jsonpath='{.data}' | jq .
```

---

## Contact Information

**Team:** Account Service Team
**Slack:** #account-service
**PagerDuty:** https://waqiti.pagerduty.com/services/PXXXXXX
**Runbook Repo:** https://github.com/waqiti/waqiti-app/tree/main/services/account-service
**Documentation:** https://docs.example.com/services/account-service

**On-Call Schedule:** See PagerDuty

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 1.0.0 | 2025-11-10 | Initial runbook creation | Engineering Team |

---

**Last Reviewed:** 2025-11-10
**Next Review:** 2026-01-10 (Quarterly)
