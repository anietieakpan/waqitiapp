package com.waqiti.payment.kafka;

import com.waqiti.common.events.FraudDetectionResultsEvent;
import com.waqiti.payment.service.PaymentProcessingService;
import com.waqiti.payment.service.TransactionSecurityService;
import com.waqiti.payment.service.NotificationService;
import com.waqiti.common.audit.AuditService;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * CRITICAL EVENT CONSUMER: Fraud Detection Results Events
 * 
 * This consumer handles fraud detection results from the ML service that were 
 * previously orphaned, causing fraud scores to be ignored and allowing potentially
 * fraudulent transactions to proceed without proper risk assessment.
 * 
 * Key Responsibilities:
 * - Process real-time fraud scores from ML models
 * - Block or flag high-risk transactions
 * - Update transaction risk assessments
 * - Trigger additional security measures
 * - Send fraud alerts to security team
 * - Update customer risk profiles
 * - Generate compliance reports for suspicious activity
 * 
 * Business Impact:
 * - Prevents fraudulent transactions from proceeding
 * - Reduces financial losses from fraud
 * - Maintains regulatory compliance for AML/BSA
 * - Protects customer accounts from unauthorized activity
 * 
 * @author Waqiti Security Team
 * @since 2.0.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FraudDetectionResultsEventConsumer {

    private final PaymentProcessingService paymentProcessingService;
    private final TransactionSecurityService transactionSecurityService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final MeterRegistry meterRegistry;
    
    private final Counter fraudEventsProcessed;
    private final Counter highRiskTransactionsBlocked;
    private final Counter fraudProcessingErrors;

    public FraudDetectionResultsEventConsumer(PaymentProcessingService paymentProcessingService,
                                            TransactionSecurityService transactionSecurityService,
                                            NotificationService notificationService,
                                            AuditService auditService,
                                            MeterRegistry meterRegistry) {
        this.paymentProcessingService = paymentProcessingService;
        this.transactionSecurityService = transactionSecurityService;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.meterRegistry = meterRegistry;
        
        // Initialize metrics
        this.fraudEventsProcessed = Counter.builder("fraud_detection_events_processed")
            .description("Number of fraud detection events processed")
            .register(meterRegistry);
            
        this.highRiskTransactionsBlocked = Counter.builder("high_risk_transactions_blocked")
            .description("Number of high-risk transactions blocked")
            .register(meterRegistry);
            
        this.fraudProcessingErrors = Counter.builder("fraud_processing_errors")
            .description("Number of fraud event processing errors")
            .register(meterRegistry);
    }

    /**
     * Processes fraud detection results from the ML service.
     * 
     * @param event The fraud detection results event
     * @param acknowledgment Kafka acknowledgment for manual commit
     * @param partition The Kafka partition
     * @param offset The message offset
     */
    @KafkaListener(
        topics = "fraud-detection-results",
        groupId = "payment-fraud-consumer-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    @Timed(value = "fraud_event_processing_duration", description = "Time to process fraud detection event")
    public void handleFraudDetectionResults(
            @Payload FraudDetectionResultsEvent event,
            Acknowledgment acknowledgment,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {
        
        try {
            log.info("Processing fraud detection results for transaction: {} score: {:.3f} risk: {}", 
                event.getTransactionId(), event.getFraudScore(), event.getRiskLevel());
            
            // Validate event data
            validateFraudDetectionEvent(event);
            
            // Update transaction risk assessment
            updateTransactionRiskAssessment(event);
            
            // Take action based on fraud score and risk level
            processFraudDecision(event);
            
            // Update customer risk profile
            updateCustomerRiskProfile(event);
            
            // Generate security alerts if needed
            generateSecurityAlerts(event);
            
            // Update ML model feedback
            provideFeedbackToMLModel(event);
            
            // Generate compliance reports for high-risk transactions
            generateComplianceReports(event);
            
            // Audit fraud detection processing
            auditFraudDetectionProcessing(event);
            
            // Update metrics
            fraudEventsProcessed.increment();
            
            // Acknowledge successful processing
            acknowledgment.acknowledge();
            
            log.info("Successfully processed fraud detection results for transaction: {}", event.getTransactionId());
            
        } catch (Exception e) {
            log.error("Error processing fraud detection results for transaction: {}", event.getTransactionId(), e);
            
            // Update error metrics
            fraudProcessingErrors.increment();
            
            // Handle processing error
            handleFraudProcessingError(event, e);
            
            // Acknowledge to prevent infinite reprocessing (error handled in DLQ)
            acknowledgment.acknowledge();
        }
    }

    private void validateFraudDetectionEvent(FraudDetectionResultsEvent event) {
        if (event.getTransactionId() == null || event.getTransactionId().trim().isEmpty()) {
            throw new IllegalArgumentException("Transaction ID is required for fraud detection event");
        }
        
        if (event.getFraudScore() == null || event.getFraudScore() < 0.0 || event.getFraudScore() > 1.0) {
            throw new IllegalArgumentException("Fraud score must be between 0.0 and 1.0");
        }
        
        if (event.getRiskLevel() == null) {
            throw new IllegalArgumentException("Risk level is required for fraud detection event");
        }
        
        if (event.getCustomerId() == null || event.getCustomerId().trim().isEmpty()) {
            throw new IllegalArgumentException("Customer ID is required for fraud detection event");
        }
    }

    private void updateTransactionRiskAssessment(FraudDetectionResultsEvent event) {
        try {
            transactionSecurityService.updateTransactionRiskAssessment(
                event.getTransactionId(),
                event.getFraudScore(),
                event.getRiskLevel(),
                event.getFraudIndicators(),
                event.getModelVersion(),
                event.getAnalyzedAt()
            );
            
            log.debug("Updated risk assessment for transaction: {}", event.getTransactionId());
            
        } catch (Exception e) {
            log.error("Failed to update risk assessment for transaction: {}", event.getTransactionId(), e);
            throw new RuntimeException("Risk assessment update failed", e);
        }
    }

    private void processFraudDecision(FraudDetectionResultsEvent event) {
        try {
            String action = determineFraudAction(event);
            
            switch (action) {
                case "BLOCK_TRANSACTION":
                    blockTransaction(event);
                    highRiskTransactionsBlocked.increment();
                    break;
                    
                case "REQUIRE_ADDITIONAL_VERIFICATION":
                    requireAdditionalVerification(event);
                    break;
                    
                case "FLAG_FOR_MANUAL_REVIEW":
                    flagForManualReview(event);
                    break;
                    
                case "ALLOW_WITH_MONITORING":
                    allowWithEnhancedMonitoring(event);
                    break;
                    
                case "ALLOW_TRANSACTION":
                    // No action needed, transaction proceeds normally
                    log.debug("Transaction {} allowed to proceed (low fraud risk)", event.getTransactionId());
                    break;
                    
                default:
                    log.warn("Unknown fraud action: {} for transaction: {}", action, event.getTransactionId());
                    flagForManualReview(event); // Default to manual review for unknown actions
                    break;
            }
            
            log.debug("Applied fraud action: {} for transaction: {}", action, event.getTransactionId());
            
        } catch (Exception e) {
            log.error("Failed to process fraud decision for transaction: {}", event.getTransactionId(), e);
            throw new RuntimeException("Fraud decision processing failed", e);
        }
    }

    private String determineFraudAction(FraudDetectionResultsEvent event) {
        double fraudScore = event.getFraudScore();
        String riskLevel = event.getRiskLevel();
        
        // Critical risk - block immediately
        if (fraudScore >= 0.9 || "CRITICAL".equals(riskLevel)) {
            return "BLOCK_TRANSACTION";
        }
        
        // High risk - require additional verification
        if (fraudScore >= 0.7 || "HIGH".equals(riskLevel)) {
            return "REQUIRE_ADDITIONAL_VERIFICATION";
        }
        
        // Medium risk - flag for manual review
        if (fraudScore >= 0.5 || "MEDIUM".equals(riskLevel)) {
            return "FLAG_FOR_MANUAL_REVIEW";
        }
        
        // Low-medium risk - allow with monitoring
        if (fraudScore >= 0.3 || "LOW_MEDIUM".equals(riskLevel)) {
            return "ALLOW_WITH_MONITORING";
        }
        
        // Low risk - allow transaction
        return "ALLOW_TRANSACTION";
    }

    private void blockTransaction(FraudDetectionResultsEvent event) {
        try {
            paymentProcessingService.blockTransaction(
                event.getTransactionId(),
                "FRAUD_DETECTED",
                String.format("High fraud score: %.3f (Risk: %s)", event.getFraudScore(), event.getRiskLevel()),
                event.getAnalyzedAt()
            );
            
            // Send immediate notification to customer and security team
            notificationService.sendFraudAlert(
                event.getCustomerId(),
                event.getTransactionId(),
                "TRANSACTION_BLOCKED",
                "Your transaction has been blocked due to security concerns. Please contact support."
            );
            
            log.warn("BLOCKED high-risk transaction: {} (Score: {:.3f})", 
                event.getTransactionId(), event.getFraudScore());
            
        } catch (Exception e) {
            log.error("Failed to block fraudulent transaction: {}", event.getTransactionId(), e);
            throw new RuntimeException("Transaction blocking failed", e);
        }
    }

    private void requireAdditionalVerification(FraudDetectionResultsEvent event) {
        try {
            transactionSecurityService.requireAdditionalVerification(
                event.getTransactionId(),
                event.getCustomerId(),
                "FRAUD_PREVENTION",
                event.getFraudIndicators()
            );
            
            // Notify customer of additional verification requirement
            notificationService.sendVerificationRequest(
                event.getCustomerId(),
                event.getTransactionId(),
                "Additional verification required for your transaction due to security protocols."
            );
            
            log.info("Required additional verification for transaction: {} (Score: {:.3f})", 
                event.getTransactionId(), event.getFraudScore());
            
        } catch (Exception e) {
            log.error("Failed to require additional verification for transaction: {}", event.getTransactionId(), e);
            throw new RuntimeException("Additional verification requirement failed", e);
        }
    }

    private void flagForManualReview(FraudDetectionResultsEvent event) {
        try {
            transactionSecurityService.flagForManualReview(
                event.getTransactionId(),
                event.getCustomerId(),
                event.getFraudScore(),
                event.getFraudIndicators(),
                "Medium fraud risk requires manual review"
            );
            
            // Notify security team
            notificationService.sendManualReviewAlert(
                event.getTransactionId(),
                event.getCustomerId(),
                event.getFraudScore(),
                event.getRiskLevel()
            );
            
            log.info("Flagged for manual review: {} (Score: {:.3f})", 
                event.getTransactionId(), event.getFraudScore());
            
        } catch (Exception e) {
            log.error("Failed to flag transaction for manual review: {}", event.getTransactionId(), e);
            throw new RuntimeException("Manual review flagging failed", e);
        }
    }

    private void allowWithEnhancedMonitoring(FraudDetectionResultsEvent event) {
        try {
            transactionSecurityService.enableEnhancedMonitoring(
                event.getTransactionId(),
                event.getCustomerId(),
                event.getFraudScore(),
                "Medium-low fraud risk - enhanced monitoring enabled"
            );
            
            log.debug("Enabled enhanced monitoring for transaction: {} (Score: {:.3f})", 
                event.getTransactionId(), event.getFraudScore());
            
        } catch (Exception e) {
            log.warn("Failed to enable enhanced monitoring for transaction: {}", event.getTransactionId(), e);
            // Don't throw exception for monitoring failures
        }
    }

    private void updateCustomerRiskProfile(FraudDetectionResultsEvent event) {
        try {
            transactionSecurityService.updateCustomerRiskProfile(
                event.getCustomerId(),
                event.getFraudScore(),
                event.getRiskLevel(),
                event.getFraudIndicators(),
                event.getAnalyzedAt()
            );
            
            log.debug("Updated customer risk profile for customer: {}", event.getCustomerId());
            
        } catch (Exception e) {
            log.warn("Failed to update customer risk profile for customer: {}", event.getCustomerId(), e);
            // Don't throw exception for risk profile update failures
        }
    }

    private void generateSecurityAlerts(FraudDetectionResultsEvent event) {
        try {
            if (event.getFraudScore() >= 0.7) {
                notificationService.sendSecurityAlert(
                    "HIGH_FRAUD_RISK",
                    event.getTransactionId(),
                    event.getCustomerId(),
                    String.format("High fraud score detected: %.3f", event.getFraudScore()),
                    event.getFraudIndicators()
                );
                
                log.debug("Generated security alert for high-risk transaction: {}", event.getTransactionId());
            }
            
        } catch (Exception e) {
            log.warn("Failed to generate security alert for transaction: {}", event.getTransactionId(), e);
            // Don't throw exception for alert generation failures
        }
    }

    private void provideFeedbackToMLModel(FraudDetectionResultsEvent event) {
        try {
            // This would typically be done after we know the actual outcome of the transaction
            // For now, just log that feedback should be provided
            log.debug("ML model feedback pending for transaction: {} (model version: {})", 
                event.getTransactionId(), event.getModelVersion());
            
        } catch (Exception e) {
            log.warn("Failed to provide ML model feedback for transaction: {}", event.getTransactionId(), e);
            // Don't throw exception for feedback failures
        }
    }

    private void generateComplianceReports(FraudDetectionResultsEvent event) {
        try {
            if (event.getFraudScore() >= 0.8) {
                auditService.generateSuspiciousActivityReport(
                    event.getTransactionId(),
                    event.getCustomerId(),
                    event.getFraudScore(),
                    event.getFraudIndicators(),
                    "High fraud score detected by ML model"
                );
                
                log.debug("Generated SAR for high-risk transaction: {}", event.getTransactionId());
            }
            
        } catch (Exception e) {
            log.warn("Failed to generate compliance report for transaction: {}", event.getTransactionId(), e);
            // Don't throw exception for compliance report failures
        }
    }

    private void auditFraudDetectionProcessing(FraudDetectionResultsEvent event) {
        try {
            auditService.auditFraudDetectionProcessing(
                event.getTransactionId(),
                event.getCustomerId(),
                event.getFraudScore(),
                event.getRiskLevel(),
                event.getFraudIndicators(),
                LocalDateTime.now()
            );
            
        } catch (Exception e) {
            log.warn("Failed to audit fraud detection processing for transaction: {}", event.getTransactionId(), e);
            // Don't throw exception for audit failures
        }
    }

    private void handleFraudProcessingError(FraudDetectionResultsEvent event, Exception error) {
        try {
            log.error("Fraud processing error - sending to DLQ. Transaction: {}, Error: {}", 
                event.getTransactionId(), error.getMessage());
            
            // Send to dead letter queue for manual investigation
            transactionSecurityService.sendFraudEventToDLQ(event, error.getMessage());
            
            // Alert security team
            notificationService.sendFraudProcessingAlert(
                event.getTransactionId(),
                event.getCustomerId(),
                error.getMessage()
            );
            
        } catch (Exception e) {
            log.error("Failed to handle fraud processing error", e);
        }
    }
}