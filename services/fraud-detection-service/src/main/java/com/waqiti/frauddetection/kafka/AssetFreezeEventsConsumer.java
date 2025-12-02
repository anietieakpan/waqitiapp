package com.waqiti.frauddetection.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.GenericKafkaEvent;
import com.waqiti.frauddetection.service.*;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production-grade Kafka consumer for asset freeze events
 * Handles asset freezing operations for fraud prevention and regulatory compliance
 *
 * Critical for: Asset protection, fraud prevention, regulatory compliance, AML
 * SLA: Must process freeze events within 10 seconds for immediate asset protection
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AssetFreezeEventsConsumer {

    private final AssetFreezeService assetFreezeService;
    private final FraudDetectionService fraudDetectionService;
    private final ComplianceService complianceService;
    private final CustomerCommunicationService customerCommunicationService;
    private final FraudNotificationService fraudNotificationService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000; // 24 hours in milliseconds

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("asset_freeze_events_processed_total")
            .description("Total number of successfully processed asset freeze events")
            .register(meterRegistry);
        errorCounter = Counter.builder("asset_freeze_events_errors_total")
            .description("Total number of asset freeze event processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("asset_freeze_events_processing_duration")
            .description("Time taken to process asset freeze events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"asset-freeze-events", "emergency-freeze", "compliance-freeze-orders"},
        groupId = "fraud-asset-freeze-events-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "6"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "asset-freeze-events", fallbackMethod = "handleAssetFreezeEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleAssetFreezeEvent(
            @Payload GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("freeze-%s-p%d-o%d", event.getId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getId(), event.getEventType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing asset freeze event: id={}, type={}, userId={}",
                event.getId(), event.getEventType(), event.getData().get("userId"));

            // Clean expired entries periodically
            cleanExpiredEntries();

            String userId = (String) event.getData().get("userId");
            String accountId = (String) event.getData().get("accountId");
            String assetType = (String) event.getData().get("assetType");

            switch (event.getEventType()) {
                case "EMERGENCY_FREEZE_INITIATED":
                    handleEmergencyFreezeInitiated(event, userId, accountId, assetType, correlationId);
                    break;

                case "FRAUD_FREEZE_TRIGGERED":
                    handleFraudFreezeTriggered(event, userId, accountId, assetType, correlationId);
                    break;

                case "COMPLIANCE_FREEZE_ORDERED":
                    handleComplianceFreezeOrdered(event, userId, accountId, assetType, correlationId);
                    break;

                case "ASSET_FREEZE_EXECUTED":
                    handleAssetFreezeExecuted(event, userId, accountId, assetType, correlationId);
                    break;

                case "FREEZE_RELEASE_REQUESTED":
                    handleFreezeReleaseRequested(event, userId, accountId, assetType, correlationId);
                    break;

                case "FREEZE_RELEASE_APPROVED":
                    handleFreezeReleaseApproved(event, userId, accountId, assetType, correlationId);
                    break;

                case "FREEZE_RELEASE_EXECUTED":
                    handleFreezeReleaseExecuted(event, userId, accountId, assetType, correlationId);
                    break;

                case "PARTIAL_FREEZE_APPLIED":
                    handlePartialFreezeApplied(event, userId, accountId, assetType, correlationId);
                    break;

                case "FREEZE_ESCALATED":
                    handleFreezeEscalated(event, userId, accountId, correlationId);
                    break;

                default:
                    log.warn("Unknown asset freeze event type: {}", event.getEventType());
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logSecurityEvent("ASSET_FREEZE_EVENT_PROCESSED", userId,
                Map.of("eventType", event.getEventType(), "accountId", accountId,
                    "assetType", assetType, "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process asset freeze event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("asset-freeze-events-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleAssetFreezeEventFallback(
            GenericKafkaEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("freeze-fallback-%s-p%d-o%d", event.getId(), partition, offset);

        log.error("Circuit breaker fallback triggered for asset freeze: id={}, error={}",
            event.getId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("asset-freeze-events-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send critical notification for asset freeze failures
        try {
            notificationService.sendCriticalAlert(
                "Asset Freeze Consumer Circuit Breaker Triggered",
                String.format("Asset freeze event %s failed: %s", event.getId(), ex.getMessage()),
                "CRITICAL"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send critical alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltAssetFreezeEvent(
            @Payload GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-freeze-%s-%d", event.getId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Asset freeze event permanently failed: id={}, topic={}, error={}",
            event.getId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logSecurityEvent("ASSET_FREEZE_DLT_EVENT",
            String.valueOf(event.getData().get("userId")),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send emergency alert for DLT asset freeze events
        try {
            notificationService.sendEmergencyAlert(
                "Asset Freeze Event Dead Letter",
                String.format("CRITICAL: Asset freeze event %s sent to DLT: %s", event.getId(), exceptionMessage),
                Map.of("eventId", event.getId(), "topic", topic, "correlationId", correlationId)
            );
        } catch (Exception ex) {
            log.error("Failed to send emergency DLT alert: {}", ex.getMessage());
        }
    }

    private void handleEmergencyFreezeInitiated(GenericKafkaEvent event, String userId, String accountId, String assetType, String correlationId) {
        String reason = (String) event.getData().get("reason");
        String initiatedBy = (String) event.getData().get("initiatedBy");
        log.warn("EMERGENCY freeze initiated: userId={}, accountId={}, reason={}, by={}",
            userId, accountId, reason, initiatedBy);

        // Execute emergency freeze immediately
        assetFreezeService.executeEmergencyFreeze(userId, accountId, assetType, reason, initiatedBy);

        // Send immediate notifications
        fraudNotificationService.sendEmergencyFreezeAlert(userId, accountId, reason, correlationId);

        // Notify compliance and legal teams
        complianceService.notifyEmergencyFreeze(userId, accountId, reason, correlationId);

        // Send to asset protection workflow
        kafkaTemplate.send("asset-protection-workflow", Map.of(
            "userId", userId,
            "accountId", accountId,
            "eventType", "EMERGENCY_FREEZE_EXECUTED",
            "priority", "CRITICAL",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Emergency freeze executed: userId={}, accountId={}", userId, accountId);
    }

    private void handleFraudFreezeTriggered(GenericKafkaEvent event, String userId, String accountId, String assetType, String correlationId) {
        String fraudType = (String) event.getData().get("fraudType");
        BigDecimal riskScore = new BigDecimal(event.getData().get("riskScore").toString());
        log.warn("Fraud freeze triggered: userId={}, accountId={}, fraudType={}, riskScore={}",
            userId, accountId, fraudType, riskScore);

        // Execute fraud-based freeze
        assetFreezeService.executeFraudFreeze(userId, accountId, assetType, fraudType, riskScore);

        // Analyze fraud patterns for additional risk
        fraudDetectionService.analyzeFraudFreezePatterns(userId, fraudType, riskScore);

        // Notify customer of freeze
        customerCommunicationService.sendFraudFreezeNotification(userId, accountId, fraudType);

        // Send to fraud investigation workflow
        kafkaTemplate.send("fraud-investigation-workflow", Map.of(
            "userId", userId,
            "accountId", accountId,
            "fraudType", fraudType,
            "eventType", "FRAUD_FREEZE_EXECUTED",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Fraud freeze executed: userId={}, accountId={}", userId, accountId);
    }

    private void handleComplianceFreezeOrdered(GenericKafkaEvent event, String userId, String accountId, String assetType, String correlationId) {
        String complianceReason = (String) event.getData().get("complianceReason");
        String legalReference = (String) event.getData().get("legalReference");
        log.info("Compliance freeze ordered: userId={}, accountId={}, reason={}, legal={}",
            userId, accountId, complianceReason, legalReference);

        // Execute compliance freeze
        assetFreezeService.executeComplianceFreeze(userId, accountId, assetType, complianceReason, legalReference);

        // Document compliance action
        complianceService.documentFreezeOrder(userId, accountId, complianceReason, legalReference);

        // Notify regulatory reporting
        complianceService.notifyRegulatoryReporting(userId, accountId, complianceReason, correlationId);

        log.info("Compliance freeze executed: userId={}, accountId={}", userId, accountId);
    }

    private void handleAssetFreezeExecuted(GenericKafkaEvent event, String userId, String accountId, String assetType, String correlationId) {
        String freezeId = (String) event.getData().get("freezeId");
        log.info("Asset freeze executed: userId={}, accountId={}, freezeId={}", userId, accountId, freezeId);

        // Record freeze execution
        assetFreezeService.recordFreezeExecution(freezeId, userId, accountId, assetType);

        // Send customer notification
        customerCommunicationService.sendFreezeExecutedNotification(userId, accountId, freezeId);

        // Update fraud detection models
        fraudDetectionService.updateModelWithFreezeEvent(userId, accountId, assetType);

        log.info("Freeze execution recorded: freezeId={}", freezeId);
    }

    private void handleFreezeReleaseRequested(GenericKafkaEvent event, String userId, String accountId, String assetType, String correlationId) {
        String freezeId = (String) event.getData().get("freezeId");
        String requestedBy = (String) event.getData().get("requestedBy");
        String releaseReason = (String) event.getData().get("releaseReason");
        log.info("Freeze release requested: userId={}, accountId={}, freezeId={}, by={}",
            userId, accountId, freezeId, requestedBy);

        // Process freeze release request
        assetFreezeService.processFreezeReleaseRequest(freezeId, requestedBy, releaseReason);

        // Route to appropriate approval workflow
        assetFreezeService.routeReleaseRequestForApproval(freezeId, requestedBy, releaseReason);

        log.info("Freeze release request processed: freezeId={}", freezeId);
    }

    private void handleFreezeReleaseApproved(GenericKafkaEvent event, String userId, String accountId, String assetType, String correlationId) {
        String freezeId = (String) event.getData().get("freezeId");
        String approvedBy = (String) event.getData().get("approvedBy");
        String approvalNotes = (String) event.getData().get("approvalNotes");
        log.info("Freeze release approved: userId={}, accountId={}, freezeId={}, by={}",
            userId, accountId, freezeId, approvedBy);

        // Execute freeze release
        assetFreezeService.executeFreezeRelease(freezeId, approvedBy, approvalNotes);

        // Send customer notification
        customerCommunicationService.sendFreezeReleasedNotification(userId, accountId, freezeId);

        // Update compliance records
        complianceService.recordFreezeRelease(freezeId, approvedBy, approvalNotes);

        log.info("Freeze release executed: freezeId={}", freezeId);
    }

    private void handleFreezeReleaseExecuted(GenericKafkaEvent event, String userId, String accountId, String assetType, String correlationId) {
        String freezeId = (String) event.getData().get("freezeId");
        log.info("Freeze release executed: userId={}, accountId={}, freezeId={}", userId, accountId, freezeId);

        // Finalize freeze release
        assetFreezeService.finalizeFreezeRelease(freezeId, userId, accountId);

        // Restore normal account functionality
        fraudDetectionService.restoreNormalMonitoring(userId, accountId);

        // Send final confirmation
        customerCommunicationService.sendAccountRestoredNotification(userId, accountId);

        log.info("Freeze release finalized: freezeId={}", freezeId);
    }

    private void handlePartialFreezeApplied(GenericKafkaEvent event, String userId, String accountId, String assetType, String correlationId) {
        String freezeId = (String) event.getData().get("freezeId");
        BigDecimal allowedAmount = new BigDecimal(event.getData().get("allowedAmount").toString());
        log.info("Partial freeze applied: userId={}, accountId={}, freezeId={}, allowedAmount={}",
            userId, accountId, freezeId, allowedAmount);

        // Execute partial freeze
        assetFreezeService.executePartialFreeze(freezeId, userId, accountId, assetType, allowedAmount);

        // Notify customer of partial restrictions
        customerCommunicationService.sendPartialFreezeNotification(userId, accountId, allowedAmount);

        log.info("Partial freeze executed: freezeId={}, allowedAmount={}", freezeId, allowedAmount);
    }

    private void handleFreezeEscalated(GenericKafkaEvent event, String userId, String accountId, String correlationId) {
        String freezeId = (String) event.getData().get("freezeId");
        String escalationReason = (String) event.getData().get("escalationReason");
        String escalatedTo = (String) event.getData().get("escalatedTo");
        log.warn("Freeze escalated: userId={}, accountId={}, freezeId={}, reason={}, to={}",
            userId, accountId, freezeId, escalationReason, escalatedTo);

        // Process freeze escalation
        assetFreezeService.escalateFreeze(freezeId, escalationReason, escalatedTo);

        // Send critical notifications
        fraudNotificationService.sendFreezeEscalationAlert(freezeId, escalationReason, escalatedTo, correlationId);

        log.info("Freeze escalation processed: freezeId={}", freezeId);
    }

    private boolean isEventProcessed(String eventKey) {
        Long timestamp = processedEvents.get(eventKey);
        if (timestamp == null) {
            return false;
        }

        // Check if the entry has expired
        if (System.currentTimeMillis() - timestamp > TTL_24_HOURS) {
            processedEvents.remove(eventKey);
            return false;
        }

        return true;
    }

    private void markEventAsProcessed(String eventKey) {
        processedEvents.put(eventKey, System.currentTimeMillis());
    }

    private void cleanExpiredEntries() {
        if (processedEvents.size() > 1000) { // Only clean when we have many entries
            long currentTime = System.currentTimeMillis();
            processedEvents.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > TTL_24_HOURS);
        }
    }
}