# Dispute Service - Production Implementation Guide

## Status: Implementation in Progress

### ✅ COMPLETED (Phase 1A)

1. **Missing DTOs Created:**
   - ✅ UpdateDisputeStatusRequest
   - ✅ AddEvidenceRequest
   - ✅ EscalateDisputeRequest
   - ✅ DisputeSearchCriteria
   - ✅ ExportRequest

2. **DLQ Infrastructure:**
   - ✅ DLQEntry entity
   - ✅ DLQStatus enum
   - ✅ RecoveryStrategy enum
   - ✅ DLQEntryRepository

3. **Distributed Idempotency:**
   - ✅ DistributedIdempotencyService (Redis + Database)
   - ✅ Enhanced ProcessedEvent entity
   - ✅ Enhanced ProcessedEventRepository

### 🚧 IN PROGRESS - Database Migrations

#### V003__Create_dlq_table.sql
```sql
CREATE TABLE dlq_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id VARCHAR(100) NOT NULL,
    source_topic VARCHAR(255) NOT NULL,
    event_json TEXT NOT NULL,
    error_message TEXT,
    error_stacktrace TEXT,
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retries INTEGER NOT NULL DEFAULT 3,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_REVIEW',
    recovery_strategy VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_retry_at TIMESTAMP,
    resolved_at TIMESTAMP,
    resolved_by VARCHAR(100),
    resolution_notes TEXT,
    alert_sent BOOLEAN NOT NULL DEFAULT FALSE,
    ticket_id VARCHAR(50),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_dlq_status ON dlq_entries(status);
CREATE INDEX idx_dlq_topic ON dlq_entries(source_topic);
CREATE INDEX idx_dlq_created ON dlq_entries(created_at);
CREATE INDEX idx_dlq_event_id ON dlq_entries(event_id);

COMMENT ON TABLE dlq_entries IS 'Dead Letter Queue entries for failed Kafka messages';
```

#### V004__Update_processed_events.sql
```sql
-- Add new columns to processed_events for distributed idempotency
ALTER TABLE processed_events ADD COLUMN IF NOT EXISTS event_key VARCHAR(255) NOT NULL DEFAULT '';
ALTER TABLE processed_events ADD COLUMN IF NOT EXISTS operation_id VARCHAR(255) NOT NULL DEFAULT '';
ALTER TABLE processed_events ADD COLUMN IF NOT EXISTS processed_at_local TIMESTAMP;
ALTER TABLE processed_events ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP;
ALTER TABLE processed_events ADD COLUMN IF NOT EXISTS result TEXT;

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_event_key ON processed_events(event_key);
CREATE INDEX IF NOT EXISTS idx_expires_at ON processed_events(expires_at);

-- Update existing records
UPDATE processed_events SET event_key = event_id WHERE event_key = '';
UPDATE processed_events SET operation_id = event_id WHERE operation_id = '';
UPDATE processed_events SET processed_at_local = CURRENT_TIMESTAMP WHERE processed_at_local IS NULL;
UPDATE processed_events SET expires_at = CURRENT_TIMESTAMP + INTERVAL '7 days' WHERE expires_at IS NULL;
```

#### V005__Add_missing_indexes.sql
```sql
-- Additional performance indexes for disputes table
CREATE INDEX IF NOT EXISTS idx_dispute_merchant ON disputes(merchant_id);
CREATE INDEX IF NOT EXISTS idx_dispute_resolved_at ON disputes(resolved_at);
CREATE INDEX IF NOT EXISTS idx_dispute_type ON disputes(dispute_type);
CREATE INDEX IF NOT EXISTS idx_dispute_escalation_level ON disputes(escalation_level);
CREATE INDEX IF NOT EXISTS idx_dispute_chargeback_code ON disputes(chargeback_code);

-- Composite indexes for common queries
CREATE INDEX IF NOT EXISTS idx_dispute_user_status ON disputes(user_id, status);
CREATE INDEX IF NOT EXISTS idx_dispute_status_created ON disputes(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_dispute_priority_status ON disputes(priority, status);

-- Indexes for dispute_evidence table (if exists)
CREATE INDEX IF NOT EXISTS idx_evidence_dispute ON dispute_evidence(dispute_id);
CREATE INDEX IF NOT EXISTS idx_evidence_submitted ON dispute_evidence(submitted_at);
CREATE INDEX IF NOT EXISTS idx_evidence_type ON dispute_evidence(evidence_type);
```

### 📋 REMAINING CRITICAL IMPLEMENTATIONS

## 1. Missing Service Classes

### DisputeAnalysisService.java
```java
package com.waqiti.dispute.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Service for dispute analysis and pattern detection
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DisputeAnalysisService {

    @Transactional(readOnly = true)
    public void updateResolutionAnalytics(UUID disputeId, UUID customerId,
            String resolutionType, String resolutionDecision,
            BigDecimal disputeAmount, String disputeCategory,
            String aiConfidenceScore, String riskScore) {

        log.info("Updating dispute analytics: disputeId={}, decision={}, amount={}",
                disputeId, resolutionDecision, disputeAmount);

        // TODO: Implement analytics tracking
        // - Track resolution patterns
        // - Update customer dispute history
        // - Calculate dispute rates
        // - Update fraud indicators
    }
}
```

### DisputeNotificationService.java
```java
package com.waqiti.dispute.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Service for dispute notifications
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DisputeNotificationService {

    // Inject actual notification service when available
    // private final NotificationService notificationService;

    public void notifyCustomer(UUID disputeId, UUID customerId,
            String resolutionDecision, BigDecimal amount,
            String currency, String explanation) {

        log.info("Sending customer notification: disputeId={}, decision={}",
                disputeId, resolutionDecision);

        // TODO: Implement customer notification
        // - Email notification
        // - SMS notification
        // - Push notification
        // - In-app notification
    }

    public void notifyMerchant(UUID disputeId, String merchantId,
            String resolutionDecision, BigDecimal amount,
            String currency, String explanation) {

        log.info("Sending merchant notification: disputeId={}, merchantId={}",
                disputeId, merchantId);

        // TODO: Implement merchant notification
    }

    public void notifyDisputeTeam(UUID disputeId, UUID customerId,
            String resolutionType, String resolutionDecision,
            BigDecimal amount, String currency, String reason) {

        log.info("Sending dispute team notification: disputeId={}, reason={}",
                disputeId, reason);

        // TODO: Implement dispute team notification
    }

    public void notifyOperationsTeam(UUID disputeId, UUID customerId,
            String merchantId, BigDecimal amount, String currency, String reason) {

        log.info("Sending operations team notification: disputeId={}", disputeId);

        // TODO: Implement operations team notification
    }

    public void sendEmergencyNotification(String disputeId, String customerId,
            String resolutionType, String resolutionDecision, String errorMessage) {

        log.error("EMERGENCY: Sending emergency notification for dispute: {}", disputeId);

        // TODO: Implement emergency notification
        // - Page on-call engineer
        // - Create high-priority ticket
        // - Alert management
    }
}
```

## 2. Missing DisputeResolutionService Methods

The current DisputeResolutionService is missing 30+ methods. Here's the complete interface that needs to be implemented:

### Required Method Implementations

```java
// In DisputeResolutionService.java - ADD THESE METHODS:

/**
 * Create dispute with proper DTO
 */
public DisputeDTO createDispute(CreateDisputeRequest request) {
    // Convert CreateDisputeRequest to internal DisputeRequest
    // Call existing createDispute method
    // Convert Dispute entity to DisputeDTO
    // Return DisputeDTO
}

/**
 * Get dispute with user validation
 */
public DisputeDTO getDispute(String disputeId, String userId) {
    Dispute dispute = getDispute(disputeId);

    // Validate user has access to this dispute
    if (!dispute.getUserId().equals(userId) && !isAdmin()) {
        throw new AccessDeniedException("User not authorized for this dispute");
    }

    return convertToDTO(dispute);
}

/**
 * Get user disputes with pagination
 */
public Page<DisputeDTO> getUserDisputes(String userId, DisputeStatus status,
        LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {

    // Build specification for filtering
    // Query with pagination
    // Convert to DTOs
    // Return Page<DisputeDTO>
}

/**
 * Update dispute status with proper request DTO
 */
public DisputeDTO updateDisputeStatus(UpdateDisputeStatusRequest request) {
    Dispute updated = updateDisputeStatus(
        request.getDisputeId(),
        request.getNewStatus(),
        request.getReason()
    );
    return convertToDTO(updated);
}

/**
 * Add evidence with proper request DTO
 */
public EvidenceDTO addEvidence(AddEvidenceRequest request) {
    // Validate file upload
    // Store file securely
    // Create evidence submission
    DisputeEvidence evidence = submitEvidence(
        request.getDisputeId(),
        createEvidenceSubmission(request)
    );
    return convertToEvidenceDTO(evidence);
}

/**
 * Escalate dispute with proper request DTO
 */
public DisputeDTO escalateDispute(EscalateDisputeRequest request) {
    Dispute escalated = escalateDispute(
        request.getDisputeId(),
        request.getEscalationReason()
    );
    // Additional escalation logic
    return convertToDTO(escalated);
}

// Auto-resolution methods
public void processAutoResolution(UUID disputeId, UUID customerId,
        String resolutionType, String resolutionDecision,
        BigDecimal disputeAmount, String currency, String disputeReason,
        String disputeCategory, LocalDateTime resolutionTimestamp,
        String aiConfidenceScore, Map<String, Object> resolutionEvidence,
        String resolutionExplanation) {
    // Implementation needed
}

public void approveDispute(UUID disputeId, UUID customerId,
        BigDecimal disputeAmount, String currency, List<String> supportingDocuments) {
    // Implementation needed
}

public void denyDispute(UUID disputeId, UUID customerId,
        BigDecimal disputeAmount, String currency, List<String> supportingDocuments) {
    // Implementation needed
}

public void partiallyApproveDispute(UUID disputeId, UUID customerId,
        BigDecimal disputeAmount, String currency, List<String> supportingDocuments) {
    // Implementation needed
}

public void issueChargeback(UUID disputeId, UUID customerId,
        BigDecimal disputeAmount, String currency, String merchantId, String transactionId) {
    // Implementation needed
}

public void assignMerchantLiability(UUID disputeId, UUID customerId,
        String merchantId, BigDecimal disputeAmount, String currency) {
    // Implementation needed
}

public void assignCustomerLiability(UUID disputeId, UUID customerId,
        BigDecimal disputeAmount, String currency, Map<String, Object> fraudIndicators) {
    // Implementation needed
}

public void escalateForManualReview(UUID disputeId, UUID customerId,
        BigDecimal disputeAmount, String currency, List<String> supportingDocuments) {
    // Implementation needed
}

public void updateDisputeStatus(UUID disputeId, UUID customerId, String newStatus,
        String resolutionType, LocalDateTime resolutionTimestamp,
        String aiConfidenceScore, Boolean requiresManualReview) {
    // Implementation needed
}

public void processRefund(UUID disputeId, UUID customerId,
        BigDecimal disputeAmount, String currency, String transactionId, String disputeReason) {
    // Implementation needed
}

public void processChargebackAdjustment(UUID disputeId, UUID customerId,
        String merchantId, BigDecimal disputeAmount, String currency, String transactionId) {
    // Implementation needed
}

public void processMerchantLiabilityAdjustment(UUID disputeId,
        String merchantId, BigDecimal disputeAmount, String currency, String disputeReason) {
    // Implementation needed
}

public void recordProcessingFailure(UUID disputeId, UUID customerId,
        String resolutionType, String resolutionDecision, String errorMessage) {
    // Implementation needed
}

public void markForEmergencyReview(UUID disputeId, UUID customerId,
        String resolutionType, String resolutionDecision, String reason) {
    // Implementation needed
}

// Query methods
public Page<DisputeDTO> searchDisputes(DisputeSearchCriteria criteria, Pageable pageable) {
    // Implementation needed
}

public DisputeStatistics getDisputeStatistics(LocalDateTime startDate, LocalDateTime endDate) {
    // Implementation needed
}

public byte[] exportDisputes(ExportRequest request) {
    // Implementation needed - generate CSV/Excel/PDF
}

public List<DisputeTimelineEvent> getDisputeTimeline(String disputeId, String userId) {
    // Implementation needed
}

public BulkUpdateResult bulkUpdateDisputes(BulkUpdateRequest request) {
    // Implementation needed
}

public List<String> getDisputeCategories() {
    // Implementation needed
}

public List<ResolutionTemplate> getResolutionTemplates(String category) {
    // Implementation needed
}
```

## 3. Base DLQ Handler Implementation

### Create: BaseDLQHandler.java
```java
package com.waqiti.dispute.kafka.dlq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.common.audit.AuditService;
import com.waqiti.dispute.entity.DLQEntry;
import com.waqiti.dispute.entity.DLQStatus;
import com.waqiti.dispute.entity.RecoveryStrategy;
import com.waqiti.dispute.repository.DLQEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Base DLQ Handler with common recovery logic
 * All specific DLQ handlers should extend this class
 */
@Component
@Slf4j
@RequiredArgsConstructor
public abstract class BaseDLQHandler {

    protected final DLQEntryRepository dlqRepository;
    protected final ObjectMapper objectMapper;
    protected final AuditService auditService;

    /**
     * Process DLQ event with recovery strategy
     */
    @Transactional
    public void processDLQEvent(String eventJson, String topic, String errorMessage) {
        try {
            // Parse event to determine type
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            String eventId = extractEventId(event);

            // Check if already in DLQ
            if (dlqRepository.findByEventId(eventId).isPresent()) {
                log.warn("DLQ entry already exists for event: {}", eventId);
                return;
            }

            // Determine recovery strategy
            RecoveryStrategy strategy = determineRecoveryStrategy(event, errorMessage);

            // Create DLQ entry
            DLQEntry dlqEntry = DLQEntry.builder()
                    .id(UUID.randomUUID().toString())
                    .eventId(eventId)
                    .sourceTopic(topic)
                    .eventJson(eventJson)
                    .errorMessage(errorMessage)
                    .retryCount(0)
                    .maxRetries(getMaxRetries(strategy))
                    .status(DLQStatus.PENDING_REVIEW)
                    .recoveryStrategy(strategy)
                    .createdAt(LocalDateTime.now())
                    .alertSent(false)
                    .build();

            dlqRepository.save(dlqEntry);

            // Execute recovery strategy
            executeRecoveryStrategy(dlqEntry, event);

            // Send alert if needed
            sendAlertIfNeeded(dlqEntry, event);

            // Audit
            auditDLQEntry(dlqEntry, event);

            log.info("DLQ entry created: eventId={}, strategy={}", eventId, strategy);

        } catch (Exception e) {
            log.error("CRITICAL: Failed to process DLQ event", e);
            writeToEmergencyLog(eventJson, errorMessage, e);
        }
    }

    /**
     * Determine recovery strategy based on error type
     */
    protected abstract RecoveryStrategy determineRecoveryStrategy(
            Map<String, Object> event, String errorMessage);

    /**
     * Execute recovery strategy
     */
    protected void executeRecoveryStrategy(DLQEntry dlqEntry, Map<String, Object> event) {
        switch (dlqEntry.getRecoveryStrategy()) {
            case RETRY_WITH_BACKOFF:
                scheduleRetry(dlqEntry);
                break;
            case TRANSFORM_AND_RETRY:
                attemptTransformAndRetry(dlqEntry, event);
                break;
            case MANUAL_INTERVENTION:
                createTicket(dlqEntry, event);
                break;
            case COMPENSATE:
                executeCompensation(dlqEntry, event);
                break;
            case DISCARD_WITH_AUDIT:
                discardWithAudit(dlqEntry, event);
                break;
            case ESCALATE_TO_EMERGENCY:
                escalateToEmergency(dlqEntry, event);
                break;
        }
    }

    protected void scheduleRetry(DLQEntry dlqEntry) {
        dlqEntry.setStatus(DLQStatus.RETRY_SCHEDULED);
        dlqEntry.setLastRetryAt(LocalDateTime.now());
        dlqRepository.save(dlqEntry);
        log.info("Scheduled retry for DLQ entry: {}", dlqEntry.getId());
    }

    protected void attemptTransformAndRetry(DLQEntry dlqEntry, Map<String, Object> event) {
        // Subclasses implement transformation logic
        log.info("Transform and retry for DLQ entry: {}", dlqEntry.getId());
    }

    protected void createTicket(DLQEntry dlqEntry, Map<String, Object> event) {
        // Create ticket in ticketing system (Jira, ServiceNow, etc.)
        String ticketId = "TKT-" + UUID.randomUUID().toString().substring(0, 8);
        dlqEntry.setTicketId(ticketId);
        dlqRepository.save(dlqEntry);
        log.info("Created ticket {} for DLQ entry: {}", ticketId, dlqEntry.getId());
    }

    protected void executeCompensation(DLQEntry dlqEntry, Map<String, Object> event) {
        // Subclasses implement compensation logic
        log.info("Executing compensation for DLQ entry: {}", dlqEntry.getId());
    }

    protected void discardWithAudit(DLQEntry dlqEntry, Map<String, Object> event) {
        dlqEntry.setStatus(DLQStatus.DISCARDED);
        dlqEntry.setResolvedAt(LocalDateTime.now());
        dlqEntry.setResolutionNotes("Intentionally discarded - event not critical");
        dlqRepository.save(dlqEntry);
        log.warn("Discarded DLQ entry with audit: {}", dlqEntry.getId());
    }

    protected void escalateToEmergency(DLQEntry dlqEntry, Map<String, Object> event) {
        dlqEntry.setStatus(DLQStatus.ESCALATED);
        dlqEntry.setAlertSent(true);
        dlqRepository.save(dlqEntry);

        // Page on-call engineer
        log.error("EMERGENCY ESCALATION: DLQ entry {}, event: {}",
                dlqEntry.getId(), event);

        // TODO: Integrate with PagerDuty/Opsgenie
    }

    protected void sendAlertIfNeeded(DLQEntry dlqEntry, Map<String, Object> event) {
        if (isCriticalEvent(event) && !dlqEntry.isAlertSent()) {
            // Send alert to operations team
            log.warn("ALERT: Critical DLQ event - {}", dlqEntry.getId());
            dlqEntry.setAlertSent(true);
            dlqRepository.save(dlqEntry);
        }
    }

    protected void auditDLQEntry(DLQEntry dlqEntry, Map<String, Object> event) {
        auditService.auditFinancialEvent(
                "DLQ_ENTRY_CREATED",
                extractUserId(event),
                "DLQ entry created for failed event processing",
                Map.of(
                        "dlqEntryId", dlqEntry.getId(),
                        "eventId", dlqEntry.getEventId(),
                        "sourceTopic", dlqEntry.getSourceTopic(),
                        "recoveryStrategy", dlqEntry.getRecoveryStrategy().name()
                )
        );
    }

    protected void writeToEmergencyLog(String eventJson, String errorMessage, Exception e) {
        // Last resort: Write to file system
        log.error("EMERGENCY LOG - Event: {}, Error: {}, Exception: {}",
                eventJson, errorMessage, e.getMessage());
        // TODO: Write to emergency log file
    }

    protected String extractEventId(Map<String, Object> event) {
        return event.getOrDefault("eventId", UUID.randomUUID().toString()).toString();
    }

    protected String extractUserId(Map<String, Object> event) {
        return event.getOrDefault("userId", "UNKNOWN").toString();
    }

    protected boolean isCriticalEvent(Map<String, Object> event) {
        // Determine if event is critical based on type, amount, etc.
        return true; // Default to critical
    }

    protected int getMaxRetries(RecoveryStrategy strategy) {
        return switch (strategy) {
            case RETRY_WITH_BACKOFF -> 3;
            case TRANSFORM_AND_RETRY -> 2;
            default -> 0;
        };
    }
}
```

## 4. Example Specific DLQ Handler Implementation

Replace the TODO in each DLQ handler with this pattern:

### DisputeAutoResolutionConsumerDlqHandler.java
```java
@Component
@Slf4j
@RequiredArgsConstructor
public class DisputeAutoResolutionConsumerDlqHandler extends BaseDLQHandler {

    private final DisputeResolutionService disputeResolutionService;

    @Override
    protected RecoveryStrategy determineRecoveryStrategy(
            Map<String, Object> event, String errorMessage) {

        // Financial events always require manual review
        if (errorMessage.contains("InsufficientFunds") ||
            errorMessage.contains("BalanceValidation")) {
            return RecoveryStrategy.MANUAL_INTERVENTION;
        }

        // Data validation errors can be retried after transformation
        if (errorMessage.contains("ValidationException") ||
            errorMessage.contains("IllegalArgumentException")) {
            return RecoveryStrategy.TRANSFORM_AND_RETRY;
        }

        // Temporary errors can be retried
        if (errorMessage.contains("Timeout") ||
            errorMessage.contains("ConnectionException")) {
            return RecoveryStrategy.RETRY_WITH_BACKOFF;
        }

        // Default to manual intervention for financial operations
        return RecoveryStrategy.MANUAL_INTERVENTION;
    }

    @Override
    protected void attemptTransformAndRetry(DLQEntry dlqEntry, Map<String, Object> event) {
        try {
            // Transform data (e.g., fix date formats, convert types)
            Map<String, Object> transformedEvent = transformEvent(event);

            // Retry processing
            disputeResolutionService.processAutoResolution(
                UUID.fromString((String) transformedEvent.get("disputeId")),
                UUID.fromString((String) transformedEvent.get("customerId")),
                (String) transformedEvent.get("resolutionType"),
                (String) transformedEvent.get("resolutionDecision"),
                new BigDecimal(transformedEvent.get("disputeAmount").toString()),
                (String) transformedEvent.get("currency"),
                (String) transformedEvent.get("disputeReason"),
                (String) transformedEvent.get("disputeCategory"),
                LocalDateTime.parse((String) transformedEvent.get("resolutionTimestamp")),
                (String) transformedEvent.getOrDefault("aiConfidenceScore", "0.0"),
                (Map<String, Object>) transformedEvent.getOrDefault("resolutionEvidence", Map.of()),
                (String) transformedEvent.getOrDefault("resolutionExplanation", "")
            );

            // Mark as resolved
            dlqEntry.setStatus(DLQStatus.RESOLVED);
            dlqEntry.setResolvedAt(LocalDateTime.now());
            dlqEntry.setResolutionNotes("Successfully processed after transformation");
            dlqRepository.save(dlqEntry);

            log.info("Successfully retried DLQ entry after transformation: {}", dlqEntry.getId());

        } catch (Exception e) {
            log.error("Transform and retry failed for DLQ entry: {}", dlqEntry.getId(), e);
            dlqEntry.setRetryCount(dlqEntry.getRetryCount() + 1);

            if (dlqEntry.getRetryCount() >= dlqEntry.getMaxRetries()) {
                dlqEntry.setStatus(DLQStatus.PERMANENT_FAILURE);
                createTicket(dlqEntry, event);
            }

            dlqRepository.save(dlqEntry);
        }
    }

    private Map<String, Object> transformEvent(Map<String, Object> event) {
        // Implement transformation logic
        // Example: Fix date formats, convert string amounts to BigDecimal, etc.
        return event;
    }
}
```

### Repeat this pattern for all 19 DLQ handlers:
1. DisputeAutoResolutionConsumerDlqHandler ✅ Example above
2. DisputeProvisionalCreditIssuedConsumerDlqHandler - Similar to above
3. ChargebackInitiatedConsumerDlqHandler - Similar to above
4. ChargebackInvestigationsConsumerDlqHandler
5. DisputeInvestigationsConsumerDlqHandler
6. DisputeEscalationsConsumerDlqHandler
7. DisputeRejectionsConsumerDlqHandler
8. ChargebackAuditEventsConsumerDlqHandler
9. ChargebackPreventionEventsConsumerDlqHandler
10. CircuitBreakerMetricsConsumerDlqHandler
11. CircuitBreakerRecommendationsConsumerDlqHandler
12. ClusteringAlertsConsumerDlqHandler
13. DisputeMonitoringTasksConsumerDlqHandler
14. ChargebackAlertCriticalFailuresConsumerDlqHandler
15. ChargebackAlertsConsumerDlqHandler
16. ChargebackManualQueueConsumerDlqHandler
17. ChargebackAlertValidationErrorsConsumerDlqHandler
18. CircuitBreakerEvaluationsConsumerDlqHandler
19. TransactionDisputeOpenedEventConsumerDlqHandler

## 5. Security Enhancements

### JWT Validation Interceptor
Create: JwtUserIdValidationInterceptor.java

### File Upload Security Service
Create: SecureFileUploadService.java
- Implement magic byte validation
- Integrate ClamAV for virus scanning
- Secure file storage with encryption

## 6. Comprehensive Test Suite (LAST PHASE)

After all production code is complete, implement:

1. **Unit Tests** (80%+ coverage target)
   - DisputeResolutionServiceTest
   - DisputeManagementServiceTest
   - DistributedIdempotencyServiceTest
   - BaseDLQHandlerTest
   - All repository tests

2. **Integration Tests**
   - DisputeControllerIntegrationTest
   - Kafka consumer integration tests
   - Database integration tests

3. **E2E Tests**
   - Complete dispute lifecycle tests
   - DLQ recovery workflow tests

## Implementation Priority Order

1. ✅ DTOs and entities
2. ✅ Distributed idempotency
3. 🚧 Database migrations (IN PROGRESS)
4. ⏳ Missing service classes
5. ⏳ DisputeResolutionService missing methods
6. ⏳ Base DLQ handler
7. ⏳ All 19 specific DLQ handlers
8. ⏳ Security enhancements
9. ⏳ Comprehensive test suite (LAST)

## Next Steps

Continue with implementation in the order listed above. Each component must be fully tested before moving to the next.
