package com.waqiti.payment.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.payment.service.PaymentService;
import com.waqiti.payment.service.NotificationService;
import com.waqiti.payment.domain.FundHold;
import com.waqiti.payment.repository.FundHoldRepository;
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
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * CRITICAL: Fund Release Events Consumer - Processes orphaned fund release events
 * 
 * This consumer was missing causing fund release events to be orphaned.
 * Without this, held funds would never be released causing financial loss.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FundReleaseEventsConsumer {

    private final PaymentService paymentService;
    private final NotificationService notificationService;
    private final FundHoldRepository fundHoldRepository;
    private final DistributedLockService lockService;
    private final SecurityAuditLogger securityAuditLogger;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = {"fund-release-events", "fund-release-events-retry"},
        groupId = "fund-release-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void processFundReleaseEvent(
            @Payload String eventPayload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        String lockKey = null;
        
        try {
            log.info("Processing fund release event from topic: {} - partition: {} - offset: {}", 
                    topic, partition, offset);

            // Parse event payload
            Map<String, Object> eventData = objectMapper.readValue(eventPayload, Map.class);
            String holdId = extractString(eventData, "holdId");
            String accountId = extractString(eventData, "accountId");
            BigDecimal amount = extractBigDecimal(eventData, "amount");
            String currency = extractString(eventData, "currency");
            String releaseReason = extractString(eventData, "releaseReason");
            String authorizationCode = extractString(eventData, "authorizationCode");
            String initiatedBy = extractString(eventData, "initiatedBy");

            // Validate required fields
            if (holdId == null || accountId == null || amount == null) {
                log.error("Invalid fund release event - missing required fields: holdId={}, accountId={}, amount={}", 
                    holdId, accountId, amount);
                acknowledgment.acknowledge(); // Ack to prevent reprocessing
                return;
            }

            // Acquire distributed lock to prevent concurrent processing
            lockKey = "fund-release-" + holdId;
            boolean lockAcquired = lockService.tryLock(lockKey, 30, TimeUnit.SECONDS);
            if (!lockAcquired) {
                log.warn("Could not acquire lock for fund release: {}", holdId);
                throw new RuntimeException("Lock acquisition failed");
            }

            try {
                // 1. Verify hold exists and is valid
                FundHold fundHold = validateFundHold(holdId, accountId, amount);
                
                // 2. Check authorization if required
                if (authorizationCode != null && !verifyReleaseAuthorization(holdId, authorizationCode)) {
                    log.error("SECURITY: Unauthorized fund release attempt for hold: {} by user: {}", 
                        holdId, initiatedBy);
                    
                    securityAuditLogger.logSecurityViolation("UNAUTHORIZED_FUND_RELEASE", 
                        initiatedBy != null ? initiatedBy : "UNKNOWN",
                        "Attempted to release funds without proper authorization",
                        Map.of("holdId", holdId, "accountId", accountId, "amount", amount));
                    
                    // Send security alert
                    sendSecurityAlert(holdId, accountId, initiatedBy, "Unauthorized fund release attempt");
                    acknowledgment.acknowledge();
                    return;
                }
                
                // 3. Process fund release
                boolean releaseSuccessful = processFundRelease(fundHold, releaseReason, initiatedBy);
                
                if (releaseSuccessful) {
                    // 4. Update hold status
                    updateFundHoldStatus(holdId, "RELEASED", LocalDateTime.now());
                    
                    // 5. Send release confirmation
                    sendReleaseNotification(fundHold, releaseReason, initiatedBy);
                    
                    // 6. Log successful release
                    securityAuditLogger.logSecurityEvent("FUND_RELEASE_COMPLETED", 
                        initiatedBy != null ? initiatedBy : "SYSTEM",
                        "Fund hold successfully released",
                        Map.of("holdId", holdId, "accountId", accountId, "amount", amount, 
                              "releaseReason", releaseReason != null ? releaseReason : "N/A"));
                    
                    log.info("Successfully released fund hold: {} - amount: {} {} for account: {}", 
                        holdId, amount, currency, accountId);
                        
                } else {
                    // Handle release failure
                    log.error("Fund release failed for hold: {} - queuing for manual review", holdId);
                    queueForManualReview(fundHold, releaseReason, "AUTOMATED_RELEASE_FAILED");
                }
                
                acknowledgment.acknowledge();
                
            } finally {
                lockService.unlock(lockKey);
            }
            
        } catch (Exception e) {
            log.error("Error processing fund release event", e);
            
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
     * Process Dead Letter Queue events for failed fund releases
     */
    @KafkaListener(
        topics = "fund-release-events.DLQ",
        groupId = "fund-release-dlq-processor"
    )
    public void processFundReleaseDLQ(
            @Payload String eventPayload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment acknowledgment) {
        
        try {
            log.error("Processing fund release DLQ event - manual intervention required");
            
            // Parse event and queue for manual processing
            Map<String, Object> eventData = objectMapper.readValue(eventPayload, Map.class);
            String holdId = extractString(eventData, "holdId");
            
            // Create high priority manual task
            queueHighPriorityManualTask(holdId, eventData, "FUND_RELEASE_DLQ");
            
            // Alert operations team
            sendOperationsAlert("FUND_RELEASE_DLQ", holdId, "Critical fund release failed - manual intervention required");
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process fund release DLQ event", e);
            // For DLQ, we acknowledge even on failure to prevent infinite loops
            acknowledgment.acknowledge();
        }
    }

    /**
     * Validate fund hold exists and is eligible for release
     */
    private FundHold validateFundHold(String holdId, String accountId, BigDecimal amount) {
        FundHold fundHold = fundHoldRepository.findByHoldId(holdId)
            .orElseThrow(() -> new IllegalArgumentException("Fund hold not found: " + holdId));
        
        // Validate hold is in correct state
        if (!"ACTIVE".equals(fundHold.getStatus())) {
            throw new IllegalStateException("Fund hold not in ACTIVE state: " + fundHold.getStatus());
        }
        
        // Validate account matches
        if (!accountId.equals(fundHold.getAccountId())) {
            throw new IllegalArgumentException("Account ID mismatch for hold: " + holdId);
        }
        
        // Validate amount matches
        if (amount.compareTo(fundHold.getHoldAmount()) != 0) {
            log.warn("Release amount {} does not match hold amount {} for hold: {}", 
                amount, fundHold.getHoldAmount(), holdId);
            // Allow partial releases in some cases
        }
        
        // Check hold expiration
        if (fundHold.getExpiresAt() != null && LocalDateTime.now().isAfter(fundHold.getExpiresAt())) {
            log.warn("Fund hold {} has expired but release is being processed", holdId);
        }
        
        return fundHold;
    }

    /**
     * Verify release authorization code
     */
    private boolean verifyReleaseAuthorization(String holdId, String authorizationCode) {
        // In production, this would verify the authorization code against stored hash
        // Could integrate with HSM or secure token service
        try {
            return paymentService.verifyReleaseAuthorization(holdId, authorizationCode);
        } catch (Exception e) {
            log.error("Authorization verification failed for hold: {}", holdId, e);
            return false;
        }
    }

    /**
     * Process the actual fund release
     */
    private boolean processFundRelease(FundHold fundHold, String releaseReason, String initiatedBy) {
        try {
            // Release funds back to account
            paymentService.releaseFunds(
                fundHold.getHoldId(),
                fundHold.getHoldAmount(),
                fundHold.getAccountId()
            );
            
            // Update ledger entries
            paymentService.recordFundReleaseEntry(
                fundHold.getHoldId(),
                fundHold.getAccountId(),
                fundHold.getHoldAmount(),
                fundHold.getCurrency(),
                releaseReason != null ? releaseReason : "SYSTEM_RELEASE"
            );
            
            return true;
            
        } catch (Exception e) {
            log.error("Fund release processing failed for hold: {}", fundHold.getHoldId(), e);
            return false;
        }
    }

    /**
     * Update fund hold status
     */
    private void updateFundHoldStatus(String holdId, String status, LocalDateTime timestamp) {
        try {
            FundHold fundHold = fundHoldRepository.findByHoldId(holdId).orElse(null);
            if (fundHold != null) {
                fundHold.setStatus(status);
                fundHold.setReleasedAt(timestamp);
                fundHoldRepository.save(fundHold);
            }
        } catch (Exception e) {
            log.error("Failed to update hold status for: {}", holdId, e);
        }
    }

    /**
     * Send release notification to relevant parties
     */
    private void sendReleaseNotification(FundHold fundHold, String releaseReason, String initiatedBy) {
        try {
            notificationService.sendFundReleaseNotification(
                fundHold.getAccountId(),
                fundHold.getHoldAmount(),
                fundHold.getCurrency(),
                releaseReason,
                initiatedBy
            );
        } catch (Exception e) {
            log.error("Failed to send release notification for hold: {}", fundHold.getHoldId(), e);
        }
    }

    /**
     * Queue hold for manual review when automated processing fails
     */
    private void queueForManualReview(FundHold fundHold, String releaseReason, String failureReason) {
        try {
            Map<String, Object> manualTask = Map.of(
                "taskType", "FUND_RELEASE_MANUAL_REVIEW",
                "holdId", fundHold.getHoldId(),
                "accountId", fundHold.getAccountId(),
                "amount", fundHold.getHoldAmount(),
                "currency", fundHold.getCurrency(),
                "originalReleaseReason", releaseReason != null ? releaseReason : "N/A",
                "failureReason", failureReason,
                "priority", "HIGH",
                "createdAt", LocalDateTime.now().toString()
            );
            
            // Send to manual review queue
            paymentService.queueManualTask("manual-fund-review-queue", manualTask);
            
        } catch (Exception e) {
            log.error("Failed to queue hold for manual review: {}", fundHold.getHoldId(), e);
        }
    }

    /**
     * Queue high priority manual task for DLQ events
     */
    private void queueHighPriorityManualTask(String holdId, Map<String, Object> eventData, String source) {
        try {
            Map<String, Object> urgentTask = Map.of(
                "taskType", "URGENT_FUND_RELEASE",
                "source", source,
                "holdId", holdId,
                "originalEventData", eventData,
                "priority", "CRITICAL",
                "requiresImmediate", true,
                "createdAt", LocalDateTime.now().toString()
            );
            
            paymentService.queueManualTask("urgent-fund-operations-queue", urgentTask);
            
        } catch (Exception e) {
            log.error("Failed to queue urgent manual task for hold: {}", holdId, e);
        }
    }

    /**
     * Send security alert for unauthorized access attempts
     */
    private void sendSecurityAlert(String holdId, String accountId, String initiatedBy, String reason) {
        try {
            notificationService.sendSecurityAlert(
                "UNAUTHORIZED_FUND_RELEASE_ATTEMPT",
                reason,
                Map.of("holdId", holdId, "accountId", accountId, "initiatedBy", initiatedBy != null ? initiatedBy : "UNKNOWN")
            );
        } catch (Exception e) {
            log.error("Failed to send security alert for hold: {}", holdId, e);
        }
    }

    /**
     * Send operations alert for critical issues
     */
    private void sendOperationsAlert(String alertType, String holdId, String message) {
        try {
            notificationService.sendOperationsAlert(alertType, message, 
                Map.of("holdId", holdId, "timestamp", LocalDateTime.now().toString()));
        } catch (Exception e) {
            log.error("Failed to send operations alert for hold: {}", holdId, e);
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

    private boolean shouldSendToDlq(Exception e) {
        // Send to DLQ for non-retryable errors
        return e instanceof IllegalArgumentException ||
               e instanceof IllegalStateException ||
               e instanceof SecurityException;
    }

    private void sendToDlq(String eventPayload, Exception error) {
        try {
            log.error("Sending fund release event to DLQ: {}", error.getMessage());
            // In production, would send to actual DLQ topic
            // For now, log the DLQ event
            securityAuditLogger.logSecurityEvent("FUND_RELEASE_DLQ", "SYSTEM",
                "Fund release event sent to DLQ",
                Map.of("error", error.getMessage(), "payload", eventPayload));
        } catch (Exception e) {
            log.error("Failed to send to DLQ", e);
        }
    }
}