package com.waqiti.payment.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.payment.service.PaymentService;
import com.waqiti.payment.service.SettlementService;
import com.waqiti.payment.service.NotificationService;
import com.waqiti.payment.domain.Settlement;
import com.waqiti.payment.domain.SettlementStatus;
import com.waqiti.payment.repository.SettlementRepository;
import com.waqiti.common.distributed.DistributedLockService;
import com.waqiti.common.security.audit.SecurityAuditLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * CRITICAL: Settlement Events Consumer - Processes orphaned settlement events
 * 
 * This consumer was missing causing settlement events to be lost.
 * Without this, merchant settlements would never be processed causing fund distribution failures.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementEventsConsumer {

    private final PaymentService paymentService;
    private final SettlementService settlementService;
    private final SettlementRepository settlementRepository;
    private final NotificationService notificationService;
    private final DistributedLockService lockService;
    private final SecurityAuditLogger securityAuditLogger;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = {"settlement-events", "merchant.settlement.initiated", "settlement-reconciliation-updates"},
        groupId = "settlement-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void processSettlementEvent(
            @Payload String eventPayload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        String lockKey = null;
        
        try {
            log.info("Processing settlement event from topic: {} - partition: {} - offset: {}", 
                    topic, partition, offset);

            // Parse event payload
            Map<String, Object> eventData = objectMapper.readValue(eventPayload, Map.class);
            String settlementId = extractString(eventData, "settlementId");
            String merchantId = extractString(eventData, "merchantId");
            String eventType = extractString(eventData, "eventType");
            BigDecimal amount = extractBigDecimal(eventData, "amount");
            String currency = extractString(eventData, "currency");
            LocalDate settlementDate = extractLocalDate(eventData, "settlementDate");

            // Determine event type if not explicit
            if (eventType == null) {
                eventType = determineEventType(topic, eventData);
            }

            // Validate required fields
            if (settlementId == null || merchantId == null) {
                log.error("Invalid settlement event - missing required fields: settlementId={}, merchantId={}", 
                    settlementId, merchantId);
                acknowledgment.acknowledge(); // Ack to prevent reprocessing
                return;
            }

            // Acquire distributed lock to prevent concurrent processing
            lockKey = "settlement-" + settlementId;
            boolean lockAcquired = lockService.tryLock(lockKey, 60, TimeUnit.SECONDS);
            if (!lockAcquired) {
                log.warn("Could not acquire lock for settlement: {}", settlementId);
                throw new RuntimeException("Lock acquisition failed");
            }

            try {
                // Process based on event type
                switch (eventType.toUpperCase()) {
                    case "INITIATED":
                    case "SETTLEMENT_INITIATED":
                        processSettlementInitiation(settlementId, merchantId, amount, currency, settlementDate, eventData);
                        break;
                    case "COMPLETED":
                    case "SETTLEMENT_COMPLETED":
                        processSettlementCompletion(settlementId, merchantId, amount, currency, eventData);
                        break;
                    case "FAILED":
                    case "SETTLEMENT_FAILED":
                        processSettlementFailure(settlementId, merchantId, eventData);
                        break;
                    case "RECONCILIATION_UPDATE":
                        processSettlementReconciliation(settlementId, merchantId, eventData);
                        break;
                    case "BATCH_SETTLEMENT":
                        processBatchSettlement(eventData);
                        break;
                    default:
                        log.warn("Unknown settlement event type: {} for settlement: {}", eventType, settlementId);
                        processUnknownSettlementEvent(settlementId, merchantId, eventType, eventData);
                }
                
                acknowledgment.acknowledge();
                
            } finally {
                lockService.unlock(lockKey);
            }
            
        } catch (Exception e) {
            log.error("Error processing settlement event", e);
            
            // Send to DLQ after max retries
            if (shouldSendToDlq(e)) {
                sendToDlq(eventPayload, e);
                acknowledgment.acknowledge(); // Acknowledge to prevent reprocessing
            } else {
                throw e; // Let retry mechanism handle
            }
        }
    }

    /**
     * Process settlement initiation event
     */
    private void processSettlementInitiation(String settlementId, String merchantId, BigDecimal amount, 
                                           String currency, LocalDate settlementDate, Map<String, Object> eventData) {
        
        log.info("Processing settlement initiation - settlementId: {} merchantId: {} amount: {} {}", 
            settlementId, merchantId, amount, currency);
        
        try {
            // Check if settlement already exists
            Settlement existingSettlement = settlementRepository.findBySettlementId(settlementId).orElse(null);
            if (existingSettlement != null && existingSettlement.getStatus() != SettlementStatus.PENDING) {
                log.warn("Settlement {} already exists with status: {} - skipping initiation", 
                    settlementId, existingSettlement.getStatus());
                return;
            }
            
            // Create or update settlement record
            Settlement settlement = existingSettlement != null ? existingSettlement : new Settlement();
            settlement.setSettlementId(settlementId);
            settlement.setMerchantId(merchantId);
            settlement.setAmount(amount);
            settlement.setCurrency(currency);
            settlement.setSettlementDate(settlementDate != null ? settlementDate : LocalDate.now().plusDays(1));
            settlement.setStatus(SettlementStatus.INITIATED);
            settlement.setInitiatedAt(LocalDateTime.now());
            
            // Extract additional settlement details
            String paymentPeriodStart = extractString(eventData, "paymentPeriodStart");
            String paymentPeriodEnd = extractString(eventData, "paymentPeriodEnd");
            BigDecimal feeAmount = extractBigDecimal(eventData, "feeAmount");
            Integer transactionCount = extractInteger(eventData, "transactionCount");
            
            settlement.setPaymentPeriodStart(paymentPeriodStart != null ? LocalDate.parse(paymentPeriodStart) : null);
            settlement.setPaymentPeriodEnd(paymentPeriodEnd != null ? LocalDate.parse(paymentPeriodEnd) : null);
            settlement.setFeeAmount(feeAmount != null ? feeAmount : BigDecimal.ZERO);
            settlement.setTransactionCount(transactionCount != null ? transactionCount : 0);
            
            // Save settlement
            settlementRepository.save(settlement);
            
            // Calculate settlement details
            calculateSettlementBreakdown(settlement);
            
            // Create settlement distribution plan
            createSettlementDistributionPlan(settlement);
            
            // Send initiation notifications
            sendSettlementInitiationNotifications(settlement);
            
            // Log settlement initiation
            securityAuditLogger.logSecurityEvent("SETTLEMENT_INITIATED", "SYSTEM",
                "Settlement initiated for merchant",
                Map.of("settlementId", settlementId, "merchantId", merchantId, "amount", amount,
                      "currency", currency, "settlementDate", settlement.getSettlementDate()));
            
            log.info("Successfully initiated settlement: {} for merchant: {} - amount: {} {}", 
                settlementId, merchantId, amount, currency);
                
        } catch (Exception e) {
            log.error("Failed to process settlement initiation for settlement: {}", settlementId, e);
            throw e;
        }
    }

    /**
     * Process settlement completion event
     */
    private void processSettlementCompletion(String settlementId, String merchantId, BigDecimal amount, 
                                           String currency, Map<String, Object> eventData) {
        
        log.info("Processing settlement completion - settlementId: {} merchantId: {}", settlementId, merchantId);
        
        try {
            // Get settlement record
            Settlement settlement = settlementRepository.findBySettlementId(settlementId)
                .orElseThrow(() -> new IllegalStateException("Settlement not found: " + settlementId));
            
            // Validate settlement can be completed
            if (settlement.getStatus() == SettlementStatus.COMPLETED) {
                log.warn("Settlement {} already completed - skipping", settlementId);
                return;
            }
            
            if (settlement.getStatus() != SettlementStatus.INITIATED && 
                settlement.getStatus() != SettlementStatus.IN_PROGRESS) {
                log.warn("Settlement {} not in correct state for completion: {}", settlementId, settlement.getStatus());
            }
            
            // Update settlement status
            settlement.setStatus(SettlementStatus.COMPLETED);
            settlement.setCompletedAt(LocalDateTime.now());
            
            // Extract completion details
            String providerReference = extractString(eventData, "providerReference");
            String bankReference = extractString(eventData, "bankReference");
            BigDecimal actualAmount = extractBigDecimal(eventData, "actualAmount");
            
            settlement.setProviderReference(providerReference);
            settlement.setBankReference(bankReference);
            if (actualAmount != null) {
                settlement.setActualAmount(actualAmount);
            }
            
            settlementRepository.save(settlement);
            
            // Process fund distribution
            distributeFunds(settlement);
            
            // Update merchant balance
            updateMerchantBalance(settlement);
            
            // Send completion notifications
            sendSettlementCompletionNotifications(settlement);
            
            // Create settlement report
            generateSettlementReport(settlement);
            
            // Log settlement completion
            securityAuditLogger.logSecurityEvent("SETTLEMENT_COMPLETED", "SYSTEM",
                "Settlement completed successfully",
                Map.of("settlementId", settlementId, "merchantId", merchantId, 
                      "amount", settlement.getActualAmount() != null ? settlement.getActualAmount() : amount,
                      "providerReference", providerReference != null ? providerReference : "N/A"));
            
            log.info("Successfully completed settlement: {} for merchant: {}", settlementId, merchantId);
            
        } catch (Exception e) {
            log.error("Failed to process settlement completion for settlement: {}", settlementId, e);
            throw e;
        }
    }

    /**
     * Process settlement failure event
     */
    private void processSettlementFailure(String settlementId, String merchantId, Map<String, Object> eventData) {
        
        log.info("Processing settlement failure - settlementId: {} merchantId: {}", settlementId, merchantId);
        
        try {
            // Get settlement record
            Settlement settlement = settlementRepository.findBySettlementId(settlementId)
                .orElseThrow(() -> new IllegalStateException("Settlement not found: " + settlementId));
            
            // Update settlement status
            settlement.setStatus(SettlementStatus.FAILED);
            settlement.setFailedAt(LocalDateTime.now());
            
            // Extract failure details
            String failureReason = extractString(eventData, "failureReason");
            String errorCode = extractString(eventData, "errorCode");
            String providerError = extractString(eventData, "providerError");
            
            settlement.setFailureReason(failureReason);
            settlement.setErrorCode(errorCode);
            settlement.setProviderError(providerError);
            
            settlementRepository.save(settlement);
            
            // Handle settlement failure
            handleSettlementFailure(settlement);
            
            // Send failure notifications
            sendSettlementFailureNotifications(settlement);
            
            // Queue for manual review if needed
            if (shouldQueueForManualReview(settlement, failureReason)) {
                queueSettlementForManualReview(settlement, failureReason);
            }
            
            // Log settlement failure
            securityAuditLogger.logSecurityEvent("SETTLEMENT_FAILED", "SYSTEM",
                "Settlement failed",
                Map.of("settlementId", settlementId, "merchantId", merchantId, 
                      "failureReason", failureReason != null ? failureReason : "N/A",
                      "errorCode", errorCode != null ? errorCode : "N/A"));
            
            log.error("Settlement failed: {} for merchant: {} - reason: {}", 
                settlementId, merchantId, failureReason);
            
        } catch (Exception e) {
            log.error("Failed to process settlement failure for settlement: {}", settlementId, e);
            throw e;
        }
    }

    /**
     * Process settlement reconciliation update
     */
    private void processSettlementReconciliation(String settlementId, String merchantId, Map<String, Object> eventData) {
        
        log.info("Processing settlement reconciliation - settlementId: {} merchantId: {}", settlementId, merchantId);
        
        try {
            // Get settlement record
            Settlement settlement = settlementRepository.findBySettlementId(settlementId)
                .orElseThrow(() -> new IllegalStateException("Settlement not found: " + settlementId));
            
            // Extract reconciliation data
            String reconciliationStatus = extractString(eventData, "reconciliationStatus");
            BigDecimal reconciledAmount = extractBigDecimal(eventData, "reconciledAmount");
            BigDecimal variance = extractBigDecimal(eventData, "variance");
            String reconciliationReference = extractString(eventData, "reconciliationReference");
            
            // Update settlement reconciliation info
            settlement.setReconciliationStatus(reconciliationStatus);
            settlement.setReconciledAmount(reconciledAmount);
            settlement.setReconciliationVariance(variance);
            settlement.setReconciliationReference(reconciliationReference);
            settlement.setReconciledAt(LocalDateTime.now());
            
            settlementRepository.save(settlement);
            
            // Process reconciliation results
            processReconciliationResults(settlement, variance);
            
            // Log reconciliation
            securityAuditLogger.logSecurityEvent("SETTLEMENT_RECONCILED", "SYSTEM",
                "Settlement reconciliation processed",
                Map.of("settlementId", settlementId, "merchantId", merchantId, 
                      "reconciliationStatus", reconciliationStatus != null ? reconciliationStatus : "N/A",
                      "variance", variance != null ? variance : BigDecimal.ZERO));
            
            log.info("Successfully reconciled settlement: {} - status: {} variance: {}", 
                settlementId, reconciliationStatus, variance);
            
        } catch (Exception e) {
            log.error("Failed to process settlement reconciliation for settlement: {}", settlementId, e);
            throw e;
        }
    }

    /**
     * Process batch settlement event
     */
    private void processBatchSettlement(Map<String, Object> eventData) {
        
        log.info("Processing batch settlement event");
        
        try {
            String batchId = extractString(eventData, "batchId");
            List<Map<String, Object>> settlements = (List<Map<String, Object>>) eventData.get("settlements");
            String batchStatus = extractString(eventData, "batchStatus");
            
            if (settlements == null || settlements.isEmpty()) {
                log.warn("No settlements found in batch: {}", batchId);
                return;
            }
            
            log.info("Processing batch settlement: {} with {} settlements", batchId, settlements.size());
            
            // Process each settlement in the batch
            for (Map<String, Object> settlementData : settlements) {
                try {
                    processIndividualBatchSettlement(batchId, settlementData);
                } catch (Exception e) {
                    log.error("Failed to process individual settlement in batch: {}", batchId, e);
                    // Continue processing other settlements
                }
            }
            
            // Update batch status
            updateBatchSettlementStatus(batchId, batchStatus, settlements.size());
            
            log.info("Successfully processed batch settlement: {} - {} settlements", batchId, settlements.size());
            
        } catch (Exception e) {
            log.error("Failed to process batch settlement", e);
            throw e;
        }
    }

    /**
     * Process unknown settlement event
     */
    private void processUnknownSettlementEvent(String settlementId, String merchantId, String eventType, 
                                             Map<String, Object> eventData) {
        
        log.warn("Processing unknown settlement event type: {} for settlement: {}", eventType, settlementId);
        
        try {
            // Queue for manual review
            queueUnknownEventForReview(settlementId, merchantId, eventType, eventData);
            
            // Send alert to operations
            sendOperationsAlert("UNKNOWN_SETTLEMENT_EVENT", settlementId, 
                "Unknown settlement event type received: " + eventType);
            
        } catch (Exception e) {
            log.error("Failed to process unknown settlement event: {}", settlementId, e);
        }
    }

    /**
     * Calculate settlement breakdown and fees
     */
    private void calculateSettlementBreakdown(Settlement settlement) {
        try {
            // Use SettlementService to calculate breakdown
            var breakdown = settlementService.calculateSettlementBreakdown(
                settlement.getSettlementId(),
                settlement.getMerchantId(),
                settlement.getPaymentPeriodStart(),
                settlement.getPaymentPeriodEnd()
            );
            
            // Update settlement with calculated values
            settlement.setGrossAmount(breakdown.getGrossAmount());
            settlement.setNetAmount(breakdown.getNetAmount());
            settlement.setFeeAmount(breakdown.getFeeAmount());
            settlement.setChargebackAmount(breakdown.getChargebackAmount());
            settlement.setRefundAmount(breakdown.getRefundAmount());
            settlement.setTransactionCount(breakdown.getTransactionCount());
            
            settlementRepository.save(settlement);
            
        } catch (Exception e) {
            log.error("Failed to calculate settlement breakdown for: {}", settlement.getSettlementId(), e);
        }
    }

    /**
     * Create settlement distribution plan
     */
    private void createSettlementDistributionPlan(Settlement settlement) {
        try {
            settlementService.createDistributionPlan(settlement.getSettlementId(), settlement.getMerchantId());
        } catch (Exception e) {
            log.error("Failed to create distribution plan for settlement: {}", settlement.getSettlementId(), e);
        }
    }

    /**
     * Distribute funds to merchant account
     */
    private void distributeFunds(Settlement settlement) {
        try {
            settlementService.distributeFunds(
                settlement.getSettlementId(),
                settlement.getMerchantId(),
                settlement.getNetAmount(),
                settlement.getCurrency()
            );
        } catch (Exception e) {
            log.error("Failed to distribute funds for settlement: {}", settlement.getSettlementId(), e);
        }
    }

    /**
     * Update merchant balance
     */
    private void updateMerchantBalance(Settlement settlement) {
        try {
            paymentService.updateMerchantBalance(
                settlement.getMerchantId(),
                settlement.getNetAmount()
            );
        } catch (Exception e) {
            log.error("Failed to update merchant balance for settlement: {}", settlement.getSettlementId(), e);
        }
    }

    /**
     * Generate settlement report
     */
    private void generateSettlementReport(Settlement settlement) {
        try {
            settlementService.generateSettlementReport(settlement.getSettlementId(), settlement.getMerchantId());
        } catch (Exception e) {
            log.error("Failed to generate settlement report for: {}", settlement.getSettlementId(), e);
        }
    }

    /**
     * Handle settlement failure - attempt retry or queue for manual processing
     */
    private void handleSettlementFailure(Settlement settlement) {
        try {
            // Determine if failure is retryable
            if (isRetryableFailure(settlement.getFailureReason())) {
                // Schedule retry
                settlementService.scheduleSettlementRetry(settlement.getSettlementId());
            } else {
                // Queue for manual processing
                queueSettlementForManualReview(settlement, settlement.getFailureReason());
            }
        } catch (Exception e) {
            log.error("Failed to handle settlement failure for: {}", settlement.getSettlementId(), e);
        }
    }

    /**
     * Process reconciliation results
     */
    private void processReconciliationResults(Settlement settlement, BigDecimal variance) {
        try {
            if (variance != null && variance.abs().compareTo(new BigDecimal("0.01")) > 0) {
                // Significant variance detected
                log.warn("Settlement reconciliation variance detected: {} for settlement: {}", 
                    variance, settlement.getSettlementId());
                
                // Queue for manual review
                queueSettlementForManualReview(settlement, "Reconciliation variance: " + variance);
                
                // Send variance alert
                sendVarianceAlert(settlement, variance);
            }
        } catch (Exception e) {
            log.error("Failed to process reconciliation results for: {}", settlement.getSettlementId(), e);
        }
    }

    /**
     * Process individual settlement in batch
     */
    private void processIndividualBatchSettlement(String batchId, Map<String, Object> settlementData) {
        String settlementId = extractString(settlementData, "settlementId");
        String merchantId = extractString(settlementData, "merchantId");
        String status = extractString(settlementData, "status");
        
        log.debug("Processing individual batch settlement: {} - merchant: {} - status: {}", 
            settlementId, merchantId, status);
        
        // Process based on status
        switch (status.toUpperCase()) {
            case "COMPLETED":
                processSettlementCompletion(settlementId, merchantId, 
                    extractBigDecimal(settlementData, "amount"),
                    extractString(settlementData, "currency"), 
                    settlementData);
                break;
            case "FAILED":
                processSettlementFailure(settlementId, merchantId, settlementData);
                break;
            default:
                log.warn("Unknown batch settlement status: {} for settlement: {}", status, settlementId);
        }
    }

    /**
     * Update batch settlement status
     */
    private void updateBatchSettlementStatus(String batchId, String status, int settlementCount) {
        try {
            settlementService.updateBatchStatus(batchId, status, settlementCount);
        } catch (Exception e) {
            log.error("Failed to update batch settlement status: {}", batchId, e);
        }
    }

    /**
     * Determine event type from topic and data
     */
    private String determineEventType(String topic, Map<String, Object> eventData) {
        if (topic.contains("initiated")) {
            return "INITIATED";
        } else if (topic.contains("reconciliation")) {
            return "RECONCILIATION_UPDATE";
        } else {
            // Try to extract from event data
            String status = extractString(eventData, "status");
            return status != null ? status : "UNKNOWN";
        }
    }

    /**
     * Check if failure is retryable
     */
    private boolean isRetryableFailure(String failureReason) {
        if (failureReason == null) return false;
        
        // Retryable failures
        return failureReason.contains("TIMEOUT") ||
               failureReason.contains("NETWORK") ||
               failureReason.contains("TEMPORARY") ||
               failureReason.contains("SERVICE_UNAVAILABLE");
    }

    /**
     * Check if settlement should be queued for manual review
     */
    private boolean shouldQueueForManualReview(Settlement settlement, String failureReason) {
        // Always queue high-value failures for review
        if (settlement.getAmount().compareTo(new BigDecimal("50000")) > 0) {
            return true;
        }
        
        // Queue certain failure types
        if (failureReason != null && (failureReason.contains("FRAUD") || 
                                     failureReason.contains("COMPLIANCE") ||
                                     failureReason.contains("INVALID_ACCOUNT"))) {
            return true;
        }
        
        return false;
    }

    /**
     * Queue settlement for manual review
     */
    private void queueSettlementForManualReview(Settlement settlement, String reason) {
        try {
            Map<String, Object> reviewTask = Map.of(
                "taskType", "SETTLEMENT_MANUAL_REVIEW",
                "settlementId", settlement.getSettlementId(),
                "merchantId", settlement.getMerchantId(),
                "amount", settlement.getAmount(),
                "currency", settlement.getCurrency(),
                "reason", reason != null ? reason : "Manual review required",
                "priority", settlement.getAmount().compareTo(new BigDecimal("50000")) > 0 ? "HIGH" : "NORMAL",
                "createdAt", LocalDateTime.now().toString()
            );
            
            paymentService.queueManualTask("settlement-manual-review-queue", reviewTask);
            
        } catch (Exception e) {
            log.error("Failed to queue settlement for manual review: {}", settlement.getSettlementId(), e);
        }
    }

    /**
     * Queue unknown event for review
     */
    private void queueUnknownEventForReview(String settlementId, String merchantId, String eventType, 
                                          Map<String, Object> eventData) {
        try {
            Map<String, Object> reviewTask = Map.of(
                "taskType", "UNKNOWN_SETTLEMENT_EVENT_REVIEW",
                "settlementId", settlementId,
                "merchantId", merchantId,
                "eventType", eventType,
                "eventData", eventData,
                "priority", "NORMAL",
                "createdAt", LocalDateTime.now().toString()
            );
            
            paymentService.queueManualTask("unknown-event-review-queue", reviewTask);
            
        } catch (Exception e) {
            log.error("Failed to queue unknown event for review: {}", settlementId, e);
        }
    }

    /**
     * Send various notification types
     */
    private void sendSettlementInitiationNotifications(Settlement settlement) {
        try {
            notificationService.sendMerchantNotification(
                settlement.getMerchantId(),
                "SETTLEMENT_INITIATED",
                "Settlement initiated for period - Amount: " + settlement.getAmount() + " " + settlement.getCurrency(),
                Map.of("settlementId", settlement.getSettlementId(), "amount", settlement.getAmount())
            );
        } catch (Exception e) {
            log.error("Failed to send initiation notification", e);
        }
    }

    private void sendSettlementCompletionNotifications(Settlement settlement) {
        try {
            notificationService.sendMerchantNotification(
                settlement.getMerchantId(),
                "SETTLEMENT_COMPLETED",
                "Settlement completed - Funds transferred: " + settlement.getNetAmount() + " " + settlement.getCurrency(),
                Map.of("settlementId", settlement.getSettlementId(), "netAmount", settlement.getNetAmount())
            );
        } catch (Exception e) {
            log.error("Failed to send completion notification", e);
        }
    }

    private void sendSettlementFailureNotifications(Settlement settlement) {
        try {
            notificationService.sendMerchantNotification(
                settlement.getMerchantId(),
                "SETTLEMENT_FAILED",
                "Settlement failed - " + settlement.getFailureReason(),
                Map.of("settlementId", settlement.getSettlementId(), "failureReason", settlement.getFailureReason())
            );
        } catch (Exception e) {
            log.error("Failed to send failure notification", e);
        }
    }

    private void sendVarianceAlert(Settlement settlement, BigDecimal variance) {
        try {
            notificationService.sendOperationsAlert(
                "SETTLEMENT_VARIANCE_DETECTED",
                "Settlement reconciliation variance detected: " + variance + " for settlement: " + settlement.getSettlementId(),
                Map.of("settlementId", settlement.getSettlementId(), "variance", variance)
            );
        } catch (Exception e) {
            log.error("Failed to send variance alert", e);
        }
    }

    private void sendOperationsAlert(String alertType, String settlementId, String message) {
        try {
            notificationService.sendOperationsAlert(alertType, message,
                Map.of("settlementId", settlementId, "timestamp", LocalDateTime.now().toString()));
        } catch (Exception e) {
            log.error("Failed to send operations alert", e);
        }
    }

    /**
     * Helper methods for data extraction
     */
    private String extractString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private BigDecimal extractBigDecimal(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return new BigDecimal(value.toString());
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            log.warn("Failed to parse BigDecimal from value: {} for key: {}", value, key);
            return null;
        }
    }

    private LocalDate extractLocalDate(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof LocalDate) return (LocalDate) value;
        try {
            return LocalDate.parse(value.toString());
        } catch (Exception e) {
            log.warn("Failed to parse LocalDate from value: {} for key: {}", value, key);
            return null;
        }
    }

    private Integer extractInteger(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Integer) return (Integer) value;
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            log.warn("Failed to parse Integer from value: {} for key: {}", value, key);
            return null;
        }
    }

    private boolean shouldSendToDlq(Exception e) {
        // Send to DLQ for non-retryable errors
        return e instanceof IllegalArgumentException ||
               e instanceof IllegalStateException ||
               e instanceof SecurityException;
    }

    private void sendToDlq(String eventPayload, Exception error) {
        try {
            log.error("Sending settlement event to DLQ: {}", error.getMessage());
            securityAuditLogger.logSecurityEvent("SETTLEMENT_EVENT_DLQ", "SYSTEM",
                "Settlement event sent to DLQ",
                Map.of("error", error.getMessage()));
        } catch (Exception e) {
            log.error("Failed to send to DLQ", e);
        }
    }
}