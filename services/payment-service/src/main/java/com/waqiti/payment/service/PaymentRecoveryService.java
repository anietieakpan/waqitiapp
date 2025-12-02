package com.waqiti.payment.service;

import com.waqiti.common.events.PaymentFailedEvent;
import com.waqiti.payment.entity.Payment;
import com.waqiti.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * PRODUCTION PAYMENT RECOVERY SERVICE - P0 BLOCKER #5 FIX
 *
 * Handles recovery operations for failed payment event processing
 *
 * Features:
 * - Payment failure processing (notifications, fund restoration)
 * - Manual review queue for unrecoverable failures
 * - Fraud team escalation for suspicious failures
 * - Permanent failure logging for audit trail
 *
 * @author Waqiti Payment Team
 * @version 2.0.0 - Production
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentRecoveryService {

    private final PaymentRepository paymentRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Process payment failure (called during DLQ recovery)
     */
    @Transactional
    public void processPaymentFailure(PaymentFailedEvent event) {
        log.info("RECOVERY: Processing payment failure - Payment: {}", event.getPaymentId());

        // Verify payment exists
        Payment payment = paymentRepository.findById(event.getPaymentId())
            .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + event.getPaymentId()));

        // Update payment status if needed
        if (!"FAILED".equals(payment.getStatus())) {
            payment.setStatus("FAILED");
            payment.setFailureReason(event.getFailureReason());
            payment.setFailedAt(LocalDateTime.now());
            paymentRepository.save(payment);
        }

        // Publish notification event for customer
        publishCustomerNotification(event);

        // If payment had reserved funds, trigger refund/reversal
        if (event.getAmount() != null && event.getAmount().compareTo(java.math.BigDecimal.ZERO) > 0) {
            triggerFundRestoration(event);
        }

        log.info("RECOVERY: Payment failure processed successfully - Payment: {}", event.getPaymentId());
    }

    /**
     * Queue payment for manual review
     */
    public void queueForManualReview(UUID paymentId, String reason) {
        log.warn("RECOVERY: Queuing payment for manual review - Payment: {}, Reason: {}",
                paymentId, reason);

        Map<String, Object> manualReviewTask = new HashMap<>();
        manualReviewTask.put("taskId", UUID.randomUUID().toString());
        manualReviewTask.put("taskType", "PAYMENT_FAILURE_MANUAL_REVIEW");
        manualReviewTask.put("priority", "HIGH");
        manualReviewTask.put("paymentId", paymentId.toString());
        manualReviewTask.put("reason", reason);
        manualReviewTask.put("queuedAt", LocalDateTime.now().toString());

        kafkaTemplate.send("manual-review-queue", paymentId.toString(), manualReviewTask);
        log.info("RECOVERY: Payment queued for manual review - Payment: {}", paymentId);
    }

    /**
     * Escalate to fraud team
     */
    public void escalateToFraudTeam(UUID paymentId, String reason) {
        log.error("RECOVERY: Escalating to fraud team - Payment: {}, Reason: {}", paymentId, reason);

        Map<String, Object> fraudEscalation = new HashMap<>();
        fraudEscalation.put("escalationId", UUID.randomUUID().toString());
        fraudEscalation.put("escalationType", "PAYMENT_FAILURE_FRAUD");
        fraudEscalation.put("priority", "CRITICAL");
        fraudEscalation.put("paymentId", paymentId.toString());
        fraudEscalation.put("reason", reason);
        fraudEscalation.put("escalatedAt", LocalDateTime.now().toString());

        kafkaTemplate.send("fraud-escalation-queue", paymentId.toString(), fraudEscalation);
        log.error("RECOVERY: Escalated to fraud team - Payment: {}", paymentId);
    }

    /**
     * Log permanent failure
     */
    public void logPermanentFailure(UUID paymentId, String reason) {
        log.error("RECOVERY: Logging permanent failure - Payment: {}, Reason: {}", paymentId, reason);

        Map<String, Object> permanentFailure = new HashMap<>();
        permanentFailure.put("failureId", UUID.randomUUID().toString());
        permanentFailure.put("failureType", "PAYMENT_PERMANENT_FAILURE");
        permanentFailure.put("paymentId", paymentId.toString());
        permanentFailure.put("reason", reason);
        permanentFailure.put("failedAt", LocalDateTime.now().toString());

        kafkaTemplate.send("permanent-failures", paymentId.toString(), permanentFailure);
    }

    // Private helper methods

    private void publishCustomerNotification(PaymentFailedEvent event) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("userId", event.getUserId().toString());
        notification.put("type", "PAYMENT_FAILED");
        notification.put("paymentId", event.getPaymentId().toString());
        notification.put("amount", event.getAmount().toString());
        notification.put("currency", event.getCurrency());
        notification.put("failureReason", event.getFailureReason());
        notification.put("timestamp", LocalDateTime.now().toString());

        kafkaTemplate.send("user-notifications", event.getUserId().toString(), notification);
    }

    private void triggerFundRestoration(PaymentFailedEvent event) {
        Map<String, Object> fundRestoration = new HashMap<>();
        fundRestoration.put("restorationId", UUID.randomUUID().toString());
        fundRestoration.put("paymentId", event.getPaymentId().toString());
        fundRestoration.put("userId", event.getUserId().toString());
        fundRestoration.put("amount", event.getAmount().toString());
        fundRestoration.put("currency", event.getCurrency());
        fundRestoration.put("reason", "Payment failure fund restoration");
        fundRestoration.put("triggeredAt", LocalDateTime.now().toString());

        kafkaTemplate.send("fund-restoration-requests", event.getPaymentId().toString(), fundRestoration);
        log.info("RECOVERY: Fund restoration triggered - Payment: {}, Amount: {}",
                event.getPaymentId(), event.getAmount());
    }
}
