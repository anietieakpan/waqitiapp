# Payroll Service - Kafka Events Documentation

## Overview

The Payroll Service uses Apache Kafka for asynchronous, event-driven processing. This document describes all Kafka topics, event schemas, and integration patterns.

---

## Kafka Topics

### Consumer Topics (Incoming Events)

#### 1. `payroll-processing-events`
**Description**: Main topic for payroll batch processing requests

**Configuration**:
```bash
kafka-topics.sh --create \
  --topic payroll-processing-events \
  --partitions 10 \
  --replication-factor 3 \
  --config retention.ms=604800000 \
  --config compression.type=lz4 \
  --bootstrap-server kafka:9092
```

**Producer**: API Gateway, Scheduled Jobs, Admin Portal
**Consumer**: `PayrollProcessingEventConsumer`
**Event Schema**: `PayrollProcessingEvent`
**Partition Key**: `companyId` (ensures all batches for same company processed in order)
**Retention**: 7 days

**Message Format**:
```json
{
  "eventId": "evt-123",
  "eventType": "PAYROLL_PROCESSING_REQUEST",
  "timestamp": "2025-01-09T10:00:00.000Z",
  "correlationId": "corr-456",
  "version": 1,
  "retry": false,
  "retryCount": 0,
  "payrollBatchId": "PB-COMP01-ABC123",
  "companyId": "COMP001",
  "companyName": "Acme Corporation",
  "payPeriod": "2025-01-15",
  "payDate": "2025-01-17",
  "payrollType": "REGULAR",
  "employees": [
    {
      "employeeId": "EMP001",
      "employeeName": "John Doe",
      "employeeEmail": "john.doe@example.com",
      "ssn": "***-**-1234",
      "hourlyRate": 25.00,
      "hoursWorked": 80.00,
      "grossAmount": 2000.00,
      "filingStatus": "SINGLE",
      "exemptions": 1,
      "state": "CA",
      "routingNumber": "121000248",
      "accountNumber": "***1234",
      "accountType": "CHECKING"
    }
  ],
  "totalGrossAmount": 100000.00,
  "employeeCount": 50,
  "requiresApproval": false,
  "submittedBy": "admin@acme.com"
}
```

---

### Producer Topics (Outgoing Events)

#### 2. `payroll-processed-events`
**Description**: Payroll batch processing completion notifications

**Configuration**:
```bash
kafka-topics.sh --create \
  --topic payroll-processed-events \
  --partitions 5 \
  --replication-factor 3 \
  --config retention.ms=2592000000 \
  --bootstrap-server kafka:9092
```

**Producer**: `PayrollProcessingEventConsumer`
**Consumer**: Notification Service, Reporting Service, Analytics Service
**Event Schema**: `PayrollProcessedEvent`
**Partition Key**: `companyId`
**Retention**: 30 days

**Message Format**:
```json
{
  "eventId": "evt-789",
  "eventType": "PAYROLL_PROCESSED",
  "timestamp": "2025-01-09T10:15:00.000Z",
  "correlationId": "corr-456",
  "payrollBatchId": "PB-COMP01-ABC123",
  "companyId": "COMP001",
  "status": "COMPLETED",
  "totalEmployees": 50,
  "successfulPayments": 48,
  "failedPayments": 2,
  "totalGrossAmount": 100000.00,
  "totalDeductions": 15000.00,
  "totalTaxWithheld": 22000.00,
  "totalNetAmount": 63000.00,
  "totalFederalTax": 12000.00,
  "totalStateTax": 5000.00,
  "totalSocialSecurityTax": 3500.00,
  "totalMedicareTax": 1500.00,
  "processingStartedAt": "2025-01-09T10:00:00.000Z",
  "processingCompletedAt": "2025-01-09T10:15:00.000Z",
  "processingTimeMs": 900000,
  "complianceViolations": 0,
  "settlementDate": "2025-01-17",
  "failedPaymentDetails": [
    {
      "employeeId": "EMP049",
      "employeeName": "Jane Smith",
      "amount": 2500.00,
      "failureReason": "Invalid bank account",
      "errorCode": "INVALID_ACCOUNT"
    }
  ]
}
```

---

#### 3. `tax-filing-events`
**Description**: Tax reporting and filing notifications

**Configuration**:
```bash
kafka-topics.sh --create \
  --topic tax-filing-events \
  --partitions 3 \
  --replication-factor 3 \
  --config retention.ms=31536000000 \
  --bootstrap-server kafka:9092
```

**Producer**: `PayrollProcessingEventConsumer`, `ReportingService`
**Consumer**: Tax Filing Service, Compliance Service, Archive Service
**Event Schema**: `TaxFilingEvent`
**Partition Key**: `companyId`
**Retention**: 365 days (1 year for tax records)

**Message Format**:
```json
{
  "eventId": "tax-evt-001",
  "eventType": "TAX_REPORT_GENERATED",
  "timestamp": "2025-01-09T10:15:00.000Z",
  "correlationId": "corr-456",
  "companyId": "COMP001",
  "companyEIN": "12-3456789",
  "companyName": "Acme Corporation",
  "taxFormType": "941",
  "taxYear": 2025,
  "taxQuarter": 1,
  "totalWages": 500000.00,
  "totalFederalTaxWithheld": 75000.00,
  "totalStateTaxWithheld": 25000.00,
  "totalSocialSecurityTax": 31000.00,
  "totalMedicareTax": 7250.00,
  "filingStatus": "PENDING",
  "filingMethod": "E_FILE",
  "dueDate": "2025-04-30",
  "employeeCount": 50,
  "reportUrl": "https://storage.example.com/tax/941-COMP001-2025-Q1.pdf"
}
```

---

#### 4. `compliance-alert-events`
**Description**: Compliance violation alerts

**Configuration**:
```bash
kafka-topics.sh --create \
  --topic compliance-alert-events \
  --partitions 3 \
  --replication-factor 3 \
  --config retention.ms=7776000000 \
  --bootstrap-server kafka:9092
```

**Producer**: `ComplianceService`
**Consumer**: Compliance Dashboard, Alert Service, Legal Team Notifications
**Event Schema**: `ComplianceAlertEvent`
**Partition Key**: `companyId`
**Retention**: 90 days

**Message Format**:
```json
{
  "eventId": "comp-evt-001",
  "eventType": "COMPLIANCE_VIOLATION",
  "timestamp": "2025-01-09T10:05:00.000Z",
  "correlationId": "corr-456",
  "companyId": "COMP001",
  "companyName": "Acme Corporation",
  "payrollBatchId": "PB-COMP01-ABC123",
  "violationType": "MINIMUM_WAGE",
  "severity": "CRITICAL",
  "regulation": "FLSA Section 6",
  "violationDescription": "Employee EMP025 hourly rate ($6.50) below minimum wage ($7.25)",
  "employeeId": "EMP025",
  "employeeName": "Robert Johnson",
  "violations": [
    {
      "field": "hourly_rate",
      "expectedValue": "7.25",
      "actualValue": "6.50",
      "reason": "Below federal minimum wage"
    }
  ],
  "actionRequired": true,
  "suggestedAction": "Increase hourly rate to at least $7.25 before processing payroll",
  "notifyEmails": ["compliance@acme.com", "legal@acme.com"]
}
```

---

#### 5. `payment-notification-events`
**Description**: Employee payment notifications (for email/SMS/push)

**Configuration**:
```bash
kafka-topics.sh --create \
  --topic payment-notification-events \
  --partitions 10 \
  --replication-factor 3 \
  --config retention.ms=604800000 \
  --bootstrap-server kafka:9092
```

**Producer**: `PayrollProcessingEventConsumer`
**Consumer**: Notification Service
**Event Schema**: `PaymentNotificationEvent`
**Partition Key**: `employeeId`
**Retention**: 7 days

**Message Format**:
```json
{
  "eventId": "pay-notif-001",
  "eventType": "PAYMENT_PROCESSED",
  "timestamp": "2025-01-09T10:15:00.000Z",
  "correlationId": "corr-456",
  "employeeId": "EMP001",
  "employeeName": "John Doe",
  "employeeEmail": "john.doe@example.com",
  "employeePhoneNumber": "+1-555-0100",
  "companyId": "COMP001",
  "companyName": "Acme Corporation",
  "payrollBatchId": "PB-COMP01-ABC123",
  "payPeriod": "2025-01-15",
  "payDate": "2025-01-17",
  "grossAmount": 2000.00,
  "totalDeductions": 300.00,
  "totalTaxWithheld": 440.00,
  "netAmount": 1260.00,
  "federalTax": 240.00,
  "stateTax": 100.00,
  "socialSecurityTax": 70.00,
  "medicareTax": 30.00,
  "health401k": 150.00,
  "healthInsurance": 100.00,
  "paymentMethod": "ACH",
  "accountLast4": "1234",
  "transactionId": "ACH-xyz789",
  "settlementDate": "2025-01-17",
  "paymentStatus": "SUBMITTED",
  "sendEmail": true,
  "sendSMS": false,
  "sendPush": true
}
```

---

#### 6. `audit-events`
**Description**: Audit trail for all payroll operations (SOX/GDPR compliance)

**Configuration**:
```bash
kafka-topics.sh --create \
  --topic audit-events \
  --partitions 5 \
  --replication-factor 3 \
  --config retention.ms=157680000000 \
  --config compression.type=gzip \
  --bootstrap-server kafka:9092
```

**Producer**: `AuditService`
**Consumer**: Audit Log Service, SIEM, Compliance Archive
**Event Schema**: `AuditEvent`
**Partition Key**: `companyId`
**Retention**: 5 years (regulatory requirement)

**Used by AuditService internally** - See `AuditService.AuditEvent` class

---

#### 7. `notification-events`
**Description**: General notification events (used by NotificationService)

**Configuration**:
```bash
kafka-topics.sh --create \
  --topic notification-events \
  --partitions 5 \
  --replication-factor 3 \
  --config retention.ms=604800000 \
  --bootstrap-server kafka:9092
```

**Producer**: `NotificationService`
**Consumer**: Notification Gateway, Email Service, SMS Service, Push Notification Service
**Event Schema**: `NotificationEvent` (from NotificationService)
**Partition Key**: `recipient`
**Retention**: 7 days

**Used by NotificationService internally** - See `NotificationService.NotificationEvent` class

---

#### 8. `alert-events`
**Description**: Critical system alerts

**Configuration**:
```bash
kafka-topics.sh --create \
  --topic alert-events \
  --partitions 3 \
  --replication-factor 3 \
  --config retention.ms=2592000000 \
  --bootstrap-server kafka:9092
```

**Producer**: `NotificationService`, `AuditService`
**Consumer**: Alert Management Service, PagerDuty, Ops Team Dashboard
**Event Schema**: `AlertEvent` (from NotificationService)
**Partition Key**: `service`
**Retention**: 30 days

**Used by NotificationService internally** - See `NotificationService.AlertEvent` class

---

### Dead Letter Queue Topics

#### 9. `payroll-processing-events-dlq`
**Description**: Failed payroll processing events

**Configuration**:
```bash
kafka-topics.sh --create \
  --topic payroll-processing-events-dlq \
  --partitions 3 \
  --replication-factor 3 \
  --config retention.ms=2592000000 \
  --bootstrap-server kafka:9092
```

**Producer**: `PayrollProcessingEventConsumer` (on unrecoverable failures)
**Consumer**: Manual review, Admin Dashboard
**Event Schema**: Same as `payroll-processing-events` + error metadata
**Retention**: 30 days

---

## Event Schema Versioning

All events include a `version` field for schema evolution:

```java
private Integer version; // Event schema version (e.g., 1, 2, 3)
```

### Version Compatibility
- **Version 1**: Initial schema
- **Version 2**: Added fields (backward compatible)
- **Version 3**: Changed field types (requires consumer upgrade)

### Best Practices
1. Always add new optional fields (never remove or change existing)
2. Increment version number when schema changes
3. Consumers should handle multiple versions gracefully
4. Use JSON Schema validation in production

---

## Kafka Configuration in application.yml

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}

    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: all
      retries: 3
      properties:
        enable.idempotence: true
        max.in.flight.requests.per.connection: 5
        compression.type: lz4

    consumer:
      group-id: payroll-service
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      auto-offset-reset: earliest
      enable-auto-commit: false
      properties:
        spring.json.trusted.packages: "*"
        isolation.level: read_committed
```

---

## Consumer Configuration

### PayrollProcessingEventConsumer

```java
@KafkaListener(
    topics = "payroll-processing-events",
    groupId = "payroll-service",
    concurrency = "3", // 3 concurrent consumers
    containerFactory = "kafkaListenerContainerFactory"
)
@Transactional(isolation = Isolation.SERIALIZABLE)
@Retryable(
    value = {PayrollProcessingException.class},
    maxAttempts = 3,
    backoff = @Backoff(delay = 5000, multiplier = 2)
)
public void handlePayrollProcessingEvent(@Payload PayrollProcessingEvent event) {
    // Process payroll batch...
}
```

---

## Error Handling Strategy

### Retryable Errors
- Database connection failures
- Temporary service unavailability
- Network timeouts

**Action**: Retry with exponential backoff (3 attempts)

### Non-Retryable Errors
- Invalid event schema
- Business validation failures (compliance violations)
- Insufficient funds

**Action**: Send to DLQ immediately

---

## Monitoring & Observability

### Key Metrics to Monitor

```yaml
# Kafka Consumer Metrics
kafka_consumer_fetch_latency_avg
kafka_consumer_records_lag_max
kafka_consumer_records_consumed_total

# Kafka Producer Metrics
kafka_producer_record_send_total
kafka_producer_record_error_total
kafka_producer_request_latency_avg

# Payroll-Specific Metrics
payroll_events_processed_total{topic="payroll-processing-events"}
payroll_events_failed_total{topic="payroll-processing-events"}
payroll_processing_duration_seconds{quantile="0.95"}
```

### Alerts

```yaml
# Consumer Lag Alert
- alert: PayrollConsumerLag
  expr: kafka_consumer_records_lag_max{topic="payroll-processing-events"} > 1000
  for: 5m
  annotations:
    summary: "Payroll service consumer lag is high"

# Processing Failure Rate Alert
- alert: PayrollProcessingFailureRate
  expr: rate(payroll_events_failed_total[5m]) > 0.1
  for: 5m
  annotations:
    summary: "Payroll processing failure rate > 10%"
```

---

## Testing Kafka Events

### Publish Test Event
```bash
# Produce a test event
echo '{
  "eventId": "test-001",
  "eventType": "PAYROLL_PROCESSING_REQUEST",
  "timestamp": "2025-01-09T10:00:00.000Z",
  "correlationId": "test-corr-001",
  "payrollBatchId": "TEST-BATCH-001",
  "companyId": "TEST-COMPANY",
  "employees": [],
  "employeeCount": 0
}' | kafka-console-producer.sh \
  --broker-list localhost:9092 \
  --topic payroll-processing-events \
  --property "key=TEST-COMPANY"
```

### Consume Events
```bash
# Consume from topic
kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic payroll-processed-events \
  --from-beginning \
  --property print.key=true
```

---

## Troubleshooting

### Issue: Consumer not receiving events
```bash
# Check consumer group lag
kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe \
  --group payroll-service

# Reset consumer offset (use with caution!)
kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --group payroll-service \
  --topic payroll-processing-events \
  --reset-offsets --to-earliest \
  --execute
```

### Issue: Events stuck in DLQ
```bash
# Check DLQ messages
kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic payroll-processing-events-dlq \
  --from-beginning
```

---

## Files Created

1. `PayrollProcessingEvent.java` - Incoming payroll batch requests
2. `PayrollProcessedEvent.java` - Payroll completion notifications
3. `TaxFilingEvent.java` - Tax reporting events
4. `ComplianceAlertEvent.java` - Compliance violation alerts
5. `PaymentNotificationEvent.java` - Employee payment notifications
6. `KAFKA_EVENTS.md` - This documentation

---

**Last Updated**: 2025-01-09
**Version**: 1.0
