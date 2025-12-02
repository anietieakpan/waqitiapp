# DLQ Handler Update Plan

## Priority Classification

### CRITICAL Priority (Immediate Update Required)
These handlers deal with regulatory filing and transaction blocking - failures cannot be tolerated:

1. ✅ **SARFilingConsumerDlqHandler** - COMPLETE (already updated)
2. **CTRFilingEventConsumerDlqHandler** - Currency Transaction Reports
3. **ComplianceAlertRaisedConsumerDlqHandler** - Critical compliance alerts
4. **RegulatoryReportingConsumerDlqHandler** - Regulatory reports
5. **OFACSanctionsScreeningEventConsumerDlqHandler** - OFAC sanctions screening
6. **SanctionsScreeningConsumerDlqHandler** - General sanctions screening
7. **AssetFreezeEventsConsumerDlqHandler** - Asset freezing
8. **WalletFreezeComplianceConsumerDlqHandler** - Wallet freezing

### HIGH Priority
Compliance and AML monitoring:

9. **AMLAlertConsumerDlqHandler** - AML alerts
10. **AMLScreeningEventsConsumerDlqHandler** - AML screening
11. **FraudDetectedEventConsumerDlqHandler** - Fraud detection
12. **KYCVerificationExpiredConsumerDlqHandler** - KYC expiration
13. **ComplianceCriticalViolationsConsumerDlqHandler** - Critical violations
14. **RegulatoryBreachEventsConsumerDlqHandler** - Regulatory breaches

### MEDIUM Priority
Standard compliance events:

15-50. Various compliance reporting, monitoring, and audit handlers

### LOW Priority
Informational and non-critical events:

51-100+. Metrics, notifications, standard updates

## Implementation Pattern

Each handler should follow this pattern:

```java
@Override
protected DlqProcessingResult handleDlqEvent(Object event, Map<String, Object> headers) {
    try {
        log.error("[PRIORITY_LEVEL]: [Handler Name] message failed and sent to DLQ.");

        Exception exception = extractException(headers);
        if (exception == null) {
            exception = new RuntimeException("[Handler description] failed - message sent to DLQ");
        }

        DLQRecord dlqRecord = dlqRecoveryService.processDLQMessage(
            "[topic-name]",
            event,
            headers,
            exception,
            DLQPriority.[CRITICAL|HIGH|MEDIUM|LOW]
        );

        log.info("[Handler Name] DLQ record created: {}", dlqRecord.getId());

        return DlqProcessingResult.[MANUAL_INTERVENTION_REQUIRED|RETRY_POSSIBLE];

    } catch (Exception e) {
        log.error("CRITICAL ERROR: Failed to process [Handler Name] DLQ message.", e);

        try {
            log.error("CATASTROPHIC FAILURE: [Handler Name] DLQ handler failed. " +
                     "Event: {}, Headers: {}, Error: {}",
                     event, headers, e.getMessage());
        } catch (Exception loggingError) {
            System.err.println("SYSTEM CRITICAL: Unable to log [Handler Name] DLQ failure");
        }

        return DlqProcessingResult.PERMANENT_FAILURE;
    }
}
```

## Batch Update Strategy

1. **Phase 1:** Update CRITICAL handlers (8 handlers) - Do manually for accuracy
2. **Phase 2:** Update HIGH handlers (6 handlers) - Can use template
3. **Phase 3:** Update MEDIUM handlers (~35 handlers) - Batch with script
4. **Phase 4:** Update LOW handlers (~50+ handlers) - Batch with script

## Estimated Effort

- CRITICAL handlers: 2-3 hours (manual, careful implementation)
- HIGH handlers: 1-2 hours (template-based)
- MEDIUM handlers: 2-3 hours (script-assisted)
- LOW handlers: 2-3 hours (script-assisted)

**Total:** 1-2 days of focused work
