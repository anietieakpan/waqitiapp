# 📘 Waqiti Platform Production Runbook

## Quick Reference
**Platform Status Dashboard**: https://status.example.com  
**Ops Channel**: #waqiti-ops-emergency  
**PagerDuty**: https://waqiti.pagerduty.com  
**War Room Bridge**: +1-800-WAQITI-911  

---

## 🚨 CRITICAL INCIDENT RESPONSE

### Severity Levels
- **SEV1**: Complete service outage, data loss risk, security breach
- **SEV2**: Major feature unavailable, significant performance degradation
- **SEV3**: Minor feature issue, non-critical service degraded
- **SEV4**: Cosmetic issue, documentation error

### SEV1 Response (< 5 minutes)
```bash
#!/bin/bash
# SEV1 Emergency Response Script

# 1. Assess scope
kubectl get pods -n waqiti-production | grep -v Running

# 2. Check critical services
curl -s https://api.example.com/health/critical | jq .

# 3. Enable emergency mode (stops non-critical operations)
kubectl apply -f /emergency/circuit-breaker-open.yaml

# 4. Page on-call team
./scripts/page-oncall.sh SEV1 "Production outage detected"

# 5. Open war room
./scripts/open-warroom.sh --severity=1 --services=all
```

---

## 🔥 Common Issues and Solutions

### 1. Payment Service Down
**Symptoms**: 
- Payment APIs returning 503
- `payment-service` pods in CrashLoopBackOff

**Diagnosis**:
```bash
# Check pod logs
kubectl logs -n waqiti-production deployment/payment-service --tail=100

# Check database connectivity
kubectl exec -n waqiti-production deployment/payment-service -- \
  pg_isready -h postgres-payment -p 5432

# Check Kafka connectivity
kubectl exec -n waqiti-production deployment/payment-service -- \
  kafka-broker-api-versions --bootstrap-server=kafka:9092
```

**Resolution**:
```bash
# Option 1: Restart pods
kubectl rollout restart deployment/payment-service -n waqiti-production

# Option 2: Rollback to previous version
kubectl rollout undo deployment/payment-service -n waqiti-production

# Option 3: Scale horizontally if load issue
kubectl scale deployment/payment-service --replicas=10 -n waqiti-production

# Option 4: Enable read-only mode
kubectl set env deployment/payment-service READ_ONLY_MODE=true -n waqiti-production
```

### 2. Database Connection Pool Exhausted
**Symptoms**:
- `HikariPool-1 - Connection is not available, request timed out`
- Slow API responses

**Diagnosis**:
```sql
-- Check active connections
SELECT count(*) FROM pg_stat_activity 
WHERE datname = 'waqiti_payments' AND state = 'active';

-- Check long-running queries
SELECT pid, now() - pg_stat_activity.query_start AS duration, query
FROM pg_stat_activity
WHERE (now() - pg_stat_activity.query_start) > interval '5 minutes';
```

**Resolution**:
```bash
# Increase pool size temporarily
kubectl set env deployment/payment-service \
  DB_POOL_MAX_SIZE=200 \
  DB_POOL_MIN_IDLE=50 \
  -n waqiti-production

# Kill long-running queries
psql -h postgres-payment -U postgres -c \
  "SELECT pg_terminate_backend(pid) FROM pg_stat_activity 
   WHERE datname = 'waqiti_payments' AND state = 'active' 
   AND (now() - pg_stat_activity.query_start) > interval '10 minutes';"

# Restart connection pool
kubectl exec -n waqiti-production deployment/payment-service -- \
  curl -X POST http://localhost:8080/actuator/refresh
```

### 3. Kafka Consumer Lag
**Symptoms**:
- Delayed payment processing
- Growing consumer lag metrics

**Diagnosis**:
```bash
# Check consumer lag
kafka-consumer-groups --bootstrap-server kafka:9092 \
  --group payment-service-consumer --describe

# Check topic partition status
kafka-topics --bootstrap-server kafka:9092 \
  --topic payment-events --describe
```

**Resolution**:
```bash
# Scale up consumers
kubectl scale deployment/payment-service --replicas=15 -n waqiti-production

# Reset consumer offset (CAUTION: may reprocess messages)
kafka-consumer-groups --bootstrap-server kafka:9092 \
  --group payment-service-consumer \
  --topic payment-events \
  --reset-offsets --to-latest --execute

# Increase partition count for better parallelism
kafka-topics --bootstrap-server kafka:9092 \
  --topic payment-events \
  --alter --partitions 50
```

### 4. High Fraud False Positive Rate
**Symptoms**:
- Legitimate transactions being blocked
- Customer complaints increasing

**Diagnosis**:
```bash
# Check fraud service metrics
curl -s http://fraud-service:8080/actuator/metrics/fraud.false.positive.rate | jq .

# Review recent ML model performance
curl -s http://ml-service:8080/api/model/metrics | jq '.models.fraud'
```

**Resolution**:
```bash
# Temporarily adjust fraud threshold
kubectl set env deployment/fraud-service \
  FRAUD_THRESHOLD=0.85 \
  -n waqiti-production

# Rollback to previous ML model
curl -X POST http://ml-service:8080/api/model/rollback \
  -H "Content-Type: application/json" \
  -d '{"model": "fraud-detection", "version": "v1.2.3"}'

# Enable manual review mode
kubectl set env deployment/fraud-service \
  MANUAL_REVIEW_MODE=true \
  MANUAL_REVIEW_THRESHOLD=0.7 \
  -n waqiti-production
```

### 5. Memory Leak / OOM Issues
**Symptoms**:
- Pods getting OOMKilled
- Gradual performance degradation

**Diagnosis**:
```bash
# Check memory usage
kubectl top pods -n waqiti-production | grep payment-service

# Get heap dump
kubectl exec -n waqiti-production deployment/payment-service -- \
  jcmd 1 GC.heap_dump /tmp/heapdump.hprof

# Copy heap dump for analysis
kubectl cp waqiti-production/payment-service-xxx:/tmp/heapdump.hprof ./heapdump.hprof
```

**Resolution**:
```bash
# Increase memory limits temporarily
kubectl set resources deployment/payment-service \
  --limits=memory=4Gi \
  --requests=memory=2Gi \
  -n waqiti-production

# Force garbage collection
kubectl exec -n waqiti-production deployment/payment-service -- \
  jcmd 1 GC.run

# Rolling restart to clear memory
kubectl rollout restart deployment/payment-service -n waqiti-production
```

---

## 📊 Monitoring & Alerts

### Critical Metrics to Watch
```bash
# Payment success rate (should be > 99.5%)
curl -s http://prometheus:9090/api/v1/query?query=rate(payment_success_total[5m])/rate(payment_total[5m])

# API latency P95 (should be < 500ms)
curl -s http://prometheus:9090/api/v1/query?query=histogram_quantile(0.95,rate(http_request_duration_seconds_bucket[5m]))

# Error rate (should be < 0.1%)
curl -s http://prometheus:9090/api/v1/query?query=rate(http_requests_total{status=~"5.."}[5m])

# Kafka consumer lag (should be < 1000)
curl -s http://prometheus:9090/api/v1/query?query=kafka_consumer_lag_sum
```

### Alert Acknowledgment
```bash
# Acknowledge alert in AlertManager
curl -X POST http://alertmanager:9093/api/v1/alerts \
  -H "Content-Type: application/json" \
  -d '[{
    "labels": {"alertname": "PaymentServiceDown"},
    "annotations": {"acknowledged": "true", "acknowledged_by": "'$USER'"}
  }]'

# Silence alert temporarily (2 hours)
curl -X POST http://alertmanager:9093/api/v1/silences \
  -H "Content-Type: application/json" \
  -d '{
    "matchers": [{"name": "alertname", "value": "PaymentServiceDown"}],
    "startsAt": "'$(date -u +%Y-%m-%dT%H:%M:%S)'",
    "endsAt": "'$(date -u -d '+2 hours' +%Y-%m-%dT%H:%M:%S)'",
    "createdBy": "'$USER'",
    "comment": "Maintenance window"
  }'
```

---

## 🔧 Maintenance Operations

### Scheduled Maintenance Mode
```bash
#!/bin/bash
# Enable maintenance mode

# 1. Notify users (30 minutes before)
kubectl apply -f /maintenance/user-notification.yaml

# 2. Enable maintenance page
kubectl patch ingress main-ingress -p \
  '{"spec":{"rules":[{"host":"api.example.com","http":{"paths":[{"path":"/","backend":{"serviceName":"maintenance-page","servicePort":80}}]}}]}}'

# 3. Drain traffic from services
for service in payment wallet fraud compliance; do
  kubectl scale deployment/$service-service --replicas=0 -n waqiti-production
done

# 4. Perform maintenance
# ... maintenance tasks ...

# 5. Restore services
for service in payment wallet fraud compliance; do
  kubectl scale deployment/$service-service --replicas=5 -n waqiti-production
done

# 6. Disable maintenance page
kubectl patch ingress main-ingress -p \
  '{"spec":{"rules":[{"host":"api.example.com","http":{"paths":[{"path":"/","backend":{"serviceName":"api-gateway","servicePort":80}}]}}]}}'
```

### Database Maintenance
```bash
# Vacuum and analyze PostgreSQL
psql -h postgres-payment -U postgres -d waqiti_payments -c "VACUUM ANALYZE;"

# Reindex for performance
psql -h postgres-payment -U postgres -d waqiti_payments -c "REINDEX DATABASE waqiti_payments;"

# Update statistics
psql -h postgres-payment -U postgres -d waqiti_payments -c "ANALYZE;"
```

### Certificate Renewal
```bash
# Check certificate expiry
echo | openssl s_client -servername api.example.com -connect api.example.com:443 2>/dev/null | openssl x509 -noout -dates

# Renew certificates (cert-manager should auto-renew)
kubectl describe certificate waqiti-tls -n waqiti-production

# Force renewal if needed
kubectl delete secret waqiti-tls -n waqiti-production
kubectl annotate certificate waqiti-tls cert-manager.io/issue-temporary-certificate="true" -n waqiti-production
```

---

## 🔄 Deployment Procedures

### Standard Deployment
```bash
# 1. Pre-deployment checks
./scripts/pre-deployment-check.sh

# 2. Create deployment
kubectl apply -f deployments/payment-service-v2.0.0.yaml

# 3. Wait for rollout
kubectl rollout status deployment/payment-service -n waqiti-production

# 4. Run smoke tests
./scripts/smoke-tests.sh payment-service

# 5. Monitor metrics (5 minutes)
./scripts/monitor-deployment.sh payment-service 300
```

### Emergency Rollback
```bash
#!/bin/bash
# Immediate rollback procedure

SERVICE=$1

# 1. Rollback deployment
kubectl rollout undo deployment/$SERVICE -n waqiti-production

# 2. Verify rollback
kubectl rollout status deployment/$SERVICE -n waqiti-production

# 3. Check service health
curl -f https://api.example.com/$SERVICE/health || exit 1

# 4. Clear caches
kubectl exec -n waqiti-production deployment/$SERVICE -- \
  curl -X DELETE http://localhost:8080/cache/clear

# 5. Notify team
./scripts/notify-rollback.sh $SERVICE
```

---

## 📞 Escalation Matrix

### Level 1 - Operations Team (24/7)
- **Response Time**: < 5 minutes
- **Responsibilities**: Initial triage, standard runbook procedures
- **Contact**: ops@example.com, PagerDuty: ops-team

### Level 2 - Service Owners
- **Response Time**: < 15 minutes
- **Responsibilities**: Service-specific issues, complex troubleshooting

| Service | Owner | Contact | Backup |
|---------|-------|---------|---------|
| Payment | John Doe | john@example.com | jane@example.com |
| Fraud | Alice Smith | alice@example.com | bob@example.com |
| Compliance | Charlie Brown | charlie@example.com | david@example.com |

### Level 3 - Engineering Leadership
- **Response Time**: < 30 minutes
- **Responsibilities**: Major incidents, architectural decisions
- **Contact**: engineering-leads@example.com

### Level 4 - Executive Team
- **Response Time**: < 1 hour
- **Responsibilities**: Business impact decisions, external communications
- **Contact**: executives@example.com

---

## 🛠️ Useful Scripts and Commands

### Health Check All Services
```bash
#!/bin/bash
for service in $(kubectl get deployments -n waqiti-production -o jsonpath='{.items[*].metadata.name}'); do
  echo "Checking $service..."
  kubectl exec -n waqiti-production deployment/$service -- curl -s http://localhost:8080/actuator/health | jq .status
done
```

### Backup Critical Data
```bash
#!/bin/bash
# Backup all critical databases
for db in payments wallets compliance ledger; do
  pg_dump -h postgres-$db -U postgres waqiti_$db | gzip > /backups/waqiti_$db_$(date +%Y%m%d_%H%M%S).sql.gz
done

# Backup Kafka topics
kafka-run-class kafka.tools.ExportZkOffsets --zkconnect zookeeper:2181 --output-file /backups/kafka-offsets-$(date +%Y%m%d).json

# Backup Redis
redis-cli -h redis-master --rdb /backups/redis-$(date +%Y%m%d).rdb
```

### Performance Profiling
```bash
# CPU profiling
kubectl exec -n waqiti-production deployment/payment-service -- \
  jcmd 1 JFR.start duration=60s filename=/tmp/profile.jfr

# Thread dump
kubectl exec -n waqiti-production deployment/payment-service -- \
  jcmd 1 Thread.print > thread-dump-$(date +%Y%m%d_%H%M%S).txt

# Heap histogram
kubectl exec -n waqiti-production deployment/payment-service -- \
  jcmd 1 GC.class_histogram > heap-histogram-$(date +%Y%m%d_%H%M%S).txt
```

---

## 📝 Post-Incident Procedures

### Incident Report Template
```markdown
## Incident Report - [DATE]

### Summary
- **Incident ID**: INC-YYYYMMDD-XXX
- **Severity**: SEV[1-4]
- **Duration**: [Start Time] - [End Time]
- **Services Affected**: 
- **Customer Impact**: 

### Timeline
- **HH:MM** - Initial alert received
- **HH:MM** - Investigation started
- **HH:MM** - Root cause identified
- **HH:MM** - Fix deployed
- **HH:MM** - Service restored
- **HH:MM** - Incident closed

### Root Cause
[Detailed explanation]

### Resolution
[Actions taken]

### Lessons Learned
1. What went well
2. What didn't go well
3. Action items

### Follow-up Actions
- [ ] Action 1 - Owner - Due date
- [ ] Action 2 - Owner - Due date
```

### Blameless Post-Mortem Process
1. Schedule within 48 hours of incident
2. Include all stakeholders
3. Focus on process, not people
4. Document in wiki
5. Share learnings company-wide
6. Track action items to completion

---

## 🔐 Security Incident Response

### Suspected Security Breach
```bash
#!/bin/bash
# IMMEDIATE ACTIONS FOR SECURITY BREACH

# 1. Isolate affected services
kubectl patch deployment $AFFECTED_SERVICE -p \
  '{"spec":{"template":{"spec":{"containers":[{"name":"'$AFFECTED_SERVICE'","env":[{"name":"ISOLATION_MODE","value":"true"}]}]}}}}'

# 2. Rotate all credentials
./scripts/rotate-all-credentials.sh --emergency

# 3. Enable enhanced logging
kubectl set env deployment/$AFFECTED_SERVICE LOG_LEVEL=DEBUG SECURITY_AUDIT=true

# 4. Capture forensic data
kubectl exec deployment/$AFFECTED_SERVICE -- tar czf /tmp/forensics.tar.gz /var/log /tmp
kubectl cp waqiti-production/$AFFECTED_SERVICE:/tmp/forensics.tar.gz ./forensics-$(date +%Y%m%d_%H%M%S).tar.gz

# 5. Notify security team
./scripts/security-incident-notification.sh --severity=critical
```

---

## 📚 Additional Resources

### Documentation
- **Architecture Docs**: https://docs.example.com/architecture
- **API Documentation**: https://api.example.com/swagger
- **Security Policies**: https://security.example.com/policies
- **Compliance Guide**: https://compliance.example.com/guide

### Tools
- **Grafana Dashboards**: https://grafana.example.com
- **Prometheus**: https://prometheus.example.com
- **Jaeger Tracing**: https://jaeger.example.com
- **Kibana Logs**: https://kibana.example.com

### Communication Channels
- **Slack**: #waqiti-ops, #waqiti-incidents
- **Email Lists**: ops@example.com, incidents@example.com
- **War Room**: https://meet.example.com/war-room
- **Status Page**: https://status.example.com

---

**Document Version**: 1.0.0  
**Last Updated**: January 2024  
**Next Review**: Monthly  
**Owner**: Operations Team  
**Emergency Contact**: +1-800-WAQITI-911