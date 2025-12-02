package com.waqiti.payment.kafka;

import com.waqiti.common.kafka.GenericKafkaEvent;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.payment.model.*;
import com.waqiti.payment.repository.PaymentTrackingRepository;
import com.waqiti.payment.service.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Production-grade Kafka consumer for payment tracking events
 * Handles real-time payment status updates, webhook notifications, and state transitions
 * 
 * Critical for: Payment visibility, customer experience, merchant operations
 * SLA: Must update payment status within 5 seconds of status change
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentTrackingConsumer {

    private final PaymentTrackingRepository trackingRepository;
    private final PaymentService paymentService;
    private final NotificationService notificationService;
    private final WebhookService webhookService;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final UniversalDLQHandler dlqHandler;

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long SLA_THRESHOLD_MS = 5000; // 5 seconds
    private static final Set<String> TERMINAL_STATUSES = Set.of(
        "COMPLETED", "FAILED", "CANCELLED", "EXPIRED", "REJECTED"
    );
    
    @KafkaListener(
        topics = {"payment-tracking"},
        groupId = "payment-tracking-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    @CircuitBreaker(name = "payment-tracking-processor", fallbackMethod = "handlePaymentTrackingFailure")
    @Retry(name = "payment-tracking-processor")
    public void processPaymentTrackingEvent(
            @Payload @Valid GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String eventId = event.getEventId();
        log.info("Processing payment tracking event: {} from topic: {} partition: {} offset: {}", 
                eventId, topic, partition, offset);

        long startTime = System.currentTimeMillis();
        
        try {
            Map<String, Object> payload = event.getPayload();
            PaymentStatusUpdate statusUpdate = extractStatusUpdate(payload);
            
            // Validate status update
            validateStatusUpdate(statusUpdate);
            
            // Check for duplicate update
            if (isDuplicateUpdate(statusUpdate)) {
                log.warn("Duplicate status update detected for payment: {}, skipping", 
                        statusUpdate.getPaymentId());
                acknowledgment.acknowledge();
                return;
            }
            
            // Get current payment state
            PaymentTracking currentTracking = getCurrentPaymentTracking(statusUpdate.getPaymentId());
            
            // Validate state transition
            validateStateTransition(currentTracking, statusUpdate);
            
            // Update payment tracking
            PaymentTracking updatedTracking = updatePaymentTracking(currentTracking, statusUpdate);
            
            // Handle status-specific logic
            handleStatusSpecificLogic(statusUpdate, updatedTracking);
            
            // Send real-time notifications
            sendRealTimeNotifications(statusUpdate, updatedTracking);
            
            // Trigger webhooks
            triggerWebhooks(statusUpdate, updatedTracking);
            
            // Update external systems
            updateExternalSystems(statusUpdate, updatedTracking);
            
            // Handle terminal status cleanup
            if (isTerminalStatus(statusUpdate.getNewStatus())) {
                handleTerminalStatus(statusUpdate, updatedTracking);
            }
            
            // Audit the status change
            auditStatusUpdate(statusUpdate, updatedTracking, event);
            
            // Record metrics
            recordMetrics(statusUpdate, startTime);
            
            // Acknowledge message
            acknowledgment.acknowledge();
            
            log.info("Successfully processed payment tracking for: {} status: {} in {}ms", 
                    statusUpdate.getPaymentId(), statusUpdate.getNewStatus(),
                    System.currentTimeMillis() - startTime);
            
        } catch (ValidationException e) {
            log.error("Validation failed for payment tracking event: {}", eventId, e);
            handleValidationError(event, e);
            acknowledgment.acknowledge();
            
        } catch (StateTransitionException e) {
            log.error("Invalid state transition for payment tracking event: {}", eventId, e);
            handleStateTransitionError(event, e);
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process payment tracking event: {}", eventId, e);
            handleProcessingError(event, e, acknowledgment);
        }
    }

    private PaymentStatusUpdate extractStatusUpdate(Map<String, Object> payload) {
        return PaymentStatusUpdate.builder()
            .updateId(extractString(payload, "updateId", UUID.randomUUID().toString()))
            .paymentId(extractString(payload, "paymentId", null))
            .transactionId(extractString(payload, "transactionId", null))
            .orderId(extractString(payload, "orderId", null))
            .previousStatus(extractString(payload, "previousStatus", null))
            .newStatus(extractString(payload, "newStatus", null))
            .statusReason(extractString(payload, "statusReason", null))
            .errorCode(extractString(payload, "errorCode", null))
            .errorMessage(extractString(payload, "errorMessage", null))
            .processingTime(extractLong(payload, "processingTime", 0L))
            .gatewayResponse(extractString(payload, "gatewayResponse", null))
            .externalTransactionId(extractString(payload, "externalTransactionId", null))
            .estimatedSettlement(extractInstant(payload, "estimatedSettlement"))
            .fees(extractBigDecimal(payload, "fees"))
            .exchangeRate(extractBigDecimal(payload, "exchangeRate"))
            .finalAmount(extractBigDecimal(payload, "finalAmount"))
            .metadata(extractMap(payload, "metadata"))
            .source(extractString(payload, "source", "SYSTEM"))
            .priority(extractString(payload, "priority", "NORMAL"))
            .timestamp(Instant.now())
            .build();
    }

    private void validateStatusUpdate(PaymentStatusUpdate update) {
        if (update.getPaymentId() == null || update.getPaymentId().isEmpty()) {
            throw new ValidationException("Payment ID is required for status update");
        }
        
        if (update.getNewStatus() == null || update.getNewStatus().isEmpty()) {
            throw new ValidationException("New status is required for update");
        }
        
        if (!isValidStatus(update.getNewStatus())) {
            throw new ValidationException("Invalid payment status: " + update.getNewStatus());
        }
        
        // Check if payment exists
        if (!paymentService.paymentExists(update.getPaymentId())) {
            throw new ValidationException("Payment not found: " + update.getPaymentId());
        }
        
        // Validate error information consistency
        if (update.getNewStatus().equals("FAILED")) {
            if (update.getErrorCode() == null && update.getErrorMessage() == null) {
                log.warn("Failed payment status without error details for payment: {}", 
                        update.getPaymentId());
            }
        }
    }

    private boolean isValidStatus(String status) {
        return Arrays.asList(
            "PENDING", "PROCESSING", "AUTHORIZED", "CAPTURED", "COMPLETED",
            "FAILED", "CANCELLED", "EXPIRED", "REJECTED", "REFUNDED",
            "PARTIALLY_REFUNDED", "DISPUTED", "CHARGEBACK"
        ).contains(status);
    }

    private boolean isDuplicateUpdate(PaymentStatusUpdate update) {
        return trackingRepository.existsByPaymentIdAndUpdateIdAndTimestampAfter(
            update.getPaymentId(),
            update.getUpdateId(),
            Instant.now().minus(1, ChronoUnit.MINUTES)
        );
    }

    private PaymentTracking getCurrentPaymentTracking(String paymentId) {
        return trackingRepository.findByPaymentIdOrderByTimestampDesc(paymentId)
            .stream()
            .findFirst()
            .orElse(PaymentTracking.builder()
                .paymentId(paymentId)
                .currentStatus("CREATED")
                .statusHistory(new ArrayList<>())
                .build());
    }

    private void validateStateTransition(PaymentTracking currentTracking, PaymentStatusUpdate update) {
        String currentStatus = currentTracking.getCurrentStatus();
        String newStatus = update.getNewStatus();
        
        // Check if current status allows transition
        if (isTerminalStatus(currentStatus) && !newStatus.equals("REFUNDED") && 
            !newStatus.equals("PARTIALLY_REFUNDED") && !newStatus.equals("DISPUTED")) {
            throw new StateTransitionException(
                String.format("Cannot transition from terminal status %s to %s", currentStatus, newStatus));
        }
        
        // Validate specific transition rules
        if (!isValidTransition(currentStatus, newStatus)) {
            throw new StateTransitionException(
                String.format("Invalid state transition from %s to %s for payment %s", 
                    currentStatus, newStatus, update.getPaymentId()));
        }
        
        // Check for backwards transition (except for refunds and disputes)
        if (isBackwardsTransition(currentStatus, newStatus) && 
            !isAllowedBackwardsTransition(newStatus)) {
            throw new StateTransitionException(
                String.format("Backwards transition not allowed from %s to %s", currentStatus, newStatus));
        }
    }

    private boolean isValidTransition(String fromStatus, String toStatus) {
        Map<String, Set<String>> validTransitions = Map.of(
            "CREATED", Set.of("PENDING", "FAILED", "CANCELLED"),
            "PENDING", Set.of("PROCESSING", "FAILED", "CANCELLED", "EXPIRED"),
            "PROCESSING", Set.of("AUTHORIZED", "FAILED", "CANCELLED"),
            "AUTHORIZED", Set.of("CAPTURED", "FAILED", "CANCELLED", "EXPIRED"),
            "CAPTURED", Set.of("COMPLETED", "FAILED"),
            "COMPLETED", Set.of("REFUNDED", "PARTIALLY_REFUNDED", "DISPUTED", "CHARGEBACK"),
            "FAILED", Set.of("PENDING"), // Allow retry
            "CANCELLED", Set.of(),
            "EXPIRED", Set.of(),
            "REJECTED", Set.of(),
            "REFUNDED", Set.of("DISPUTED"),
            "PARTIALLY_REFUNDED", Set.of("REFUNDED", "DISPUTED"),
            "DISPUTED", Set.of("COMPLETED", "REFUNDED", "CHARGEBACK"),
            "CHARGEBACK", Set.of()
        );
        
        return validTransitions.getOrDefault(fromStatus, Set.of()).contains(toStatus);
    }

    private boolean isBackwardsTransition(String fromStatus, String toStatus) {
        List<String> statusOrder = Arrays.asList(
            "CREATED", "PENDING", "PROCESSING", "AUTHORIZED", "CAPTURED", "COMPLETED"
        );
        
        int fromIndex = statusOrder.indexOf(fromStatus);
        int toIndex = statusOrder.indexOf(toStatus);
        
        return fromIndex != -1 && toIndex != -1 && toIndex < fromIndex;
    }

    private boolean isAllowedBackwardsTransition(String toStatus) {
        return Arrays.asList("REFUNDED", "PARTIALLY_REFUNDED", "DISPUTED", "CHARGEBACK")
            .contains(toStatus);
    }

    private PaymentTracking updatePaymentTracking(PaymentTracking currentTracking, 
                                                  PaymentStatusUpdate update) {
        // Create status history entry
        StatusHistoryEntry historyEntry = StatusHistoryEntry.builder()
            .previousStatus(currentTracking.getCurrentStatus())
            .newStatus(update.getNewStatus())
            .statusReason(update.getStatusReason())
            .errorCode(update.getErrorCode())
            .errorMessage(update.getErrorMessage())
            .processingTime(update.getProcessingTime())
            .source(update.getSource())
            .timestamp(update.getTimestamp())
            .metadata(update.getMetadata())
            .build();
        
        // Update tracking record
        currentTracking.setPreviousStatus(currentTracking.getCurrentStatus());
        currentTracking.setCurrentStatus(update.getNewStatus());
        currentTracking.setLastUpdated(update.getTimestamp());
        currentTracking.setTransitionCount(currentTracking.getTransitionCount() + 1);
        
        // Add to history
        if (currentTracking.getStatusHistory() == null) {
            currentTracking.setStatusHistory(new ArrayList<>());
        }
        currentTracking.getStatusHistory().add(historyEntry);
        
        // Update additional fields
        if (update.getExternalTransactionId() != null) {
            currentTracking.setExternalTransactionId(update.getExternalTransactionId());
        }
        if (update.getEstimatedSettlement() != null) {
            currentTracking.setEstimatedSettlement(update.getEstimatedSettlement());
        }
        if (update.getFees() != null) {
            currentTracking.setFees(update.getFees());
        }
        if (update.getFinalAmount() != null) {
            currentTracking.setFinalAmount(update.getFinalAmount());
        }
        
        // Update processing metrics
        updateProcessingMetrics(currentTracking, update);
        
        // Save updated tracking
        return trackingRepository.save(currentTracking);
    }

    private void updateProcessingMetrics(PaymentTracking tracking, PaymentStatusUpdate update) {
        if (tracking.getFirstStatusTime() == null) {
            tracking.setFirstStatusTime(update.getTimestamp());
        }
        
        // Calculate total processing time for completed payments
        if (isTerminalStatus(update.getNewStatus())) {
            long totalProcessingTime = ChronoUnit.MILLIS.between(
                tracking.getFirstStatusTime(),
                update.getTimestamp()
            );
            tracking.setTotalProcessingTime(totalProcessingTime);
        }
        
        // Track status-specific timings
        switch (update.getNewStatus()) {
            case "AUTHORIZED":
                tracking.setAuthorizationTime(update.getTimestamp());
                break;
            case "CAPTURED":
                tracking.setCaptureTime(update.getTimestamp());
                break;
            case "COMPLETED":
                tracking.setCompletionTime(update.getTimestamp());
                break;
            case "FAILED":
                tracking.setFailureTime(update.getTimestamp());
                tracking.setFailureReason(update.getErrorMessage());
                break;
        }
    }

    private void handleStatusSpecificLogic(PaymentStatusUpdate update, PaymentTracking tracking) {
        switch (update.getNewStatus()) {
            case "AUTHORIZED":
                handleAuthorizedStatus(update, tracking);
                break;
                
            case "COMPLETED":
                handleCompletedStatus(update, tracking);
                break;
                
            case "FAILED":
                handleFailedStatus(update, tracking);
                break;
                
            case "CANCELLED":
                handleCancelledStatus(update, tracking);
                break;
                
            case "EXPIRED":
                handleExpiredStatus(update, tracking);
                break;
                
            case "REFUNDED":
            case "PARTIALLY_REFUNDED":
                handleRefundedStatus(update, tracking);
                break;
                
            case "DISPUTED":
                handleDisputedStatus(update, tracking);
                break;
                
            case "CHARGEBACK":
                handleChargebackStatus(update, tracking);
                break;
        }
    }

    private void handleAuthorizedStatus(PaymentStatusUpdate update, PaymentTracking tracking) {
        // Schedule auto-capture if configured
        Payment payment = paymentService.getPayment(update.getPaymentId());
        if (payment.isAutoCaptureEnabled()) {
            long captureDelay = payment.getAutoCaptureDelayMinutes();
            scheduleAutoCapture(update.getPaymentId(), captureDelay);
        }
        
        // Update inventory holds if applicable
        if (payment.hasInventoryItems()) {
            inventoryService.confirmHolds(payment.getInventoryItems());
        }
    }

    private void handleCompletedStatus(PaymentStatusUpdate update, PaymentTracking tracking) {
        // Update account balances
        paymentService.updateAccountBalances(update.getPaymentId());
        
        // Release any holds
        paymentService.releasePaymentHolds(update.getPaymentId());
        
        // Update merchant settlement
        merchantService.updateSettlement(update.getPaymentId(), update.getFinalAmount());
        
        // Trigger loyalty points if applicable
        Payment payment = paymentService.getPayment(update.getPaymentId());
        if (payment.hasLoyaltyProgram()) {
            loyaltyService.awardPoints(payment.getCustomerId(), payment.getAmount());
        }
    }

    private void handleFailedStatus(PaymentStatusUpdate update, PaymentTracking tracking) {
        // Release any inventory holds
        Payment payment = paymentService.getPayment(update.getPaymentId());
        if (payment.hasInventoryItems()) {
            inventoryService.releaseHolds(payment.getInventoryItems());
        }
        
        // Update failure analytics
        analyticsService.recordPaymentFailure(
            update.getPaymentId(),
            update.getErrorCode(),
            update.getErrorMessage()
        );
        
        // Check if retry is appropriate
        if (shouldScheduleRetry(update)) {
            schedulePaymentRetry(update.getPaymentId());
        }
    }

    private void handleCancelledStatus(PaymentStatusUpdate update, PaymentTracking tracking) {
        // Release all holds and reserved funds
        paymentService.releaseAllHolds(update.getPaymentId());
        
        // Update cancellation metrics
        metricsService.recordCancellation(
            update.getPaymentId(),
            update.getStatusReason()
        );
    }

    private void handleExpiredStatus(PaymentStatusUpdate update, PaymentTracking tracking) {
        // Clean up expired authorizations
        paymentService.cleanupExpiredAuthorization(update.getPaymentId());
        
        // Update expiration analytics
        analyticsService.recordExpiration(update.getPaymentId());
    }

    private void handleRefundedStatus(PaymentStatusUpdate update, PaymentTracking tracking) {
        // Update refund tracking
        refundService.updateRefundStatus(update.getPaymentId(), update.getNewStatus());
        
        // Reverse loyalty points if applicable
        Payment payment = paymentService.getPayment(update.getPaymentId());
        if (payment.hasLoyaltyPoints()) {
            loyaltyService.reversePoints(payment.getCustomerId(), payment.getLoyaltyPoints());
        }
    }

    private void handleDisputedStatus(PaymentStatusUpdate update, PaymentTracking tracking) {
        // Create dispute case
        disputeService.createDisputeCase(
            update.getPaymentId(),
            update.getStatusReason()
        );
        
        // Apply merchant liability holds
        merchantService.applyDisputeHold(update.getPaymentId());
    }

    private void handleChargebackStatus(PaymentStatusUpdate update, PaymentTracking tracking) {
        // Process chargeback liability
        chargebackService.processLiability(
            update.getPaymentId(),
            update.getMetadata()
        );
        
        // Update merchant risk metrics
        riskService.updateChargebackRisk(
            paymentService.getMerchantId(update.getPaymentId())
        );
    }

    private void sendRealTimeNotifications(PaymentStatusUpdate update, PaymentTracking tracking) {
        Payment payment = paymentService.getPayment(update.getPaymentId());
        
        Map<String, Object> notificationData = Map.of(
            "paymentId", update.getPaymentId(),
            "orderId", payment.getOrderId(),
            "status", update.getNewStatus(),
            "amount", payment.getAmount(),
            "currency", payment.getCurrency(),
            "timestamp", update.getTimestamp()
        );
        
        // Customer notification for significant status changes
        if (shouldNotifyCustomer(update.getNewStatus())) {
            CompletableFuture.runAsync(() -> {
                notificationService.sendCustomerNotification(
                    payment.getCustomerId(),
                    "PAYMENT_STATUS_" + update.getNewStatus(),
                    notificationData
                );
            });
        }
        
        // Merchant notification
        CompletableFuture.runAsync(() -> {
            notificationService.sendMerchantNotification(
                payment.getMerchantId(),
                "PAYMENT_STATUS_" + update.getNewStatus(),
                notificationData
            );
        });
        
        // Real-time dashboard updates
        CompletableFuture.runAsync(() -> {
            realtimeService.broadcastPaymentUpdate(
                payment.getMerchantId(),
                notificationData
            );
        });
    }

    private boolean shouldNotifyCustomer(String status) {
        return Arrays.asList("COMPLETED", "FAILED", "CANCELLED", "REFUNDED").contains(status);
    }

    private void triggerWebhooks(PaymentStatusUpdate update, PaymentTracking tracking) {
        Payment payment = paymentService.getPayment(update.getPaymentId());
        
        if (!payment.hasWebhookUrl()) {
            return;
        }
        
        WebhookPayload payload = WebhookPayload.builder()
            .eventType("payment.status_changed")
            .paymentId(update.getPaymentId())
            .orderId(payment.getOrderId())
            .status(update.getNewStatus())
            .previousStatus(update.getPreviousStatus())
            .amount(payment.getAmount())
            .currency(payment.getCurrency())
            .timestamp(update.getTimestamp())
            .metadata(update.getMetadata())
            .build();
        
        // Send webhook asynchronously with retry logic
        CompletableFuture.runAsync(() -> {
            webhookService.sendWebhook(
                payment.getWebhookUrl(),
                payload,
                payment.getWebhookSecret()
            );
        });
    }

    private void updateExternalSystems(PaymentStatusUpdate update, PaymentTracking tracking) {
        Payment payment = paymentService.getPayment(update.getPaymentId());
        
        // Update external payment processors
        if (payment.hasExternalProcessorId()) {
            CompletableFuture.runAsync(() -> {
                externalProcessorService.updatePaymentStatus(
                    payment.getExternalProcessorId(),
                    update.getPaymentId(),
                    update.getNewStatus()
                );
            });
        }
        
        // Update accounting system
        if (isAccountingRelevantStatus(update.getNewStatus())) {
            CompletableFuture.runAsync(() -> {
                accountingService.updatePaymentRecord(
                    update.getPaymentId(),
                    update.getNewStatus(),
                    update.getFinalAmount()
                );
            });
        }
        
        // Update analytics and reporting
        CompletableFuture.runAsync(() -> {
            analyticsService.recordPaymentEvent(
                update.getPaymentId(),
                update.getNewStatus(),
                update.getTimestamp()
            );
        });
    }

    private boolean isAccountingRelevantStatus(String status) {
        return Arrays.asList("COMPLETED", "REFUNDED", "PARTIALLY_REFUNDED", "CHARGEBACK")
            .contains(status);
    }

    private void handleTerminalStatus(PaymentStatusUpdate update, PaymentTracking tracking) {
        // Mark tracking as complete
        tracking.setIsComplete(true);
        tracking.setCompletionTime(update.getTimestamp());
        trackingRepository.save(tracking);
        
        // Schedule cleanup of temporary data
        scheduleDataCleanup(update.getPaymentId());
        
        // Generate final reports if needed
        if (Arrays.asList("COMPLETED", "REFUNDED").contains(update.getNewStatus())) {
            reportService.generatePaymentReport(update.getPaymentId());
        }
    }

    private boolean isTerminalStatus(String status) {
        return TERMINAL_STATUSES.contains(status);
    }

    private void scheduleAutoCapture(String paymentId, long delayMinutes) {
        scheduledExecutor.schedule(() -> {
            try {
                paymentService.capturePayment(paymentId);
            } catch (Exception e) {
                log.error("Failed to auto-capture payment: {}", paymentId, e);
                alertingService.createAlert(
                    "AUTO_CAPTURE_FAILED",
                    "Failed to auto-capture payment: " + paymentId,
                    "MEDIUM"
                );
            }
        }, delayMinutes, TimeUnit.MINUTES);
    }

    private boolean shouldScheduleRetry(PaymentStatusUpdate update) {
        String errorCode = update.getErrorCode();
        
        // Retry for temporary failures
        return errorCode != null && (
            errorCode.startsWith("TEMP_") ||
            errorCode.equals("NETWORK_ERROR") ||
            errorCode.equals("GATEWAY_TIMEOUT") ||
            errorCode.equals("INSUFFICIENT_FUNDS_TEMP")
        );
    }

    private void schedulePaymentRetry(String paymentId) {
        // Retry after exponential backoff
        scheduledExecutor.schedule(() -> {
            try {
                paymentService.retryPayment(paymentId);
            } catch (Exception e) {
                log.error("Failed to retry payment: {}", paymentId, e);
            }
        }, 5, TimeUnit.MINUTES);
    }

    private void scheduleDataCleanup(String paymentId) {
        // Clean up after 30 days
        scheduledExecutor.schedule(() -> {
            cleanupService.cleanupPaymentData(paymentId);
        }, 30, TimeUnit.DAYS);
    }

    private void auditStatusUpdate(PaymentStatusUpdate update, PaymentTracking tracking, 
                                  GenericKafkaEvent event) {
        auditService.auditPaymentStatusChange(
            update.getPaymentId(),
            update.getPreviousStatus(),
            update.getNewStatus(),
            update.getStatusReason(),
            event.getEventId()
        );
    }

    private void recordMetrics(PaymentStatusUpdate update, long startTime) {
        long processingTime = System.currentTimeMillis() - startTime;
        
        // Record processing time metrics
        metricsService.recordPaymentTrackingMetrics(
            update.getNewStatus(),
            processingTime,
            processingTime <= SLA_THRESHOLD_MS
        );
        
        // Record status transition metrics
        metricsService.recordStatusTransition(
            update.getPreviousStatus(),
            update.getNewStatus()
        );
    }

    // Error handling methods
    private void handleValidationError(GenericKafkaEvent event, ValidationException e) {
        auditService.logValidationError(event.getEventId(), e.getMessage());
        kafkaTemplate.send("payment-tracking-validation-errors", event);
    }

    private void handleStateTransitionError(GenericKafkaEvent event, StateTransitionException e) {
        auditService.logStateTransitionError(event.getEventId(), e.getMessage());
        kafkaTemplate.send("payment-tracking-transition-errors", event);
        
        // Alert for potential data integrity issues
        alertingService.createAlert(
            "INVALID_STATE_TRANSITION",
            "Invalid payment state transition detected: " + e.getMessage(),
            "HIGH"
        );
    }

    private void handleProcessingError(GenericKafkaEvent event, Exception e, Acknowledgment acknowledgment) {
        String eventId = event.getEventId();
        Integer retryCount = event.getMetadataValue("retryCount", Integer.class, 0);

        if (retryCount < MAX_RETRY_ATTEMPTS) {
            long retryDelay = (long) Math.pow(2, retryCount) * 1000;

            log.warn("Retrying payment tracking event {} after {}ms (attempt {})",
                    eventId, retryDelay, retryCount + 1);

            event.setMetadataValue("retryCount", retryCount + 1);
            event.setMetadataValue("lastError", e.getMessage());

            scheduledExecutor.schedule(() -> {
                kafkaTemplate.send("payment-tracking-retry", event);
            }, retryDelay, TimeUnit.MILLISECONDS);

            acknowledgment.acknowledge();
        } else {
            log.error("Max retries exceeded for payment tracking event {}, sending to DLQ", eventId);

            // Send to DLQ with context
            Map<String, Object> payload = event.getPayload();
            dlqHandler.handleFailedMessage(
                "payment-tracking",
                event,
                e,
                Map.of(
                    "eventId", eventId,
                    "paymentId", extractString(payload, "paymentId", "unknown"),
                    "newStatus", extractString(payload, "newStatus", "unknown"),
                    "retryCount", String.valueOf(retryCount)
                )
            );

            sendToDLQ(event, e);
            acknowledgment.acknowledge();
        }
    }

    private void sendToDLQ(GenericKafkaEvent event, Exception e) {
        event.setMetadataValue("dlqReason", e.getMessage());
        event.setMetadataValue("dlqTimestamp", Instant.now());
        event.setMetadataValue("originalTopic", "payment-tracking");
        
        kafkaTemplate.send("payment-tracking.DLQ", event);
        
        alertingService.createDLQAlert(
            "payment-tracking",
            event.getEventId(),
            e.getMessage()
        );
    }

    // Fallback method for circuit breaker
    public void handlePaymentTrackingFailure(GenericKafkaEvent event, String topic, int partition,
                                            long offset, Acknowledgment acknowledgment, Exception e) {
        log.error("Circuit breaker activated for payment tracking: {}", e.getMessage());
        
        failedEventRepository.save(
            FailedEvent.builder()
                .eventId(event.getEventId())
                .topic(topic)
                .payload(event)
                .errorMessage(e.getMessage())
                .createdAt(Instant.now())
                .build()
        );
        
        alertingService.sendCriticalAlert(
            "Payment Tracking Circuit Breaker Open",
            "Payment tracking processing is failing. Customer experience impacted."
        );
        
        acknowledgment.acknowledge();
    }

    // Helper extraction methods
    private String extractString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private Long extractLong(Map<String, Object> map, String key, Long defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Long) return (Long) value;
        if (value instanceof Number) return ((Number) value).longValue();
        return Long.parseLong(value.toString());
    }

    private BigDecimal extractBigDecimal(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return new BigDecimal(value.toString());
        return new BigDecimal(value.toString());
    }

    private Instant extractInstant(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Instant) return (Instant) value;
        if (value instanceof Long) return Instant.ofEpochMilli((Long) value);
        return Instant.parse(value.toString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return new HashMap<>();
    }

    // Custom exceptions
    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) {
            super(message);
        }
    }

    public static class StateTransitionException extends RuntimeException {
        public StateTransitionException(String message) {
            super(message);
        }
    }
}