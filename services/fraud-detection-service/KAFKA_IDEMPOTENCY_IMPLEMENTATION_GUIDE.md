# KAFKA IDEMPOTENCY IMPLEMENTATION GUIDE

**Status:** 1/68 consumers updated
**Priority:** CRITICAL - Prevents duplicate processing and data corruption
**Created:** October 16, 2025

---

## PROBLEM

Kafka guarantees "at-least-once" delivery, not "exactly-once":
- **Consumer restarts** → Reprocess messages from last commit
- **Network failures** → Retry causes duplicates
- **Rebalancing** → Messages processed multiple times
- **Result** → Double charges, double fraud checks, corrupted metrics

## SOLUTION

Apply IdempotencyService to ALL 68 Kafka consumers.

---

## IMPLEMENTATION PATTERN

### Step 1: Add IdempotencyService Dependency

```java
import com.waqiti.frauddetection.service.IdempotencyService;

@Component
@RequiredArgsConstructor
public class YourConsumer {

    // Existing dependencies...
    private final IdempotencyService idempotencyService; // ADD THIS
}
```

### Step 2: Add Idempotency Check at Start of Consumer Method

```java
@KafkaListener(topics = "your-topic")
@Transactional
public void handleEvent(@Payload YourEvent event, Acknowledgment ack) {

    String eventId = event.getId(); // or event.getTransactionId(), event.getEventId(), etc.

    // IDEMPOTENCY CHECK (ADD THIS)
    if (!idempotencyService.checkAndMark(eventId, "your-namespace")) {
        log.info("Duplicate event ignored: {}", eventId);
        if (ack != null) {
            ack.acknowledge();
        }
        return; // Skip duplicate
    }

    // Existing processing logic continues here...
    processEvent(event);
}
```

### Step 3: Choose Appropriate Namespace

Use descriptive namespace for each consumer type:

| Consumer Type | Namespace | Example |
|--------------|-----------|---------|
| Fraud Detection | `fraud-detection` | FraudDetectionTriggerConsumer |
| Fraud Alerts | `fraud-alert` | FraudAlertsConsumer |
| Velocity Checks | `velocity-check` | VelocityCheckEventsConsumer |
| Risk Assessment | `risk-assessment` | RiskAssessmentConsumer |
| Pattern Analysis | `pattern-analysis` | PatternAnalysisConsumer |
| Chargeback Alerts | `chargeback` | ChargebackAlertsConsumer |
| Bot Detection | `bot-detection` | BotDetectionEventsConsumer |
| ATO Detection | `ato-detection` | AccountTakeoverDetectionConsumer |
| Device Fingerprint | `device-fingerprint` | DeviceFingerprintEventsConsumer |
| Geo Location | `geo-location` | GeoLocationEventsConsumer |

---

## COMPLETE EXAMPLE

**File:** `FraudDetectionTriggerConsumer.java` (REFERENCE IMPLEMENTATION)

```java
package com.waqiti.frauddetection.kafka;

import com.waqiti.frauddetection.service.IdempotencyService; // ADDED

@Component
@RequiredArgsConstructor
public class FraudDetectionTriggerConsumer {

    private final FraudDetectionService fraudDetectionService;
    private final IdempotencyService idempotencyService; // ADDED

    @KafkaListener(topics = FRAUD_DETECTION_TRIGGER)
    @Transactional
    public void processFraudDetectionTrigger(
            @Payload PaymentInitiatedEvent event,
            Acknowledgment acknowledgment) {

        String eventId = event.getTransactionId();

        // IDEMPOTENCY CHECK (ADDED)
        if (!idempotencyService.checkAndMark(eventId, "fraud-detection")) {
            log.info("Duplicate fraud detection event ignored: {}", eventId);
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
            }
            return;
        }

        // Existing logic continues...
        FraudDetectionResult result = fraudDetectionService.analyze(event);
        publishResult(result);
    }
}
```

---

## CONSUMERS REQUIRING UPDATE (67 REMAINING)

### High Priority (Process Transactions - CRITICAL)

1. ✅ `FraudDetectionTriggerConsumer.java` - **DONE**
2. ⏳ `PaymentInitiatedEventConsumer.java`
3. ⏳ `TransactionFraudEventsConsumer.java`
4. ⏳ `VelocityCheckEventsConsumer.java`
5. ⏳ `RiskAssessmentConsumer.java`
6. ⏳ `CardFraudDetectionConsumer.java`
7. ⏳ `WalletTopUpFraudConsumer.java`
8. ⏳ `WalletTransferFraudConsumer.java`
9. ⏳ `WalletWithdrawalFraudConsumer.java`
10. ⏳ `ATMWithdrawalRequestedConsumer.java`

### Medium Priority (Alerts & Monitoring)

11. ⏳ `FraudAlertsConsumer.java`
12. ⏳ `FraudAlertConsumer.java`
13. ⏳ `FraudAlertCriticalConsumer.java`
14. ⏳ `ChargebackAlertsConsumer.java`
15. ⏳ `SuspiciousTransactionConsumer.java`
16. ⏳ `TransactionAnomalyEventsConsumer.java`
17. ⏳ `SuspiciousPatternEventsConsumer.java`
18. ⏳ `PatternAnalysisConsumer.java`
19. ⏳ `RiskAssessmentEventsConsumer.java`
20. ⏳ `RiskAssessmentEventConsumer.java`
21. ⏳ `RiskScoresEventsConsumer.java`

### Medium Priority (Security & Detection)

22. ⏳ `AccountCompromiseEventsConsumer.java`
23. ⏳ `AccountTakeoverDetectionRetryConsumer.java`
24. ⏳ `AuthAnomalyDetectionConsumer.java`
25. ⏳ `AuthAnomalyCriticalFailuresConsumer.java`
26. ⏳ `AuthAnomalyValidationErrorsConsumer.java`
27. ⏳ `AuthFailuresConsumer.java`
28. ⏳ `AuthRevocationsConsumer.java`
29. ⏳ `BotDetectionEventsConsumer.java`
30. ⏳ `BruteForceDetectionEventsConsumer.java`
31. ⏳ `PhishingDetectionEventsConsumer.java`
32. ⏳ `BehavioralBiometricEventsConsumer.java`

### Medium Priority (Device & Location)

33. ⏳ `DeviceFingerprintEventsConsumer.java`
34. ⏳ `GeoLocationEventsConsumer.java`
35. ⏳ `IPBlacklistEventsConsumer.java`

### Lower Priority (Processing & Investigation)

36. ⏳ `FraudProcessingConsumer.java`
37. ⏳ `FraudDetectionConsumer.java`
38. ⏳ `FraudInvestigationsConsumer.java`
39. ⏳ `FraudInvestigationEventsConsumer.java`
40. ⏳ `FraudActivityLogsConsumer.java`
41. ⏳ `MerchantRiskScoringConsumer.java`
42. ⏳ `VelocityMonitoringConsumer.java`
43. ⏳ `AssetFreezeEventsConsumer.java`
44. ⏳ `BlockedTransactionsConsumer.java`
45. ⏳ `TransactionBlockEventConsumer.java`
46. ⏳ `AnomalyReviewQueueConsumer.java`
47. ⏳ `AppealReviewTasksConsumer.java`

### Lower Priority (DLQ & Error Handling)

48. ⏳ `FraudDetectionDlqConsumer.java`
49. ⏳ `FraudAlertDlqConsumer.java`
50. ⏳ `FraudReportingDlqConsumer.java`
51. ⏳ `FraudInvestigationDlqConsumer.java`
52. ⏳ `FraudInvestigationEventsDlqConsumer.java`
53. ⏳ `FraudActivityLogsDlqConsumer.java`
54. ⏳ `FraudPreventionDlqConsumer.java`
55. ⏳ `CriticalFraudDetectionDlqConsumer.java`
56. ⏳ `VelocityCheckEventsDlqConsumer.java`
57. ⏳ `SuspiciousPatternEventsDlqConsumer.java`
58. ⏳ `ATODetectionCriticalFailuresConsumer.java`
59. ⏳ `ATODetectionValidationErrorsConsumer.java`

### Lower Priority (Infrastructure & Circuit Breaking)

60. ⏳ `AsyncReversalTrackingConsumer.java`
61. ⏳ `AsyncReversalPendingConsumer.java`
62. ⏳ `AsyncReversalValidationErrorsConsumer.java`
63. ⏳ `ApiTimeoutEventsConsumer.java`
64. ⏳ `ApiCircuitBreakerConsumer.java`

### Other Files (Non-consumers)

65-68. Event definition files (no changes needed)

---

## VERIFICATION CHECKLIST

For each consumer, verify:

- [ ] Import `IdempotencyService` added
- [ ] Service injected via constructor
- [ ] `checkAndMark()` called at method start
- [ ] Correct event ID extracted
- [ ] Appropriate namespace used
- [ ] Acknowledgment handled on duplicate
- [ ] Return statement added after duplicate detection
- [ ] No code execution after duplicate return

---

## TESTING

### Unit Test Template

```java
@Test
void shouldSkipDuplicateEvents() {
    // Given
    YourEvent event = createTestEvent();
    when(idempotencyService.checkAndMark(event.getId(), "namespace"))
        .thenReturn(false); // Simulate duplicate

    // When
    consumer.handleEvent(event, mockAck);

    // Then
    verify(yourService, never()).processEvent(any()); // Should not process
    verify(mockAck).acknowledge(); // Should still acknowledge
}

@Test
void shouldProcessNewEvents() {
    // Given
    YourEvent event = createTestEvent();
    when(idempotencyService.checkAndMark(event.getId(), "namespace"))
        .thenReturn(true); // Simulate new event

    // When
    consumer.handleEvent(event, mockAck);

    // Then
    verify(yourService).processEvent(event); // Should process
}
```

---

## ROLLOUT PLAN

### Phase 1: Critical Consumers (Week 1)
Update consumers 1-10 (transaction processing)

### Phase 2: Security & Alerts (Week 1-2)
Update consumers 11-32 (alerts, security, detection)

### Phase 3: Processing & Infrastructure (Week 2)
Update consumers 33-64 (lower priority)

### Phase 4: Testing & Validation (Week 2)
- Integration testing with duplicate messages
- Performance testing (Redis load)
- Production monitoring

---

## COMMON MISTAKES TO AVOID

1. **❌ Wrong:** Checking idempotency AFTER processing
   ```java
   processEvent(event); // TOO LATE!
   if (!idempotencyService.checkAndMark(...)) return;
   ```

2. **❌ Wrong:** Not acknowledging duplicate messages
   ```java
   if (!idempotencyService.checkAndMark(...)) {
       return; // Kafka will redeliver!
   }
   ```

3. **❌ Wrong:** Using wrong event ID
   ```java
   // Using random UUID instead of event's unique ID
   idempotencyService.checkAndMark(UUID.randomUUID(), "namespace");
   ```

4. **✅ Correct:** Idempotency check first, acknowledge duplicate
   ```java
   if (!idempotencyService.checkAndMark(event.getId(), "namespace")) {
       log.info("Duplicate ignored: {}", event.getId());
       if (ack != null) ack.acknowledge();
       return;
   }
   processEvent(event);
   ```

---

## MONITORING

After deployment, monitor:

1. **Duplicate Detection Rate**
   ```java
   idempotencyService.getStats("fraud-detection")
   ```

2. **Redis Memory Usage**
   - 7-day TTL × event volume
   - Expected: ~1-10 MB per million events

3. **Processing Time Impact**
   - Redis lookup adds ~1-2ms per event
   - Acceptable overhead for correctness

---

## COMPLETION CHECKLIST

- [ ] Update all 67 remaining consumers
- [ ] Add unit tests for all consumers
- [ ] Integration test with duplicate messages
- [ ] Monitor Redis memory usage
- [ ] Verify no duplicate processing in logs
- [ ] Document any exceptions/edge cases

---

**Reference Implementation:** `FraudDetectionTriggerConsumer.java` (line 86-93)
**Estimated Time:** 3-4 hours for all 67 consumers
**Priority:** CRITICAL - Deploy immediately after testing

Last Updated: October 16, 2025
