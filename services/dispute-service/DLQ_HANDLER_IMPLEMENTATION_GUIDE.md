# DLQ Handler Implementation Guide

## Current Status

All 18 DLQ handlers exist but have TODO placeholders at line 51.
They all extend `BaseDlqConsumer<Object>` from `com.example.common.kafka.BaseDlqConsumer`.

## Structure Analysis

Each handler follows this pattern:
```java
@Override
protected DlqProcessingResult handleDlqEvent(Object event, Map<String, Object> headers) {
    try {
        // TODO: Implement custom recovery logic  ← LINE 51 - NEEDS IMPLEMENTATION
        log.warn("DLQ event received but no custom recovery logic implemented");
        return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
    } catch (Exception e) {
        log.error("Error handling DLQ event", e);
        return DlqProcessingResult.PERMANENT_FAILURE;
    }
}
```

## Implementation Strategy for Each Handler

### Critical Handlers (Financial Impact)

#### 1. DisputeAutoResolutionConsumerDlqHandler
**Replace TODO with:**
```java
@Override
protected DlqProcessingResult handleDlqEvent(Object event, Map<String, Object> headers) {
    try {
        log.error("CRITICAL: Auto-resolution failed - DLQ event: {}", event);

        // Parse event
        Map<String, Object> eventData = (Map<String, Object>) event;
        String disputeId = (String) eventData.get("disputeId");
        String resolutionDecision = (String) eventData.get("resolutionDecision");

        // Store in DLQ database
        storeDLQEntry(eventData, headers, "DisputeAutoResolution");

        // Financial operations ALWAYS require manual review
        createHighPriorityTicket(disputeId, "Auto-resolution failed", eventData);

        // Alert operations team
        sendCriticalAlert("Dispute auto-resolution failed", disputeId);

        return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

    } catch (Exception e) {
        log.error("Failed to process DLQ event", e);
        return DlqProcessingResult.PERMANENT_FAILURE;
    }
}
```

#### 2. DisputeProvisionalCreditIssuedConsumerDlqHandler
```java
@Override
protected DlqProcessingResult handleDlqEvent(Object event, Map<String, Object> headers) {
    try {
        log.error("CRITICAL: Provisional credit issuance failed - DLQ event: {}", event);

        Map<String, Object> eventData = (Map<String, Object>) event;
        String disputeId = (String) eventData.get("disputeId");
        String customerId = (String) eventData.get("customerId");
        BigDecimal amount = new BigDecimal(eventData.get("amount").toString());

        // Store for recovery
        storeDLQEntry(eventData, headers, "ProvisionalCredit");

        // CRITICAL: Money not issued - immediate escalation
        createEmergencyTicket(disputeId, "Provisional credit failed", amount);
        pageOnCall("Provisional credit issuance failed for customer: " + customerId);

        return DlqProcessingResult.ESCALATE_TO_EMERGENCY;

    } catch (Exception e) {
        log.error("Failed to process provisional credit DLQ", e);
        return DlqProcessingResult.PERMANENT_FAILURE;
    }
}
```

#### 3. ChargebackInitiatedConsumerDlqHandler
```java
@Override
protected DlqProcessingResult handleDlqEvent(Object event, Map<String, Object> headers) {
    try {
        log.error("CRITICAL: Chargeback initiation failed - DLQ event: {}", event);

        Map<String, Object> eventData = (Map<String, Object>) event;
        String disputeId = (String) eventData.get("disputeId");
        String merchantId = (String) eventData.get("merchantId");

        storeDLQEntry(eventData, headers, "ChargebackInitiation");

        // Chargeback failures require immediate action
        createHighPriorityTicket(disputeId, "Chargeback initiation failed", eventData);
        notifyMerchantTeam(merchantId, "Chargeback processing issue");

        return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

    } catch (Exception e) {
        log.error("Failed to process chargeback DLQ", e);
        return DlqProcessingResult.PERMANENT_FAILURE;
    }
}
```

### High Priority Handlers

#### 4-11. Investigation/Escalation Handlers
Template for:
- DisputeInvestigationsConsumerDlqHandler
- ChargebackInvestigationsConsumerDlqHandler
- DisputeEscalationsConsumerDlqHandler
- DisputeRejectionsConsumerDlqHandler

```java
@Override
protected DlqProcessingResult handleDlqEvent(Object event, Map<String, Object> headers) {
    try {
        Map<String, Object> eventData = (Map<String, Object>) event;
        String disputeId = (String) eventData.getOrDefault("disputeId", "UNKNOWN");

        log.warn("{} event failed - DisputeId: {}", getServiceName(), disputeId);

        // Store for later processing
        storeDLQEntry(eventData, headers, getServiceName());

        // Create ticket for review
        createStandardTicket(disputeId, getServiceName() + " failed", eventData);

        // Can be retried after investigation
        return DlqProcessingResult.RETRY_AFTER_DELAY;

    } catch (Exception e) {
        log.error("Failed to process {} DLQ event", getServiceName(), e);
        return DlqProcessingResult.PERMANENT_FAILURE;
    }
}
```

### Medium Priority (Audit/Alerts)

#### 12-15. Audit and Alert Handlers
Template for:
- ChargebackAuditEventsConsumerDlqHandler
- ChargebackPreventionEventsConsumerDlqHandler
- ChargebackAlertCriticalFailuresConsumerDlqHandler
- ChargebackAlertsConsumerDlqHandler

```java
@Override
protected DlqProcessingResult handleDlqEvent(Object event, Map<String, Object> headers) {
    try {
        Map<String, Object> eventData = (Map<String, Object>) event;

        log.info("{} event in DLQ - Not critical, storing for analysis", getServiceName());

        // Store for audit purposes
        storeDLQEntry(eventData, headers, getServiceName());

        // Audit/Alert failures are not critical - can be processed later
        // Create low-priority ticket
        createLowPriorityTicket("N/A", getServiceName() + " event failed", eventData);

        // Can be safely retried or discarded after audit
        return DlqProcessingResult.DISCARD_WITH_AUDIT;

    } catch (Exception e) {
        log.error("Failed to process {} DLQ event", getServiceName(), e);
        return DlqProcessingResult.PERMANENT_FAILURE;
    }
}
```

### Low Priority (Metrics/Monitoring)

#### 16-18. Circuit Breaker and Metrics Handlers
Template for:
- CircuitBreakerMetricsConsumerDlqHandler
- CircuitBreakerRecommendationsConsumerDlqHandler
- CircuitBreakerEvaluationsConsumerDlqHandler
- ClusteringAlertsConsumerDlqHandler

```java
@Override
protected DlqProcessingResult handleDlqEvent(Object event, Map<String, Object> headers) {
    try {
        log.debug("{} metrics event in DLQ - Non-critical", getServiceName());

        // Metrics failures are not critical - just log
        Map<String, Object> eventData = (Map<String, Object>) event;
        storeDLQEntry(eventData, headers, getServiceName());

        // Metrics can be lost without major impact
        return DlqProcessingResult.DISCARD_WITH_AUDIT;

    } catch (Exception e) {
        log.warn("Failed to process {} DLQ event - ignoring", getServiceName(), e);
        return DlqProcessingResult.DISCARD_WITH_AUDIT;
    }
}
```

## Helper Methods to Add

Add these helper methods to each handler:

```java
private void storeDLQEntry(Map<String, Object> eventData, Map<String, Object> headers, String source) {
    try {
        DLQEntry entry = DLQEntry.builder()
                .id(UUID.randomUUID().toString())
                .eventId(extractEventId(eventData))
                .sourceTopic(source)
                .eventJson(objectMapper.writeValueAsString(eventData))
                .errorMessage(extractErrorMessage(headers))
                .status(DLQStatus.PENDING_REVIEW)
                .createdAt(LocalDateTime.now())
                .build();

        dlqRepository.save(entry);
        log.info("Stored DLQ entry: {}", entry.getId());
    } catch (Exception e) {
        log.error("Failed to store DLQ entry", e);
    }
}

private void createHighPriorityTicket(String disputeId, String reason, Map<String, Object> eventData) {
    String ticketId = "DLQ-HIGH-" + UUID.randomUUID().toString().substring(0, 8);
    log.error("HIGH PRIORITY TICKET: {} - DisputeId: {}, Reason: {}", ticketId, disputeId, reason);
    // TODO: Integrate with Jira/ServiceNow
}

private void createEmergencyTicket(String disputeId, String reason, BigDecimal amount) {
    String ticketId = "DLQ-EMERGENCY-" + UUID.randomUUID().toString().substring(0, 8);
    log.error("🚨 EMERGENCY TICKET: {} - DisputeId: {}, Amount: {}", ticketId, disputeId, amount);
    // TODO: Integrate with PagerDuty
}

private void sendCriticalAlert(String message, String disputeId) {
    log.error("🔴 CRITICAL ALERT: {} - DisputeId: {}", message, disputeId);
    // TODO: Send to Slack/Teams
}

private String extractEventId(Map<String, Object> event) {
    if (event.containsKey("eventId")) return event.get("eventId").toString();
    if (event.containsKey("disputeId")) return event.get("disputeId").toString();
    return "EVT-" + UUID.randomUUID().toString();
}
```

## Required Dependencies

Add to each handler that needs database access:
```java
@Service
@Slf4j
public class XyzConsumerDlqHandler extends BaseDlqConsumer<Object> {

    private final DLQEntryRepository dlqRepository;
    private final ObjectMapper objectMapper;

    public XyzConsumerDlqHandler(MeterRegistry meterRegistry,
                                  DLQEntryRepository dlqRepository,
                                  ObjectMapper objectMapper) {
        super(meterRegistry);
        this.dlqRepository = dlqRepository;
        this.objectMapper = objectMapper;
    }

    // ... rest of implementation
}
```

## Implementation Priority

1. **CRITICAL (Do First):**
   - DisputeAutoResolutionConsumerDlqHandler
   - DisputeProvisionalCreditIssuedConsumerDlqHandler
   - ChargebackInitiatedConsumerDlqHandler

2. **HIGH:**
   - DisputeInvestigationsConsumerDlqHandler
   - ChargebackInvestigationsConsumerDlqHandler
   - DisputeEscalationsConsumerDlqHandler

3. **MEDIUM:**
   - All Audit/Alert handlers

4. **LOW:**
   - All Metrics/Circuit Breaker handlers

## Testing Requirements

After implementation, test each handler:
1. Simulate DLQ event
2. Verify database entry created
3. Verify correct DlqProcessingResult returned
4. Verify logs captured
5. Verify metrics incremented

## Estimated Effort

- 3 Critical handlers: 2 hours
- 8 High/Medium handlers: 3 hours
- 7 Low priority handlers: 2 hours
- **Total: 7 hours**

All handlers now have clear implementation patterns based on criticality!
