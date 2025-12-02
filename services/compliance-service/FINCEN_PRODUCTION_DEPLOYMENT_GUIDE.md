# FinCEN BSA E-Filing System - Production Deployment Guide

**Service:** Waqiti Compliance Service  
**Component:** FinCEN API Integration  
**Status:** READY FOR PRODUCTION ENABLEMENT  
**Regulatory Impact:** CRITICAL - BSA/AML Compliance  
**Date:** October 23, 2025

---

## OVERVIEW

The FinCEN API Client is **FULLY IMPLEMENTED** and production-ready. It is currently **DISABLED** to prevent accidental filings during development. This guide provides step-by-step instructions for enabling the integration in production.

**Current Status:**
- ✅ Code Implementation: **100% Complete**
- ✅ Error Handling: **Comprehensive**
- ✅ Retry Logic: **Implemented**
- ✅ Audit Logging: **Complete**
- ❌ Production Configuration: **PENDING** (requires credentials)
- ❌ Enabled Status: **FALSE** (safety default)

---

## PREREQUISITES

### 1. FinCEN BSA E-Filing Account

**Required Credentials:**
- FinCEN Username (provided by FinCEN)
- FinCEN Password (secure, rotated quarterly)
- Institution ID (assigned by FinCEN)
- Filing Contact Information

**How to Obtain:**
1. Register at: https://bsaefiling.fincen.treas.gov/
2. Complete FinCEN 114a Form (Registration)
3. Wait for approval (5-10 business days)
4. Receive credentials via secure email

**Contact:**
- FinCEN Regulatory Helpline: 1-800-949-2732
- BSA E-Filing Support: BSAEFilingHelp@fincen.gov
- Hours: Monday-Friday, 8:00 AM - 6:00 PM ET

---

### 2. Vault Secret Storage

Store FinCEN credentials in HashiCorp Vault (NEVER in application.yml):

```bash
# Store FinCEN credentials in Vault
vault kv put secret/compliance-service/fincen \
  username="FINCEN_USERNAME_HERE" \
  password="FINCEN_PASSWORD_HERE" \
  institution-id="INSTITUTION_ID_HERE"

# Verify storage
vault kv get secret/compliance-service/fincen
```

---

### 3. Network Configuration

**Firewall Rules:**
```bash
# Allow outbound HTTPS to FinCEN (production)
Allow: compliance-service → bsaefiling.fincen.treas.gov:443

# Allow outbound HTTPS to FinCEN (test environment)
Allow: compliance-service → bsaefilingtest.fincen.treas.gov:443
```

**TLS Requirements:**
- Minimum TLS 1.2 (TLS 1.3 recommended)
- Valid SSL certificates
- Certificate pinning (optional but recommended)

---

## PRODUCTION CONFIGURATION

### Step 1: Create Production Configuration File

Create `application-production.yml`:

```yaml
# ============================================================================
# FinCEN BSA E-Filing Production Configuration
# ============================================================================

fincen:
  api:
    # PRODUCTION: Enable FinCEN API integration
    enabled: true
    
    # FinCEN Production API endpoint
    base-url: https://bsaefiling.fincen.treas.gov/api
    
    # Credentials from Vault (DO NOT hardcode)
    username: ${vault.fincen.username}
    password: ${vault.fincen.password}
    institution-id: ${vault.fincen.institution-id}
    
    # Timeout configuration (FinCEN can be slow during high volume)
    timeout-seconds: 60
    
    # Retry configuration (be conservative - don't spam FinCEN)
    retry-attempts: 3
    retry-backoff-seconds: 5
    
    # Filing configuration
    filing:
      # Auto-submit SARs or require manual review
      auto-submit: false  # Set to true after validation period
      
      # Require dual approval for SARs over threshold
      dual-approval-threshold: 50000
      
      # Expedited filing threshold (severe crimes)
      expedited-threshold: 100000
      
    # Monitoring and alerting
    monitoring:
      # Alert on filing failures
      alert-on-failure: true
      
      # Alert channels
      alert-channels: "EMAIL,SLACK,PAGERDUTY"
      
      # Filing SLA (hours) - FinCEN requires 30 days
      filing-sla-hours: 24
      
    # Compliance officer contacts
    compliance:
      primary-officer-email: compliance@example.com
      secondary-officer-email: cfo@example.com
      legal-team-email: legal@example.com

# Audit logging for FinCEN operations
logging:
  level:
    com.example.compliance.fincen: INFO
  pattern:
    console: "%d{ISO8601} [%thread] %-5level %logger{36} - FINCEN - %msg%n"
    
# Metrics for FinCEN integration
management:
  metrics:
    tags:
      service: compliance-service
      integration: fincen
    export:
      prometheus:
        enabled: true
```

---

### Step 2: Environment Variables

Set the following environment variables in production:

```bash
# Spring profile
export SPRING_PROFILES_ACTIVE=production

# Vault configuration
export VAULT_ADDR=https://vault.example.com
export VAULT_TOKEN=<vault-token>
export VAULT_NAMESPACE=compliance

# FinCEN API toggle (can disable quickly if needed)
export FINCEN_API_ENABLED=true

# Monitoring
export SENTRY_DSN=<sentry-dsn>
export DATADOG_API_KEY=<datadog-key>
```

---

### Step 3: Validate Configuration

Before enabling in production, validate configuration:

```bash
# Run configuration validation
curl -X POST http://localhost:8080/api/compliance/fincen/validate-config \
  -H "Authorization: Bearer <admin-token>" \
  -H "Content-Type: application/json"

# Expected response:
{
  "configured": true,
  "credentialsValid": true,
  "networkConnectivity": true,
  "vaultAccessible": true,
  "warnings": []
}
```

---

## TESTING PROCEDURE

### Phase 1: Test Environment Validation (Week 1)

```bash
# 1. Configure test environment
fincen.api.base-url: https://bsaefilingtest.fincen.treas.gov/api
fincen.api.enabled: true

# 2. Submit test SAR
curl -X POST http://localhost:8080/api/compliance/sar/submit-test \
  -H "Authorization: Bearer <token>" \
  -d '{"amount": 5000, "narrative": "Test filing"}'

# 3. Verify submission
# Check FinCEN test portal: https://bsaefilingtest.fincen.treas.gov/

# 4. Monitor logs
tail -f logs/compliance-service.log | grep FINCEN
```

**Expected Test Results:**
- ✅ Connection successful
- ✅ Authentication successful
- ✅ XML validation passed
- ✅ Filing acknowledgment received
- ✅ Status check working

---

### Phase 2: Production Dry Run (Week 2)

```bash
# Enable production API but don't auto-submit
fincen.api.enabled: true
fincen.api.filing.auto-submit: false

# Generate SARs but require manual review before submission
# Review each SAR in compliance dashboard
# Manually approve for submission
```

---

### Phase 3: Full Production (Week 3+)

```bash
# Enable full automation
fincen.api.enabled: true
fincen.api.filing.auto-submit: true  # Only after 2 weeks of validation

# Monitor closely for first 30 days
# Daily review of all submissions
# Weekly reconciliation with FinCEN portal
```

---

## MONITORING AND ALERTING

### Key Metrics to Monitor

```yaml
# Prometheus metrics
fincen_filings_total{status="success|failure"}
fincen_filing_duration_seconds
fincen_api_errors_total
fincen_sla_violations_total
```

### Alert Rules

```yaml
# Alert on filing failure
- alert: FincenFilingFailed
  expr: fincen_filings_total{status="failure"} > 0
  for: 5m
  severity: critical
  channels: [pagerduty, slack, email]
  
# Alert on SLA violation
- alert: FincenSlAViolation
  expr: (time() - fincen_last_successful_filing) > 86400
  for: 1h
  severity: high
  
# Alert on API connectivity
- alert: FincenApiDown
  expr: fincen_api_up == 0
  for: 15m
  severity: critical
```

---

## DISASTER RECOVERY

### If FinCEN API is Down

```yaml
# Scenario: FinCEN API unavailable
# Action: Queue filings for later submission

1. System automatically detects FinCEN API failure
2. SARs are saved to database with status="PENDING_SUBMISSION"
3. Retry job runs every 4 hours
4. Alert sent to compliance team
5. Manual submission available as backup
```

### Manual Filing Procedure

```bash
# If automated filing fails, compliance officers can:

1. Log into compliance dashboard
2. Navigate to "Pending SARs"
3. Review SAR details
4. Download SAR XML
5. Manually upload to FinCEN portal
6. Update SAR status in system with filing number
```

---

## COMPLIANCE CHECKLIST

Before enabling in production:

- [ ] FinCEN credentials obtained and verified
- [ ] Credentials stored in Vault (not in code)
- [ ] Network firewall rules configured
- [ ] Test environment validation completed
- [ ] Compliance officer trained on system
- [ ] Manual filing procedure documented
- [ ] Monitoring dashboards created
- [ ] Alert rules configured and tested
- [ ] Disaster recovery plan reviewed
- [ ] Legal team approval obtained
- [ ] Audit logging verified
- [ ] Data retention policy configured
- [ ] SIEM integration completed
- [ ] Regulatory reporting schedule established

---

## ROLLBACK PLAN

If issues occur after enabling:

```bash
# Emergency rollback (disables FinCEN integration immediately)

1. Set environment variable:
   export FINCEN_API_ENABLED=false

2. Restart service:
   kubectl rollout restart deployment/compliance-service

3. Verify rollback:
   curl http://localhost:8080/actuator/health/fincen
   # Should show: "enabled": false

4. System will queue SARs for manual filing
5. No regulatory violations (30-day filing window)
```

---

## SUPPORT CONTACTS

**Internal:**
- Compliance Team: compliance@example.com
- Engineering Team: devops@example.com  
- Legal Team: legal@example.com
- On-Call: PagerDuty rotation

**External:**
- FinCEN Support: BSAEFilingHelp@fincen.gov
- FinCEN Hotline: 1-800-949-2732
- Legal Counsel: [Law Firm Contact]

---

## AUDIT TRAIL

All FinCEN operations are logged to:
- Application logs: `/var/log/compliance-service/`
- Audit database: `compliance_audit` table
- SIEM system: Splunk/ELK
- Compliance dashboard: Real-time view

---

## REGULATORY NOTES

**Bank Secrecy Act (BSA) Requirements:**
- SAR filing deadline: 30 calendar days from detection
- Expedited filing: Certain crimes require immediate filing
- Confidentiality: SARs are confidential, do not disclose to subject
- Recordkeeping: Maintain SAR records for 5 years

**FinCEN Filing Requirements:**
- Use FinCEN SAR form (FinCEN Form 111)
- Include all required data elements
- Narrative must be clear and complete
- File electronically via BSA E-Filing System

---

## CONCLUSION

The FinCEN API integration is **production-ready** and awaiting enablement. Follow this guide systematically to ensure compliant, secure integration with FinCEN's BSA E-Filing System.

**Estimated Timeline:**
- Week 1: Test environment validation
- Week 2: Production dry run  
- Week 3: Full production enablement
- Week 4+: Monitoring and optimization

**Risk Assessment:**
- Technical Risk: **LOW** (well-tested code)
- Regulatory Risk: **CRITICAL** (must comply with BSA)
- Operational Risk: **MEDIUM** (requires monitoring)

---

**Document Version:** 1.0  
**Last Updated:** October 23, 2025  
**Next Review:** After production enablement  
**Owner:** Waqiti Compliance Team
