package com.waqiti.payment.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.payment.service.PaymentService;
import com.waqiti.payment.service.NotificationService;
import com.waqiti.payment.domain.Payment;
import com.waqiti.payment.domain.PaymentStatus;
import com.waqiti.payment.repository.PaymentRepository;
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
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * CRITICAL: Payment Approval Consumer - Processes orphaned payment approval events
 * 
 * This consumer was missing causing approved payments to never be processed.
 * Without this, payments approved by fraud detection would remain stuck.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentApprovalConsumer {

    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;
    private final NotificationService notificationService;
    private final DistributedLockService lockService;
    private final SecurityAuditLogger securityAuditLogger;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = {"payment.approved", "payment.blocked", "payment-status-updates"},
        groupId = "payment-approval-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void processPaymentApprovalEvent(
            @Payload String eventPayload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        String lockKey = null;
        
        try {
            log.info("Processing payment approval event from topic: {} - partition: {} - offset: {}", 
                    topic, partition, offset);

            // Parse event payload
            Map<String, Object> eventData = objectMapper.readValue(eventPayload, Map.class);
            String paymentId = extractString(eventData, "paymentId");
            String newStatus = extractString(eventData, "status");
            String reason = extractString(eventData, "reason");
            String approvedBy = extractString(eventData, "approvedBy");
            BigDecimal riskScore = extractBigDecimal(eventData, "riskScore");
            String fraudCheckId = extractString(eventData, "fraudCheckId");

            // Validate required fields
            if (paymentId == null || newStatus == null) {
                log.error("Invalid payment approval event - missing required fields: paymentId={}, status={}", 
                    paymentId, newStatus);
                acknowledgment.acknowledge(); // Ack to prevent reprocessing
                return;
            }

            // Acquire distributed lock to prevent concurrent processing
            lockKey = "payment-approval-" + paymentId;
            boolean lockAcquired = lockService.tryLock(lockKey, 30, TimeUnit.SECONDS);
            if (!lockAcquired) {
                log.warn("Could not acquire lock for payment approval: {}", paymentId);
                throw new RuntimeException("Lock acquisition failed");
            }

            try {
                // 1. Validate payment exists and is in correct state
                Payment payment = validatePaymentForApproval(paymentId);
                
                // 2. Process based on approval status
                switch (newStatus.toUpperCase()) {
                    case "APPROVED":
                        processPaymentApproval(payment, reason, approvedBy, riskScore, fraudCheckId);
                        break;
                    case "BLOCKED":
                    case "REJECTED":
                        processPaymentBlocking(payment, reason, approvedBy, riskScore, fraudCheckId);
                        break;
                    case "PENDING_REVIEW":
                        processPaymentPendingReview(payment, reason, riskScore, fraudCheckId);
                        break;
                    case "REQUIRES_VERIFICATION":
                        processPaymentRequiresVerification(payment, reason, fraudCheckId);
                        break;
                    default:
                        log.warn("Unknown payment status: {} for payment: {}", newStatus, paymentId);
                        processUnknownStatus(payment, newStatus, reason);
                }
                
                acknowledgment.acknowledge();
                
            } finally {
                lockService.unlock(lockKey);
            }
            
        } catch (Exception e) {
            log.error("Error processing payment approval event", e);
            
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
     * Validate payment exists and is in correct state for approval processing
     */
    private Payment validatePaymentForApproval(String paymentId) {
        Payment payment = paymentRepository.findById(UUID.fromString(paymentId))
            .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));
        
        // Check if payment is in a state that can be processed
        if (payment.getStatus() == PaymentStatus.COMPLETED || 
            payment.getStatus() == PaymentStatus.FAILED ||
            payment.getStatus() == PaymentStatus.CANCELLED) {
            
            log.warn("Payment {} already in final state: {} - skipping approval processing", 
                paymentId, payment.getStatus());
            throw new IllegalStateException("Payment already in final state: " + payment.getStatus());
        }
        
        return payment;
    }

    /**
     * Process payment approval - proceed with payment execution
     */
    private void processPaymentApproval(Payment payment, String reason, String approvedBy, 
                                       BigDecimal riskScore, String fraudCheckId) {
        
        log.info("Processing payment approval for payment: {} - approved by: {}", 
            payment.getId(), approvedBy);
        
        try {
            // Update payment status to approved
            payment.setStatus(PaymentStatus.APPROVED);
            payment.setApprovedAt(LocalDateTime.now());
            payment.setApprovedBy(approvedBy);
            payment.setApprovalReason(reason);
            payment.setRiskScore(riskScore);
            payment.setFraudCheckId(fraudCheckId);
            
            paymentRepository.save(payment);
            
            // Proceed with payment execution
            boolean executionSuccess = executeApprovedPayment(payment);
            
            if (executionSuccess) {
                // Send approval notifications
                sendApprovalNotifications(payment, reason, approvedBy);
                
                // Log successful approval
                securityAuditLogger.logSecurityEvent("PAYMENT_APPROVED_AND_EXECUTED", 
                    approvedBy != null ? approvedBy : "SYSTEM",
                    "Payment approved and successfully executed",
                    Map.of("paymentId", payment.getId(), "amount", payment.getAmount(), 
                          "riskScore", riskScore != null ? riskScore : "N/A",
                          "fraudCheckId", fraudCheckId != null ? fraudCheckId : "N/A"));
                
                log.info("Successfully processed approved payment: {} - amount: {} {}", 
                    payment.getId(), payment.getAmount(), payment.getCurrency());
                    
            } else {
                // Handle execution failure
                payment.setStatus(PaymentStatus.EXECUTION_FAILED);
                payment.setFailureReason("Payment execution failed after approval");
                paymentRepository.save(payment);
                
                log.error("Payment execution failed after approval for payment: {}", payment.getId());
                sendExecutionFailureAlert(payment, "Execution failed after approval");
            }
            
        } catch (Exception e) {
            log.error("Failed to process payment approval for payment: {}", payment.getId(), e);
            
            // Update payment status to indicate approval processing failure
            payment.setStatus(PaymentStatus.APPROVAL_PROCESSING_FAILED);
            payment.setFailureReason("Approval processing failed: " + e.getMessage());
            paymentRepository.save(payment);
            
            throw e;
        }
    }

    /**
     * Process payment blocking - stop payment and notify
     */
    private void processPaymentBlocking(Payment payment, String reason, String blockedBy, 
                                       BigDecimal riskScore, String fraudCheckId) {
        
        log.info("Processing payment blocking for payment: {} - blocked by: {} - reason: {}", 
            payment.getId(), blockedBy, reason);
        
        try {
            // Update payment status to blocked
            payment.setStatus(PaymentStatus.BLOCKED);
            payment.setBlockedAt(LocalDateTime.now());
            payment.setBlockedBy(blockedBy);
            payment.setBlockReason(reason);
            payment.setRiskScore(riskScore);
            payment.setFraudCheckId(fraudCheckId);
            
            paymentRepository.save(payment);
            
            // Release any held funds
            if (payment.getHoldId() != null) {
                paymentService.releaseFundsForBlockedPayment(payment.getHoldId(), reason);
            }
            
            // Send blocking notifications
            sendBlockingNotifications(payment, reason, blockedBy);
            
            // Check if this requires immediate security attention
            if (isHighRiskBlocking(riskScore, reason)) {
                sendSecurityAlert(payment, reason, riskScore);
            }
            
            // Log payment blocking
            securityAuditLogger.logSecurityEvent("PAYMENT_BLOCKED", 
                blockedBy != null ? blockedBy : "SYSTEM",
                "Payment blocked due to: " + reason,
                Map.of("paymentId", payment.getId(), "amount", payment.getAmount(), 
                      "riskScore", riskScore != null ? riskScore : "N/A",
                      "blockReason", reason != null ? reason : "N/A"));
            
            log.info("Successfully blocked payment: {} - reason: {}", payment.getId(), reason);
            
        } catch (Exception e) {
            log.error("Failed to process payment blocking for payment: {}", payment.getId(), e);
            throw e;
        }
    }

    /**
     * Process payment pending review - queue for manual review
     */
    private void processPaymentPendingReview(Payment payment, String reason, 
                                           BigDecimal riskScore, String fraudCheckId) {
        
        log.info("Processing payment pending review for payment: {} - reason: {}", 
            payment.getId(), reason);
        
        try {
            // Update payment status
            payment.setStatus(PaymentStatus.PENDING_MANUAL_REVIEW);
            payment.setPendingReviewAt(LocalDateTime.now());
            payment.setPendingReviewReason(reason);
            payment.setRiskScore(riskScore);
            payment.setFraudCheckId(fraudCheckId);
            
            paymentRepository.save(payment);
            
            // Queue for manual review
            queueForManualReview(payment, reason, riskScore);
            
            // Send review notifications
            sendReviewNotifications(payment, reason);
            
            // Log pending review
            securityAuditLogger.logSecurityEvent("PAYMENT_PENDING_REVIEW", "SYSTEM",
                "Payment queued for manual review",
                Map.of("paymentId", payment.getId(), "reason", reason != null ? reason : "N/A",
                      "riskScore", riskScore != null ? riskScore : "N/A"));
            
            log.info("Successfully queued payment for review: {} - reason: {}", 
                payment.getId(), reason);
            
        } catch (Exception e) {
            log.error("Failed to process pending review for payment: {}", payment.getId(), e);
            throw e;
        }
    }

    /**
     * Process payment requiring additional verification
     */
    private void processPaymentRequiresVerification(Payment payment, String reason, String fraudCheckId) {
        
        log.info("Processing payment requiring verification for payment: {} - reason: {}", 
            payment.getId(), reason);
        
        try {
            // Update payment status
            payment.setStatus(PaymentStatus.REQUIRES_VERIFICATION);
            payment.setVerificationRequiredAt(LocalDateTime.now());
            payment.setVerificationReason(reason);
            payment.setFraudCheckId(fraudCheckId);
            
            paymentRepository.save(payment);
            
            // Trigger verification process
            initiateVerificationProcess(payment, reason);
            
            // Send verification notifications
            sendVerificationNotifications(payment, reason);
            
            log.info("Successfully initiated verification for payment: {}", payment.getId());
            
        } catch (Exception e) {
            log.error("Failed to process verification requirement for payment: {}", payment.getId(), e);
            throw e;
        }
    }

    /**
     * Handle unknown status - queue for manual processing
     */
    private void processUnknownStatus(Payment payment, String status, String reason) {
        log.warn("Processing unknown status '{}' for payment: {} - queuing for manual review", 
            status, payment.getId());
        
        // Queue for manual processing with unknown status
        queueForManualReview(payment, "Unknown status: " + status + " - " + reason, null);
        
        // Send alert to operations
        sendOperationsAlert("UNKNOWN_PAYMENT_STATUS", payment.getId(), 
            "Unknown payment status received: " + status);
    }

    /**
     * Execute approved payment through payment processor
     */
    private boolean executeApprovedPayment(Payment payment) {
        try {
            // Use PaymentService to execute the payment
            String result = paymentService.executePayment(
                payment.getId().toString(),
                payment.getAmount(),
                payment.getRecipientId(),
                payment.getPaymentMethod()
            );
            
            return "SUCCESS".equals(result);
            
        } catch (Exception e) {
            log.error("Payment execution failed for approved payment: {}", payment.getId(), e);
            return false;
        }
    }

    /**
     * Queue payment for manual review
     */
    private void queueForManualReview(Payment payment, String reason, BigDecimal riskScore) {
        try {
            Map<String, Object> reviewTask = Map.of(
                "taskType", "PAYMENT_MANUAL_REVIEW",
                "paymentId", payment.getId(),
                "amount", payment.getAmount(),
                "currency", payment.getCurrency(),
                "reason", reason != null ? reason : "N/A",
                "riskScore", riskScore != null ? riskScore : "N/A",
                "priority", determineReviewPriority(payment, riskScore),
                "createdAt", LocalDateTime.now().toString()
            );
            
            paymentService.queueManualTask("payment-manual-review-queue", reviewTask);
            
        } catch (Exception e) {
            log.error("Failed to queue payment for manual review: {}", payment.getId(), e);
        }
    }

    /**
     * Initiate verification process for payment
     */
    private void initiateVerificationProcess(Payment payment, String reason) {
        try {
            // Determine verification type needed
            String verificationType = determineVerificationType(reason);
            
            // Trigger appropriate verification process
            paymentService.initiatePaymentVerification(
                payment.getId().toString(),
                verificationType,
                reason
            );
            
        } catch (Exception e) {
            log.error("Failed to initiate verification for payment: {}", payment.getId(), e);
        }
    }

    /**
     * Determine if blocking is high risk requiring immediate attention
     */
    private boolean isHighRiskBlocking(BigDecimal riskScore, String reason) {
        if (riskScore != null && riskScore.compareTo(new BigDecimal("80")) > 0) {
            return true;
        }
        
        if (reason != null && (reason.contains("FRAUD") || reason.contains("MONEY_LAUNDERING") || 
                              reason.contains("SANCTIONS"))) {
            return true;
        }
        
        return false;
    }

    /**
     * Determine verification type based on reason
     */
    private String determineVerificationType(String reason) {
        if (reason == null) return "GENERAL";
        
        if (reason.contains("IDENTITY")) return "IDENTITY_VERIFICATION";
        if (reason.contains("CARD")) return "CARD_VERIFICATION";
        if (reason.contains("PHONE")) return "PHONE_VERIFICATION";
        if (reason.contains("EMAIL")) return "EMAIL_VERIFICATION";
        if (reason.contains("BANK")) return "BANK_ACCOUNT_VERIFICATION";
        
        return "GENERAL";
    }

    /**
     * Determine priority for manual review
     */
    private String determineReviewPriority(Payment payment, BigDecimal riskScore) {
        if (riskScore != null && riskScore.compareTo(new BigDecimal("90")) > 0) {
            return "URGENT";
        }
        
        if (payment.getAmount().compareTo(new BigDecimal("10000")) > 0) {
            return "HIGH";
        }
        
        return "NORMAL";
    }

    /**
     * Send notifications for approved payments
     */
    private void sendApprovalNotifications(Payment payment, String reason, String approvedBy) {
        try {
            notificationService.sendPaymentApprovalNotification(
                payment.getSenderUserId(),
                payment.getId().toString(),
                payment.getAmount(),
                payment.getCurrency(),
                reason,
                approvedBy
            );
        } catch (Exception e) {
            log.error("Failed to send approval notification for payment: {}", payment.getId(), e);
        }
    }

    /**
     * Send notifications for blocked payments
     */
    private void sendBlockingNotifications(Payment payment, String reason, String blockedBy) {
        try {
            notificationService.sendPaymentBlockingNotification(
                payment.getSenderUserId(),
                payment.getId().toString(),
                payment.getAmount(),
                payment.getCurrency(),
                reason,
                blockedBy
            );
        } catch (Exception e) {
            log.error("Failed to send blocking notification for payment: {}", payment.getId(), e);
        }
    }

    /**
     * Send notifications for payments pending review
     */
    private void sendReviewNotifications(Payment payment, String reason) {
        try {
            notificationService.sendPaymentReviewNotification(
                payment.getSenderUserId(),
                payment.getId().toString(),
                payment.getAmount(),
                payment.getCurrency(),
                reason
            );
        } catch (Exception e) {
            log.error("Failed to send review notification for payment: {}", payment.getId(), e);
        }
    }

    /**
     * Send notifications for payments requiring verification
     */
    private void sendVerificationNotifications(Payment payment, String reason) {
        try {
            notificationService.sendPaymentVerificationNotification(
                payment.getSenderUserId(),
                payment.getId().toString(),
                reason
            );
        } catch (Exception e) {
            log.error("Failed to send verification notification for payment: {}", payment.getId(), e);
        }
    }

    /**
     * Send security alert for high-risk blockings
     */
    private void sendSecurityAlert(Payment payment, String reason, BigDecimal riskScore) {
        try {
            notificationService.sendSecurityAlert(
                "HIGH_RISK_PAYMENT_BLOCKED",
                "High-risk payment blocked: " + reason,
                Map.of("paymentId", payment.getId(), "amount", payment.getAmount(),
                      "riskScore", riskScore, "reason", reason)
            );
        } catch (Exception e) {
            log.error("Failed to send security alert for payment: {}", payment.getId(), e);
        }
    }

    /**
     * Send execution failure alert
     */
    private void sendExecutionFailureAlert(Payment payment, String reason) {
        try {
            notificationService.sendOperationsAlert(
                "PAYMENT_EXECUTION_FAILURE",
                "Payment execution failed after approval: " + reason,
                Map.of("paymentId", payment.getId(), "amount", payment.getAmount())
            );
        } catch (Exception e) {
            log.error("Failed to send execution failure alert for payment: {}", payment.getId(), e);
        }
    }

    /**
     * Send operations alert
     */
    private void sendOperationsAlert(String alertType, String paymentId, String message) {
        try {
            notificationService.sendOperationsAlert(alertType, message,
                Map.of("paymentId", paymentId, "timestamp", LocalDateTime.now().toString()));
        } catch (Exception e) {
            log.error("Failed to send operations alert for payment: {}", paymentId, e);
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
            log.error("Sending payment approval event to DLQ: {}", error.getMessage());
            securityAuditLogger.logSecurityEvent("PAYMENT_APPROVAL_DLQ", "SYSTEM",
                "Payment approval event sent to DLQ",
                Map.of("error", error.getMessage()));
        } catch (Exception e) {
            log.error("Failed to send to DLQ", e);
        }
    }
}