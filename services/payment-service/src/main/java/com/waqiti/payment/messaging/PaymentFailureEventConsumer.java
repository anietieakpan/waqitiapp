package com.waqiti.payment.messaging;

import com.waqiti.payment.domain.Payment;
import com.waqiti.payment.domain.PaymentStatus;
import com.waqiti.payment.domain.PaymentFailure;
import com.waqiti.payment.domain.Refund;
import com.waqiti.payment.repository.PaymentRepository;
import com.waqiti.payment.repository.PaymentFailureRepository;
import com.waqiti.payment.repository.RefundRepository;
import com.waqiti.payment.service.PaymentRecoveryService;
import com.waqiti.payment.service.RefundProcessingService;
import com.waqiti.common.events.PaymentFailureEvent;
import com.waqiti.common.events.PaymentRefundEvent;
import com.waqiti.common.audit.AuditService;
import com.waqiti.notification.client.NotificationServiceClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Enterprise Payment Failure and Refund Event Consumer
 * 
 * Handles critical payment failure scenarios and refund processing with:
 * - Automatic failure analysis and categorization
 * - Payment recovery workflows
 * - Customer notification management
 * - Comprehensive audit logging
 * - Fraud detection integration
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentFailureEventConsumer {

    private final PaymentRepository paymentRepository;
    private final PaymentFailureRepository paymentFailureRepository;
    private final RefundRepository refundRepository;
    private final PaymentRecoveryService paymentRecoveryService;
    private final RefundProcessingService refundProcessingService;
    private final NotificationServiceClient notificationServiceClient;
    private final AuditService auditService;

    /**
     * Process payment failure events with automatic recovery and notification
     */
    @KafkaListener(topics = "payment-failures", groupId = "payment-service-failures-group")
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 2000, multiplier = 2),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class}
    )
    @Transactional
    public void handlePaymentFailure(@Valid @Payload PaymentFailureEvent event,
                                   @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                   @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                   Acknowledgment acknowledgment) {
        
        log.warn("Processing payment failure event - Payment: {}, Error: {}, Provider: {}", 
                event.getPaymentId(), event.getErrorCode(), event.getPaymentProvider());

        try {
            // Validate event
            validatePaymentFailureEvent(event);
            
            // Find the payment
            Optional<Payment> paymentOpt = paymentRepository.findById(UUID.fromString(event.getPaymentId()));
            if (paymentOpt.isEmpty()) {
                log.error("Payment not found for failure event: {}", event.getPaymentId());
                throw new IllegalArgumentException("Payment not found: " + event.getPaymentId());
            }

            Payment payment = paymentOpt.get();
            
            // Update payment status
            PaymentStatus previousStatus = payment.getStatus();
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailedAt(LocalDateTime.now());
            payment.setLastModifiedAt(LocalDateTime.now());
            
            // Create detailed failure record
            PaymentFailure paymentFailure = createPaymentFailureRecord(event, payment);
            paymentFailureRepository.save(paymentFailure);
            
            // Analyze failure and determine recovery strategy
            PaymentFailureAnalysis failureAnalysis = analyzePaymentFailure(event, payment);
            
            // Update payment with failure details
            payment.setFailureReason(event.getErrorMessage());
            payment.setFailureCode(event.getErrorCode());
            payment.setCanRetry(failureAnalysis.isRetryable());
            
            Payment savedPayment = paymentRepository.save(payment);

            // Execute recovery strategy if applicable
            if (failureAnalysis.isRetryable() && shouldAttemptRecovery(payment, event)) {
                log.info("Initiating payment recovery for payment: {}", event.getPaymentId());
                paymentRecoveryService.initiatePaymentRecovery(
                    savedPayment, 
                    failureAnalysis.getRecoveryStrategy(),
                    event.getRetryDelay()
                );
            }

            // Send customer notifications
            sendPaymentFailureNotifications(savedPayment, event, failureAnalysis);

            // Check for potential fraud indicators
            if (failureAnalysis.isSuspicious()) {
                publishFraudAlert(savedPayment, event, failureAnalysis);
            }

            // Audit the failure
            auditService.auditPaymentEvent(
                "PAYMENT_FAILURE_PROCESSED",
                savedPayment.getId().toString(),
                String.format("Payment failure processed - Error: %s, Provider: %s, Recovery: %s", 
                    event.getErrorCode(), event.getPaymentProvider(), 
                    failureAnalysis.isRetryable() ? "SCHEDULED" : "NOT_APPLICABLE"),
                Map.of(
                    "paymentId", event.getPaymentId(),
                    "errorCode", event.getErrorCode(),
                    "errorMessage", event.getErrorMessage(),
                    "paymentProvider", event.getPaymentProvider(),
                    "previousStatus", previousStatus,
                    "recoveryStrategy", failureAnalysis.getRecoveryStrategy(),
                    "suspicious", failureAnalysis.isSuspicious(),
                    "failedAt", LocalDateTime.now()
                )
            );

            acknowledgment.acknowledge();

            log.info("Successfully processed payment failure for payment: {} - Recovery: {}", 
                    event.getPaymentId(), failureAnalysis.isRetryable() ? "SCHEDULED" : "FINAL");

        } catch (Exception e) {
            log.error("Failed to process payment failure event for payment: {}", event.getPaymentId(), e);
            
            auditService.auditPaymentEvent(
                "PAYMENT_FAILURE_PROCESSING_ERROR",
                event.getPaymentId(),
                "Failed to process payment failure: " + e.getMessage(),
                Map.of("error", e.getClass().getSimpleName(), "errorCode", event.getErrorCode())
            );
            
            throw e;
        }
    }

    /**
     * Process payment refund events with comprehensive tracking
     */
    @KafkaListener(topics = "payment-refunds", groupId = "payment-service-refunds-group")
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1500, multiplier = 2)
    )
    @Transactional
    public void handlePaymentRefund(@Valid @Payload PaymentRefundEvent event,
                                  Acknowledgment acknowledgment) {
        
        log.info("Processing payment refund event - Payment: {}, Refund: {}, Amount: {} {}", 
                event.getOriginalPaymentId(), event.getRefundId(), 
                event.getRefundAmount(), event.getCurrency());

        try {
            validatePaymentRefundEvent(event);
            
            // Find the original payment
            Optional<Payment> paymentOpt = paymentRepository.findById(UUID.fromString(event.getOriginalPaymentId()));
            if (paymentOpt.isEmpty()) {
                throw new IllegalArgumentException("Original payment not found: " + event.getOriginalPaymentId());
            }

            Payment originalPayment = paymentOpt.get();
            
            // Create or update refund record
            Refund refund = createOrUpdateRefundRecord(event, originalPayment);
            
            // Process the refund based on status
            switch (event.getRefundStatus().toUpperCase()) {
                case "INITIATED":
                    processRefundInitiation(refund, event);
                    break;
                case "COMPLETED":
                    processRefundCompletion(refund, event);
                    break;
                case "FAILED":
                    processRefundFailure(refund, event);
                    break;
                default:
                    log.warn("Unknown refund status: {} for refund: {}", 
                        event.getRefundStatus(), event.getRefundId());
            }

            // Update original payment refund tracking
            updateOriginalPaymentRefundStatus(originalPayment, refund, event);

            // Send notifications
            sendRefundNotifications(originalPayment, refund, event);

            // Audit the refund processing
            auditService.auditPaymentEvent(
                "PAYMENT_REFUND_PROCESSED",
                event.getOriginalPaymentId(),
                String.format("Refund processed - Status: %s, Amount: %s %s", 
                    event.getRefundStatus(), event.getRefundAmount(), event.getCurrency()),
                Map.of(
                    "refundId", event.getRefundId(),
                    "refundStatus", event.getRefundStatus(),
                    "refundAmount", event.getRefundAmount(),
                    "currency", event.getCurrency(),
                    "refundReason", event.getRefundReason(),
                    "processedAt", LocalDateTime.now()
                )
            );

            acknowledgment.acknowledge();

            log.info("Successfully processed refund event - RefundId: {}, Status: {}", 
                    event.getRefundId(), event.getRefundStatus());

        } catch (Exception e) {
            log.error("Failed to process payment refund event - RefundId: {}", event.getRefundId(), e);
            
            auditService.auditPaymentEvent(
                "PAYMENT_REFUND_PROCESSING_ERROR",
                event.getOriginalPaymentId(),
                "Failed to process refund: " + e.getMessage(),
                Map.of("refundId", event.getRefundId(), "error", e.getClass().getSimpleName())
            );
            
            throw e;
        }
    }

    // Private helper methods

    private void validatePaymentFailureEvent(PaymentFailureEvent event) {
        if (event.getPaymentId() == null || event.getPaymentId().trim().isEmpty()) {
            throw new IllegalArgumentException("Payment ID is required");
        }
        if (event.getErrorCode() == null || event.getErrorCode().trim().isEmpty()) {
            throw new IllegalArgumentException("Error code is required");
        }
        if (event.getPaymentProvider() == null || event.getPaymentProvider().trim().isEmpty()) {
            throw new IllegalArgumentException("Payment provider is required");
        }
    }

    private void validatePaymentRefundEvent(PaymentRefundEvent event) {
        if (event.getOriginalPaymentId() == null || event.getOriginalPaymentId().trim().isEmpty()) {
            throw new IllegalArgumentException("Original payment ID is required");
        }
        if (event.getRefundId() == null || event.getRefundId().trim().isEmpty()) {
            throw new IllegalArgumentException("Refund ID is required");
        }
        if (event.getRefundAmount() == null || event.getRefundAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Valid refund amount is required");
        }
    }

    private PaymentFailure createPaymentFailureRecord(PaymentFailureEvent event, Payment payment) {
        return PaymentFailure.builder()
                .id(UUID.randomUUID())
                .paymentId(payment.getId())
                .errorCode(event.getErrorCode())
                .errorMessage(event.getErrorMessage())
                .paymentProvider(event.getPaymentProvider())
                .providerTransactionId(event.getProviderTransactionId())
                .failureCategory(categorizeFailure(event.getErrorCode()))
                .isRetryable(determineRetryability(event.getErrorCode()))
                .failedAt(LocalDateTime.now())
                .providerResponse(event.getProviderResponse())
                .httpStatusCode(event.getHttpStatusCode())
                .networkLatency(event.getNetworkLatency())
                .build();
    }

    private PaymentFailureAnalysis analyzePaymentFailure(PaymentFailureEvent event, Payment payment) {
        boolean isRetryable = determineRetryability(event.getErrorCode());
        String recoveryStrategy = determineRecoveryStrategy(event.getErrorCode(), payment);
        boolean isSuspicious = detectSuspiciousActivity(event, payment);
        
        return PaymentFailureAnalysis.builder()
                .retryable(isRetryable)
                .recoveryStrategy(recoveryStrategy)
                .suspicious(isSuspicious)
                .failureCategory(categorizeFailure(event.getErrorCode()))
                .riskLevel(calculateRiskLevel(event, payment))
                .build();
    }

    private boolean shouldAttemptRecovery(Payment payment, PaymentFailureEvent event) {
        // Check retry count and cooldown period
        int maxRetries = 3;
        return payment.getRetryCount() < maxRetries && 
               !isInCooldownPeriod(payment) &&
               !isBlacklistedError(event.getErrorCode());
    }

    private void sendPaymentFailureNotifications(Payment payment, PaymentFailureEvent event, PaymentFailureAnalysis analysis) {
        try {
            if (analysis.isRetryable()) {
                notificationServiceClient.sendPaymentRetryNotification(
                    payment.getUserId().toString(),
                    payment.getId().toString(),
                    event.getErrorMessage(),
                    analysis.getRecoveryStrategy()
                );
            } else {
                notificationServiceClient.sendPaymentFailureNotification(
                    payment.getUserId().toString(),
                    payment.getId().toString(),
                    payment.getAmount(),
                    payment.getCurrency(),
                    event.getErrorMessage()
                );
            }
        } catch (Exception e) {
            log.warn("Failed to send payment failure notifications for payment: {}", payment.getId(), e);
        }
    }

    private Refund createOrUpdateRefundRecord(PaymentRefundEvent event, Payment originalPayment) {
        Optional<Refund> existingRefund = refundRepository.findByRefundId(event.getRefundId());
        
        if (existingRefund.isPresent()) {
            Refund refund = existingRefund.get();
            refund.setStatus(event.getRefundStatus());
            refund.setLastModifiedAt(LocalDateTime.now());
            return refundRepository.save(refund);
        } else {
            Refund newRefund = Refund.builder()
                    .id(UUID.randomUUID())
                    .refundId(event.getRefundId())
                    .originalPaymentId(originalPayment.getId())
                    .refundAmount(event.getRefundAmount())
                    .currency(event.getCurrency())
                    .refundReason(event.getRefundReason())
                    .status(event.getRefundStatus())
                    .initiatedAt(LocalDateTime.now())
                    .lastModifiedAt(LocalDateTime.now())
                    .build();
            return refundRepository.save(newRefund);
        }
    }

    private void processRefundInitiation(Refund refund, PaymentRefundEvent event) {
        refund.setStatus("PROCESSING");
        refund.setInitiatedAt(LocalDateTime.now());
        refundRepository.save(refund);
        
        log.info("Refund initiated: {} for amount: {} {}", 
                refund.getRefundId(), refund.getRefundAmount(), refund.getCurrency());
    }

    private void processRefundCompletion(Refund refund, PaymentRefundEvent event) {
        refund.setStatus("COMPLETED");
        refund.setCompletedAt(LocalDateTime.now());
        refund.setProviderRefundId(event.getProviderRefundId());
        refundRepository.save(refund);
        
        log.info("Refund completed: {} for amount: {} {}", 
                refund.getRefundId(), refund.getRefundAmount(), refund.getCurrency());
    }

    private void processRefundFailure(Refund refund, PaymentRefundEvent event) {
        refund.setStatus("FAILED");
        refund.setFailedAt(LocalDateTime.now());
        refund.setFailureReason(event.getFailureReason());
        refundRepository.save(refund);
        
        log.error("Refund failed: {} - Reason: {}", refund.getRefundId(), event.getFailureReason());
    }

    private void updateOriginalPaymentRefundStatus(Payment payment, Refund refund, PaymentRefundEvent event) {
        if ("COMPLETED".equals(event.getRefundStatus())) {
            payment.setRefundStatus("REFUNDED");
            payment.setRefundedAmount(refund.getRefundAmount());
            payment.setRefundedAt(LocalDateTime.now());
        } else if ("FAILED".equals(event.getRefundStatus())) {
            payment.setRefundStatus("REFUND_FAILED");
        } else {
            payment.setRefundStatus("REFUND_PENDING");
        }
        
        paymentRepository.save(payment);
    }

    private void sendRefundNotifications(Payment payment, Refund refund, PaymentRefundEvent event) {
        try {
            switch (event.getRefundStatus().toUpperCase()) {
                case "COMPLETED":
                    notificationServiceClient.sendRefundCompletedNotification(
                        payment.getUserId().toString(),
                        refund.getRefundId(),
                        refund.getRefundAmount(),
                        refund.getCurrency()
                    );
                    break;
                case "FAILED":
                    notificationServiceClient.sendRefundFailedNotification(
                        payment.getUserId().toString(),
                        refund.getRefundId(),
                        event.getFailureReason()
                    );
                    break;
            }
        } catch (Exception e) {
            log.warn("Failed to send refund notifications for refund: {}", refund.getRefundId(), e);
        }
    }

    // Utility methods for failure analysis

    private String categorizeFailure(String errorCode) {
        if (errorCode.startsWith("INSUFFICIENT_FUNDS") || errorCode.contains("NSF")) {
            return "INSUFFICIENT_FUNDS";
        } else if (errorCode.contains("DECLINED") || errorCode.contains("REJECTED")) {
            return "DECLINED_BY_ISSUER";
        } else if (errorCode.contains("TIMEOUT") || errorCode.contains("NETWORK")) {
            return "NETWORK_ERROR";
        } else if (errorCode.contains("FRAUD") || errorCode.contains("SECURITY")) {
            return "SECURITY_DECLINE";
        } else {
            return "SYSTEM_ERROR";
        }
    }

    private boolean determineRetryability(String errorCode) {
        return errorCode.contains("TIMEOUT") || 
               errorCode.contains("NETWORK") || 
               errorCode.contains("TEMPORARY") ||
               errorCode.startsWith("5"); // HTTP 5xx errors
    }

    private String determineRecoveryStrategy(String errorCode, Payment payment) {
        String category = categorizeFailure(errorCode);
        
        return switch (category) {
            case "NETWORK_ERROR" -> "IMMEDIATE_RETRY";
            case "INSUFFICIENT_FUNDS" -> "DELAYED_RETRY";
            case "SYSTEM_ERROR" -> "EXPONENTIAL_BACKOFF";
            default -> "NO_RETRY";
        };
    }

    private boolean detectSuspiciousActivity(PaymentFailureEvent event, Payment payment) {
        // Check for patterns indicating potential fraud
        return payment.getRetryCount() > 5 || 
               event.getErrorCode().contains("FRAUD") ||
               isHighVelocityFailure(payment.getUserId());
    }

    private String calculateRiskLevel(PaymentFailureEvent event, Payment payment) {
        if (detectSuspiciousActivity(event, payment)) {
            return "HIGH";
        } else if (payment.getRetryCount() > 2) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }

    private boolean isInCooldownPeriod(Payment payment) {
        return payment.getLastRetryAt() != null &&
               payment.getLastRetryAt().isAfter(LocalDateTime.now().minusMinutes(5));
    }

    private boolean isBlacklistedError(String errorCode) {
        return errorCode.contains("FRAUD") || 
               errorCode.contains("BLOCKED") ||
               errorCode.contains("PERMANENTLY_DECLINED");
    }

    private boolean isHighVelocityFailure(UUID userId) {
        // Check if user has had multiple failures in short time period
        // Implementation would query recent failure history
        return false; // Placeholder
    }

    private void publishFraudAlert(Payment payment, PaymentFailureEvent event, PaymentFailureAnalysis analysis) {
        // Publish fraud alert event for investigation
        log.warn("Publishing fraud alert for suspicious payment failure - Payment: {}, User: {}", 
                payment.getId(), payment.getUserId());
        // Implementation would publish to fraud-alerts topic
    }

    // Inner classes for analysis results

    @lombok.Data
    @lombok.Builder
    private static class PaymentFailureAnalysis {
        private boolean retryable;
        private String recoveryStrategy;
        private boolean suspicious;
        private String failureCategory;
        private String riskLevel;
    }
}