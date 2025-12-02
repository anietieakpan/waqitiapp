package com.waqiti.payment.consumer;

import com.waqiti.common.events.FraudCheckCompletedEvent;
import com.waqiti.payment.service.PaymentProcessingService;
import com.waqiti.payment.service.PaymentCompensationService;
import com.waqiti.payment.service.NotificationService;
import com.waqiti.payment.repository.ProcessedEventRepository;
import com.waqiti.payment.repository.PaymentRepository;
import com.waqiti.payment.model.ProcessedEvent;
import com.waqiti.payment.model.Payment;
import com.waqiti.payment.model.PaymentStatus;
import com.waqiti.common.events.PaymentApprovedEvent;
import com.waqiti.common.events.PaymentBlockedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Consumer for FraudCheckCompletedEvent - Critical for payment flow continuation
 * Processes fraud check results and continues or blocks payment processing
 * ZERO TOLERANCE: All payments must pass fraud screening before completion
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FraudCheckCompletedEventConsumer {
    
    private final PaymentProcessingService paymentProcessingService;
    private final PaymentCompensationService compensationService;
    private final NotificationService notificationService;
    private final ProcessedEventRepository processedEventRepository;
    private final PaymentRepository paymentRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @KafkaListener(
        topics = "fraud.check.completed",
        groupId = "payment-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleFraudCheckCompleted(FraudCheckCompletedEvent event) {
        log.info("Processing fraud check result for payment: {}, decision: {}", 
            event.getPaymentId(), event.getDecision());
        
        // IDEMPOTENCY CHECK
        if (processedEventRepository.existsByEventId(event.getEventId())) {
            log.info("Fraud check result already processed for event: {}", event.getEventId());
            return;
        }
        
        try {
            // Get payment record
            Payment payment = paymentRepository.findByPaymentId(event.getPaymentId())
                .orElseThrow(() -> new RuntimeException("Payment not found: " + event.getPaymentId()));
            
            // Process based on fraud decision
            switch (event.getDecision().toUpperCase()) {
                case "APPROVED":
                    processApprovedPayment(event, payment);
                    break;
                    
                case "BLOCKED":
                    processBlockedPayment(event, payment);
                    break;
                    
                case "REVIEW_REQUIRED":
                    processPaymentForReview(event, payment);
                    break;
                    
                default:
                    log.error("Unknown fraud decision: {} for payment: {}", 
                        event.getDecision(), event.getPaymentId());
                    processUnknownDecision(event, payment);
            }
            
            // Record event processing
            ProcessedEvent processedEvent = ProcessedEvent.builder()
                .eventId(event.getEventId())
                .eventType("FraudCheckCompletedEvent")
                .processedAt(Instant.now())
                .paymentId(event.getPaymentId())
                .fraudDecision(event.getDecision())
                .riskScore(event.getRiskScore())
                .build();
                
            processedEventRepository.save(processedEvent);
            
            log.info("Successfully processed fraud check for payment: {} with decision: {}", 
                event.getPaymentId(), event.getDecision());
                
        } catch (Exception e) {
            log.error("Failed to process fraud check result for event: {}", 
                event.getEventId(), e);
            throw new RuntimeException("Fraud check processing failed", e);
        }
    }
    
    private void processApprovedPayment(FraudCheckCompletedEvent event, Payment payment) {
        log.info("Processing approved payment: {} with risk score: {}", 
            event.getPaymentId(), event.getRiskScore());
        
        try {
            // STEP 1: Update payment status to processing
            payment.setStatus(PaymentStatus.PROCESSING);
            payment.setFraudCheckStatus("APPROVED");
            payment.setRiskScore(event.getRiskScore());
            payment.setFraudCheckCompletedAt(Instant.now());
            paymentRepository.save(payment);
            
            // STEP 2: Continue with payment processing
            PaymentProcessingResult result = paymentProcessingService.processPayment(payment);
            
            // STEP 3: Update payment with processing result
            if (result.isSuccess()) {
                payment.setStatus(PaymentStatus.COMPLETED);
                payment.setCompletedAt(Instant.now());
                payment.setProviderTransactionId(result.getProviderTransactionId());
                
                // Publish payment completion event
                PaymentApprovedEvent approvedEvent = PaymentApprovedEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .paymentId(payment.getPaymentId())
                    .amount(payment.getAmount())
                    .currency(payment.getCurrency())
                    .fromAccount(payment.getFromAccount())
                    .toAccount(payment.getToAccount())
                    .completedAt(Instant.now())
                    .riskScore(event.getRiskScore())
                    .correlationId(event.getCorrelationId())
                    .build();
                    
                kafkaTemplate.send("payment.approved", approvedEvent);
                
            } else {
                // Processing failed - handle as failed payment
                handlePaymentProcessingFailure(payment, result.getErrorMessage());
            }
            
            // STEP 4: Send notification to user
            notificationService.sendPaymentStatusNotification(
                payment.getSenderUserId(),
                payment.getPaymentId(),
                "Payment approved and processed successfully",
                payment.getStatus()
            );
            
        } catch (Exception e) {
            log.error("Failed to process approved payment: {}", event.getPaymentId(), e);
            
            // Rollback payment on processing failure
            compensationService.rollbackPayment(payment, "Processing failed after fraud approval: " + e.getMessage());
            
            throw e;
        }
    }
    
    private void processBlockedPayment(FraudCheckCompletedEvent event, Payment payment) {
        log.warn("Processing blocked payment: {} with risk score: {}, reason: {}", 
            event.getPaymentId(), event.getRiskScore(), event.getReason());
        
        try {
            // STEP 1: Update payment status to blocked
            payment.setStatus(PaymentStatus.BLOCKED);
            payment.setFraudCheckStatus("BLOCKED");
            payment.setRiskScore(event.getRiskScore());
            payment.setBlockedReason(event.getReason());
            payment.setBlockedAt(Instant.now());
            paymentRepository.save(payment);
            
            // STEP 2: Release any held funds
            compensationService.releaseFundHolds(payment);
            
            // STEP 3: Create fraud case record
            fraudCaseService.createFraudCase(\n                payment.getPaymentId(),\n                payment.getSenderUserId(),\n                event.getRiskScore(),\n                event.getReason(),\n                \"HIGH_RISK_TRANSACTION\"\n            );\n            \n            // STEP 4: Notify compliance team\n            complianceNotificationService.sendFraudAlert(\n                payment,\n                event.getRiskScore(),\n                event.getReason()\n            );\n            \n            // STEP 5: Notify user of blocked transaction\n            notificationService.sendPaymentBlockedNotification(\n                payment.getSenderUserId(),\n                payment.getPaymentId(),\n                \"Payment blocked due to security concerns. Contact support for assistance.\",\n                event.getReason()\n            );\n            \n            // STEP 6: Publish payment blocked event\n            PaymentBlockedEvent blockedEvent = PaymentBlockedEvent.builder()\n                .eventId(UUID.randomUUID().toString())\n                .paymentId(payment.getPaymentId())\n                .amount(payment.getAmount())\n                .currency(payment.getCurrency())\n                .fromAccount(payment.getFromAccount())\n                .toAccount(payment.getToAccount())\n                .blockedAt(Instant.now())\n                .riskScore(event.getRiskScore())\n                .blockReason(event.getReason())\n                .correlationId(event.getCorrelationId())\n                .build();\n                \n            kafkaTemplate.send(\"payment.blocked\", blockedEvent);\n            \n        } catch (Exception e) {\n            log.error(\"Failed to process blocked payment: {}\", event.getPaymentId(), e);\n            throw e;\n        }\n    }\n    \n    private void processPaymentForReview(FraudCheckCompletedEvent event, Payment payment) {\n        log.info(\"Processing payment for manual review: {} with risk score: {}\", \n            event.getPaymentId(), event.getRiskScore());\n        \n        try {\n            // STEP 1: Update payment status to pending review\n            payment.setStatus(PaymentStatus.PENDING_REVIEW);\n            payment.setFraudCheckStatus(\"REVIEW_REQUIRED\");\n            payment.setRiskScore(event.getRiskScore());\n            payment.setPendingReviewAt(Instant.now());\n            paymentRepository.save(payment);\n            \n            // STEP 2: Create review case for operations team\n            reviewCaseService.createReviewCase(\n                payment.getPaymentId(),\n                payment.getSenderUserId(),\n                event.getRiskScore(),\n                event.getReason(),\n                determinePriority(event.getRiskScore(), payment.getAmount())\n            );\n            \n            // STEP 3: Hold funds pending review\n            fundHoldService.createHold(\n                payment.getFromAccount(),\n                payment.getAmount(),\n                payment.getPaymentId(),\n                \"Pending fraud review\",\n                Duration.ofHours(24) // Review must be completed within 24 hours\n            );\n            \n            // STEP 4: Notify user of review status\n            notificationService.sendPaymentReviewNotification(\n                payment.getSenderUserId(),\n                payment.getPaymentId(),\n                \"Your payment is being reviewed for security. We'll update you within 24 hours.\",\n                \"24 hours\"\n            );\n            \n        } catch (Exception e) {\n            log.error(\"Failed to process payment for review: {}\", event.getPaymentId(), e);\n            throw e;\n        }\n    }\n    \n    private void processUnknownDecision(FraudCheckCompletedEvent event, Payment payment) {\n        log.error(\"Unknown fraud decision received: {} for payment: {}\", \n            event.getDecision(), event.getPaymentId());\n        \n        // Default to blocking the payment for safety\n        payment.setStatus(PaymentStatus.BLOCKED);\n        payment.setFraudCheckStatus(\"UNKNOWN_DECISION\");\n        payment.setBlockedReason(\"Unknown fraud decision: \" + event.getDecision());\n        payment.setBlockedAt(Instant.now());\n        paymentRepository.save(payment);\n        \n        // Alert engineering team\n        alertService.sendCriticalAlert(\n            \"Unknown Fraud Decision\",\n            String.format(\"Unknown fraud decision '%s' received for payment %s\", \n                event.getDecision(), event.getPaymentId())\n        );\n        \n        // Release funds\n        compensationService.releaseFundHolds(payment);\n    }\n    \n    private void handlePaymentProcessingFailure(Payment payment, String errorMessage) {\n        payment.setStatus(PaymentStatus.FAILED);\n        payment.setFailedAt(Instant.now());\n        payment.setFailureReason(errorMessage);\n        paymentRepository.save(payment);\n        \n        // Compensate for the failure\n        compensationService.compensateFailedPayment(payment);\n        \n        // Notify user\n        notificationService.sendPaymentFailureNotification(\n            payment.getSenderUserId(),\n            payment.getPaymentId(),\n            \"Payment processing failed: \" + errorMessage\n        );\n    }\n    \n    private String determinePriority(double riskScore, BigDecimal amount) {\n        if (riskScore > 0.7 || amount.compareTo(new BigDecimal(\"10000\")) >= 0) {\n            return \"HIGH\";\n        } else if (riskScore > 0.5 || amount.compareTo(new BigDecimal(\"5000\")) >= 0) {\n            return \"MEDIUM\";\n        } else {\n            return \"LOW\";\n        }\n    }\n}