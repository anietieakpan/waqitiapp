# Notification Services Setup Guide

**Version:** 1.0
**Date:** November 10, 2025
**Services:** SendGrid Email, Slack Webhooks, PagerDuty

---

## TABLE OF CONTENTS

1. [Prerequisites](#prerequisites)
2. [SendGrid Email Setup](#sendgrid-email-setup)
3. [Slack Webhooks Setup](#slack-webhooks-setup)
4. [PagerDuty Setup](#pagerduty-setup)
5. [Environment Configuration](#environment-configuration)
6. [Testing & Validation](#testing--validation)
7. [Troubleshooting](#troubleshooting)

---

## PREREQUISITES

Before setting up notification services, ensure you have:

- ✅ Access to company email (for SendGrid account)
- ✅ Slack workspace admin permissions (or contact admin)
- ✅ PagerDuty account with admin access (or contact admin)
- ✅ Access to production environment variables/secrets management
- ✅ Ability to deploy configuration changes

---

## SENDGRID EMAIL SETUP

### Step 1: Create SendGrid Account

1. Go to https://sendgrid.com/
2. Sign up or log in with company account
3. Complete email verification

### Step 2: Create API Key

1. Navigate to **Settings** → **API Keys**
2. Click **Create API Key**
3. Name: `Waqiti Compliance Service - Production`
4. Permissions: **Mail Send** (Full Access)
5. Click **Create & View**
6. **IMPORTANT:** Copy the API key immediately (shown only once)

```
Example API Key:
SG.XXXXXXXXXXXXXXXXXXXX.XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
```

### Step 3: Verify Sender Domain

1. Navigate to **Settings** → **Sender Authentication**
2. Click **Authenticate Your Domain**
3. Select your DNS provider
4. Add the provided DNS records:
   ```
   Type: CNAME
   Host: em1234.example.com
   Value: u1234567.wl123.sendgrid.net

   Type: CNAME
   Host: s1._domainkey.example.com
   Value: s1.domainkey.u1234567.wl123.sendgrid.net

   Type: CNAME
   Host: s2._domainkey.example.com
   Value: s2.domainkey.u1234567.wl123.sendgrid.net
   ```
4. Wait for DNS propagation (5-30 minutes)
5. Click **Verify** in SendGrid dashboard

### Step 4: Configure Compliance Team Email

1. Create/verify distribution list: `compliance-team@example.com`
2. Add team members:
   - Chief Compliance Officer
   - Compliance Analysts
   - AML Officers
   - Regulatory Reporting Team

### Step 5: Test Email Delivery

```bash
curl --request POST \
  --url https://api.sendgrid.com/v3/mail/send \
  --header 'Authorization: Bearer YOUR_API_KEY' \
  --header 'Content-Type: application/json' \
  --data '{
    "personalizations": [{
      "to": [{"email": "compliance-team@example.com"}]
    }],
    "from": {"email": "compliance-no-reply@example.com", "name": "Waqiti Compliance"},
    "subject": "Test Email - Compliance Service Setup",
    "content": [{
      "type": "text/plain",
      "value": "This is a test email from the compliance service notification system."
    }]
  }'
```

**Expected Response:** `202 Accepted`

---

## SLACK WEBHOOKS SETUP

### Step 1: Create Slack Channels

Create three channels for different alert priorities:

1. **#compliance-critical**
   - Purpose: CRITICAL DLQ failures, regulatory violations
   - Members: @compliance-team, @engineering-oncall, @cto
   - Notification: @here for all messages

2. **#compliance-alerts**
   - Purpose: HIGH priority DLQ messages, escalations
   - Members: @compliance-team, @engineering-team
   - Notification: Normal

3. **#compliance-notifications**
   - Purpose: MEDIUM/LOW priority info, standard updates
   - Members: @compliance-team
   - Notification: Normal

### Step 2: Create Slack App

1. Go to https://api.slack.com/apps
2. Click **Create New App** → **From scratch**
3. App Name: `Waqiti Compliance Notifications`
4. Workspace: Select your workspace
5. Click **Create App**

### Step 3: Enable Incoming Webhooks

1. In app settings, navigate to **Incoming Webhooks**
2. Toggle **Activate Incoming Webhooks** to ON
3. Click **Add New Webhook to Workspace**
4. Select channel: `#compliance-critical`
5. Click **Allow**
6. Copy the Webhook URL

```
Example Webhook URL:
https://hooks.slack.com/services/T00000000/B00000000/XXXXXXXXXXXXXXXXXXXX
```

7. Repeat for `#compliance-alerts` and `#compliance-notifications`

### Step 4: Customize App Appearance

1. Navigate to **Basic Information**
2. **Display Information:**
   - App Name: `Waqiti Compliance Notifications`
   - Short Description: `Critical compliance and DLQ failure alerts`
   - App Icon: Upload compliance icon
   - Background Color: `#dc3545` (red for compliance alerts)

### Step 5: Test Webhook

```bash
# Test #compliance-critical webhook
curl -X POST https://hooks.slack.com/services/T00000000/B00000000/XXXXXXXXXXXXXXXXXXXX \
  -H 'Content-Type: application/json' \
  -d '{
    "text": "🚨 TEST: Compliance Service Notification System",
    "blocks": [
      {
        "type": "header",
        "text": {
          "type": "plain_text",
          "text": "🚨 CRITICAL COMPLIANCE ALERT"
        }
      },
      {
        "type": "section",
        "text": {
          "type": "mrkdwn",
          "text": "*This is a test message from the compliance service notification system.*"
        }
      }
    ]
  }'
```

**Expected Response:** `ok`

---

## PAGERDUTY SETUP

### Step 1: Create PagerDuty Service

1. Log in to https://app.pagerduty.com/
2. Navigate to **Services** → **Service Directory**
3. Click **New Service**
4. Configure service:
   - **Name:** Waqiti Compliance Service
   - **Description:** Critical compliance failures and DLQ alerts
   - **Escalation Policy:** Select existing or create new
   - **Incident Urgency:** High
   - **Auto-Resolve:** 4 hours
   - **Acknowledgment Timeout:** 30 minutes

### Step 2: Configure Escalation Policy

1. Navigate to **Escalation Policies**
2. Create/Edit policy: `Compliance Oncall Escalation`
3. Configure escalation levels:
   ```
   Level 1 (Immediately):
     - Compliance Officer on-call
     - Notify via: Phone call, SMS, Push

   Level 2 (After 15 minutes):
     - Senior Compliance Manager
     - Chief Compliance Officer
     - Notify via: Phone call, SMS, Email

   Level 3 (After 30 minutes):
     - CTO
     - VP Engineering
     - Notify via: Phone call, SMS
   ```

### Step 3: Enable Events API v2 Integration

1. In service settings, navigate to **Integrations**
2. Click **Add Integration**
3. Select **Events API v2**
4. Click **Add**
5. Copy the **Integration Key**

```
Example Integration Key:
R00000000000000000000000000XXXX
```

### Step 4: Configure On-Call Schedule

1. Navigate to **People** → **On-Call Schedules**
2. Create schedule: `Compliance Team On-Call`
3. Configure rotation:
   - **Rotation Type:** Weekly
   - **Handoff Time:** Monday 9:00 AM
   - **Time Zone:** Company timezone
   - **Team Members:** Add compliance team

### Step 5: Test Integration

```bash
curl --request POST \
  --url https://events.pagerduty.com/v2/enqueue \
  --header 'Content-Type: application/json' \
  --data '{
    "routing_key": "YOUR_INTEGRATION_KEY",
    "event_action": "trigger",
    "dedup_key": "compliance-test-incident",
    "payload": {
      "summary": "TEST: Compliance Service Notification System",
      "source": "compliance-service",
      "severity": "info",
      "custom_details": {
        "test": "true",
        "message": "This is a test incident from the compliance service"
      }
    }
  }'
```

**Expected Response:**
```json
{
  "status": "success",
  "message": "Event processed",
  "dedup_key": "compliance-test-incident"
}
```

**Resolve test incident:**
```bash
curl --request POST \
  --url https://events.pagerduty.com/v2/enqueue \
  --header 'Content-Type: application/json' \
  --data '{
    "routing_key": "YOUR_INTEGRATION_KEY",
    "event_action": "resolve",
    "dedup_key": "compliance-test-incident"
  }'
```

---

## ENVIRONMENT CONFIGURATION

### Application Properties

Add to `application.properties` or `application.yml`:

```yaml
# SendGrid Configuration
sendgrid:
  api-key: ${SENDGRID_API_KEY}
  enabled: true
  from-email: compliance-no-reply@example.com
  from-name: Waqiti Compliance System
  compliance-team-email: compliance-team@example.com

# Slack Configuration
slack:
  enabled: true
  webhook:
    critical: ${SLACK_WEBHOOK_CRITICAL}
    alerts: ${SLACK_WEBHOOK_ALERTS}
    notifications: ${SLACK_WEBHOOK_NOTIFICATIONS}

# PagerDuty Configuration
pagerduty:
  enabled: true
  integration-key: ${PAGERDUTY_INTEGRATION_KEY}

# Compliance Notifications
compliance:
  notifications:
    enabled: true
    executive-enabled: true
    emergency-phone-enabled: true
    retention-days: 365
```

### Environment Variables

Set these environment variables in your deployment environment:

```bash
# SendGrid
export SENDGRID_API_KEY="SG.XXXXXXXXXXXXXXXXXXXX.XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"

# Slack
export SLACK_WEBHOOK_CRITICAL="https://hooks.slack.com/services/T00000000/B00000000/XXXXXXXXXXXXXXXXXXXX"
export SLACK_WEBHOOK_ALERTS="https://hooks.slack.com/services/T00000000/B00000001/YYYYYYYYYYYYYYYYYYYY"
export SLACK_WEBHOOK_NOTIFICATIONS="https://hooks.slack.com/services/T00000000/B00000002/ZZZZZZZZZZZZZZZZZZZZ"

# PagerDuty
export PAGERDUTY_INTEGRATION_KEY="R00000000000000000000000000XXXX"
```

### Kubernetes Secrets (if applicable)

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: compliance-service-notifications
  namespace: production
type: Opaque
stringData:
  SENDGRID_API_KEY: "SG.XXXX..."
  SLACK_WEBHOOK_CRITICAL: "https://hooks.slack.com/services/T00000000/B00000000/XXXX..."
  SLACK_WEBHOOK_ALERTS: "https://hooks.slack.com/services/T00000000/B00000001/YYYY..."
  SLACK_WEBHOOK_NOTIFICATIONS: "https://hooks.slack.com/services/T00000000/B00000002/ZZZZ..."
  PAGERDUTY_INTEGRATION_KEY: "R00000000000000000000000000XXXX"
```

### AWS Secrets Manager (if applicable)

```bash
aws secretsmanager create-secret \
  --name compliance-service/notifications \
  --description "Notification service credentials for compliance-service" \
  --secret-string '{
    "SENDGRID_API_KEY": "SG.XXXX...",
    "SLACK_WEBHOOK_CRITICAL": "https://hooks.slack.com/...",
    "SLACK_WEBHOOK_ALERTS": "https://hooks.slack.com/...",
    "SLACK_WEBHOOK_NOTIFICATIONS": "https://hooks.slack.com/...",
    "PAGERDUTY_INTEGRATION_KEY": "R00000000000000000000000000XXXX"
  }'
```

---

## TESTING & VALIDATION

### End-to-End Test Script

Create `test-notifications.sh`:

```bash
#!/bin/bash
set -e

echo "=== Compliance Service Notification Test ==="
echo ""

# Test SendGrid
echo "Testing SendGrid email delivery..."
curl -X POST http://localhost:8080/api/compliance/test/notification/email \
  -H 'Content-Type: application/json' \
  -d '{"recipient": "compliance-team@example.com", "subject": "Test Email", "message": "Test"}' \
  && echo "✅ Email test passed" || echo "❌ Email test failed"

# Test Slack
echo ""
echo "Testing Slack notifications..."
curl -X POST http://localhost:8080/api/compliance/test/notification/slack \
  -H 'Content-Type: application/json' \
  -d '{"channel": "#compliance-critical", "message": "Test Slack Alert"}' \
  && echo "✅ Slack test passed" || echo "❌ Slack test failed"

# Test PagerDuty
echo ""
echo "Testing PagerDuty incidents..."
curl -X POST http://localhost:8080/api/compliance/test/notification/pagerduty \
  -H 'Content-Type: application/json' \
  -d '{"summary": "Test Incident", "severity": "info"}' \
  && echo "✅ PagerDuty test passed" || echo "❌ PagerDuty test failed"

echo ""
echo "=== Test Complete ==="
```

### Manual Validation Checklist

- [ ] SendGrid email received by compliance team
- [ ] Slack message appears in #compliance-critical
- [ ] Slack message appears in #compliance-alerts
- [ ] Slack message appears in #compliance-notifications
- [ ] PagerDuty incident created
- [ ] PagerDuty notification received (phone/SMS/push)
- [ ] PagerDuty incident resolved successfully
- [ ] All notifications logged in Redis
- [ ] All notifications tracked in audit trail

---

## TROUBLESHOOTING

### SendGrid Issues

**Problem:** API returns 401 Unauthorized
**Solution:**
- Verify API key is correct
- Check API key has "Mail Send" permission
- Regenerate API key if compromised

**Problem:** Emails not delivered
**Solution:**
- Check domain authentication status
- Verify recipient email exists
- Check SendGrid activity logs
- Review spam folder

### Slack Issues

**Problem:** Webhook returns 404 Not Found
**Solution:**
- Verify webhook URL is complete and correct
- Check if webhook was revoked in Slack settings
- Regenerate webhook if needed

**Problem:** Messages not appearing in channel
**Solution:**
- Verify bot has access to channel
- Check channel name matches webhook configuration
- Test webhook with curl command

### PagerDuty Issues

**Problem:** API returns 400 Bad Request
**Solution:**
- Verify integration key format
- Check JSON payload structure
- Ensure required fields (summary, source, severity) are present

**Problem:** Incident not triggering notifications
**Solution:**
- Check escalation policy configuration
- Verify on-call schedule is active
- Review notification rules in user settings
- Check if incident was auto-resolved

### General Issues

**Problem:** Service not sending notifications
**Solution:**
- Check `compliance.notifications.enabled=true` in config
- Verify all environment variables are set
- Review application logs for errors
- Test with fallback logging (should see log messages)

---

## MONITORING & MAINTENANCE

### Daily Checks

- Monitor #compliance-critical for any alerts
- Review PagerDuty incident dashboard
- Check email delivery rates in SendGrid

### Weekly Maintenance

- Review notification delivery metrics
- Update on-call schedule in PagerDuty
- Rotate API keys if policy requires
- Test escalation procedures

### Monthly Review

- Analyze notification patterns
- Optimize alert thresholds
- Review and update escalation policies
- Conduct notification drill

---

## SECURITY BEST PRACTICES

✅ **API Keys & Webhooks:**
- Never commit API keys to source control
- Use environment variables or secrets management
- Rotate keys quarterly or after any security incident
- Limit API key permissions to minimum required

✅ **Access Control:**
- Restrict PagerDuty admin access
- Use role-based access for Slack workspace
- Monitor SendGrid account activity

✅ **Audit Trail:**
- All notifications logged to Redis
- Delivery status tracked
- Failed notifications alerted

---

## SUPPORT CONTACTS

**SendGrid Support:**
- Email: support@sendgrid.com
- Docs: https://docs.sendgrid.com/

**Slack Support:**
- Help Center: https://slack.com/help
- API Docs: https://api.slack.com/

**PagerDuty Support:**
- Email: support@pagerduty.com
- Docs: https://support.pagerduty.com/

**Internal Contacts:**
- Compliance Team: compliance@example.com
- Engineering On-Call: oncall@example.com
- DevOps Team: devops@example.com

---

**Document Status:** COMPLETE
**Last Updated:** November 10, 2025
**Next Review:** December 10, 2025
