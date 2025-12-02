package com.waqiti.reconciliation.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.audit.AuditService;
import com.waqiti.reconciliation.domain.*;
import com.waqiti.reconciliation.repository.ReconciliationDiscrepancyRepository;
import com.waqiti.reconciliation.service.DiscrepancyResolutionService;
import com.waqiti.reconciliation.service.NotificationService;
import com.waqiti.reconciliation.service.FraudDetectionService;
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
import java.util.*;

/**
 * CRITICAL KAFKA CONSUMER - Payment Discrepancy Detected
 * 
 * This consumer handles payment discrepancy detection events which indicate
 * mismatches between expected and actual payment amounts, missing payments,
 * duplicate payments, or other reconciliation failures.
 * 
 * Business Impact:
 * - IMMEDIATE fraud detection and prevention
 * - Revenue leakage identification
 * - Financial integrity preservation
 * - Regulatory compliance maintenance
 * - Automated corrective action triggering
 * 
 * Event Source: reconciliation-service (from PaymentReconciliationInitiatedConsumer)
 * Topic: payment.discrepancy.detected
 * 
 * Severity Levels:
 * - CRITICAL: Amount differences > $1000, missing payments, potential fraud
 * - HIGH: Amount differences $100-$1000, duplicate payments
 * - MEDIUM: Amount differences $10-$100, minor timing discrepancies
 * - LOW: Amount differences < $10, rounding errors
 * 
 * @author Waqiti Platform Team
 * @since 1.0.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentDiscrepancyDetectedConsumer {

    private final ReconciliationDiscrepancyRepository discrepancyRepository;
    private final DiscrepancyResolutionService resolutionService;
    private final NotificationService notificationService;
    private final FraudDetectionService fraudDetectionService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // Thresholds for automated actions
    private static final BigDecimal CRITICAL_AMOUNT_THRESHOLD = new BigDecimal("1000.00");
    private static final BigDecimal HIGH_AMOUNT_THRESHOLD = new BigDecimal("100.00");
    private static final BigDecimal MEDIUM_AMOUNT_THRESHOLD = new BigDecimal("10.00");

    /**
     * Processes payment discrepancy detection events.
     * 
     * Retry Strategy:
     * - 3 attempts with exponential backoff
     * - CRITICAL: Discrepancies must not be lost - DLQ for manual review
     * - Each discrepancy may represent financial loss or fraud
     */
    @KafkaListener(
        topics = "payment.discrepancy.detected",
        groupId = "reconciliation-discrepancy-processing-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 500, multiplier = 2.0, maxDelay = 2000),
        autoCreateTopics = "false",
        dltTopicSuffix = "-dlt",
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        include = {Exception.class}
    )
    @Transactional(rollbackFor = Exception.class)
    public void handlePaymentDiscrepancyDetected(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
            ConsumerRecord<String, String> record,
            Acknowledgment acknowledgment) {
        
        long startTime = System.currentTimeMillis();
        
        try {
            log.warn("Processing PAYMENT DISCREPANCY: key={}, partition={}, offset={}", 
                key, partition, record.offset());
            
            // Parse the discrepancy event
            Map<String, Object> event = objectMapper.readValue(message, Map.class);
            
            String discrepancyId = (String) event.get("discrepancyId");
            String reconciliationRunId = (String) event.get("reconciliationRunId");
            String discrepancyType = (String) event.get("discrepancyType");
            String paymentId = (String) event.getOrDefault("paymentId", "N/A");
            String transactionId = (String) event.getOrDefault("transactionId", "N/A");
            
            BigDecimal expectedAmount = new BigDecimal(event.getOrDefault("expectedAmount", "0").toString());
            BigDecimal actualAmount = new BigDecimal(event.getOrDefault("actualAmount", "0").toString());
            BigDecimal amountDifference = new BigDecimal(event.getOrDefault("amountDifference", "0").toString());
            
            String severity = (String) event.get("severity");
            String description = (String) event.get("description");
            
            // Validate required fields
            if (discrepancyId == null || discrepancyType == null) {
                log.error("Invalid discrepancy event - missing required fields: {}", event);
                acknowledgment.acknowledge();
                return;
            }
            
            // Load the discrepancy record
            Optional<ReconciliationDiscrepancy> discrepancyOpt = discrepancyRepository
                .findById(UUID.fromString(discrepancyId));
            
            if (discrepancyOpt.isEmpty()) {
                log.error("Discrepancy record not found: {}", discrepancyId);
                acknowledgment.acknowledge();
                return;
            }
            
            ReconciliationDiscrepancy discrepancy = discrepancyOpt.get();
            
            // Determine if this is potentially fraudulent
            boolean potentialFraud = checkForFraud(discrepancy, paymentId, transactionId);
            
            if (potentialFraud) {
                log.error("POTENTIAL FRAUD DETECTED in discrepancy: {} for payment: {}", 
                    discrepancyId, paymentId);
                discrepancy.setPotentialFraud(true);
                discrepancy.setFraudScore(fraudDetectionService.calculateFraudScore(discrepancy));
            }
            
            // Categorize severity if not already set
            if (discrepancy.getSeverity() == null) {
                DiscrepancySeverity computedSeverity = categorizeDiscrepancySeverity(
                    amountDifference,
                    discrepancyType,
                    potentialFraud
                );
                discrepancy.setSeverity(computedSeverity);
            }
            
            // Update status
            discrepancy.setStatus(DiscrepancyStatus.PENDING_INVESTIGATION);
            discrepancy.setDetectedAt(LocalDateTime.now());
            discrepancy = discrepancyRepository.save(discrepancy);
            
            // Trigger immediate actions based on severity
            handleDiscrepancyBySeverity(discrepancy);
            
            // Attempt automated resolution for low-severity discrepancies
            if (discrepancy.getSeverity() == DiscrepancySeverity.LOW) {
                attemptAutomatedResolution(discrepancy);
            }
            
            // Send alerts based on severity
            sendDiscrepancyAlerts(discrepancy);
            
            // Audit the discrepancy detection
            auditService.logSecurityEvent("PAYMENT_DISCREPANCY_DETECTED", Map.of(
                "discrepancyId", discrepancyId,
                "reconciliationRunId", reconciliationRunId,
                "discrepancyType", discrepancyType,
                "paymentId", paymentId,
                "transactionId", transactionId,
                "expectedAmount", expectedAmount.toString(),
                "actualAmount", actualAmount.toString(),
                "amountDifference", amountDifference.toString(),
                "severity", discrepancy.getSeverity().toString(),
                "potentialFraud", potentialFraud,
                "processingTimeMs", (System.currentTimeMillis() - startTime)
            ));
            
            long duration = System.currentTimeMillis() - startTime;
            log.warn("Processed payment discrepancy: {} (severity: {}) in {}ms", 
                discrepancyId, discrepancy.getSeverity(), duration);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("CRITICAL ERROR processing payment discrepancy", e);
            
            // Extract discrepancy ID for error reporting
            String discrepancyId = extractDiscrepancyId(message);
            
            // Audit the failure
            auditService.logSecurityEvent("PAYMENT_DISCREPANCY_PROCESSING_FAILURE", Map.of(
                "discrepancyId", discrepancyId != null ? discrepancyId : "unknown",
                "errorType", e.getClass().getSimpleName(),
                "errorMessage", e.getMessage(),
                "stackTrace", getStackTraceAsString(e),
                "timestamp", LocalDateTime.now().toString()
            ));
            
            // Send critical alert for processing failure
            try {
                notificationService.sendCriticalAlertToOpsTeam(
                    "Payment Discrepancy Processing Failure",
                    "Failed to process payment discrepancy: " + discrepancyId + ". " +
                    "Error: " + e.getMessage()
                );
            } catch (Exception notifException) {
                log.error("Failed to send critical alert", notifException);
            }
            
            // Rethrow to trigger retry mechanism
            throw new RuntimeException("Failed to process payment discrepancy", e);
        }
    }
    
    /**
     * Checks if the discrepancy shows signs of fraudulent activity.
     */
    private boolean checkForFraud(ReconciliationDiscrepancy discrepancy, String paymentId, String transactionId) {
        try {
            // Check for fraud indicators
            boolean hasLargeDiscrepancy = discrepancy.getAmountDifference() != null &&
                discrepancy.getAmountDifference().abs().compareTo(CRITICAL_AMOUNT_THRESHOLD) > 0;
            
            boolean isMissingPayment = discrepancy.getDiscrepancyType() == DiscrepancyType.MISSING_PAYMENT;
            
            boolean isDuplicatePayment = discrepancy.getDiscrepancyType() == DiscrepancyType.DUPLICATE_PAYMENT;
            
            // Additional fraud checks via fraud detection service
            boolean suspiciousPattern = false;
            if (!"N/A".equals(paymentId)) {
                suspiciousPattern = fraudDetectionService.checkForSuspiciousPattern(
                    paymentId,
                    transactionId,
                    discrepancy.getAmountDifference()
                );
            }
            
            return hasLargeDiscrepancy || isMissingPayment || isDuplicatePayment || suspiciousPattern;
            
        } catch (Exception e) {
            log.error("Error checking for fraud in discrepancy: {}", discrepancy.getId(), e);
            // Err on the side of caution - mark as potential fraud for manual review
            return true;
        }
    }
    
    /**
     * Categorizes discrepancy severity based on amount and type.
     */
    private DiscrepancySeverity categorizeDiscrepancySeverity(
            BigDecimal amountDifference, 
            String discrepancyType,
            boolean potentialFraud) {
        
        BigDecimal absoluteDifference = amountDifference.abs();
        
        // Fraud always critical
        if (potentialFraud) {
            return DiscrepancySeverity.CRITICAL;
        }
        
        // Missing or duplicate payments are high severity
        if ("MISSING_PAYMENT".equals(discrepancyType) || "DUPLICATE_PAYMENT".equals(discrepancyType)) {
            return DiscrepancySeverity.HIGH;
        }
        
        // Categorize by amount
        if (absoluteDifference.compareTo(CRITICAL_AMOUNT_THRESHOLD) >= 0) {
            return DiscrepancySeverity.CRITICAL;
        } else if (absoluteDifference.compareTo(HIGH_AMOUNT_THRESHOLD) >= 0) {
            return DiscrepancySeverity.HIGH;
        } else if (absoluteDifference.compareTo(MEDIUM_AMOUNT_THRESHOLD) >= 0) {
            return DiscrepancySeverity.MEDIUM;
        } else {
            return DiscrepancySeverity.LOW;
        }
    }
    
    /**
     * Handles discrepancy processing based on severity level.
     */
    private void handleDiscrepancyBySeverity(ReconciliationDiscrepancy discrepancy) {
        try {
            switch (discrepancy.getSeverity()) {
                case CRITICAL:
                    handleCriticalDiscrepancy(discrepancy);
                    break;
                case HIGH:
                    handleHighDiscrepancy(discrepancy);
                    break;
                case MEDIUM:
                    handleMediumDiscrepancy(discrepancy);
                    break;
                case LOW:
                    handleLowDiscrepancy(discrepancy);
                    break;
            }
        } catch (Exception e) {
            log.error("Error handling discrepancy by severity: {}", discrepancy.getId(), e);
            // Non-critical - log but don't fail the entire process
        }
    }
    
    private void handleCriticalDiscrepancy(ReconciliationDiscrepancy discrepancy) {
        log.error("CRITICAL DISCREPANCY: {} - Immediate action required", discrepancy.getId());
        
        // Create immediate investigation task
        resolutionService.createUrgentInvestigationTask(discrepancy);
        
        // If potential fraud, freeze related accounts/wallets
        if (discrepancy.isPotentialFraud()) {
            resolutionService.initiateEmergencyFraudProtocol(discrepancy);
        }
        
        // Escalate to senior management
        notificationService.sendCriticalDiscrepancyToManagement(discrepancy);
    }
    
    private void handleHighDiscrepancy(ReconciliationDiscrepancy discrepancy) {
        log.warn("HIGH SEVERITY DISCREPANCY: {} - Investigation needed", discrepancy.getId());
        
        // Create investigation task
        resolutionService.createInvestigationTask(discrepancy);
        
        // Notify finance team lead
        notificationService.sendHighSeverityDiscrepancyToFinanceLead(discrepancy);
    }
    
    private void handleMediumDiscrepancy(ReconciliationDiscrepancy discrepancy) {
        log.warn("MEDIUM SEVERITY DISCREPANCY: {} - Review required", discrepancy.getId());
        
        // Queue for review
        resolutionService.queueForReview(discrepancy);
        
        // Notify finance team
        notificationService.sendDiscrepancyToFinanceTeam(discrepancy);
    }
    
    private void handleLowDiscrepancy(ReconciliationDiscrepancy discrepancy) {
        log.info("LOW SEVERITY DISCREPANCY: {} - Attempting automated resolution", discrepancy.getId());
        
        // Queue for batch processing
        resolutionService.queueForBatchProcessing(discrepancy);
    }
    
    /**
     * Attempts automated resolution for low-severity discrepancies.
     */
    private void attemptAutomatedResolution(ReconciliationDiscrepancy discrepancy) {
        try {
            log.info("Attempting automated resolution for discrepancy: {}", discrepancy.getId());
            
            ResolutionResult result = resolutionService.attemptAutomatedResolution(discrepancy);
            
            if (result.isResolved()) {
                discrepancy.setStatus(DiscrepancyStatus.AUTO_RESOLVED);
                discrepancy.setResolvedAt(LocalDateTime.now());
                discrepancy.setResolutionMethod("AUTOMATED");
                discrepancy.setResolutionNotes(result.getResolutionNotes());
                discrepancyRepository.save(discrepancy);
                
                log.info("Successfully auto-resolved discrepancy: {}", discrepancy.getId());
                
                // Publish resolution event
                publishDiscrepancyResolvedEvent(discrepancy);
            } else {
                log.info("Could not auto-resolve discrepancy: {}, will require manual review", 
                    discrepancy.getId());
            }
            
        } catch (Exception e) {
            log.error("Error attempting automated resolution for: {}", discrepancy.getId(), e);
            // Non-critical - will fall back to manual resolution
        }
    }
    
    /**
     * Sends alerts based on discrepancy severity.
     */
    private void sendDiscrepancyAlerts(ReconciliationDiscrepancy discrepancy) {
        try {
            switch (discrepancy.getSeverity()) {
                case CRITICAL:
                    notificationService.sendCriticalAlertToOpsTeam(
                        "CRITICAL Payment Discrepancy",
                        formatDiscrepancyMessage(discrepancy)
                    );
                    notificationService.sendSMSAlertToFinanceManager(
                        formatDiscrepancyMessage(discrepancy)
                    );
                    break;
                    
                case HIGH:
                    notificationService.sendHighPriorityEmailToFinanceTeam(
                        "High Priority Payment Discrepancy",
                        formatDiscrepancyMessage(discrepancy)
                    );
                    break;
                    
                case MEDIUM:
                    notificationService.sendEmailToFinanceTeam(
                        "Payment Discrepancy Detected",
                        formatDiscrepancyMessage(discrepancy)
                    );
                    break;
                    
                case LOW:
                    // Low severity - included in daily digest only
                    break;
            }
        } catch (Exception e) {
            log.error("Error sending discrepancy alerts: {}", discrepancy.getId(), e);
            // Non-critical - log but don't fail
        }
    }
    
    private String formatDiscrepancyMessage(ReconciliationDiscrepancy discrepancy) {
        return String.format(
            "Discrepancy ID: %s\n" +
            "Type: %s\n" +
            "Severity: %s\n" +
            "Payment ID: %s\n" +
            "Transaction ID: %s\n" +
            "Expected Amount: %s\n" +
            "Actual Amount: %s\n" +
            "Difference: %s\n" +
            "Potential Fraud: %s\n" +
            "Description: %s",
            discrepancy.getId(),
            discrepancy.getDiscrepancyType(),
            discrepancy.getSeverity(),
            discrepancy.getPaymentId(),
            discrepancy.getTransactionId(),
            discrepancy.getExpectedAmount(),
            discrepancy.getActualAmount(),
            discrepancy.getAmountDifference(),
            discrepancy.isPotentialFraud(),
            discrepancy.getDescription()
        );
    }
    
    private void publishDiscrepancyResolvedEvent(ReconciliationDiscrepancy discrepancy) {
        try {
            Map<String, Object> event = Map.of(
                "eventType", "payment-discrepancy-resolved",
                "discrepancyId", discrepancy.getId().toString(),
                "resolutionMethod", discrepancy.getResolutionMethod(),
                "resolvedAt", discrepancy.getResolvedAt().toString(),
                "timestamp", LocalDateTime.now().toString()
            );
            
            kafkaTemplate.send("payment.discrepancy.resolved", discrepancy.getId().toString(), event);
            
            log.debug("Published payment discrepancy resolved event: {}", discrepancy.getId());
            
        } catch (Exception e) {
            log.error("Failed to publish discrepancy resolved event", e);
            // Non-critical - log but don't fail
        }
    }
    
    private String extractDiscrepancyId(String message) {
        try {
            Map<String, Object> event = objectMapper.readValue(message, Map.class);
            return (String) event.get("discrepancyId");
        } catch (Exception e) {
            log.error("Failed to extract discrepancyId from message", e);
            return null;
        }
    }
    
    private String getStackTraceAsString(Exception e) {
        return org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace(e);
    }
}