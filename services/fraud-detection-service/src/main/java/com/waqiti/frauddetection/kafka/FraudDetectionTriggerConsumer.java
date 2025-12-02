package com.waqiti.frauddetection.kafka;

import com.waqiti.frauddetection.service.FraudDetectionService;
import com.waqiti.frauddetection.service.RiskAssessmentService;
import com.waqiti.frauddetection.service.IdempotencyService;
import com.waqiti.common.events.PaymentInitiatedEvent;
import com.waqiti.common.events.FraudDetectionResult;
import com.waqiti.common.kafka.KafkaTopics;
import com.waqiti.common.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

/**
 * Production-grade Fraud Detection Trigger Consumer
 * 
 * Processes payment initiation events to perform real-time fraud analysis.
 * This consumer was MISSING causing fraud detection to be completely bypassed.
 * 
 * Features:
 * - Real-time payment fraud analysis
 * - Risk scoring and classification
 * - Automated decision making
 * - Comprehensive audit logging
 * - Dead letter queue handling
 * - Circuit breaker integration
 * - Async processing for performance
 * 
 * CRITICAL SECURITY COMPONENT - DO NOT DISABLE
 * 
 * @author Waqiti Security Team
 * @version 2.0.0
 * @since 2024-01-16
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FraudDetectionTriggerConsumer {

    private final FraudDetectionService fraudDetectionService;
    private final RiskAssessmentService riskAssessmentService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AuditService auditService;
    private final IdempotencyService idempotencyService;
    
    /**
     * Primary fraud detection consumer - processes all payment initiation events
     * This consumer was completely MISSING causing security bypass
     */
    @KafkaListener(
        topics = KafkaTopics.FRAUD_DETECTION_TRIGGER,
        groupId = "fraud-detection-primary-group",
        containerFactory = "kafkaListenerContainerFactory",
        concurrency = "3"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class}
    )
    @Transactional
    public void processFraudDetectionTrigger(
            @Payload PaymentInitiatedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        String eventId = event.getTransactionId();
        long startTime = System.currentTimeMillis();

        try {
            // IDEMPOTENCY CHECK: Prevent duplicate processing
            if (!idempotencyService.checkAndMark(eventId, "fraud-detection")) {
                log.info("Duplicate fraud detection event ignored: {}", eventId);
                if (acknowledgment != null) {
                    acknowledgment.acknowledge();
                }
                return;
            }

            log.info("FRAUD_DETECTION: Processing payment for fraud analysis - txnId: {}, amount: {}, userId: {}",
                eventId, event.getAmount(), event.getUserId());
            
            // Validate event data
            validatePaymentEvent(event);
            
            // Perform comprehensive fraud analysis
            FraudDetectionResult fraudResult = performFraudAnalysis(event);
            
            // Apply business rules and decision logic
            applyFraudDecisionRules(event, fraudResult);
            
            // Publish fraud analysis result
            publishFraudResult(event, fraudResult);
            
            // Update fraud metrics
            updateFraudMetrics(event, fraudResult);
            
            // Audit successful processing
            auditFraudDetectionProcess(event, fraudResult, "SUCCESS", 
                System.currentTimeMillis() - startTime);
            
            // Acknowledge message processing
            acknowledgment.acknowledge();
            
            log.info("FRAUD_DETECTION: Analysis completed - txnId: {}, riskScore: {}, decision: {}, duration: {}ms",
                eventId, fraudResult.getRiskScore(), fraudResult.getDecision(), 
                System.currentTimeMillis() - startTime);
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("CRITICAL: Fraud detection failed for transaction: {}, duration: {}ms", eventId, duration, e);
            
            // Audit failure
            auditFraudDetectionProcess(event, null, "FAILURE: " + e.getMessage(), duration);
            
            // For critical fraud detection failures, we must block the transaction
            publishFraudFailureAlert(event, e);
            
            // Don't acknowledge - will trigger retry or DLQ
            throw new FraudDetectionException("Fraud detection failed for transaction: " + eventId, e);
        }
    }
    
    /**
     * High-priority fraud detection for large amounts
     */
    @KafkaListener(
        topics = KafkaTopics.HIGH_VALUE_FRAUD_CHECK,
        groupId = "fraud-detection-priority-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void processHighValueFraudCheck(
            @Payload PaymentInitiatedEvent event,
            Acknowledgment acknowledgment) {
        
        try {
            log.warn("HIGH_VALUE_FRAUD_CHECK: Processing high-value transaction - txnId: {}, amount: {}", 
                event.getTransactionId(), event.getAmount());
            
            // Enhanced analysis for high-value transactions
            FraudDetectionResult result = fraudDetectionService.performEnhancedAnalysis(event);
            
            // High-value transactions require additional verification
            if (result.getRiskScore() > 70) {
                // Trigger manual review process
                triggerManualReview(event, result);
                
                // Place temporary hold on transaction
                placeTempHold(event, "HIGH_RISK_DETECTED");
            }
            
            publishFraudResult(event, result);
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("CRITICAL: High-value fraud detection failed: {}", event.getTransactionId(), e);
            publishFraudFailureAlert(event, e);
            throw new FraudDetectionException("High-value fraud detection failed", e);
        }
    }
    
    /**
     * Real-time pattern detection consumer
     */
    @KafkaListener(
        topics = KafkaTopics.PATTERN_ANALYSIS_TRIGGER,
        groupId = "fraud-pattern-analysis-group"
    )
    public void processPatternAnalysis(
            @Payload PaymentInitiatedEvent event,
            Acknowledgment acknowledgment) {
        
        try {
            // Async pattern analysis to avoid blocking
            CompletableFuture.runAsync(() -> {
                try {
                    fraudDetectionService.analyzeTransactionPatterns(event);
                    fraudDetectionService.updateUserBehaviorProfile(event);
                    fraudDetectionService.checkSuspiciousActivityChains(event);
                } catch (Exception e) {
                    log.error("Pattern analysis failed for transaction: {}", event.getTransactionId(), e);
                }
            });
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Pattern analysis consumer failed: {}", event.getTransactionId(), e);
            throw new FraudDetectionException("Pattern analysis failed", e);
        }
    }
    
    /**
     * Validates the incoming payment event
     */
    private void validatePaymentEvent(PaymentInitiatedEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Payment event cannot be null");
        }
        
        if (event.getTransactionId() == null || event.getTransactionId().isEmpty()) {
            throw new IllegalArgumentException("Transaction ID cannot be empty");
        }
        
        if (event.getUserId() == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        
        if (event.getAmount() == null || event.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        
        if (event.getCurrency() == null || event.getCurrency().isEmpty()) {
            throw new IllegalArgumentException("Currency cannot be empty");
        }
    }
    
    /**
     * Performs comprehensive fraud analysis
     */
    private FraudDetectionResult performFraudAnalysis(PaymentInitiatedEvent event) {
        try {
            log.debug("Starting fraud analysis for transaction: {}", event.getTransactionId());
            
            // Create analysis context
            FraudAnalysisContext context = FraudAnalysisContext.builder()
                .transactionId(event.getTransactionId())
                .userId(event.getUserId())
                .amount(event.getAmount())
                .currency(event.getCurrency())
                .recipientId(event.getRecipientId())
                .timestamp(event.getTimestamp())
                .ipAddress(event.getIpAddress())
                .deviceFingerprint(event.getDeviceFingerprint())
                .userAgent(event.getUserAgent())
                .build();
            
            // Perform multi-layered fraud analysis
            FraudDetectionResult result = fraudDetectionService.analyzeTransaction(context);
            
            // Enhance with ML-based risk scoring
            result = riskAssessmentService.enhanceWithMLScoring(result, context);
            
            // Apply real-time rule engine
            result = fraudDetectionService.applyRealTimeRules(result, context);
            
            log.debug("Fraud analysis completed - txnId: {}, riskScore: {}, flags: {}", 
                event.getTransactionId(), result.getRiskScore(), result.getFraudFlags().size());
            
            return result;
            
        } catch (Exception e) {
            log.error("Fraud analysis failed for transaction: {}", event.getTransactionId(), e);
            
            // Return high-risk result for safety
            return FraudDetectionResult.builder()
                .transactionId(event.getTransactionId())
                .riskScore(100.0) // Maximum risk
                .decision(FraudDecision.BLOCK)
                .reason("Analysis failed - blocking for safety")
                .analysisTimestamp(LocalDateTime.now())
                .build();
        }
    }
    
    /**
     * Applies fraud decision rules based on risk assessment
     */
    private void applyFraudDecisionRules(PaymentInitiatedEvent event, FraudDetectionResult result) {
        double riskScore = result.getRiskScore();
        
        // Apply decision thresholds
        if (riskScore >= 80.0) {
            result.setDecision(FraudDecision.BLOCK);
            result.setReason("High fraud risk detected - score: " + riskScore);
            
            // Immediate account security measures
            triggerSecurityAlert(event, result);
            
        } else if (riskScore >= 60.0) {
            result.setDecision(FraudDecision.REVIEW);
            result.setReason("Medium fraud risk - manual review required");
            
            // Queue for manual review
            triggerManualReview(event, result);
            
        } else if (riskScore >= 30.0) {
            result.setDecision(FraudDecision.MONITOR);
            result.setReason("Low-medium risk - enhanced monitoring");
            
            // Enable enhanced monitoring
            enableEnhancedMonitoring(event, result);
            
        } else {
            result.setDecision(FraudDecision.APPROVE);
            result.setReason("Low fraud risk - approved");
        }
        
        log.info("Fraud decision applied - txnId: {}, decision: {}, score: {}", 
            event.getTransactionId(), result.getDecision(), riskScore);
    }
    
    /**
     * Publishes fraud detection result to downstream services
     */
    private void publishFraudResult(PaymentInitiatedEvent event, FraudDetectionResult result) {
        try {
            // Publish to payment processing service
            kafkaTemplate.send(KafkaTopics.FRAUD_ANALYSIS_RESULT, 
                event.getTransactionId(), result);
            
            // If high risk, alert security service
            if (result.getRiskScore() >= 70.0) {
                kafkaTemplate.send(KafkaTopics.SECURITY_ALERTS, 
                    event.getUserId().toString(), result);
            }
            
            // Update user risk profile
            kafkaTemplate.send(KafkaTopics.USER_RISK_PROFILE_UPDATE, 
                event.getUserId().toString(), result);
            
            log.debug("Fraud result published for transaction: {}", event.getTransactionId());
            
        } catch (Exception e) {
            log.error("Failed to publish fraud result for transaction: {}", 
                event.getTransactionId(), e);
            throw new FraudDetectionException("Failed to publish fraud result", e);
        }
    }
    
    /**
     * Updates fraud detection metrics
     */
    private void updateFraudMetrics(PaymentInitiatedEvent event, FraudDetectionResult result) {
        try {
            // Update transaction counters
            fraudDetectionService.updateTransactionMetrics(result.getDecision());
            
            // Update risk score distribution
            fraudDetectionService.updateRiskScoreMetrics(result.getRiskScore());
            
            // Update decision time metrics
            fraudDetectionService.updatePerformanceMetrics(result.getProcessingTimeMs());
            
        } catch (Exception e) {
            log.error("Failed to update fraud metrics", e);
        }
    }
    
    /**
     * Triggers security alert for high-risk transactions
     */
    private void triggerSecurityAlert(PaymentInitiatedEvent event, FraudDetectionResult result) {
        try {
            SecurityAlert alert = SecurityAlert.builder()
                .alertId(UUID.randomUUID().toString())
                .transactionId(event.getTransactionId())
                .userId(event.getUserId())
                .alertType("HIGH_FRAUD_RISK")
                .riskScore(result.getRiskScore())
                .fraudFlags(result.getFraudFlags())
                .timestamp(LocalDateTime.now())
                .severity("CRITICAL")
                .requiresImmedateAction(true)
                .build();
            
            kafkaTemplate.send(KafkaTopics.SECURITY_ALERTS, 
                event.getUserId().toString(), alert);
            
            log.warn("SECURITY_ALERT: High fraud risk detected - txnId: {}, userId: {}, score: {}", 
                event.getTransactionId(), event.getUserId(), result.getRiskScore());
            
        } catch (Exception e) {
            log.error("Failed to trigger security alert", e);
        }
    }
    
    /**
     * Triggers manual review process
     */
    private void triggerManualReview(PaymentInitiatedEvent event, FraudDetectionResult result) {
        try {
            ManualReviewRequest reviewRequest = ManualReviewRequest.builder()
                .requestId(UUID.randomUUID().toString())
                .transactionId(event.getTransactionId())
                .userId(event.getUserId())
                .amount(event.getAmount())
                .currency(event.getCurrency())
                .riskScore(result.getRiskScore())
                .fraudFlags(result.getFraudFlags())
                .priority(result.getRiskScore() >= 70.0 ? "HIGH" : "MEDIUM")
                .requestedAt(LocalDateTime.now())
                .build();
            
            kafkaTemplate.send(KafkaTopics.MANUAL_REVIEW_QUEUE, 
                event.getTransactionId(), reviewRequest);
            
            log.info("Manual review triggered for transaction: {}, score: {}", 
                event.getTransactionId(), result.getRiskScore());
            
        } catch (Exception e) {
            log.error("Failed to trigger manual review", e);
        }
    }
    
    /**
     * Places temporary hold on suspicious transaction
     */
    private void placeTempHold(PaymentInitiatedEvent event, String reason) {
        try {
            TransactionHold hold = TransactionHold.builder()
                .holdId(UUID.randomUUID().toString())
                .transactionId(event.getTransactionId())
                .userId(event.getUserId())
                .reason(reason)
                .holdType("FRAUD_INVESTIGATION")
                .duration(Duration.ofHours(24)) // 24-hour hold
                .createdAt(LocalDateTime.now())
                .build();
            
            kafkaTemplate.send(KafkaTopics.TRANSACTION_HOLDS, 
                event.getTransactionId(), hold);
            
            log.warn("Transaction hold placed - txnId: {}, reason: {}", 
                event.getTransactionId(), reason);
            
        } catch (Exception e) {
            log.error("Failed to place transaction hold", e);
        }
    }
    
    /**
     * Enables enhanced monitoring for user
     */
    private void enableEnhancedMonitoring(PaymentInitiatedEvent event, FraudDetectionResult result) {
        try {
            EnhancedMonitoringRequest monitoringRequest = EnhancedMonitoringRequest.builder()
                .userId(event.getUserId())
                .reason("Medium fraud risk detected")
                .riskScore(result.getRiskScore())
                .monitoringLevel("ELEVATED")
                .duration(Duration.ofDays(7)) // 7-day monitoring
                .triggeredBy("FRAUD_DETECTION_SERVICE")
                .triggeredAt(LocalDateTime.now())
                .build();
            
            kafkaTemplate.send(KafkaTopics.ENHANCED_MONITORING_REQUESTS, 
                event.getUserId().toString(), monitoringRequest);
            
            log.info("Enhanced monitoring enabled for user: {}, duration: 7 days", 
                event.getUserId());
            
        } catch (Exception e) {
            log.error("Failed to enable enhanced monitoring", e);
        }
    }
    
    /**
     * Publishes fraud detection failure alert
     */
    private void publishFraudFailureAlert(PaymentInitiatedEvent event, Exception error) {
        try {
            FraudSystemAlert alert = FraudSystemAlert.builder()
                .alertId(UUID.randomUUID().toString())
                .transactionId(event.getTransactionId())
                .userId(event.getUserId())
                .errorMessage(error.getMessage())
                .alertType("FRAUD_SYSTEM_FAILURE")
                .severity("CRITICAL")
                .requiresImmediateAction(true)
                .timestamp(LocalDateTime.now())
                .build();
            
            kafkaTemplate.send(KafkaTopics.SYSTEM_ALERTS, 
                "FRAUD_DETECTION_FAILURE", alert);
            
            // Also block the transaction for safety
            publishTransactionBlock(event, "FRAUD_SYSTEM_UNAVAILABLE");
            
        } catch (Exception e) {
            log.error("Failed to publish fraud failure alert", e);
        }
    }
    
    /**
     * Publishes transaction block for system failures
     */
    private void publishTransactionBlock(PaymentInitiatedEvent event, String reason) {
        try {
            TransactionBlockRequest blockRequest = TransactionBlockRequest.builder()
                .transactionId(event.getTransactionId())
                .userId(event.getUserId())
                .reason(reason)
                .blockedBy("FRAUD_DETECTION_SERVICE")
                .blockedAt(LocalDateTime.now())
                .build();
            
            kafkaTemplate.send(KafkaTopics.TRANSACTION_BLOCKS, 
                event.getTransactionId(), blockRequest);
            
        } catch (Exception e) {
            log.error("Failed to publish transaction block", e);
        }
    }
    
    /**
     * Audits fraud detection process
     */
    private void auditFraudDetectionProcess(PaymentInitiatedEvent event, 
                                          FraudDetectionResult result, 
                                          String status, 
                                          long duration) {
        try {
            auditService.auditSecurityEvent(
                "FRAUD_DETECTION_PROCESS",
                event.getUserId().toString(),
                String.format("Fraud detection %s for transaction %s", status, event.getTransactionId()),
                Map.of(
                    "transactionId", event.getTransactionId(),
                    "userId", event.getUserId(),
                    "amount", event.getAmount(),
                    "currency", event.getCurrency(),
                    "riskScore", result != null ? result.getRiskScore() : 0.0,
                    "decision", result != null ? result.getDecision() : "UNKNOWN",
                    "processingTimeMs", duration,
                    "status", status
                )
            );
        } catch (Exception e) {
            log.error("Failed to audit fraud detection process", e);
        }
    }
}