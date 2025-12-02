package com.waqiti.payment.kafka;

import com.waqiti.common.events.FraudDetectionCompletedEvent;
import com.waqiti.common.events.PaymentStatusUpdatedEvent;
import com.waqiti.common.events.FraudAlertEvent;
import com.waqiti.payment.domain.Payment;
import com.waqiti.payment.domain.PaymentStatus;
import com.waqiti.payment.domain.FraudCheckResult;
import com.waqiti.payment.repository.PaymentRepository;
import com.waqiti.payment.service.PaymentService;
import com.waqiti.payment.service.PaymentReversalService;
import com.waqiti.payment.exception.PaymentNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * CRITICAL FIX: Consumer for FraudDetectionCompletedEvent
 * This was missing and causing fraudulent payments to be processed
 * 
 * Responsibilities:
 * - Process fraud detection results
 * - Block high-risk payments
 * - Flag payments for manual review
 * - Initiate automatic reversals for confirmed fraud
 * 
 * @author Waqiti Engineering
 * @since 1.0.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FraudDetectionCompletedEventConsumer {
    
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final PaymentReversalService reversalService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final FraudActionService fraudActionService;
    private final NotificationService notificationService;
    
    private static final String PAYMENT_STATUS_TOPIC = "payment-status-updated-events";
    private static final String FRAUD_ALERT_TOPIC = "fraud-alert-events";
    private static final String DLQ_TOPIC = "fraud-detection-completed-events-dlq";
    
    // Risk score thresholds
    private static final double HIGH_RISK_THRESHOLD = 80.0;
    private static final double MEDIUM_RISK_THRESHOLD = 50.0;
    private static final double REVIEW_THRESHOLD = 30.0;
    
    /**
     * Processes fraud detection completion events
     * 
     * CRITICAL: This prevents fraudulent payments from completing
     * Immediate action is taken on high-risk transactions
     * 
     * @param event The fraud detection completed event
     * @param partition Kafka partition
     * @param offset Message offset
     * @param acknowledgment Manual acknowledgment
     */
    @KafkaListener(
        topics = "fraud-detection-completed-events",
        groupId = "payment-service-group",
        containerFactory = "kafkaListenerContainerFactory",
        concurrency = "5"
    )
    @Transactional
    public void handleFraudDetectionCompleted(
            @Payload FraudDetectionCompletedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("fraud-%s-p%d-o%d",
            event.getPaymentId(), partition, offset);
        
        log.info("Processing fraud detection result: paymentId={}, riskScore={}, decision={}, correlation={}",
            event.getPaymentId(), event.getRiskScore(), event.getDecision(), correlationId);
        
        try {
            // Validate event
            validateEvent(event);
            
            // Find the payment
            Payment payment = paymentRepository.findById(event.getPaymentId())
                .orElseThrow(() -> new PaymentNotFoundException(
                    "Payment not found for fraud detection: " + event.getPaymentId()));
            
            // Check if already processed (idempotency)
            if (payment.getFraudCheckId() != null && 
                payment.getFraudCheckId().equals(event.getFraudCheckId())) {
                log.debug("Fraud check already processed for payment: {}", event.getPaymentId());
                acknowledgment.acknowledge();
                return;
            }
            
            // Process based on risk score and decision
            processFraudDecision(payment, event, correlationId);
            
            // Acknowledge message
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process fraud detection result: paymentId={}, error={}",
                event.getPaymentId(), e.getMessage(), e);
            
            // Send to DLQ for manual review
            sendToDeadLetterQueue(event, e);
            acknowledgment.acknowledge();
            
            // Alert operations team
            alertService.sendOperationalAlert(
                "FRAUD_PROCESSING_FAILED",
                String.format("Failed to process fraud detection for payment %s: %s",
                    event.getPaymentId(), e.getMessage()),
                AlertService.Priority.HIGH
            );
        }
    }
    
    /**
     * Process the fraud detection decision
     */
    private void processFraudDecision(Payment payment, FraudDetectionCompletedEvent event, String correlationId) {
        
        // Store fraud check details
        payment.setFraudCheckId(event.getFraudCheckId());
        payment.setFraudScore(event.getRiskScore());
        payment.setFraudDecision(event.getDecision());
        payment.setFraudCheckTimestamp(LocalDateTime.now());
        
        // Store risk factors for audit
        if (event.getRiskFactors() != null && !event.getRiskFactors().isEmpty()) {
            payment.setFraudRiskFactors(String.join(", ", event.getRiskFactors()));
        }
        
        // Take action based on decision
        FraudDecision decision = FraudDecision.valueOf(event.getDecision());
        
        switch (decision) {
            case REJECT:
                handleRejectedPayment(payment, event, correlationId);
                break;
                
            case MANUAL_REVIEW:
                handleManualReviewPayment(payment, event, correlationId);
                break;
                
            case ADDITIONAL_VERIFICATION:
                handleAdditionalVerification(payment, event, correlationId);
                break;
                
            case APPROVE:
                handleApprovedPayment(payment, event, correlationId);
                break;
                
            default:
                log.error("Unknown fraud decision: {}", event.getDecision());
                handleManualReviewPayment(payment, event, correlationId);
        }
        
        // Save payment with fraud details
        paymentRepository.save(payment);
    }
    
    /**
     * Handle rejected payment due to fraud
     */
    private void handleRejectedPayment(Payment payment, FraudDetectionCompletedEvent event, String correlationId) {
        log.warn("Payment REJECTED due to fraud: paymentId={}, riskScore={}, factors={}",
            payment.getId(), event.getRiskScore(), event.getRiskFactors());
        
        // Update payment status
        payment.setStatus(PaymentStatus.REJECTED_FRAUD);
        payment.setRejectionReason("High fraud risk detected");
        payment.setCompletedAt(LocalDateTime.now());
        
        // Initiate immediate reversal if funds were reserved
        if (payment.getFundsReserved()) {
            try {
                reversalService.initiateAutomaticReversal(
                    payment.getId(),
                    "FRAUD_DETECTED",
                    String.format("Fraud score: %.2f", event.getRiskScore())
                );
                payment.setReversalInitiated(true);
            } catch (Exception e) {
                log.error("Failed to initiate automatic reversal for fraudulent payment: {}",
                    payment.getId(), e);
            }
        }
        
        // Create fraud alert
        FraudAlertEvent alertEvent = FraudAlertEvent.builder()
            .alertId(UUID.randomUUID())
            .paymentId(payment.getId())
            .userId(payment.getUserId())
            .alertType("PAYMENT_REJECTED")
            .severity("HIGH")
            .riskScore(event.getRiskScore())
            .riskFactors(event.getRiskFactors())
            .amount(payment.getAmount())
            .currency(payment.getCurrency())
            .timestamp(Instant.now())
            .correlationId(correlationId)
            .build();
        
        kafkaTemplate.send(FRAUD_ALERT_TOPIC, alertEvent);
        
        // Block user if pattern detected
        if (event.getRiskScore() > 95.0) {
            fraudActionService.evaluateUserBlock(payment.getUserId(), event);
        }
        
        // Notify user
        notificationService.sendFraudNotification(
            payment.getUserId(),
            "Payment Blocked",
            "Your payment has been blocked for security reasons. Please contact support if you believe this is an error.",
            payment.getId()
        );
        
        // Publish status update
        publishStatusUpdate(payment, "FRAUD_REJECTED", correlationId);
    }
    
    /**
     * Handle payment requiring manual review
     */
    private void handleManualReviewPayment(Payment payment, FraudDetectionCompletedEvent event, String correlationId) {
        log.info("Payment flagged for MANUAL REVIEW: paymentId={}, riskScore={}",
            payment.getId(), event.getRiskScore());
        
        // Update payment status
        payment.setStatus(PaymentStatus.PENDING_REVIEW);
        payment.setReviewReason("Fraud risk score: " + event.getRiskScore());
        payment.setReviewRequestedAt(LocalDateTime.now());
        
        // Create review task
        ReviewTask reviewTask = ReviewTask.builder()
            .taskId(UUID.randomUUID())
            .paymentId(payment.getId())
            .userId(payment.getUserId())
            .amount(payment.getAmount())
            .currency(payment.getCurrency())
            .riskScore(event.getRiskScore())
            .riskFactors(event.getRiskFactors())
            .priority(calculateReviewPriority(event.getRiskScore(), payment.getAmount()))
            .createdAt(LocalDateTime.now())
            .build();
        
        fraudActionService.createReviewTask(reviewTask);
        
        // Notify fraud team
        alertService.sendToFraudTeam(
            "Payment Review Required",
            String.format("Payment %s requires manual review. Amount: %s %s, Risk Score: %.2f",
                payment.getId(), payment.getAmount(), payment.getCurrency(), event.getRiskScore())
        );
        
        // Notify user of delay
        notificationService.sendNotification(
            payment.getUserId(),
            "Payment Under Review",
            "Your payment is being reviewed for security purposes. This usually takes 1-2 hours.",
            payment.getId()
        );
        
        // Publish status update
        publishStatusUpdate(payment, "MANUAL_REVIEW", correlationId);
    }
    
    /**
     * Handle payment requiring additional verification
     */
    private void handleAdditionalVerification(Payment payment, FraudDetectionCompletedEvent event, String correlationId) {
        log.info("Payment requires ADDITIONAL VERIFICATION: paymentId={}, riskScore={}",
            payment.getId(), event.getRiskScore());
        
        // Update payment status
        payment.setStatus(PaymentStatus.PENDING_VERIFICATION);
        payment.setVerificationReason("Security check required");
        payment.setVerificationRequestedAt(LocalDateTime.now());
        
        // Generate verification challenge
        VerificationChallenge challenge = fraudActionService.generateVerificationChallenge(
            payment.getUserId(),
            payment.getId(),
            event.getSuggestedVerificationMethods()
        );
        
        payment.setVerificationChallengeId(challenge.getChallengeId());
        
        // Notify user
        notificationService.sendVerificationRequest(
            payment.getUserId(),
            "Verification Required",
            "Please complete additional verification to process your payment.",
            payment.getId(),
            challenge
        );
        
        // Set timeout for verification
        fraudActionService.scheduleVerificationTimeout(
            payment.getId(),
            30, // 30 minutes timeout
            TimeUnit.MINUTES
        );
        
        // Publish status update
        publishStatusUpdate(payment, "VERIFICATION_REQUIRED", correlationId);
    }
    
    /**
     * Handle approved payment after fraud check
     */
    private void handleApprovedPayment(Payment payment, FraudDetectionCompletedEvent event, String correlationId) {
        log.info("Payment APPROVED after fraud check: paymentId={}, riskScore={}",
            payment.getId(), event.getRiskScore());
        
        // Update payment status
        payment.setFraudCheckPassed(true);
        payment.setFraudCheckCompletedAt(LocalDateTime.now());
        
        // Continue payment processing if it was waiting for fraud check
        if (payment.getStatus() == PaymentStatus.PENDING_FRAUD_CHECK) {
            try {
                paymentService.continuePaymentProcessing(payment.getId());
                payment.setStatus(PaymentStatus.PROCESSING);
            } catch (Exception e) {
                log.error("Failed to continue payment processing after fraud approval: {}",
                    payment.getId(), e);
                payment.setStatus(PaymentStatus.FAILED);
                payment.setFailureReason("Failed to continue after fraud check");
            }
        }
        
        // Publish status update
        publishStatusUpdate(payment, "FRAUD_APPROVED", correlationId);
    }
    
    /**
     * Validate the fraud detection event
     */
    private void validateEvent(FraudDetectionCompletedEvent event) {
        if (event.getPaymentId() == null) {
            throw new IllegalArgumentException("Payment ID is required");
        }
        if (event.getFraudCheckId() == null) {
            throw new IllegalArgumentException("Fraud check ID is required");
        }
        if (event.getRiskScore() == null || event.getRiskScore() < 0 || event.getRiskScore() > 100) {
            throw new IllegalArgumentException("Invalid risk score: " + event.getRiskScore());
        }
        if (event.getDecision() == null || event.getDecision().isEmpty()) {
            throw new IllegalArgumentException("Fraud decision is required");
        }
    }
    
    /**
     * Calculate review priority based on risk and amount
     */
    private String calculateReviewPriority(Double riskScore, BigDecimal amount) {
        if (riskScore > 70 || amount.compareTo(new BigDecimal("10000")) > 0) {
            return "URGENT";
        } else if (riskScore > 50 || amount.compareTo(new BigDecimal("5000")) > 0) {
            return "HIGH";
        } else {
            return "NORMAL";
        }
    }
    
    /**
     * Publish payment status update event
     */
    private void publishStatusUpdate(Payment payment, String reason, String correlationId) {
        PaymentStatusUpdatedEvent statusEvent = PaymentStatusUpdatedEvent.builder()
            .paymentId(payment.getId())
            .previousStatus(payment.getPreviousStatus())
            .newStatus(payment.getStatus().name())
            .reason(reason)
            .timestamp(Instant.now())
            .correlationId(correlationId)
            .build();
        
        kafkaTemplate.send(PAYMENT_STATUS_TOPIC, statusEvent);
    }
    
    /**
     * Send failed events to dead letter queue
     */
    private void sendToDeadLetterQueue(FraudDetectionCompletedEvent event, Exception error) {
        try {
            Map<String, Object> dlqMessage = new HashMap<>();
            dlqMessage.put("originalEvent", event);
            dlqMessage.put("errorMessage", error.getMessage());
            dlqMessage.put("errorClass", error.getClass().getName());
            dlqMessage.put("failedAt", Instant.now());
            dlqMessage.put("service", "payment-service");
            
            kafkaTemplate.send(DLQ_TOPIC, dlqMessage);
            
            log.warn("Sent failed fraud detection event to DLQ: paymentId={}, fraudCheckId={}",
                event.getPaymentId(), event.getFraudCheckId());
            
        } catch (Exception dlqError) {
            log.error("Failed to send fraud event to DLQ", dlqError);
        }
    }
    
    /**
     * Fraud decision enum
     */
    private enum FraudDecision {
        APPROVE,
        REJECT,
        MANUAL_REVIEW,
        ADDITIONAL_VERIFICATION
    }
}