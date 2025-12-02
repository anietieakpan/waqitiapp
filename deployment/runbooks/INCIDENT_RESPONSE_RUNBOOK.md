# 🚨 Waqiti Platform - Incident Response Runbook

**Version**: 1.0
**Last Updated**: 2025-10-23
**On-Call Schedule**: [PagerDuty Link]

---

## 📞 Emergency Contacts

| Role | Name | Phone | Slack | PagerDuty |
|------|------|-------|-------|-----------|
| On-Call Engineer | [Auto-paged] | - | @oncall | Auto |
| Engineering Manager | [Name] | [Phone] | @eng-manager | Manual |
| CTO | [Name] | [Phone] | @cto | Manual |
| Security Lead | [Name] | [Phone] | @security-lead | Manual |
| Compliance Officer | [Name] | [Phone] | @compliance | Manual |

**War Room**: Slack #waqiti-incidents + Zoom [link]

---

## 🎯 Incident Severity Classification

### SEV1 - Critical (Response: Immediate)
- **Definition**: Complete service outage or data breach
- **Examples**:
  - All services down
  - Database corruption
  - Security breach
  - Data loss
  - Encryption audit trail stopped
- **Response Time**: 5 minutes
- **Escalation**: Immediate to Engineering Manager + CTO
- **Communication**: Updates every 15 minutes

### SEV2 - High (Response: 15 minutes)
- **Definition**: Major feature degraded, affecting >10% users
- **Examples**:
  - Payment processing failing
  - Circuit breakers stuck OPEN
  - High SAGA compensation rate (>5%)
  - Alert delivery latency >10s
  - PDF generation failing
- **Response Time**: 15 minutes
- **Escalation**: After 30 minutes to Engineering Manager
- **Communication**: Updates every 30 minutes

### SEV3 - Medium (Response: 1 hour)
- **Definition**: Minor feature impacted, <10% users affected
- **Examples**:
  - Single service degraded
  - High memory usage (>85%)
  - Kafka consumer lag
  - Slow database queries
- **Response Time**: 1 hour
- **Escalation**: After 2 hours to Engineering Manager
- **Communication**: Updates every 1 hour

### SEV4 - Low (Response: 4 hours)
- **Definition**: Cosmetic issues, no user impact
- **Examples**:
  - Dashboard display issues
  - Non-critical logging errors
  - Warning alerts
- **Response Time**: 4 hours
- **Escalation**: None
- **Communication**: Status update when resolved

---

## 🔥 Critical Incident Playbooks

### Playbook 1: Alert Delivery Failure

**Symptoms**:
- PagerDuty incidents not being created
- Slack messages not appearing
- Alert delivery latency >10s

**Diagnosis**:
```bash
# Check alert service health
kubectl get pods -l app=unified-alerting-service
kubectl logs -l app=unified-alerting-service --tail=100

# Check PagerDuty connectivity
curl -X POST https://events.pagerduty.com/v2/enqueue \
  -H "Content-Type: application/json" \
  -d '{"routing_key":"test","event_action":"trigger","payload":{"summary":"test"}}'

# Check Slack webhook
curl -X POST $SLACK_WEBHOOK_CRITICAL \
  -H "Content-Type: application/json" \
  -d '{"text":"Test message"}'

# Check circuit breaker state
curl http://unified-alerting-service:8080/actuator/metrics/resilience4j.circuitbreaker.state
```

**Resolution Steps**:
1. **If PagerDuty/Slack is down** (external outage):
   - Switch to backup notification channel (email)
   - Monitor PagerDuty status page
   - Escalate manually via phone

2. **If circuit breaker is OPEN**:
   ```bash
   # Reset circuit breaker
   curl -X POST http://unified-alerting-service:8080/actuator/circuitbreakers/pagerduty/reset
   curl -X POST http://unified-alerting-service:8080/actuator/circuitbreakers/slack/reset
   ```

3. **If service is crashing**:
   ```bash
   # Check logs for errors
   kubectl logs -l app=unified-alerting-service --tail=500 | grep ERROR

   # Restart service
   kubectl rollout restart deployment/unified-alerting-service

   # Verify recovery
   kubectl get pods -l app=unified-alerting-service -w
   ```

4. **If credentials are invalid**:
   ```bash
   # Check Vault secrets
   vault kv get secret/alerting/pagerduty
   vault kv get secret/alerting/slack

   # Rotate if needed
   vault kv put secret/alerting/pagerduty integration_key=<new-key>

   # Restart service to pick up new secrets
   kubectl rollout restart deployment/unified-alerting-service
   ```

**Verification**:
```bash
# Send test alert
curl -X POST http://unified-alerting-service:8080/api/v1/alerts/test \
  -H "Content-Type: application/json" \
  -d '{"severity":"critical","title":"Test Alert","description":"Testing alert delivery"}'

# Verify PagerDuty incident created
# Verify Slack message in #waqiti-critical
```

**Prevention**:
- Regular PagerDuty/Slack connectivity tests
- Alert on circuit breaker state changes
- Backup notification channels configured

---

### Playbook 2: Payment SAGA Compensation Storm

**Symptoms**:
- SAGA compensation rate >5%
- Circuit breakers opening frequently
- Payment failures increasing
- Grafana alert: `SagaCompensationRateHigh`

**Diagnosis**:
```bash
# Check SAGA metrics
curl http://payment-service:8080/actuator/metrics/saga.compensated.total
curl http://payment-service:8080/actuator/metrics/saga.started.total

# Check circuit breaker states
curl http://payment-service:8080/actuator/circuitbreakers

# Check database connectivity
curl http://payment-service:8080/actuator/health | jq '.components.db'

# Check Kafka lag
kafka-consumer-groups --bootstrap-server kafka:9092 --group payment-saga --describe

# Review recent failed SAGAs
kubectl logs -l app=payment-service --tail=1000 | grep "SAGA_FAILED\|COMPENSATION"
```

**Resolution Steps**:
1. **Identify root cause**:
   - Database slow? Check query performance
   - External service down? Check circuit breaker states
   - Kafka lag? Check consumer health
   - Network issues? Check connectivity

2. **If database is slow**:
   ```bash
   # Check slow queries
   kubectl exec -it postgres-0 -- psql -U postgres -c "SELECT query, calls, mean_exec_time FROM pg_stat_statements ORDER BY mean_exec_time DESC LIMIT 10;"

   # Check connection pool
   curl http://payment-service:8080/actuator/metrics/hikaricp.connections.active

   # Scale up database if needed
   # Or kill long-running queries
   ```

3. **If external service is down**:
   ```bash
   # Check which circuit breakers are OPEN
   curl http://payment-service:8080/actuator/circuitbreakers | jq '.circuitBreakers[] | select(.state=="OPEN")'

   # Wait for circuit breaker to transition to HALF_OPEN
   # Or manually transition if issue is resolved
   curl -X POST http://payment-service:8080/actuator/circuitbreakers/<name>/transition?state=HALF_OPEN
   ```

4. **If Kafka lag is high**:
   ```bash
   # Scale up consumer instances
   kubectl scale deployment/payment-saga-consumer --replicas=5

   # Check for poison messages
   kafka-console-consumer --bootstrap-server kafka:9092 \
     --topic payment-saga-events \
     --from-beginning \
     --max-messages 10
   ```

5. **Emergency: Disable new payments** (last resort):
   ```bash
   # Enable circuit breaker manually
   curl -X POST http://payment-service:8080/actuator/circuitbreakers/payment-saga/open

   # Or use feature flag
   curl -X POST http://config-service:8888/admin/feature-flags \
     -H "Authorization: Bearer $ADMIN_TOKEN" \
     -d '{"flag":"payments.enabled","value":false}'
   ```

**Verification**:
```bash
# Test a single payment
curl -X POST http://payment-service:8080/api/v1/payments/test \
  -H "Authorization: Bearer $TEST_TOKEN" \
  -d '{"amount":1.00,"currency":"USD"}'

# Monitor compensation rate
watch -n 5 'curl -s http://payment-service:8080/actuator/metrics/saga.compensated.total | jq ".measurements[0].value"'
```

**Prevention**:
- Regular load testing
- Circuit breaker threshold tuning
- Database connection pool sizing
- Kafka consumer scaling rules

---

### Playbook 3: Encryption Audit Trail Gap

**Symptoms**:
- Encryption operations > Audit records
- Grafana alert: `EncryptionAuditGap`
- Compliance violation risk
- **CRITICAL**: This is a regulatory issue

**Diagnosis**:
```bash
# Check audit service health
kubectl get pods -l app=encryption-audit-service
kubectl logs -l app=encryption-audit-service --tail=200

# Check Kafka producer metrics
curl http://compliance-service:8080/actuator/metrics/kafka.producer.record.send.total

# Check Kafka topic lag
kafka-consumer-groups --bootstrap-server kafka:9092 --group encryption-audit --describe

# Check for audit service errors
kubectl logs -l app=encryption-audit-service | grep ERROR

# Query recent encryption operations
curl http://compliance-service:8080/api/internal/encryption/operations?since=1h

# Query recent audit records
kafka-console-consumer --bootstrap-server kafka:9092 \
  --topic encryption-audit-log \
  --from-beginning \
  --max-messages 100
```

**Resolution Steps**:
1. **If Kafka is down**:
   ```bash
   # Check Kafka cluster health
   kubectl get pods -l app=kafka

   # Check Kafka broker logs
   kubectl logs kafka-0 --tail=200

   # Restart Kafka if needed (careful!)
   kubectl rollout restart statefulset/kafka
   ```

2. **If audit service is failing**:
   ```bash
   # Check for exceptions
   kubectl logs -l app=encryption-audit-service | grep -A 10 "Exception"

   # Check circuit breaker to Kafka
   curl http://encryption-audit-service:8080/actuator/circuitbreakers/kafka

   # Restart audit service
   kubectl rollout restart deployment/encryption-audit-service
   ```

3. **If audit records are lost** (CRITICAL):
   ```bash
   # Check Kafka retention
   kafka-configs --bootstrap-server kafka:9092 \
     --describe --entity-type topics \
     --entity-name encryption-audit-log

   # Check for deleted messages
   kafka-run-class kafka.tools.GetOffsetShell \
     --broker-list kafka:9092 \
     --topic encryption-audit-log \
     --time -1

   # ESCALATE IMMEDIATELY to Compliance Officer
   # This is a regulatory violation
   ```

4. **Recovery** (if records can be reconstructed):
   ```bash
   # Extract encryption operations from application logs
   kubectl logs -l app=compliance-service --since=24h | grep "ENCRYPTION_OPERATION" > encryption_ops.log

   # Parse and replay to audit log
   python replay_audit_records.py encryption_ops.log

   # Verify completeness
   ```

**Verification**:
```bash
# Encrypt test data
curl -X POST http://compliance-service:8080/api/internal/encrypt \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"data":"test","type":"PII"}'

# Verify audit record created (within 1 second)
kafka-console-consumer --bootstrap-server kafka:9092 \
  --topic encryption-audit-log \
  --from-beginning \
  --max-messages 1 \
  --timeout-ms 2000

# Check metrics match
# encryption_operations_total == encryption_audit_records_total
```

**Prevention**:
- Kafka cluster monitoring
- Audit service health checks
- Daily audit completeness reports
- Backup audit records to S3/Azure Blob

**Escalation**:
- **Immediately** notify Compliance Officer
- Document gap in incident report
- Prepare regulatory disclosure if needed

---

### Playbook 4: IDOR Security Incident

**Symptoms**:
- User accessing data they don't own
- `X-User-ID` header manipulation detected
- Security alert triggered
- Failed authorization in logs

**Diagnosis**:
```bash
# Check recent 403 errors
kubectl logs -l app=payment-service --since=1h | grep "403\|Forbidden"

# Check SecurityContext extraction failures
curl http://payment-service:8080/actuator/metrics/security.context.extraction.failed

# Check for X-User-ID header usage (should be ZERO)
kubectl logs -l app=payment-service --since=1h | grep "X-User-ID" | wc -l

# Review access logs for suspicious patterns
kubectl logs -l app=api-gateway --since=1h | grep "GET /api/v1/payments/crypto" | jq '.headers."X-User-ID"'
```

**Resolution Steps**:
1. **Confirm it's an attack** (not a bug):
   ```bash
   # Check if same user/IP trying multiple user IDs
   kubectl logs -l app=api-gateway --since=1h | \
     grep "X-User-ID" | \
     jq -r '[.client_ip, .headers."X-User-ID"] | @csv' | \
     sort | uniq -c | sort -nr

   # If multiple different user IDs from same IP = ATTACK
   ```

2. **Block attacker immediately**:
   ```bash
   # Get attacker IP
   ATTACKER_IP="1.2.3.4"

   # Block at API gateway
   kubectl exec -it api-gateway-0 -- \
     iptables -A INPUT -s $ATTACKER_IP -j DROP

   # Or use API gateway rate limiter
   curl -X POST http://api-gateway:8080/admin/block-ip \
     -H "Authorization: Bearer $ADMIN_TOKEN" \
     -d "{\"ip\":\"$ATTACKER_IP\"}"
   ```

3. **Verify fix is deployed**:
   ```bash
   # Check CryptoPaymentController code
   kubectl exec -it payment-service-0 -- \
     cat /app/src/main/java/com/waqiti/payment/controller/CryptoPaymentController.java | \
     grep -A 5 "sendCryptoPayment"

   # Should see: SecurityContextUtil.getAuthenticatedUserId()
   # Should NOT see: @RequestHeader("X-User-ID")
   ```

4. **Audit for data breach**:
   ```bash
   # Check if attacker accessed data
   kubectl logs -l app=payment-service --since=24h | \
     grep $ATTACKER_IP | \
     grep "200\|wallet\|balance\|transactions"

   # If successful responses = DATA BREACH
   # ESCALATE to Security Lead + Legal
   ```

5. **Notify affected users** (if breach confirmed):
   ```bash
   # Extract affected user IDs
   kubectl logs -l app=payment-service --since=24h | \
     grep $ATTACKER_IP | \
     grep "200" | \
     jq -r '.user_id' | sort -u > affected_users.txt

   # Trigger compliance notification
   curl -X POST http://compliance-service:8080/api/internal/notifications/data-breach \
     -H "Authorization: Bearer $ADMIN_TOKEN" \
     -F "users=@affected_users.txt"
   ```

**Verification**:
```bash
# Test IDOR attempt (should FAIL)
curl -X GET http://api-gateway:8080/api/v1/payments/crypto/wallet/balance \
  -H "Authorization: Bearer $USER1_TOKEN" \
  -H "X-User-ID: user2-id"

# Should return user1's balance (ignore X-User-ID)
# Response should NOT contain user2 data
```

**Prevention**:
- Regular security scanning
- IDOR detection rules
- User ID from JWT only
- Audit all security context extractions

**Escalation**:
- **Immediately** notify Security Lead
- **If data breach** notify Legal + Compliance
- Prepare incident report for regulators (72 hours)

---

### Playbook 5: Legal PDF Generation Failure

**Symptoms**:
- PDF generation failing
- Subpoena processing stuck
- iText library errors
- Grafana alert: `PDFGenerationFailureHigh`

**Diagnosis**:
```bash
# Check PDF service health
kubectl get pods -l app=legal-service
kubectl logs -l app=legal-service --tail=200 | grep PDF

# Check recent PDF generation attempts
curl http://legal-service:8080/actuator/metrics/legal.pdf.generation.attempts.total

# Check failures
curl http://legal-service:8080/actuator/metrics/legal.pdf.generation.failed.total

# Check disk space (PDFs written to /var/waqiti/legal)
kubectl exec -it legal-service-0 -- df -h /var/waqiti/legal

# Check for iText license issues
kubectl logs -l app=legal-service | grep -i "license\|itext"
```

**Resolution Steps**:
1. **If disk is full**:
   ```bash
   # Clean up old PDFs
   kubectl exec -it legal-service-0 -- \
     find /var/waqiti/legal/documents -mtime +30 -delete

   # Or increase volume size
   kubectl patch pvc legal-documents -p '{"spec":{"resources":{"requests":{"storage":"100Gi"}}}}'
   ```

2. **If iText license issue**:
   ```bash
   # Check license
   kubectl get secret itext-license -o yaml

   # Update license if expired
   kubectl create secret generic itext-license \
     --from-file=license.xml=/path/to/new/license.xml \
     --dry-run=client -o yaml | kubectl apply -f -

   # Restart service
   kubectl rollout restart deployment/legal-service
   ```

3. **If fonts are missing**:
   ```bash
   # Check fonts directory
   kubectl exec -it legal-service-0 -- ls -la /usr/share/fonts

   # Install fonts if needed
   kubectl exec -it legal-service-0 -- \
     apt-get update && apt-get install -y fonts-liberation
   ```

4. **If template is corrupt**:
   ```bash
   # Check template files
   kubectl exec -it legal-service-0 -- \
     ls -la /app/templates/

   # Re-deploy templates from git
   kubectl rollout restart deployment/legal-service
   ```

**Verification**:
```bash
# Generate test PDF
curl -X POST http://legal-service:8080/api/v1/legal/documents/test \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"type":"SUBPOENA_COMPLIANCE","subpoenaId":"TEST-123"}'

# Check PDF created
kubectl exec -it legal-service-0 -- \
  ls -lh /var/waqiti/legal/documents/subpoena/TEST-123.pdf

# Download and verify
kubectl cp legal-service-0:/var/waqiti/legal/documents/subpoena/TEST-123.pdf ./test.pdf
open test.pdf  # Verify it opens correctly
```

**Prevention**:
- Disk space monitoring
- iText license expiration alerts
- PDF generation smoke tests (daily)
- Template validation

---

## 📊 Key Metrics to Monitor

| Metric | Normal | Warning | Critical |
|--------|--------|---------|----------|
| Alert Delivery Time | <2s | 2-5s | >5s |
| SAGA Compensation Rate | <0.1% | 0.1-1% | >1% |
| PDF Generation Success | >99.5% | 99-99.5% | <99% |
| Encryption Audit Completeness | 100% | 99.99% | <99.99% |
| Circuit Breaker Open Count | 0 | 1-2 | >2 |
| Failed Authentication Rate | <0.1% | 0.1-0.5% | >0.5% |
| Database Response Time | <100ms | 100-500ms | >500ms |
| JVM Memory Usage | <70% | 70-85% | >85% |

---

## 🔄 Post-Incident Process

### Immediate (Within 1 hour of resolution)
1. [ ] Incident resolved and verified
2. [ ] Communication sent to stakeholders
3. [ ] Rollback (if needed) completed
4. [ ] Monitoring confirmed green

### Short Term (Within 24 hours)
1. [ ] Preliminary incident report drafted
2. [ ] Root cause identified
3. [ ] Temporary fix verified
4. [ ] Customer communication (if needed)

### Long Term (Within 48 hours)
1. [ ] Full post-mortem completed
2. [ ] Root cause analysis (5 whys)
3. [ ] Action items identified
4. [ ] Preventive measures documented
5. [ ] Runbook updated
6. [ ] Team debrief scheduled

### Post-Mortem Template
```markdown
# Incident Post-Mortem: [Title]

**Date**: [Date]
**Severity**: [SEV1/2/3/4]
**Duration**: [Start - End]
**Impact**: [# users affected, downtime]

## Summary
[Brief description of what happened]

## Timeline
- HH:MM - Detection
- HH:MM - Response started
- HH:MM - Root cause identified
- HH:MM - Fix applied
- HH:MM - Incident resolved

## Root Cause
[Technical root cause]

## Resolution
[What was done to resolve]

## Impact
- Users affected: [number]
- Services impacted: [list]
- Data loss: [yes/no]
- Regulatory impact: [yes/no]

## Action Items
1. [ ] [Action item with owner]
2. [ ] [Action item with owner]

## Lessons Learned
- What went well
- What could be improved

## Prevention
[How to prevent this in future]
```

---

**This runbook is a living document. Update after each incident.**

**Last Updated**: 2025-10-23
**Next Review**: 2025-11-23
