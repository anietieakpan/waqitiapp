package com.waqiti.reconciliation.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.audit.AuditService;
import com.waqiti.reconciliation.domain.*;
import com.waqiti.reconciliation.repository.ReconciliationRepository;
import com.waqiti.reconciliation.repository.ReconciliationDiscrepancyRepository;
import com.waqiti.reconciliation.service.PaymentReconciliationService;
import com.waqiti.reconciliation.service.LedgerService;
import com.waqiti.reconciliation.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * CRITICAL KAFKA CONSUMER - Payment Reconciliation Initiated
 * 
 * This consumer handles the initiation of payment reconciliation processes.
 * Reconciliation ensures that payment records across different systems (payment service,
 * ledger, external providers) are consistent and complete.
 * 
 * Business Impact:
 * - Detects missing or duplicate payments
 * - Identifies amount mismatches between systems
 * - Ensures financial integrity and audit compliance
 * - Triggers corrective actions for discrepancies
 * 
 * Event Source: payment-service, batch-processing-service
 * Topic: payment.reconciliation.initiated
 * 
 * @author Waqiti Platform Team
 * @since 1.0.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentReconciliationInitiatedConsumer {

    private final ReconciliationRepository reconciliationRepository;
    private final ReconciliationDiscrepancyRepository discrepancyRepository;
    private final PaymentReconciliationService reconciliationService;
    private final LedgerService ledgerService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Processes payment reconciliation initiation events.
     * 
     * Retry Strategy:
     * - 3 attempts with exponential backoff (1s, 2s, 4s)
     * - Failed messages sent to DLQ after max attempts
     * - Critical for financial integrity - must not lose messages
     */
    @KafkaListener(
        topics = "payment.reconciliation.initiated",
        groupId = "reconciliation-payment-processing-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 4000),
        autoCreateTopics = "false",
        dltTopicSuffix = "-dlt",
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        include = {Exception.class}
    )
    @Transactional(rollbackFor = Exception.class)
    public void handlePaymentReconciliationInitiated(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
            ConsumerRecord<String, String> record,
            Acknowledgment acknowledgment) {
        
        long startTime = System.currentTimeMillis();
        
        try {
            log.info("Processing payment reconciliation initiation: key={}, partition={}, offset={}", 
                key, partition, record.offset());
            
            // Parse the reconciliation event
            Map<String, Object> event = objectMapper.readValue(message, Map.class);
            
            String reconciliationId = (String) event.get("reconciliationId");
            String reconciliationType = (String) event.get("reconciliationType");
            String startDateStr = (String) event.get("startDate");
            String endDateStr = (String) event.get("endDate");
            String initiatedBy = (String) event.get("initiatedBy");
            String reconciliationScope = (String) event.getOrDefault("scope", "PAYMENT");
            Integer batchSize = (Integer) event.getOrDefault("batchSize", 1000);
            
            // Validate required fields
            if (reconciliationId == null || reconciliationType == null) {
                log.error("Invalid reconciliation event - missing required fields: {}", event);
                publishReconciliationFailedEvent(reconciliationId, "VALIDATION_ERROR", 
                    "Missing required fields: reconciliationId or reconciliationType");
                acknowledgment.acknowledge();
                return;
            }
            
            // Parse dates
            LocalDateTime startDate = LocalDateTime.parse(startDateStr, DateTimeFormatter.ISO_DATE_TIME);
            LocalDateTime endDate = LocalDateTime.parse(endDateStr, DateTimeFormatter.ISO_DATE_TIME);
            
            // Create reconciliation record
            ReconciliationRun reconciliationRun = ReconciliationRun.builder()
                    .id(UUID.fromString(reconciliationId))
                    .reconciliationType(ReconciliationType.valueOf(reconciliationType))
                    .scope(ReconciliationScope.valueOf(reconciliationScope))
                    .startDate(startDate)
                    .endDate(endDate)
                    .status(ReconciliationStatus.IN_PROGRESS)
                    .initiatedBy(initiatedBy)
                    .initiatedAt(LocalDateTime.now())
                    .batchSize(batchSize)
                    .totalRecordsToReconcile(0L)
                    .recordsProcessed(0L)
                    .discrepanciesFound(0L)
                    .build();
            
            // Save reconciliation run
            reconciliationRun = reconciliationRepository.save(reconciliationRun);
            
            // Audit the initiation
            auditService.logEvent("RECONCILIATION_INITIATED", Map.of(
                "reconciliationId", reconciliationId,
                "type", reconciliationType,
                "scope", reconciliationScope,
                "startDate", startDateStr,
                "endDate", endDateStr,
                "initiatedBy", initiatedBy
            ));
            
            // Start the actual reconciliation process asynchronously
            startReconciliationProcess(reconciliationRun);
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("Successfully initiated payment reconciliation: {} in {}ms", 
                reconciliationId, duration);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Critical error processing payment reconciliation initiation", e);
            
            // Extract reconciliation ID for error reporting
            String reconciliationId = extractReconciliationId(message);
            
            // Publish failure event
            publishReconciliationFailedEvent(reconciliationId, "PROCESSING_ERROR", e.getMessage());
            
            // Audit the failure
            auditService.logEvent("RECONCILIATION_INITIATION_FAILURE", Map.of(
                "reconciliationId", reconciliationId != null ? reconciliationId : "unknown",
                "errorType", e.getClass().getSimpleName(),
                "errorMessage", e.getMessage(),
                "stackTrace", getStackTraceAsString(e),
                "timestamp", LocalDateTime.now().toString()
            ));
            
            // Rethrow to trigger retry mechanism
            throw new RuntimeException("Failed to process payment reconciliation initiation", e);
        }
    }
    
    /**
     * Starts the actual reconciliation process.
     * This involves comparing payment records across systems and identifying discrepancies.
     */
    private void startReconciliationProcess(ReconciliationRun reconciliationRun) {
        try {
            log.info("Starting reconciliation process for run: {}", reconciliationRun.getId());
            
            // Fetch payment records from payment service
            List<PaymentRecord> paymentRecords = reconciliationService.fetchPaymentRecords(
                reconciliationRun.getStartDate(),
                reconciliationRun.getEndDate(),
                reconciliationRun.getBatchSize()
            );
            
            // Fetch ledger entries
            List<LedgerEntry> ledgerEntries = ledgerService.fetchLedgerEntries(
                reconciliationRun.getStartDate(),
                reconciliationRun.getEndDate()
            );
            
            // Update total records count
            reconciliationRun.setTotalRecordsToReconcile((long) paymentRecords.size());
            reconciliationRepository.save(reconciliationRun);
            
            // Perform reconciliation comparison
            ReconciliationResult result = reconciliationService.compareRecords(
                paymentRecords,
                ledgerEntries,
                reconciliationRun
            );
            
            // Save any discrepancies found
            if (!result.getDiscrepancies().isEmpty()) {
                log.warn("Found {} discrepancies in reconciliation run: {}", 
                    result.getDiscrepancies().size(), reconciliationRun.getId());
                
                for (ReconciliationDiscrepancy discrepancy : result.getDiscrepancies()) {
                    discrepancy.setReconciliationRunId(reconciliationRun.getId());
                    discrepancyRepository.save(discrepancy);
                    
                    // Publish discrepancy detected event for each one
                    publishDiscrepancyDetectedEvent(reconciliationRun.getId(), discrepancy);
                }
            }
            
            // Update reconciliation run status
            reconciliationRun.setStatus(ReconciliationStatus.COMPLETED);
            reconciliationRun.setCompletedAt(LocalDateTime.now());
            reconciliationRun.setRecordsProcessed((long) paymentRecords.size());
            reconciliationRun.setDiscrepanciesFound((long) result.getDiscrepancies().size());
            reconciliationRun.setMatchedRecords(result.getMatchedCount());
            reconciliationRun.setUnmatchedRecords(result.getUnmatchedCount());
            reconciliationRepository.save(reconciliationRun);
            
            // Publish reconciliation completed event
            publishReconciliationCompletedEvent(reconciliationRun);
            
            // Send summary notification
            notificationService.sendReconciliationCompletedNotification(reconciliationRun);
            
            log.info("Completed reconciliation process for run: {} - Matched: {}, Unmatched: {}, Discrepancies: {}", 
                reconciliationRun.getId(), 
                result.getMatchedCount(),
                result.getUnmatchedCount(),
                result.getDiscrepancies().size());
            
        } catch (Exception e) {
            log.error("Error during reconciliation process for run: {}", reconciliationRun.getId(), e);
            
            // Update run status to failed
            reconciliationRun.setStatus(ReconciliationStatus.FAILED);
            reconciliationRun.setCompletedAt(LocalDateTime.now());
            reconciliationRun.setErrorMessage(e.getMessage());
            reconciliationRepository.save(reconciliationRun);
            
            // Publish failure event
            publishReconciliationFailedEvent(
                reconciliationRun.getId().toString(),
                "RECONCILIATION_PROCESS_ERROR",
                e.getMessage()
            );
            
            // Notify operations team
            notificationService.sendReconciliationFailedNotification(reconciliationRun, e);
        }
    }
    
    private void publishReconciliationCompletedEvent(ReconciliationRun reconciliationRun) {
        try {
            Map<String, Object> event = Map.of(
                "eventType", "payment-reconciliation-completed",
                "reconciliationId", reconciliationRun.getId().toString(),
                "reconciliationType", reconciliationRun.getReconciliationType().toString(),
                "scope", reconciliationRun.getScope().toString(),
                "startDate", reconciliationRun.getStartDate().toString(),
                "endDate", reconciliationRun.getEndDate().toString(),
                "totalRecords", reconciliationRun.getTotalRecordsToReconcile(),
                "matchedRecords", reconciliationRun.getMatchedRecords(),
                "unmatchedRecords", reconciliationRun.getUnmatchedRecords(),
                "discrepanciesFound", reconciliationRun.getDiscrepanciesFound(),
                "completedAt", reconciliationRun.getCompletedAt().toString(),
                "timestamp", LocalDateTime.now().toString()
            );
            
            kafkaTemplate.send("payment.reconciliation.completed", 
                reconciliationRun.getId().toString(), event);
            
            log.debug("Published payment reconciliation completed event: {}", reconciliationRun.getId());
            
        } catch (Exception e) {
            log.error("Failed to publish reconciliation completed event", e);
            // Non-critical - log but don't fail the reconciliation
        }
    }
    
    private void publishDiscrepancyDetectedEvent(UUID reconciliationRunId, ReconciliationDiscrepancy discrepancy) {
        try {
            Map<String, Object> event = Map.of(
                "eventType", "payment-discrepancy-detected",
                "reconciliationRunId", reconciliationRunId.toString(),
                "discrepancyId", discrepancy.getId().toString(),
                "discrepancyType", discrepancy.getDiscrepancyType().toString(),
                "paymentId", discrepancy.getPaymentId() != null ? discrepancy.getPaymentId() : "N/A",
                "transactionId", discrepancy.getTransactionId() != null ? discrepancy.getTransactionId() : "N/A",
                "expectedAmount", discrepancy.getExpectedAmount() != null ? discrepancy.getExpectedAmount().toString() : "0",
                "actualAmount", discrepancy.getActualAmount() != null ? discrepancy.getActualAmount().toString() : "0",
                "amountDifference", discrepancy.getAmountDifference() != null ? discrepancy.getAmountDifference().toString() : "0",
                "severity", discrepancy.getSeverity().toString(),
                "description", discrepancy.getDescription(),
                "timestamp", LocalDateTime.now().toString()
            );
            
            kafkaTemplate.send("payment.discrepancy.detected", discrepancy.getId().toString(), event);
            
            log.debug("Published payment discrepancy detected event: {}", discrepancy.getId());
            
        } catch (Exception e) {
            log.error("Failed to publish discrepancy detected event", e);
            // Non-critical - log but don't fail the reconciliation
        }
    }
    
    private void publishReconciliationFailedEvent(String reconciliationId, String errorCode, String errorMessage) {
        try {
            Map<String, Object> event = Map.of(
                "eventType", "payment-reconciliation-failed",
                "reconciliationId", reconciliationId != null ? reconciliationId : "unknown",
                "errorCode", errorCode,
                "errorMessage", errorMessage,
                "timestamp", LocalDateTime.now().toString()
            );
            
            kafkaTemplate.send("payment.reconciliation.failed", reconciliationId, event);
            
            log.debug("Published payment reconciliation failed event: reconciliationId={}, error={}", 
                reconciliationId, errorCode);
            
        } catch (Exception e) {
            log.error("Failed to publish reconciliation failed event", e);
            // Last resort - audit the failure
            try {
                auditService.logEvent("RECONCILIATION_EVENT_PUBLISH_FAILURE", Map.of(
                    "reconciliationId", reconciliationId != null ? reconciliationId : "unknown",
                    "errorCode", errorCode,
                    "publishError", e.getMessage()
                ));
            } catch (Exception auditException) {
                log.error("CRITICAL: Unable to audit or publish reconciliation failure - manual intervention required", 
                    auditException);
            }
        }
    }
    
    private String extractReconciliationId(String message) {
        try {
            Map<String, Object> event = objectMapper.readValue(message, Map.class);
            return (String) event.get("reconciliationId");
        } catch (Exception e) {
            log.error("Failed to extract reconciliationId from message", e);
            return null;
        }
    }
    
    private String getStackTraceAsString(Exception e) {
        return org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace(e);
    }
}