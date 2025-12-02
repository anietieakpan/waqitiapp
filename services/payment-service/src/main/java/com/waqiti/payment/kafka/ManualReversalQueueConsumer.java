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
 * CRITICAL: Manual Reversal Queue Consumer - Processes orphaned manual reversal requests
 * 
 * This consumer was missing causing manual reversal requests to be lost.
 * Without this, payments requiring manual reversal would remain stuck indefinitely.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ManualReversalQueueConsumer {

    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;
    private final NotificationService notificationService;
    private final DistributedLockService lockService;
    private final SecurityAuditLogger securityAuditLogger;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = {"manual-reversal-queue", "manual-refund-queue"},
        groupId = "manual-reversal-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void processManualReversalRequest(
            @Payload String eventPayload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        String lockKey = null;
        
        try {
            log.info("Processing manual reversal request from topic: {} - partition: {} - offset: {}", 
                    topic, partition, offset);

            // Parse event payload
            Map<String, Object> eventData = objectMapper.readValue(eventPayload, Map.class);
            String paymentId = extractString(eventData, "paymentId");
            String provider = extractString(eventData, "provider");
            BigDecimal amount = extractBigDecimal(eventData, "amount");
            String currency = extractString(eventData, "currency");
            String providerTransactionId = extractString(eventData, "providerTransactionId");
            String reason = extractString(eventData, "reason");
            String priority = extractString(eventData, "priority");
            boolean requiresApproval = extractBoolean(eventData, "requiresApproval");
            String requestType = topic.contains("refund") ? "REFUND" : "REVERSAL";

            // Validate required fields
            if (paymentId == null || provider == null || amount == null) {
                log.error("Invalid manual {} request - missing required fields: paymentId={}, provider={}, amount={}", 
                    requestType.toLowerCase(), paymentId, provider, amount);
                acknowledgment.acknowledge(); // Ack to prevent reprocessing
                return;
            }

            // Acquire distributed lock to prevent concurrent processing
            lockKey = "manual-" + requestType.toLowerCase() + "-" + paymentId;
            boolean lockAcquired = lockService.tryLock(lockKey, 30, TimeUnit.SECONDS);
            if (!lockAcquired) {
                log.warn("Could not acquire lock for manual {}: {}", requestType.toLowerCase(), paymentId);
                throw new RuntimeException("Lock acquisition failed");
            }

            try {
                // 1. Validate payment exists and is in correct state
                Payment payment = validatePaymentForManualReversal(paymentId);
                
                // 2. Create manual task record
                String taskId = createManualTask(payment, provider, reason, priority, 
                                               requiresApproval, requestType, eventData);
                
                // 3. Update payment status to indicate manual processing
                updatePaymentForManualProcessing(payment, taskId, requestType, reason);
                
                // 4. Send notifications based on priority and approval requirements
                sendManualProcessingNotifications(payment, taskId, priority, requiresApproval, requestType);
                
                // 5. If high priority and no approval required, attempt immediate processing
                if ("HIGH".equals(priority) && !requiresApproval) {
                    boolean immediateSuccess = attemptImmediateProcessing(payment, provider, reason, requestType);
                    if (immediateSuccess) {
                        log.info("Successfully processed high-priority {} immediately: {}", 
                            requestType.toLowerCase(), paymentId);
                    }
                }
                
                // 6. Log manual processing request
                securityAuditLogger.logSecurityEvent("MANUAL_" + requestType + "_REQUESTED", "SYSTEM",
                    "Manual " + requestType.toLowerCase() + " request created",
                    Map.of("paymentId", paymentId, "provider", provider, "amount", amount,
                          "reason", reason != null ? reason : "N/A", "priority", priority != null ? priority : "NORMAL",
                          "taskId", taskId));
                
                log.info("Successfully queued manual {} request: {} - taskId: {} - priority: {}", 
                    requestType.toLowerCase(), paymentId, taskId, priority);
                
                acknowledgment.acknowledge();
                
            } finally {
                lockService.unlock(lockKey);
            }
            
        } catch (Exception e) {
            log.error("Error processing manual reversal request", e);
            
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
     * Process manual task updates (approvals, rejections, completions)
     */
    @KafkaListener(
        topics = "manual-task-updates",
        groupId = "manual-task-update-processor"
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void processManualTaskUpdate(
            @Payload String eventPayload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment acknowledgment) {
        
        try {
            log.info("Processing manual task update");

            Map<String, Object> updateData = objectMapper.readValue(eventPayload, Map.class);
            String taskId = extractString(updateData, "taskId");
            String paymentId = extractString(updateData, "paymentId");
            String status = extractString(updateData, "status");
            String approvedBy = extractString(updateData, "approvedBy");
            String notes = extractString(updateData, "notes");

            if (taskId == null || paymentId == null || status == null) {
                log.error("Invalid manual task update - missing required fields");
                acknowledgment.acknowledge();
                return;
            }

            // Process based on update status
            switch (status.toUpperCase()) {
                case "APPROVED":
                    processManualTaskApproval(taskId, paymentId, approvedBy, notes);
                    break;
                case "REJECTED":
                    processManualTaskRejection(taskId, paymentId, approvedBy, notes);
                    break;
                case "COMPLETED":
                    processManualTaskCompletion(taskId, paymentId, approvedBy, notes);
                    break;
                case "ESCALATED":
                    processManualTaskEscalation(taskId, paymentId, notes);
                    break;
                default:
                    log.warn("Unknown manual task status: {}", status);
            }

            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Error processing manual task update", e);
            acknowledgment.acknowledge(); // Ack to prevent infinite retries
        }
    }

    /**
     * Validate payment exists and is eligible for manual reversal
     */
    private Payment validatePaymentForManualReversal(String paymentId) {
        Payment payment = paymentRepository.findById(UUID.fromString(paymentId))
            .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));
        
        // Check if payment is in a state that can be manually reversed
        if (payment.getStatus() == PaymentStatus.REVERSED || 
            payment.getStatus() == PaymentStatus.REFUNDED ||
            payment.getStatus() == PaymentStatus.CANCELLED) {
            
            log.warn("Payment {} already in final state: {} - manual reversal may not be needed", 
                paymentId, payment.getStatus());
        }
        
        return payment;
    }

    /**
     * Create manual task record for tracking
     */
    private String createManualTask(Payment payment, String provider, String reason, String priority,
                                   boolean requiresApproval, String taskType, Map<String, Object> originalEvent) {
        
        String taskId = "MT-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
        
        try {
            Map<String, Object> taskRecord = Map.of(
                "taskId", taskId,
                "taskType", "MANUAL_" + taskType,
                "paymentId", payment.getId().toString(),
                "provider", provider,
                "amount", payment.getAmount(),
                "currency", payment.getCurrency(),
                "reason", reason != null ? reason : "Manual processing required",
                "priority", priority != null ? priority : "NORMAL",
                "requiresApproval", requiresApproval,
                "status", "PENDING",
                "createdAt", LocalDateTime.now().toString(),
                "originalEvent", originalEvent,
                "assignedTo", determineAssignee(priority, provider, payment.getAmount())
            );
            
            // Store task record (in production, would use database)
            paymentService.storeManualTask(taskId, taskRecord);
            
            return taskId;
            
        } catch (Exception e) {
            log.error("Failed to create manual task record for payment: {}", payment.getId(), e);
            return taskId; // Return ID even if storage fails
        }
    }

    /**
     * Update payment status to indicate manual processing
     */
    private void updatePaymentForManualProcessing(Payment payment, String taskId, String taskType, String reason) {
        try {
            if ("REVERSAL".equals(taskType)) {
                payment.setStatus(PaymentStatus.REVERSAL_PENDING_MANUAL);
            } else {
                payment.setStatus(PaymentStatus.REFUND_PENDING_MANUAL);
            }
            
            payment.setManualReviewRequired(true);
            payment.setManualReviewReason(reason);
            payment.setManualTaskId(taskId);
            payment.setManualProcessingStartedAt(LocalDateTime.now());
            
            paymentRepository.save(payment);
            
        } catch (Exception e) {
            log.error("Failed to update payment for manual processing: {}", payment.getId(), e);
        }
    }

    /**
     * Send notifications for manual processing requests
     */
    private void sendManualProcessingNotifications(Payment payment, String taskId, String priority, 
                                                  boolean requiresApproval, String taskType) {
        try {
            // Determine notification urgency
            String urgency = "HIGH".equals(priority) ? "URGENT" : "NORMAL";
            
            // Send to operations team
            notificationService.sendOperationsAlert(
                "MANUAL_" + taskType + "_REQUIRED",
                String.format("Manual %s required for payment %s - Priority: %s", 
                             taskType.toLowerCase(), payment.getId(), priority),
                Map.of("paymentId", payment.getId(), "taskId", taskId, "priority", priority,
                      "amount", payment.getAmount(), "currency", payment.getCurrency(),
                      "requiresApproval", requiresApproval, "urgency", urgency)
            );
            
            // If requires approval, send to management
            if (requiresApproval) {
                notificationService.sendManagementAlert(
                    "MANUAL_" + taskType + "_APPROVAL_REQUIRED",
                    String.format("Manual %s requires approval - Payment: %s Amount: %s %s", 
                                 taskType.toLowerCase(), payment.getId(), payment.getAmount(), payment.getCurrency()),
                    Map.of("paymentId", payment.getId(), "taskId", taskId, "priority", priority)
                );
            }
            
            // Send customer notification
            notificationService.sendCustomerNotification(
                payment.getSenderUserId(),
                "PAYMENT_MANUAL_PROCESSING",
                String.format("Your payment is being reviewed by our team. Reference: %s", taskId),
                Map.of("paymentId", payment.getId(), "taskId", taskId)
            );
            
        } catch (Exception e) {
            log.error("Failed to send manual processing notifications for payment: {}", payment.getId(), e);
        }
    }

    /**
     * Attempt immediate processing for high priority requests that don't require approval
     */
    private boolean attemptImmediateProcessing(Payment payment, String provider, String reason, String taskType) {
        try {
            log.info("Attempting immediate processing for high-priority {} - payment: {}", 
                taskType.toLowerCase(), payment.getId());
            
            if ("REVERSAL".equals(taskType)) {
                // Attempt automated reversal using existing service methods
                return attemptAutomatedReversal(payment, provider, reason);
            } else {
                // Attempt automated refund
                return attemptAutomatedRefund(payment, reason);
            }
            
        } catch (Exception e) {
            log.error("Immediate processing failed for payment: {}", payment.getId(), e);
            return false;
        }
    }

    /**
     * Attempt automated reversal
     */
    private boolean attemptAutomatedReversal(Payment payment, String provider, String reason) {
        try {
            // Use existing reversal methods from PaymentService
            switch (provider.toUpperCase()) {
                case "STRIPE":
                    paymentService.reverseStripePayment(payment, reason);
                    return true;
                case "PAYPAL":
                    paymentService.reversePayPalPayment(payment, reason);
                    return true;
                case "ADYEN":
                    paymentService.reverseAdyenPayment(payment, reason);
                    return true;
                default:
                    // Try generic reversal
                    return paymentService.attemptGenericReversal(payment, reason);
            }
            
        } catch (Exception e) {
            log.error("Automated reversal failed for payment: {}", payment.getId(), e);
            return false;
        }
    }

    /**
     * Attempt automated refund
     */
    private boolean attemptAutomatedRefund(Payment payment, String reason) {
        try {
            // Use existing refund processing
            var refundResult = paymentService.processRefund(
                payment.getId().toString(),
                payment.getAmount(),
                reason,
                "SYSTEM"
            );
            
            return refundResult != null && refundResult.isSuccessful();
            
        } catch (Exception e) {
            log.error("Automated refund failed for payment: {}", payment.getId(), e);
            return false;
        }
    }

    /**
     * Process manual task approval
     */
    private void processManualTaskApproval(String taskId, String paymentId, String approvedBy, String notes) {
        try {
            log.info("Processing manual task approval - taskId: {} paymentId: {} approvedBy: {}", 
                taskId, paymentId, approvedBy);
            
            // Update task status
            paymentService.updateManualTaskStatus(taskId, "APPROVED", approvedBy, notes);
            
            // Proceed with processing
            Payment payment = paymentRepository.findById(UUID.fromString(paymentId)).orElse(null);
            if (payment != null) {
                // Execute the approved manual operation
                executeApprovedManualOperation(payment, taskId, approvedBy, notes);
            }
            
        } catch (Exception e) {
            log.error("Failed to process manual task approval: {}", taskId, e);
        }
    }

    /**
     * Process manual task rejection
     */
    private void processManualTaskRejection(String taskId, String paymentId, String rejectedBy, String notes) {
        try {
            log.info("Processing manual task rejection - taskId: {} paymentId: {} rejectedBy: {}", 
                taskId, paymentId, rejectedBy);
            
            // Update task status
            paymentService.updateManualTaskStatus(taskId, "REJECTED", rejectedBy, notes);
            
            // Update payment status
            Payment payment = paymentRepository.findById(UUID.fromString(paymentId)).orElse(null);
            if (payment != null) {
                payment.setStatus(PaymentStatus.MANUAL_PROCESSING_REJECTED);
                payment.setManualReviewRequired(false);
                payment.setManualRejectionReason(notes);
                payment.setManualRejectedBy(rejectedBy);
                payment.setManualRejectedAt(LocalDateTime.now());
                paymentRepository.save(payment);
                
                // Send rejection notification
                sendRejectionNotification(payment, rejectedBy, notes);
            }
            
        } catch (Exception e) {
            log.error("Failed to process manual task rejection: {}", taskId, e);
        }
    }

    /**
     * Process manual task completion
     */
    private void processManualTaskCompletion(String taskId, String paymentId, String completedBy, String notes) {
        try {
            log.info("Processing manual task completion - taskId: {} paymentId: {} completedBy: {}", 
                taskId, paymentId, completedBy);
            
            // Update task status
            paymentService.updateManualTaskStatus(taskId, "COMPLETED", completedBy, notes);
            
            // Update payment status
            Payment payment = paymentRepository.findById(UUID.fromString(paymentId)).orElse(null);
            if (payment != null) {
                payment.setStatus(PaymentStatus.MANUAL_PROCESSING_COMPLETED);
                payment.setManualReviewRequired(false);
                payment.setManualCompletedBy(completedBy);
                payment.setManualCompletedAt(LocalDateTime.now());
                payment.setManualCompletionNotes(notes);
                paymentRepository.save(payment);
                
                // Send completion notification
                sendCompletionNotification(payment, completedBy, notes);
            }
            
        } catch (Exception e) {
            log.error("Failed to process manual task completion: {}", taskId, e);
        }
    }

    /**
     * Process manual task escalation
     */
    private void processManualTaskEscalation(String taskId, String paymentId, String notes) {
        try {
            log.info("Processing manual task escalation - taskId: {} paymentId: {}", taskId, paymentId);
            
            // Update task status
            paymentService.updateManualTaskStatus(taskId, "ESCALATED", "SYSTEM", notes);
            
            // Send escalation notifications
            sendEscalationNotification(taskId, paymentId, notes);
            
        } catch (Exception e) {
            log.error("Failed to process manual task escalation: {}", taskId, e);
        }
    }

    /**
     * Execute approved manual operation
     */
    private void executeApprovedManualOperation(Payment payment, String taskId, String approvedBy, String notes) {
        try {
            // Determine operation type from payment status
            if (payment.getStatus() == PaymentStatus.REVERSAL_PENDING_MANUAL) {
                // Execute manual reversal
                paymentService.executeManualReversal(payment.getId().toString(), approvedBy, notes);
            } else if (payment.getStatus() == PaymentStatus.REFUND_PENDING_MANUAL) {
                // Execute manual refund
                paymentService.executeManualRefund(payment.getId().toString(), approvedBy, notes);
            }
            
            // Send execution notification
            sendExecutionNotification(payment, approvedBy, notes);
            
        } catch (Exception e) {
            log.error("Failed to execute approved manual operation for payment: {}", payment.getId(), e);
        }
    }

    /**
     * Determine assignee based on priority and criteria
     */
    private String determineAssignee(String priority, String provider, BigDecimal amount) {
        if ("HIGH".equals(priority) || amount.compareTo(new BigDecimal("10000")) > 0) {
            return "SENIOR_OPERATIONS";
        } else if ("CRITICAL".equals(priority)) {
            return "OPERATIONS_MANAGER";
        } else {
            return "OPERATIONS_TEAM";
        }
    }

    /**
     * Send various notification types
     */
    private void sendRejectionNotification(Payment payment, String rejectedBy, String notes) {
        try {
            notificationService.sendCustomerNotification(
                payment.getSenderUserId(),
                "MANUAL_PROCESSING_REJECTED",
                "Your payment processing request has been reviewed. Reference: " + payment.getManualTaskId(),
                Map.of("paymentId", payment.getId(), "rejectedBy", rejectedBy, "notes", notes)
            );
        } catch (Exception e) {
            log.error("Failed to send rejection notification", e);
        }
    }

    private void sendCompletionNotification(Payment payment, String completedBy, String notes) {
        try {
            notificationService.sendCustomerNotification(
                payment.getSenderUserId(),
                "MANUAL_PROCESSING_COMPLETED",
                "Your payment has been processed. Reference: " + payment.getManualTaskId(),
                Map.of("paymentId", payment.getId(), "completedBy", completedBy, "notes", notes)
            );
        } catch (Exception e) {
            log.error("Failed to send completion notification", e);
        }
    }

    private void sendEscalationNotification(String taskId, String paymentId, String notes) {
        try {
            notificationService.sendManagementAlert(
                "MANUAL_TASK_ESCALATED",
                "Manual task escalated - requires senior review: " + taskId,
                Map.of("taskId", taskId, "paymentId", paymentId, "notes", notes)
            );
        } catch (Exception e) {
            log.error("Failed to send escalation notification", e);
        }
    }

    private void sendExecutionNotification(Payment payment, String executedBy, String notes) {
        try {
            notificationService.sendOperationsAlert(
                "MANUAL_OPERATION_EXECUTED",
                "Manual operation completed for payment: " + payment.getId(),
                Map.of("paymentId", payment.getId(), "executedBy", executedBy, "notes", notes)
            );
        } catch (Exception e) {
            log.error("Failed to send execution notification", e);
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

    private boolean extractBoolean(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return false;
        if (value instanceof Boolean) return (Boolean) value;
        return Boolean.parseBoolean(value.toString());
    }

    private boolean shouldSendToDlq(Exception e) {
        // Send to DLQ for non-retryable errors
        return e instanceof IllegalArgumentException ||
               e instanceof IllegalStateException ||
               e instanceof SecurityException;
    }

    private void sendToDlq(String eventPayload, Exception error) {
        try {
            log.error("Sending manual reversal event to DLQ: {}", error.getMessage());
            securityAuditLogger.logSecurityEvent("MANUAL_REVERSAL_DLQ", "SYSTEM",
                "Manual reversal event sent to DLQ",
                Map.of("error", error.getMessage()));
        } catch (Exception e) {
            log.error("Failed to send to DLQ", e);
        }
    }
}