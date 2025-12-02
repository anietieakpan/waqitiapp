# 🚀 Waqiti Platform - Production Deployment Checklist

**Version**: 1.0
**Last Updated**: 2025-10-23
**Target Production Date**: [TBD]

---

## ✅ Pre-Deployment Checklist

### Phase 1: Configuration (Week 0)

#### PagerDuty Configuration
- [ ] Create PagerDuty service: "Waqiti Production"
- [ ] Generate integration key for Events API v2
- [ ] Configure escalation policy:
  - [ ] Level 1: On-call engineer (immediate)
  - [ ] Level 2: Senior engineer (after 15 min)
  - [ ] Level 3: Engineering manager (after 30 min)
- [ ] Set integration key in Vault: `vault kv put secret/alerting/pagerduty integration_key=<key>`
- [ ] Test incident creation: `POST https://events.pagerduty.com/v2/enqueue`

**Environment Variables**:
```bash
PAGERDUTY_INTEGRATION_KEY=${vault:secret/alerting/pagerduty#integration_key}
PAGERDUTY_ENABLED=true
```

#### Slack Configuration
- [ ] Create Slack channels:
  - [ ] #waqiti-critical (for critical system alerts)
  - [ ] #waqiti-fraud (for fraud detection alerts)
  - [ ] #waqiti-compliance (for compliance notifications)
  - [ ] #waqiti-engineering (for general engineering alerts)
- [ ] Create Slack app: "Waqiti Alerts"
- [ ] Enable Incoming Webhooks
- [ ] Generate webhook URLs for each channel
- [ ] Store in Vault:
  ```bash
  vault kv put secret/alerting/slack \
    critical_webhook=<url1> \
    fraud_webhook=<url2> \
    compliance_webhook=<url3> \
    engineering_webhook=<url4>
  ```
- [ ] Test message delivery to all channels

**Environment Variables**:
```bash
SLACK_WEBHOOK_CRITICAL=${vault:secret/alerting/slack#critical_webhook}
SLACK_WEBHOOK_FRAUD=${vault:secret/alerting/slack#fraud_webhook}
SLACK_WEBHOOK_COMPLIANCE=${vault:secret/alerting/slack#compliance_webhook}
SLACK_WEBHOOK_ENGINEERING=${vault:secret/alerting/slack#engineering_webhook}
SLACK_ENABLED=true
```

#### Kafka Topics
- [ ] Create topic: `encryption-audit-log`
  ```bash
  kafka-topics --create \
    --topic encryption-audit-log \
    --partitions 10 \
    --replication-factor 3 \
    --config retention.ms=220752000000 \  # 7 years
    --config compression.type=lz4 \
    --config segment.bytes=1073741824
  ```
- [ ] Verify topic: `kafka-topics --describe --topic encryption-audit-log`
- [ ] Grant ACLs for encryption-audit-service producer
- [ ] Grant ACLs for audit-query-service consumer

#### Legal Documents Storage
- [ ] Create directory: `/var/waqiti/legal/documents`
  ```bash
  sudo mkdir -p /var/waqiti/legal/documents/{subpoena,certification,rfpa,hold}
  sudo chown -R waqiti:waqiti /var/waqiti/legal
  sudo chmod 750 /var/waqiti/legal/documents
  ```
- [ ] Set up backup cron job (daily to S3/Azure Blob)
- [ ] Configure log rotation for PDF generation logs

#### Vault Secrets
- [ ] Redis passwords for all services:
  ```bash
  vault kv put secret/redis/transaction-service password=<strong-password>
  vault kv put secret/redis/payment-service password=<strong-password>
  # Repeat for all 108 services
  ```
- [ ] Database credentials (per service):
  ```bash
  vault kv put secret/database/transaction-service \
    username=transaction_svc \
    password=<strong-password>
  ```
- [ ] External API keys:
  - [ ] Stripe API key
  - [ ] Plaid API credentials
  - [ ] Twilio credentials (SMS)
  - [ ] SendGrid API key (Email)
  - [ ] Jumio KYC credentials
  - [ ] OFAC screening API

---

### Phase 2: Staging Deployment & Testing (Week 1-2)

#### Deployment
- [ ] Deploy new services to staging:
  - [ ] PagerDutyAlertService
  - [ ] SlackAlertService
  - [ ] UnifiedAlertingService
  - [ ] EncryptionAuditService
  - [ ] LegalDocumentGenerationService
  - [ ] LegalOrderAssignmentService
- [ ] Deploy enhanced services:
  - [ ] ComplianceNotificationService
  - [ ] ComplianceDataEncryptionService
  - [ ] SubpoenaProcessingService
  - [ ] PaymentSagaOrchestrator
  - [ ] RefreshController (config-service)
  - [ ] CryptoPaymentController (payment-service)
  - [ ] NFCPaymentController (payment-service)
- [ ] Verify all services started successfully
- [ ] Check health endpoints: `GET /actuator/health`
- [ ] Review startup logs for errors

#### Functional Testing
- [ ] **Alerting Tests**:
  - [ ] Trigger critical alert → verify PagerDuty incident created
  - [ ] Trigger critical alert → verify Slack @channel in #waqiti-critical
  - [ ] Trigger fraud alert → verify Slack message in #waqiti-fraud
  - [ ] Verify alert deduplication (same alert twice = 1 incident)
  - [ ] Verify incident resolution (resolve PagerDuty incident)

- [ ] **Compliance Notification Tests**:
  - [ ] SAR notification → verify email sent
  - [ ] SAR notification → verify SMS sent
  - [ ] Asset freeze → verify phone call initiated
  - [ ] RFPA notification → verify all channels
  - [ ] Check Redis for notification tracking

- [ ] **Encryption Audit Tests**:
  - [ ] Encrypt data → verify audit record in Kafka
  - [ ] Decrypt data → verify audit record in Kafka
  - [ ] Check audit record signature (SHA-256)
  - [ ] Verify audit record immutability
  - [ ] Query audit trail by user ID
  - [ ] Query audit trail by time range

- [ ] **Legal PDF Tests**:
  - [ ] Generate Subpoena Compliance Certificate
  - [ ] Generate Business Records Certification
  - [ ] Generate RFPA Customer Notification
  - [ ] Generate Legal Hold Notice
  - [ ] Verify PDF quality (open in Adobe Reader)
  - [ ] Verify Bates numbering
  - [ ] Test with DocuSign (signature placement)

- [ ] **Payment Saga Tests**:
  - [ ] Normal payment flow → verify success
  - [ ] Payment with timeout → verify compensation
  - [ ] Circuit breaker open → verify fallback + compensation
  - [ ] Check Kafka events published
  - [ ] Verify state persistence in database
  - [ ] Check PagerDuty alert for failed saga

- [ ] **Security Tests**:
  - [ ] **IDOR Test**: Try manipulating X-User-ID header → verify ignored
  - [ ] **Admin Test**: Try /admin/refresh without auth → verify 403
  - [ ] **Admin Test**: Try /admin/refresh with USER role → verify 403
  - [ ] **Admin Test**: Try /admin/refresh with ADMIN role → verify 200
  - [ ] Public crypto endpoints → verify accessible
  - [ ] Private crypto endpoints → verify require authentication

- [ ] **Legal Assignment Tests**:
  - [ ] Create subpoena → verify auto-assignment
  - [ ] Check workload balancing (multiple subpoenas)
  - [ ] Test skill-level routing (complex vs simple)
  - [ ] Test capacity limits (attorney at max workload)
  - [ ] Test escalation (no available attorneys)

#### Performance Testing
- [ ] **Load Test: Baseline (Expected Peak)**
  - [ ] 1,000 requests/second for 10 minutes
  - [ ] Measure alert delivery time (<5s target)
  - [ ] Measure PDF generation time (<2s target)
  - [ ] Measure saga completion time (<5s target)
  - [ ] Check circuit breaker state (should be CLOSED)

- [ ] **Load Test: 2x Peak**
  - [ ] 2,000 requests/second for 10 minutes
  - [ ] Monitor memory usage (<80% heap)
  - [ ] Monitor CPU usage (<70%)
  - [ ] Check for error rate (<0.1%)

- [ ] **Load Test: 5x Peak (Stress Test)**
  - [ ] 5,000 requests/second for 5 minutes
  - [ ] Monitor circuit breaker transitions
  - [ ] Verify graceful degradation
  - [ ] Check saga compensation under stress

- [ ] **Chaos Engineering**
  - [ ] Kill Kafka broker → verify producer retry
  - [ ] Kill Redis → verify cache fallback
  - [ ] Kill database → verify circuit breaker opens
  - [ ] Network latency injection → verify timeouts
  - [ ] Slow consumer → verify backpressure handling

#### Security Audit
- [ ] **Automated Scanning**:
  - [ ] OWASP ZAP security scan
  - [ ] Snyk dependency vulnerability scan
  - [ ] SonarQube code quality scan
  - [ ] Aqua container image scan

- [ ] **Manual Penetration Testing**:
  - [ ] IDOR attempts (crypto endpoints)
  - [ ] Authorization bypass attempts
  - [ ] SQL injection attempts
  - [ ] XSS attempts
  - [ ] CSRF attempts
  - [ ] Rate limiting bypass

- [ ] **Compliance Checks**:
  - [ ] PCI-DSS self-assessment questionnaire
  - [ ] GDPR data flow mapping
  - [ ] Encryption verification (data at rest + in transit)
  - [ ] Audit log completeness check

---

### Phase 3: Production Preparation (Week 2-3)

#### Documentation
- [ ] **Runbooks Created**:
  - [ ] PagerDuty incident response
  - [ ] Slack alert interpretation
  - [ ] Payment saga troubleshooting
  - [ ] Encryption audit queries
  - [ ] Legal PDF generation procedures
  - [ ] Circuit breaker recovery
  - [ ] Kafka topic maintenance

- [ ] **API Documentation Updated**:
  - [ ] Swagger/OpenAPI specs for new endpoints
  - [ ] Security requirement documentation
  - [ ] Rate limiting documentation
  - [ ] Error code catalog

#### Team Training
- [ ] **Operations Team Training** (8 hours):
  - [ ] PagerDuty response procedures (2 hours)
  - [ ] Monitoring dashboards walkthrough (2 hours)
  - [ ] Payment saga troubleshooting (2 hours)
  - [ ] Incident escalation process (1 hour)
  - [ ] Runbook review (1 hour)

- [ ] **Legal Team Training** (2 hours):
  - [ ] PDF generation process demo
  - [ ] Document review procedures
  - [ ] Manual override procedures

- [ ] **Compliance Team Training** (3 hours):
  - [ ] Encryption audit trail queries
  - [ ] Notification tracking dashboard
  - [ ] Regulatory reporting

- [ ] **Development Team Training** (4 hours):
  - [ ] Security best practices review
  - [ ] IDOR vulnerability prevention
  - [ ] Circuit breaker pattern usage
  - [ ] Distributed tracing interpretation

#### Monitoring Setup
- [ ] **Grafana Dashboards**:
  - [ ] Alerting System Dashboard
    - Alert delivery time (histogram)
    - PagerDuty incidents (count)
    - Slack messages (count)
    - Circuit breaker state
  - [ ] Compliance Dashboard
    - Notification success rate
    - Encryption audit completeness
    - PDF generation success rate
  - [ ] Payment SAGA Dashboard
    - SAGA success rate
    - Compensation rate
    - Average duration
    - Error breakdown
  - [ ] Security Dashboard
    - Failed authentication attempts
    - IDOR attempt count
    - Rate limit violations
    - Admin access log

- [ ] **Prometheus Alert Rules**:
  ```yaml
  - alert: HighAlertDeliveryLatency
    expr: alert_delivery_seconds > 5
    for: 2m
    annotations:
      summary: "Alert delivery taking >5 seconds"

  - alert: SagaCompensationRateHigh
    expr: saga_compensation_rate > 0.01
    for: 5m
    annotations:
      summary: "SAGA compensation rate >1%"

  - alert: EncryptionAuditGap
    expr: increase(encryption_operations_total[5m]) > increase(audit_records_total[5m])
    annotations:
      summary: "Encryption audit trail incomplete"
  ```

- [ ] **Log Aggregation**:
  - [ ] ELK Stack / Splunk configured
  - [ ] Log retention policy (90 days hot, 1 year cold)
  - [ ] Log parsing rules for structured logging
  - [ ] Search saved queries for common investigations

---

### Phase 4: Production Deployment (Week 3)

#### Pre-Deployment
- [ ] Change freeze announcement (48 hours before)
- [ ] Backup verification (all databases)
- [ ] Rollback plan documented and tested
- [ ] On-call schedule confirmed (24/7 coverage)
- [ ] War room setup (Slack channel + Zoom link)

#### Deployment Window
- [ ] **T-0 hours: Deployment Start**
  - [ ] Enable maintenance mode (optional)
  - [ ] Deploy new services
  - [ ] Deploy enhanced services
  - [ ] Run database migrations (if any)
  - [ ] Restart dependent services

- [ ] **T+30 min: Smoke Tests**
  - [ ] All services healthy: `GET /actuator/health`
  - [ ] Test PagerDuty integration
  - [ ] Test Slack integration
  - [ ] Generate 1 test PDF
  - [ ] Run 1 test payment saga

- [ ] **T+1 hour: Feature Flag Enabled (0%)**
  - [ ] Enable feature flags (no traffic yet)
  - [ ] Monitor for 1 hour
  - [ ] Check error logs
  - [ ] Verify no unexpected behavior

- [ ] **T+2 hours: Soft Launch Decision**
  - [ ] Go/No-Go decision
  - [ ] If Go: Enable 5% traffic
  - [ ] If No-Go: Rollback

#### Post-Deployment Monitoring (Week 3-4)
- [ ] **5% Traffic (Day 1-3)**:
  - [ ] Monitor alert delivery time (target <5s)
  - [ ] Monitor PDF generation success (target >99%)
  - [ ] Monitor saga compensation rate (target <1%)
  - [ ] Monitor security incidents (target 0)
  - [ ] Collect user feedback
  - [ ] Review error logs daily

- [ ] **10% Traffic (Day 4-5)**:
  - [ ] Continue monitoring
  - [ ] Review performance metrics
  - [ ] Check for any anomalies

- [ ] **25% Traffic (Day 6-7)**:
  - [ ] Full performance review
  - [ ] Cost analysis (infrastructure)
  - [ ] User satisfaction survey

---

### Phase 5: Full Rollout (Week 4-8)

#### Gradual Rollout Schedule
- [ ] **Week 4**: 10% users
- [ ] **Week 5**: 25% users
- [ ] **Week 6**: 50% users
- [ ] **Week 7**: 75% users
- [ ] **Week 8**: 100% users

#### Success Metrics (Per Phase)
| Metric | Target | Gate |
|--------|--------|------|
| Alert Delivery Time | <5s | Must pass |
| PDF Generation Success | >99% | Must pass |
| SAGA Compensation Rate | <1% | Must pass |
| Security Incidents | 0 | Must pass |
| Service Availability | >99.9% | Must pass |
| Error Rate | <0.1% | Must pass |

#### Rollback Criteria (Auto-Rollback if ANY):
- [ ] Security incident detected
- [ ] Error rate >1%
- [ ] Service availability <99%
- [ ] Alert delivery time >10s consistently
- [ ] Data corruption detected
- [ ] Audit trail gaps detected

---

## 🚨 Emergency Procedures

### Rollback Plan
1. **Immediate**: Disable feature flags (0% traffic)
2. **Within 15 min**: Revert deployments to previous version
3. **Within 30 min**: Verify old version stable
4. **Within 1 hour**: Post-mortem started

### Incident Response
1. **Detection**: PagerDuty alert or monitoring alarm
2. **Acknowledgment**: On-call engineer acknowledges (SLA: 5 min)
3. **Assessment**: Severity classification (SEV1/SEV2/SEV3)
4. **Escalation**: Page additional engineers if needed
5. **Resolution**: Fix applied and verified
6. **Communication**: Status updates every 30 min
7. **Post-Mortem**: Within 48 hours of resolution

### Contact List
- **On-Call Engineer**: PagerDuty automatic paging
- **Engineering Manager**: [Phone/Email]
- **CTO**: [Phone/Email]
- **Security Lead**: [Phone/Email]
- **Compliance Officer**: [Phone/Email]

---

## ✅ Sign-Off

- [ ] **Engineering Lead**: Deployment tested and verified
- [ ] **Security Lead**: Security audit passed
- [ ] **Compliance Officer**: Regulatory requirements met
- [ ] **Operations Lead**: Monitoring and runbooks ready
- [ ] **CTO**: Final approval for production deployment

**Deployment Approved By**: ___________________
**Date**: ___________________
**Production Go-Live Date**: ___________________

---

**Version History**:
- v1.0 (2025-10-23): Initial checklist created
